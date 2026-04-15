package io.mec.edge.controller.tariff;

import java.time.LocalTime;

/**
 * Represents a single Time-of-Use tariff slot with a half-open time range
 * [from, to), an energy rate (₹/kWh), and a demand charge (₹/kVA).
 *
 * <p>
 * Midnight wrap-around is supported: if {@code from} is after {@code to}
 * (e.g. 22:00–06:00) the slot is considered active when the time is either
 * {@code >= from} or {@code < to}.
 */
public class TariffSlot {

	private final LocalTime from;
	private final LocalTime to;
	private final double rateKwh;
	private final double demandChargeKva;

	/**
	 * Constructs a new TariffSlot.
	 *
	 * @param from             start of the slot (inclusive)
	 * @param to               end of the slot (exclusive)
	 * @param rateKwh          energy rate in ₹/kWh
	 * @param demandChargeKva  demand charge in ₹/kVA
	 */
	public TariffSlot(LocalTime from, LocalTime to, double rateKwh, double demandChargeKva) {
		this.from = from;
		this.to = to;
		this.rateKwh = rateKwh;
		this.demandChargeKva = demandChargeKva;
	}

	/**
	 * Returns {@code true} if the given time falls within this slot's half-open
	 * range [from, to).
	 *
	 * <p>
	 * For normal (non-wrap) slots where {@code from < to}, a time {@code t} matches
	 * when {@code from <= t < to}. For wrap-around slots where {@code from >= to}
	 * (e.g. 22:00–06:00), a time matches when {@code t >= from} OR {@code t < to}.
	 *
	 * @param time the {@link LocalTime} to test
	 * @return {@code true} if {@code time} is within this slot
	 */
	public boolean contains(LocalTime time) {
		if (this.from.equals(this.to)) {
			// Zero-length or full-day slot — treat as always active
			return true;
		}
		if (this.from.isBefore(this.to)) {
			// Normal slot: from < to
			return !time.isBefore(this.from) && time.isBefore(this.to);
		}
		// Wrap-around slot: from > to (crosses midnight)
		return !time.isBefore(this.from) || time.isBefore(this.to);
	}

	// ── getters ───────────────────────────────────────────────────────────────

	public LocalTime getFrom() {
		return this.from;
	}

	public LocalTime getTo() {
		return this.to;
	}

	public double getRateKwh() {
		return this.rateKwh;
	}

	public double getDemandChargeKva() {
		return this.demandChargeKva;
	}

	@Override
	public String toString() {
		return "TariffSlot{from=" + this.from + ", to=" + this.to
				+ ", rate=" + this.rateKwh + "₹/kWh"
				+ ", demand=" + this.demandChargeKva + "₹/kVA}";
	}
}
