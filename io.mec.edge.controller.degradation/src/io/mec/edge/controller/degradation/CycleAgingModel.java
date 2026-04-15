package io.mec.edge.controller.degradation;

import java.util.Map;
import java.util.TreeMap;

/**
 * Cycle aging model using a piecewise-linear DoD → capacity-fade-per-cycle
 * lookup table.
 *
 * <p>Default LFP table:
 * <pre>
 *   DoD 20%  → 0.000002 (0.0002 %) per cycle
 *   DoD 50%  → 0.000006 (0.0006 %) per cycle
 *   DoD 80%  → 0.000015 (0.0015 %) per cycle
 *   DoD 100% → 0.000030 (0.0030 %) per cycle
 * </pre>
 *
 * <p>Equivalent full cycles (EFC) accumulate proportionally to DoD
 * (0.5 DoD = 0.5 EFC per cycle event).
 */
public class CycleAgingModel {

	/** DoD (0–1) → capacity fade per cycle (0–1), ascending by DoD. */
	private final TreeMap<Double, Double> dodFadeTable;

	/** Cumulative cycle capacity fade (0.0–1.0). */
	private double cumulativeFade = 0.0;

	/** Equivalent full cycles (DoD-proportional). */
	private double equivalentFullCycles = 0.0;

	/**
	 * Constructs a {@link CycleAgingModel} with the supplied lookup table.
	 *
	 * @param dodFadeTable map of DoD fraction (0–1) → fade fraction per cycle
	 */
	public CycleAgingModel(Map<Double, Double> dodFadeTable) {
		this.dodFadeTable = new TreeMap<>(dodFadeTable);
		this.validateTable();
	}

	/**
	 * Creates a {@link CycleAgingModel} with the default LFP fade table.
	 *
	 * @return a new {@link CycleAgingModel}
	 */
	public static CycleAgingModel lfpDefault() {
		return new CycleAgingModel(Map.of(
				0.20, 0.000002,
				0.50, 0.000006,
				0.80, 0.000015,
				1.00, 0.000030));
	}

	/**
	 * Records a completed charge/discharge cycle event.
	 *
	 * @param dod depth of discharge as a fraction (0.0–1.0)
	 */
	public void recordCycle(double dod) {
		if (dod <= 0.0) {
			return;
		}
		dod = Math.min(1.0, dod);
		var fadePerCycle = this.interpolateFade(dod);
		this.cumulativeFade = Math.min(1.0, this.cumulativeFade + fadePerCycle);
		this.equivalentFullCycles += dod;
	}

	/**
	 * Capacity fade per kWh dispatched at the given DoD and nameplate capacity.
	 *
	 * <p>Used for cost model: {@code cost = (replacement_INR / nameplate_kWh) × fadePerKwh}
	 *
	 * @param dod          depth of discharge fraction (0–1)
	 * @param nameplateKwh battery nameplate energy in kWh
	 * @return fade fraction per kWh dispatched
	 */
	public double fadePerKwhDispatched(double dod, double nameplateKwh) {
		if (dod <= 0.0 || nameplateKwh <= 0.0) {
			return 0.0;
		}
		var fadePerCycle = this.interpolateFade(Math.min(1.0, dod));
		var energyPerCycleKwh = nameplateKwh * dod;
		return fadePerCycle / energyPerCycleKwh;
	}

	/**
	 * Piecewise-linear interpolation through the DoD table.
	 *
	 * @param dod depth of discharge fraction (0–1)
	 * @return interpolated capacity fade per cycle
	 */
	public double interpolateFade(double dod) {
		var lower = this.dodFadeTable.floorKey(dod);
		var upper = this.dodFadeTable.ceilingKey(dod);

		if (lower == null && upper == null) {
			return 0.0;
		}
		if (lower == null) {
			return this.dodFadeTable.get(upper);
		}
		if (upper == null || lower.equals(upper)) {
			return this.dodFadeTable.get(lower);
		}
		// Linear interpolation
		var fadeLow = this.dodFadeTable.get(lower);
		var fadeHigh = this.dodFadeTable.get(upper);
		var fraction = (dod - lower) / (upper - lower);
		return fadeLow + fraction * (fadeHigh - fadeLow);
	}

	/**
	 * Returns cumulative cycle capacity fade (0.0–1.0).
	 *
	 * @return cumulative cycle fade
	 */
	public double getCumulativeFade() {
		return this.cumulativeFade;
	}

	/**
	 * Restores persisted state on bundle restart.
	 *
	 * @param fade fade value in [0.0, 1.0]
	 */
	public void setCumulativeFade(double fade) {
		this.cumulativeFade = Math.max(0.0, Math.min(1.0, fade));
	}

	/**
	 * Returns floor of equivalent full cycles accumulated.
	 *
	 * @return integer EFC
	 */
	public int getEquivalentFullCycles() {
		return (int) Math.floor(this.equivalentFullCycles);
	}

	/**
	 * Returns raw (fractional) equivalent full cycles.
	 *
	 * @return raw EFC
	 */
	public double getRawEquivalentFullCycles() {
		return this.equivalentFullCycles;
	}

	/**
	 * Restores persisted EFC state on bundle restart.
	 *
	 * @param efc EFC value
	 */
	public void setEquivalentFullCycles(double efc) {
		this.equivalentFullCycles = Math.max(0.0, efc);
	}

	private void validateTable() {
		if (this.dodFadeTable.isEmpty()) {
			throw new IllegalArgumentException("DoD fade table must not be empty");
		}
		for (var e : this.dodFadeTable.entrySet()) {
			if (e.getKey() < 0.0 || e.getKey() > 1.0) {
				throw new IllegalArgumentException("DoD key must be in [0,1], got: " + e.getKey());
			}
			if (e.getValue() < 0.0) {
				throw new IllegalArgumentException("Fade per cycle must be >= 0, got: " + e.getValue());
			}
		}
	}
}
