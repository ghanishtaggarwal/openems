package io.mec.edge.controller.degradation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.ComponentManager;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

/**
 * MEC Degradation Controller — OSGi component.
 *
 * <p>Computes real-time battery SoH and marginal degradation cost using:
 * <ol>
 * <li>Arrhenius calendar aging (temperature-dependent fade per day)
 * <li>Piecewise-linear cycle aging (fade per cycle as a function of DoD)
 * </ol>
 *
 * <p>All heavy computation runs on a background scheduler thread every
 * {@value #UPDATE_INTERVAL_MINUTES} minutes. {@link #run()} is a no-op —
 * the OSGi event thread is never blocked.
 */
@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Mec.Controller.Degradation", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class DegradationControllerImpl extends AbstractOpenemsComponent
		implements DegradationController, Controller, OpenemsComponent {

	private static final Logger LOG = LoggerFactory.getLogger(DegradationControllerImpl.class);

	/** Aging model update interval in minutes. */
	private static final long UPDATE_INTERVAL_MINUTES = 5;

	/** Fallback cell temperature when BMS reading is unavailable (°C). */
	private static final double FALLBACK_TEMPERATURE_CELSIUS = 25.0;

	/** SoH below which end-of-life alert is logged. */
	private static final double EOL_SOH_THRESHOLD = 0.80;

	@Reference
	private ConfigurationAdmin cm;

	@Reference
	private ComponentManager componentManager;

	@Reference
	private Battery battery;

	private Config config;
	private ArrheniusCalendarAging calendarAging;
	private CycleAgingModel cycleAging;

	private double nameplateKwh;
	private double replacementCostInr;
	private LocalDate commissionDate;

	private double lastSoc = -1.0;
	private final AtomicLong lastUpdateMs = new AtomicLong(System.currentTimeMillis());

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		var t = new Thread(r, "mec-degradation");
		t.setDaemon(true);
		return t;
	});

	public DegradationControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				DegradationController.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;
		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "battery", config.battery_id())) {
			return;
		}
		this.initialise(config);
		this.scheduler.scheduleAtFixedRate(this::runAgingUpdate, 0, UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES);
		LOG.info("[MEC Degradation] activated for battery: {}", config.battery_id());
	}

	@Override
	@Deactivate
	protected void deactivate() {
		this.scheduler.shutdown();
		super.deactivate();
	}

	/**
	 * Controller.run() is called every cycle by the OpenEMS scheduler.
	 * The aging computation runs on a separate background thread; this method
	 * is intentionally a no-op.
	 */
	@Override
	public void run() throws OpenemsNamedException {
		// Channels are updated by the background scheduler thread.
	}

	// ---- Initialisation -------------------------------------------------------

	private void initialise(Config config) {
		this.nameplateKwh = config.nameplateKwh();
		this.replacementCostInr = config.replacementCostInr();

		try {
			this.commissionDate = LocalDate.parse(config.commissionDate());
		} catch (Exception e) {
			LOG.warn("[MEC Degradation] Invalid commissionDate '{}', defaulting to today.", config.commissionDate());
			this.commissionDate = LocalDate.now();
		}

		this.calendarAging = new ArrheniusCalendarAging(config.arrheniusA(), config.arrheniusEaEv());
		this.calendarAging.setCumulativeFade(config.persistedCalendarFade());

		this.cycleAging = CycleAgingModel.lfpDefault();
		this.cycleAging.setEquivalentFullCycles(config.persistedEfc());

		this.lastUpdateMs.set(System.currentTimeMillis());
	}

	// ---- Background aging update ---------------------------------------------

	void runAgingUpdate() {
		if (!this.isEnabled()) {
			return;
		}
		try {
			var nowMs = System.currentTimeMillis();
			var elapsedDays = (nowMs - this.lastUpdateMs.getAndSet(nowMs)) / 86_400_000.0;

			// 1. Read BMS temperature
			var temperatureCelsius = this.readBatteryTemperature();

			// 2. Calendar aging
			this.calendarAging.accumulateFadeForPeriod(elapsedDays, temperatureCelsius);

			// 3. Cycle aging — approximate half-cycle from SoC delta
			var currentSoc = this.readBatterySoc();
			if (this.lastSoc >= 0 && currentSoc >= 0) {
				var socDelta = Math.abs(currentSoc - this.lastSoc) / 100.0;
				if (socDelta > 0.005) {
					this.cycleAging.recordCycle(socDelta);
				}
			}
			if (currentSoc >= 0) {
				this.lastSoc = currentSoc;
			}

			// 4. Combined SoH
			var calFade = this.calendarAging.getCumulativeFade();
			var cycFade = this.cycleAging.getCumulativeFade();
			var soh = (float) Math.max(0.0, 1.0 - calFade - cycFade);

			// 5. Marginal cost per kWh dispatched
			var currentDod = (currentSoc >= 0) ? (1.0 - currentSoc / 100.0) : 0.5;
			var fadePerKwh = this.cycleAging.fadePerKwhDispatched(Math.max(0.01, currentDod), this.nameplateKwh);
			var costPerKwh = (float) ((this.replacementCostInr / this.nameplateKwh) * fadePerKwh);

			// 6. Calendar age and remaining life
			var calendarAgeDays = (int) ChronoUnit.DAYS.between(this.commissionDate, LocalDate.now());
			var fadeBudget = Math.max(0.0, (1.0 - EOL_SOH_THRESHOLD) - calFade - cycFade);
			var totalFadeRatePerDay = this.calendarAging.fadeRatePerDay(temperatureCelsius) * 2.0;
			var remainingMonths = (totalFadeRatePerDay > 0 && fadeBudget > 0)
					? (int) Math.floor(fadeBudget / totalFadeRatePerDay / 30.0)
					: 0;

			// 7. Publish channels
			this._setStateOfHealth(soh);
			this._setCostPerKwhDispatched(costPerKwh);
			this.channel(DegradationController.ChannelId.CALENDAR_AGE_DAYS).setNextValue(calendarAgeDays);
			this.channel(DegradationController.ChannelId.CYCLE_COUNT).setNextValue(this.cycleAging.getEquivalentFullCycles());
			this.channel(DegradationController.ChannelId.ESTIMATED_REMAINING_LIFE_MONTHS).setNextValue(remainingMonths);

			if (soh < EOL_SOH_THRESHOLD) {
				LOG.warn("[MEC Degradation] SoH = {:.3f} below EOL threshold {:.2f} — initiate replacement planning.",
						soh, EOL_SOH_THRESHOLD);
			}

		} catch (Exception e) {
			LOG.error("[MEC Degradation] Error in aging update", e);
		}
	}

	// ---- BMS channel helpers -------------------------------------------------

	/** Returns SoC in percent (0–100), or -1 if unavailable. */
	private double readBatterySoc() {
		try {
			var soc = this.battery.getSoc().get();
			return (soc != null) ? soc.doubleValue() : -1.0;
		} catch (Exception e) {
			return -1.0;
		}
	}

	/** Returns average cell temperature in °C, or FALLBACK if unavailable. */
	private double readBatteryTemperature() {
		try {
			var minDeciC = this.battery.getMinCellTemperature().get();
			var maxDeciC = this.battery.getMaxCellTemperature().get();
			if (minDeciC != null && maxDeciC != null) {
				return ((minDeciC + maxDeciC) / 2.0) / 10.0;
			}
		} catch (Exception e) {
			// fall through
		}
		return FALLBACK_TEMPERATURE_CELSIUS;
	}

	// ---- Visible for testing -------------------------------------------------

	ArrheniusCalendarAging getCalendarAging() {
		return this.calendarAging;
	}

	CycleAgingModel getCycleAging() {
		return this.cycleAging;
	}
}
