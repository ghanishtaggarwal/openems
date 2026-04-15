package io.mec.edge.controller.degradation;

/**
 * Arrhenius-based calendar aging model for LFP batteries.
 *
 * <pre>
 *   dCapacity/dt = A · exp( -Ea / (R · T) )
 *
 *   R  = 8.314 J mol⁻¹ K⁻¹   universal gas constant
 *   Ea = activation energy in J/mol  (config: eV × Faraday = 96 485 C/mol)
 *   A  = pre-exponential factor (capacity fade per day at reference conditions)
 *   T  = cell temperature in Kelvin
 * </pre>
 *
 * <p>The model is integrated in fractional-day increments via
 * {@link #accumulateFadeForPeriod(double, double)}.
 */
public class ArrheniusCalendarAging {

	/** Faraday constant — converts eV → J/mol. */
	private static final double FARADAY = 96_485.0;

	/** Universal gas constant J/(mol·K). */
	private static final double R = 8.314;

	private final double preExponentialPerDay;    // A
	private final double activationEnergyJPerMol; // Ea in J/mol

	/** Cumulative calendar capacity fade (0.0–1.0). */
	private double cumulativeFade = 0.0;

	/**
	 * Constructs an {@link ArrheniusCalendarAging} model.
	 *
	 * @param preExponentialPerDay frequency factor A (fade/day at reference)
	 * @param activationEnergyEv   activation energy Ea in eV (e.g. 0.60 for LFP)
	 */
	public ArrheniusCalendarAging(double preExponentialPerDay, double activationEnergyEv) {
		this.preExponentialPerDay = preExponentialPerDay;
		this.activationEnergyJPerMol = activationEnergyEv * FARADAY;
	}

	/**
	 * Instantaneous capacity fade rate (fraction per day) at given temperature.
	 *
	 * @param temperatureCelsius cell temperature in °C
	 * @return fade per day (e.g. 1.4e-3 means 0.14 %/day)
	 */
	public double fadeRatePerDay(double temperatureCelsius) {
		var tempKelvin = temperatureCelsius + 273.15;
		return this.preExponentialPerDay * Math.exp(-this.activationEnergyJPerMol / (R * tempKelvin));
	}

	/**
	 * Accumulate fade over {@code elapsedDays} at constant temperature.
	 *
	 * @param elapsedDays        fractional days elapsed since last call
	 * @param temperatureCelsius cell temperature during this period
	 */
	public void accumulateFadeForPeriod(double elapsedDays, double temperatureCelsius) {
		var fade = this.fadeRatePerDay(temperatureCelsius) * elapsedDays;
		this.cumulativeFade = Math.min(1.0, this.cumulativeFade + fade);
	}

	/**
	 * Returns cumulative calendar capacity fade (0.0–1.0).
	 *
	 * @return cumulative fade
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
	 * Days until cumulative fade reaches {@code targetFade} at constant temperature.
	 *
	 * @param targetFade         absolute target fade level (e.g. 0.20)
	 * @param temperatureCelsius assumed future temperature
	 * @return days until target, or 0.0 if already past target
	 */
	public double daysUntilFade(double targetFade, double temperatureCelsius) {
		var remaining = targetFade - this.cumulativeFade;
		if (remaining <= 0) {
			return 0.0;
		}
		var ratePerDay = this.fadeRatePerDay(temperatureCelsius);
		return (ratePerDay > 0) ? remaining / ratePerDay : Double.MAX_VALUE;
	}
}
