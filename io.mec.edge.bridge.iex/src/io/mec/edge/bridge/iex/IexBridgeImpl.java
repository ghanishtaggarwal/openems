package io.mec.edge.bridge.iex;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.utils.ThreadPoolUtils;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

/**
 * MEC IEX RTM Bridge — OSGi implementation.
 *
 * <p>A background {@link ScheduledExecutorService} polls the IEX API every
 * {@value Config#fetchIntervalMinutes} minutes. The main OSGi event thread
 * is never blocked.
 *
 * <p>Fetch cascade (tried in order):
 * <ol>
 * <li>Live API call (requires {@code IEX_USER} + {@code IEX_PASS} env vars)
 * <li>Local JSON cache if ≤ {@link Config#cacheMaxAgeMinutes()} old
 * <li>Configured fallback price ({@link Config#fallbackPriceInrKwh()})
 * </ol>
 *
 * <p>The component also implements {@link TimeOfUseTariff} so the OpenEMS
 * Energy Optimizer can use live IEX prices for arbitrage scheduling.
 */
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Mec.Bridge.Iex", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class IexBridgeImpl extends AbstractOpenemsComponent
		implements IexBridge, TimeOfUseTariff, OpenemsComponent {

	private static final Logger LOG = LoggerFactory.getLogger(IexBridgeImpl.class);
	private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
		var t = new Thread(r, "mec-iex-fetcher");
		t.setDaemon(true);
		return t;
	});

	private final AtomicReference<TimeOfUsePrices> prices = new AtomicReference<>(TimeOfUsePrices.EMPTY_PRICES);

	private Config config;
	private IexApiClient apiClient;
	private PriceCache cache;

	public IexBridgeImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				IexBridge.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		this.initialise(config);
		if (config.enabled()) {
			this.executor.scheduleAtFixedRate(this::fetchAndPublish,
					0, config.fetchIntervalMinutes(), TimeUnit.MINUTES);
		}
		LOG.info("[MEC IEX] Bridge activated. Poll interval: {} min", config.fetchIntervalMinutes());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		ThreadPoolUtils.shutdownAndAwaitTermination(this.executor, 5);
		super.deactivate();
	}

	// ---- TimeOfUseTariff -----------------------------------------------------

	@Override
	public TimeOfUsePrices getPrices() {
		return this.prices.get();
	}

	// ---- Initialisation -------------------------------------------------------

	private void initialise(Config config) {
		this.cache = new PriceCache(config.cacheFilePath());

		var user = System.getenv("IEX_USER");
		var pass = System.getenv("IEX_PASS");
		if (user != null && !user.isBlank() && pass != null && !pass.isBlank()) {
			this.apiClient = new IexApiClient(user, pass);
		} else {
			LOG.warn("[MEC IEX] IEX_USER or IEX_PASS not set — live API calls disabled. Will use cache/fallback.");
			this.apiClient = null;
		}
	}

	// ---- Background fetch ----------------------------------------------------

	void fetchAndPublish() {
		if (!this.isEnabled()) {
			return;
		}
		var result = this.doFetch();
		this.publishChannels(result);
		this.updateTimeOfUsePrices(result.price());
	}

	private FetchResult doFetch() {
		// 1. Live API
		if (this.apiClient != null) {
			try {
				var price = this.apiClient.fetchRtmPriceInrKwh();
				this.cache.write(price, System.currentTimeMillis());
				LOG.debug("[MEC IEX] Live fetch: ₹{}/kWh", price);
				return new FetchResult(price, System.currentTimeMillis(), ApiFetchStatus.SUCCESS);
			} catch (IexApiClient.IexApiException e) {
				LOG.warn("[MEC IEX] Live fetch failed: {}", e.getMessage());
				this.apiClient.resetSession();
			}
		}

		// 2. Cache
		var maxAgeMs = (long) this.config.cacheMaxAgeMinutes() * 60_000L;
		var entry = this.cache.read(maxAgeMs);
		if (entry != null) {
			LOG.info("[MEC IEX] Using cached price: ₹{}/kWh", entry.priceInrKwh());
			return new FetchResult(entry.priceInrKwh(), entry.timestampMs(), ApiFetchStatus.CACHED);
		}

		// 3. Fallback
		var fallback = this.config.fallbackPriceInrKwh();
		LOG.warn("[MEC IEX] Cache expired or unavailable — using fallback: ₹{}/kWh", fallback);
		return new FetchResult(fallback, System.currentTimeMillis(), ApiFetchStatus.FALLBACK);
	}

	private void publishChannels(FetchResult result) {
		this.channel(IexBridge.ChannelId.CURRENT_RTM_PRICE).setNextValue((float) result.price());
		this.channel(IexBridge.ChannelId.LAST_FETCH_TIMESTAMP).setNextValue(result.timestampMs());
		this.channel(IexBridge.ChannelId.API_FETCH_STATUS).setNextValue(result.status().name());
	}

	/**
	 * Builds a 96-slot {@link TimeOfUsePrices} from a single flat price value.
	 *
	 * <p>In a future v0.2, this should be replaced with per-quarter IEX RTM data
	 * for the next 24 hours (IEX publishes day-ahead market data).
	 */
	private void updateTimeOfUsePrices(double priceInrKwh) {
		// TimeOfUsePrices uses ₹/MWh internally
		var pricePerMwh = priceInrKwh * 1000.0;
		var now = ZonedDateTime.now(IST).toInstant();

		// Fill 96 quarters with the flat current price
		var values = new Double[96];
		for (var i = 0; i < 96; i++) {
			values[i] = pricePerMwh;
		}
		this.prices.set(TimeOfUsePrices.from(now, values));
	}

	// ---- Internal records ----------------------------------------------------

	private record FetchResult(double price, long timestampMs, ApiFetchStatus status) {
	}

	// ---- Visible for testing -------------------------------------------------

	void setApiClient(IexApiClient client) {
		this.apiClient = client;
	}

	void setCache(PriceCache cache) {
		this.cache = cache;
	}
}
