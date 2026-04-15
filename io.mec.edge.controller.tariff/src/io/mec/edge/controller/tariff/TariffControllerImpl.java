package io.mec.edge.controller.tariff;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.utils.ThreadPoolUtils;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.meter.api.ElectricityMeter;
import io.openems.edge.timeofusetariff.api.TimeOfUsePrices;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Mec.Controller.Tariff", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class TariffControllerImpl extends AbstractOpenemsComponent
		implements TariffController, TimeOfUseTariff, OpenemsComponent {

	/** Number of 15-minute quarters in 24 hours. */
	private static final int QUARTERS_PER_DAY = 96;

	/** Window for peak-demand rolling average: 15 minutes = 15 samples at 1/min. */
	private static final int PEAK_WINDOW_SIZE = 15;

	private final Logger log = LoggerFactory.getLogger(TariffControllerImpl.class);
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
	private final AtomicReference<TimeOfUsePrices> prices =
			new AtomicReference<>(TimeOfUsePrices.EMPTY_PRICES);

	/** Sliding window of apparent power samples (kVA) for peak-demand tracking. */
	private final Deque<Double> peakWindow = new ArrayDeque<>(PEAK_WINDOW_SIZE + 1);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(//
			policy = ReferencePolicy.DYNAMIC, //
			policyOption = org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.OPTIONAL //
	)
	private volatile ElectricityMeter meter;

	private Config config;
	private TariffConfig tariffConfig;

	public TariffControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				TariffController.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "meter",
				config.meter_id())) {
			return;
		}

		// Load tariff configuration
		try {
			this.tariffConfig = TariffConfigLoader.load(config.tariffConfigDir(), config.state());
			this.logInfo(this.log, "Loaded tariff config for DISCOM: "
					+ this.tariffConfig.getDiscom());
		} catch (IOException e) {
			this.logError(this.log, "Failed to load tariff config for state '"
					+ config.state() + "': " + e.getMessage());
			return;
		}

		if (config.enabled()) {
			// Schedule first run immediately, then every minute
			this.executor.scheduleAtFixedRate(this.task, 0, 1, TimeUnit.MINUTES);
		}
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
		ThreadPoolUtils.shutdownAndAwaitTermination(this.executor, 0);
	}

	/** Periodic task — runs every minute to refresh tariff prices and peak demand. */
	protected final Runnable task = () -> {
		if (this.tariffConfig == null) {
			return;
		}

		try {
			LocalTime now = LocalTime.now(ZoneId.systemDefault());

			// ── 1. Build 96 quarterly price slots ─────────────────────────────
			Double[] quarterlyPrices = new Double[QUARTERS_PER_DAY];
			for (int q = 0; q < QUARTERS_PER_DAY; q++) {
				LocalTime slotStart = LocalTime.ofSecondOfDay((long) q * 15 * 60);
				// Convert ₹/kWh → ₹/MWh (TimeOfUsePrices uses per-MWh units)
				quarterlyPrices[q] = this.tariffConfig.rateAt(slotStart) * 1_000.0;
			}

			// ── 2. Update prices AtomicReference ──────────────────────────────
			this.prices.set(TimeOfUsePrices.from(Instant.now(), quarterlyPrices));

			// ── 3. Update CURRENT_TARIFF_RATE channel ─────────────────────────
			double currentRate = this.tariffConfig.rateAt(now);
			this.channel(TariffController.ChannelId.CURRENT_TARIFF_RATE)
					.setNextValue((float) currentRate);

			// ── 4. Update rolling 15-min peak kVA ────────────────────────────
			updatePeakDemand();

		} catch (Exception e) {
			this.logError(this.log, "Error in tariff task: " + e.getMessage());
		}
	};

	/**
	 * Samples apparent power from the meter (if available) and maintains a
	 * sliding-window peak kVA value.
	 */
	private void updatePeakDemand() {
		ElectricityMeter m = this.meter;
		if (m == null) {
			return;
		}

		// Apparent power = sqrt(P² + Q²) in W → convert to kVA
		Integer activePowerW = m.getActivePower().get();
		Integer reactivePowerVar = m.getReactivePower().get();
		if (activePowerW == null || reactivePowerVar == null) {
			return;
		}

		double apparentKva = Math.sqrt(
				Math.pow(activePowerW / 1_000.0, 2) + Math.pow(reactivePowerVar / 1_000.0, 2));

		this.peakWindow.addLast(apparentKva);
		while (this.peakWindow.size() > PEAK_WINDOW_SIZE) {
			this.peakWindow.removeFirst();
		}

		double peak = this.peakWindow.stream()
				.mapToDouble(Double::doubleValue)
				.max()
				.orElse(0.0);

		this.channel(TariffController.ChannelId.PEAK_DEMAND_KVA).setNextValue((float) peak);
	}

	// ── TimeOfUseTariff ───────────────────────────────────────────────────────

	@Override
	public TimeOfUsePrices getPrices() {
		return this.prices.get();
	}
}
