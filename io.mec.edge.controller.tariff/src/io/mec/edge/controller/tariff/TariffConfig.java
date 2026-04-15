package io.mec.edge.controller.tariff;

import java.time.LocalTime;
import java.util.List;

/**
 * Holds the full tariff configuration for a single DISCOM, including all
 * Time-of-Use slots, the fuel-adjustment component, and the power-factor
 * penalty threshold.
 */
public class TariffConfig {

	private final String discom;
	private final List<TariffSlot> touSlots;
	private final double fuelAdjComponent;
	private final double powerFactorPenaltyThreshold;

	/**
	 * Constructs a new TariffConfig.
	 *
	 * @param discom                      distribution company name
	 * @param touSlots                    time-of-use tariff slots; order is
	 *                                    preserved for fallback
	 * @param fuelAdjComponent            fuel-adjustment surcharge in ₹/kWh added
	 *                                    to every slot rate
	 * @param powerFactorPenaltyThreshold minimum power factor below which a penalty
	 *                                    is applied
	 */
	public TariffConfig(String discom, List<TariffSlot> touSlots,
			double fuelAdjComponent, double powerFactorPenaltyThreshold) {
		this.discom = discom;
		this.touSlots = List.copyOf(touSlots);
		this.fuelAdjComponent = fuelAdjComponent;
		this.powerFactorPenaltyThreshold = powerFactorPenaltyThreshold;
	}

	// ── query methods ─────────────────────────────────────────────────────────

	/**
	 * Returns the effective tariff rate (slot rate + fuel-adjustment component) for
	 * the given time.
	 *
	 * <p>
	 * Falls back to the last slot's rate if no slot matches.
	 *
	 * @param time the {@link LocalTime} to look up
	 * @return effective rate in ₹/kWh
	 */
	public double rateAt(LocalTime time) {
		for (TariffSlot slot : this.touSlots) {
			if (slot.contains(time)) {
				return slot.getRateKwh() + this.fuelAdjComponent;
			}
		}
		// Fallback: use the last slot
		if (!this.touSlots.isEmpty()) {
			return this.touSlots.get(this.touSlots.size() - 1).getRateKwh() + this.fuelAdjComponent;
		}
		return this.fuelAdjComponent;
	}

	/**
	 * Returns the demand charge for the given time.
	 *
	 * <p>
	 * Falls back to the last slot's demand charge if no slot matches.
	 *
	 * @param time the {@link LocalTime} to look up
	 * @return demand charge in ₹/kVA
	 */
	public double demandChargeAt(LocalTime time) {
		for (TariffSlot slot : this.touSlots) {
			if (slot.contains(time)) {
				return slot.getDemandChargeKva();
			}
		}
		// Fallback: use the last slot
		if (!this.touSlots.isEmpty()) {
			return this.touSlots.get(this.touSlots.size() - 1).getDemandChargeKva();
		}
		return 0.0;
	}

	// ── getters ───────────────────────────────────────────────────────────────

	public String getDiscom() {
		return this.discom;
	}

	public List<TariffSlot> getTouSlots() {
		return this.touSlots;
	}

	public double getFuelAdjComponent() {
		return this.fuelAdjComponent;
	}

	public double getPowerFactorPenaltyThreshold() {
		return this.powerFactorPenaltyThreshold;
	}

	@Override
	public String toString() {
		return "TariffConfig{discom='" + this.discom + "', slots=" + this.touSlots.size()
				+ ", fac=" + this.fuelAdjComponent + "}";
	}
}
