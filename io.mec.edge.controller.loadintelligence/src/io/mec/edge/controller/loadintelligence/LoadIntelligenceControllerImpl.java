package io.mec.edge.controller.loadintelligence;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

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

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.edge.battery.api.Battery;
import io.openems.edge.common.component.AbstractOpenemsComponent;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

@Designate(ocd = Config.class, factory = true)
@Component(//
		name = "Mec.Controller.LoadIntelligence", //
		immediate = true, //
		configurationPolicy = ConfigurationPolicy.REQUIRE //
)
public class LoadIntelligenceControllerImpl extends AbstractOpenemsComponent
		implements LoadIntelligenceController, Controller, OpenemsComponent {

	private final Logger log = LoggerFactory.getLogger(LoadIntelligenceControllerImpl.class);

	@Reference
	private ConfigurationAdmin cm;

	@Reference(//
			policy = ReferencePolicy.DYNAMIC, //
			policyOption = org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY, //
			cardinality = ReferenceCardinality.OPTIONAL //
	)
	private volatile Battery battery;

	private Config config;
	private List<Circuit> circuits = Collections.emptyList();
	private SheddingSequencer sequencer;

	/** Timestamp of the last {@link #run()} call; used for elapsed-time calculation. */
	private long lastRunMs = 0;

	public LoadIntelligenceControllerImpl() {
		super(//
				OpenemsComponent.ChannelId.values(), //
				Controller.ChannelId.values(), //
				LoadIntelligenceController.ChannelId.values() //
		);
	}

	@Activate
	private void activate(ComponentContext context, Config config) {
		super.activate(context, config.id(), config.alias(), config.enabled());
		this.config = config;

		if (OpenemsComponent.updateReferenceFilter(this.cm, this.servicePid(), "battery",
				config.battery_id())) {
			return;
		}

		// Load circuit configuration from YAML
		String yamlPath = Paths.get(config.loadConfigDir(), "loads.yaml").toString();
		try {
			this.circuits = LoadConfigLoader.load(yamlPath);
			this.logInfo(this.log, "Loaded " + this.circuits.size() + " circuit(s) from " + yamlPath);
		} catch (IOException e) {
			this.logWarn(this.log, "Could not load circuit config from '" + yamlPath
					+ "': " + e.getMessage() + ". Starting with empty circuit list.");
			this.circuits = Collections.emptyList();
		}

		// Create the shedding sequencer
		long interRestoreDelayMs = (long) config.interRestoreDelaySeconds() * 1_000L;
		this.sequencer = new SheddingSequencer(
				config.shedThresholdSoc(),
				config.restoreThresholdSoc(),
				interRestoreDelayMs,
				config.maxSiteInrushAmps());

		this.lastRunMs = System.currentTimeMillis();
	}

	@Override
	@Deactivate
	protected void deactivate() {
		super.deactivate();
	}

	@Override
	public void run() throws OpenemsNamedException {
		long nowMs = System.currentTimeMillis();
		long elapsedMs = this.lastRunMs == 0 ? 0 : nowMs - this.lastRunMs;
		this.lastRunMs = nowMs;

		// ── 1. Read SoC from battery ───────────────────────────────────────────
		Battery bat = this.battery;
		if (bat == null) {
			this.logWarn(this.log, "Battery reference not available – skipping run.");
			return;
		}

		Integer socPercent = bat.getSoc().get();
		if (socPercent == null) {
			this.logWarn(this.log, "Battery SoC is null – skipping run.");
			return;
		}
		double socFraction = socPercent / 100.0;

		// ── 2. Update measurements for each circuit (CT stub) ─────────────────
		double voltageV = this.config.nominalVoltageV();
		for (Circuit c : this.circuits) {
			double currentAmps = readCtRegister(c.getCtRegister());
			c.updateMeasurement(currentAmps, voltageV, elapsedMs);
		}

		// ── 3. Run shedding/restore logic ─────────────────────────────────────
		this.sequencer.evaluate(socFraction, this.circuits);

		// ── 4. Aggregate and publish channels ─────────────────────────────────
		double criticalKw = 0.0;
		double nonCriticalKw = 0.0;
		double availabilitySum = 0.0;
		int nonCriticalCount = 0;

		for (Circuit c : this.circuits) {
			if (c.getClassification() == CircuitClassification.CRITICAL) {
				criticalKw += c.getPowerKw();
			} else {
				nonCriticalKw += c.getPowerKw();
				availabilitySum += c.getAvailabilityPercent();
				nonCriticalCount++;
			}
		}

		double siteAvailability = nonCriticalCount > 0
				? availabilitySum / nonCriticalCount
				: 100.0;

		this.channel(LoadIntelligenceController.ChannelId.CRITICAL_LOAD_KW)
				.setNextValue((float) criticalKw);
		this.channel(LoadIntelligenceController.ChannelId.NON_CRITICAL_LOAD_KW)
				.setNextValue((float) nonCriticalKw);
		this.channel(LoadIntelligenceController.ChannelId.ACTIVE_SHED_COUNT)
				.setNextValue(this.sequencer.activeShedCount(this.circuits));
		this.channel(LoadIntelligenceController.ChannelId.SITE_AVAILABILITY_PERCENT)
				.setNextValue((float) siteAvailability);
	}

	/**
	 * Reads a CT current value from the given Modbus register.
	 *
	 * <p>
	 * TODO: inject a {@code ModbusBridge} and perform a real register read.
	 * Returns 0.0 until the bridge integration is implemented.
	 *
	 * @param register Modbus register address
	 * @return measured current in amperes (stub: always 0.0)
	 */
	private double readCtRegister(int register) {
		// TODO: implement Modbus bridge read
		return 0.0;
	}

	// ── package-private accessors for testing ─────────────────────────────────

	/**
	 * Replaces the circuit list — used by unit tests to inject test fixtures
	 * without a real YAML file.
	 *
	 * @param circuits the circuit list to use
	 */
	void setCircuits(List<Circuit> circuits) {
		this.circuits = circuits;
	}

	/**
	 * Returns the {@link SheddingSequencer} instance — used by unit tests to
	 * inspect or manipulate sequencer state.
	 *
	 * @return the sequencer
	 */
	SheddingSequencer getSequencer() {
		return this.sequencer;
	}
}
