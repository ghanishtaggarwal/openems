package io.mec.edge.controller.loadintelligence;

/**
 * Represents a single metered electrical circuit tracked by a CT sensor.
 *
 * <p>
 * Immutable identity fields (id, name, ctRegister, priority, classification,
 * maxInrushFactor) are set at construction time. Mutable measurement state
 * (currentAmps, powerKw, energyKwh, connected) is updated every control cycle
 * via {@link #updateMeasurement(double, double, long)}.
 */
public class Circuit {

	// ── identity ──────────────────────────────────────────────────────────────
	private final String id;
	private final String name;
	private final int ctRegister;
	private final int priority;
	private final CircuitClassification classification;
	private final double maxInrushFactor;

	// ── mutable state ─────────────────────────────────────────────────────────
	private boolean connected = true;
	private double currentAmps;
	private double powerKw;
	private double energyKwh;
	private long availableMs;
	private long windowStartMs;

	/**
	 * Constructs a new Circuit.
	 *
	 * @param id               unique circuit identifier
	 * @param name             human-readable label
	 * @param ctRegister       Modbus register address of the CT reading
	 * @param priority         shed/restore ordering (ascending = shed first)
	 * @param classification   {@link CircuitClassification#CRITICAL} or
	 *                         {@link CircuitClassification#NON_CRITICAL}
	 * @param maxInrushFactor  multiplier applied to steady-state amps when
	 *                         estimating reconnection inrush (e.g. 2.5)
	 */
	public Circuit(String id, String name, int ctRegister, int priority,
			CircuitClassification classification, double maxInrushFactor) {
		this.id = id;
		this.name = name;
		this.ctRegister = ctRegister;
		this.priority = priority;
		this.classification = classification;
		this.maxInrushFactor = maxInrushFactor;
		this.windowStartMs = System.currentTimeMillis();
	}

	// ── measurement update ────────────────────────────────────────────────────

	/**
	 * Updates electrical measurements for one control cycle.
	 *
	 * @param currentAmps  CT-measured RMS current in amperes
	 * @param voltageV     nominal phase voltage in volts
	 * @param elapsedMs    milliseconds elapsed since the previous update
	 */
	public void updateMeasurement(double currentAmps, double voltageV, long elapsedMs) {
		this.currentAmps = currentAmps;
		this.powerKw = currentAmps * voltageV / 1_000.0;
		this.energyKwh += this.powerKw * (elapsedMs / 3_600_000.0);
		if (this.connected) {
			this.availableMs += elapsedMs;
		}
	}

	// ── availability tracking ─────────────────────────────────────────────────

	/**
	 * Returns the percentage of time this circuit has been connected since the
	 * start of the current availability window, clamped to [0, 100].
	 *
	 * @return availability percentage in the range [0.0, 100.0]
	 */
	public double getAvailabilityPercent() {
		long windowDurationMs = System.currentTimeMillis() - this.windowStartMs;
		if (windowDurationMs <= 0) {
			return this.connected ? 100.0 : 0.0;
		}
		double raw = this.availableMs * 100.0 / windowDurationMs;
		return Math.max(0.0, Math.min(100.0, raw));
	}

	/**
	 * Resets the availability tracking window to the current time.
	 */
	public void resetAvailabilityWindow() {
		this.windowStartMs = System.currentTimeMillis();
		this.availableMs = 0;
	}

	// ── getters ───────────────────────────────────────────────────────────────

	public String getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public int getCtRegister() {
		return this.ctRegister;
	}

	public int getPriority() {
		return this.priority;
	}

	public CircuitClassification getClassification() {
		return this.classification;
	}

	public double getMaxInrushFactor() {
		return this.maxInrushFactor;
	}

	public boolean isConnected() {
		return this.connected;
	}

	public double getCurrentAmps() {
		return this.currentAmps;
	}

	public double getPowerKw() {
		return this.powerKw;
	}

	public double getEnergyKwh() {
		return this.energyKwh;
	}

	public long getAvailableMs() {
		return this.availableMs;
	}

	public long getWindowStartMs() {
		return this.windowStartMs;
	}

	// ── setters ───────────────────────────────────────────────────────────────

	public void setConnected(boolean connected) {
		this.connected = connected;
	}

	/** Package-private setter for testing. */
	void setAvailableMs(long ms) {
		this.availableMs = ms;
	}

	/** Package-private setter for testing. */
	void setWindowStartMs(long ms) {
		this.windowStartMs = ms;
	}

	@Override
	public String toString() {
		return "Circuit{id='" + this.id + "', priority=" + this.priority
				+ ", classification=" + this.classification
				+ ", connected=" + this.connected
				+ ", currentAmps=" + this.currentAmps + "A"
				+ ", powerKw=" + this.powerKw + "kW}";
	}
}
