package io.mec.edge.controller.degradation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MEC Degradation Controller pure logic classes.
 *
 * <p>Tests operate on {@link ArrheniusCalendarAging} and {@link CycleAgingModel}
 * without OSGi/OpenEMS framework dependencies.
 */
public class DegradationControllerTest {

	private static final double NAMEPLATE_KWH = 200.0;
	private static final double REPLACEMENT_COST_INR = 7_000_000.0;

	private ArrheniusCalendarAging calendarAging;
	private CycleAgingModel cycleAging;

	@BeforeEach
	public void setup() {
		// LFP defaults: A = 1.4e-3 / day, Ea = 0.60 eV
		this.calendarAging = new ArrheniusCalendarAging(1.4e-3, 0.60);
		this.cycleAging = CycleAgingModel.lfpDefault();
	}

	// ---- 1. New battery -------------------------------------------------------

	@Test
	@DisplayName("New battery: SoH = 1.0, zero fade, zero cycles")
	public void testNewBattery_sohIsOne() {
		var calFade = this.calendarAging.getCumulativeFade();
		var cycFade = this.cycleAging.getCumulativeFade();
		var soh = 1.0 - calFade - cycFade;

		assertEquals(0.0, calFade, 1e-10, "Calendar fade must be 0.0 on a fresh model");
		assertEquals(0.0, cycFade, 1e-10, "Cycle fade must be 0.0 on a fresh model");
		assertEquals(1.0, soh, 1e-10, "SoH must be 1.0 for a new battery");
		assertEquals(0, this.cycleAging.getEquivalentFullCycles());
	}

	// ---- 2. 80% SoH replacement threshold ------------------------------------

	@Test
	@DisplayName("80% SoH: combined fade of 0.20 triggers replacement alert")
	public void testSoh80_replacementThreshold() {
		this.calendarAging.setCumulativeFade(0.10);
		this.cycleAging.setCumulativeFade(0.10);

		var soh = 1.0 - this.calendarAging.getCumulativeFade() - this.cycleAging.getCumulativeFade();
		assertEquals(0.80, soh, 1e-6, "SoH at combined fade=0.20 must be 0.80");
		assertTrue(soh <= 0.80, "SoH at EOL threshold must trigger replacement alert");
	}

	// ---- 3. High-temperature accelerated aging --------------------------------

	@Test
	@DisplayName("Calendar aging: 45 °C fade rate > 25 °C fade rate")
	public void testHighTemperature_acceleratesAging() {
		var rateAt25 = this.calendarAging.fadeRatePerDay(25.0);
		var rateAt45 = this.calendarAging.fadeRatePerDay(45.0);

		assertTrue(rateAt45 > rateAt25,
				"45 °C fade rate (%.4e) must exceed 25 °C rate (%.4e)".formatted(rateAt45, rateAt25));
		assertTrue((rateAt45 / rateAt25) > 1.5,
				"Acceleration ratio over 20 °C must exceed 1.5×");
	}

	@Test
	@DisplayName("Calendar aging: rate at any temperature is positive and finite")
	public void testFadeRate_isPositiveAndFinite() {
		for (var tempC : new double[] { 0.0, 25.0, 40.0, 55.0 }) {
			var rate = this.calendarAging.fadeRatePerDay(tempC);
			assertTrue(rate > 0.0, "Fade rate at %s°C must be positive".formatted(tempC));
			assertFalse(Double.isInfinite(rate), "Fade rate at %s°C must not be infinite".formatted(tempC));
			assertFalse(Double.isNaN(rate), "Fade rate at %s°C must not be NaN".formatted(tempC));
		}
	}

	// ---- 4. Deep DoD cycle scenario ------------------------------------------

	@Test
	@DisplayName("DoD table: 100% DoD fade > 20% DoD fade")
	public void testDodTable_deepCycleMoreFade() {
		var fade20 = this.cycleAging.interpolateFade(0.20);
		var fade100 = this.cycleAging.interpolateFade(1.00);
		assertTrue(fade100 > fade20,
				"100%% DoD fade (%.6f) must exceed 20%% DoD fade (%.6f)".formatted(fade100, fade20));
	}

	@Test
	@DisplayName("DoD table: exact table entries")
	public void testDodTable_exactEntries() {
		assertEquals(0.000002, this.cycleAging.interpolateFade(0.20), 1e-9, "20% DoD");
		assertEquals(0.000006, this.cycleAging.interpolateFade(0.50), 1e-9, "50% DoD");
		assertEquals(0.000015, this.cycleAging.interpolateFade(0.80), 1e-9, "80% DoD");
		assertEquals(0.000030, this.cycleAging.interpolateFade(1.00), 1e-9, "100% DoD");
	}

	@Test
	@DisplayName("Piecewise interpolation: 65% DoD is between 50% and 80% table entries")
	public void testInterpolation_65percent() {
		var fade50 = this.cycleAging.interpolateFade(0.50);
		var fade80 = this.cycleAging.interpolateFade(0.80);
		var fade65 = this.cycleAging.interpolateFade(0.65);
		assertTrue(fade65 > fade50 && fade65 < fade80,
				"65%% DoD fade must lie between 50%% and 80%% entries");
	}

	@Test
	@DisplayName("3333 full cycles at 100% DoD produces ~10% capacity fade")
	public void testCycles_3333fullCycles() {
		for (var i = 0; i < 3333; i++) {
			this.cycleAging.recordCycle(1.0);
		}
		assertEquals(0.0999, this.cycleAging.getCumulativeFade(), 0.001,
				"3333 full cycles should produce ~10% capacity fade");
	}

	// ---- 5. Cost model -------------------------------------------------------

	@Test
	@DisplayName("fadePerKwh at 100% DoD, 200 kWh = 0.000030 / 200 = 1.5e-7")
	public void testFadePerKwh_fullDod() {
		var result = this.cycleAging.fadePerKwhDispatched(1.0, NAMEPLATE_KWH);
		assertEquals(1.5e-7, result, 1e-9);
	}

	@Test
	@DisplayName("Degradation cost is positive and finite for new LFP at 80% DoD")
	public void testDegradationCost_positiveAndFinite() {
		var fadePerKwh = this.cycleAging.fadePerKwhDispatched(0.80, NAMEPLATE_KWH);
		var cost = (REPLACEMENT_COST_INR / NAMEPLATE_KWH) * fadePerKwh;
		assertTrue(cost > 0.0);
		assertFalse(Double.isInfinite(cost));
		assertFalse(Double.isNaN(cost));
	}

	@Test
	@DisplayName("Cost at 80% DoD > cost at 20% DoD (deeper discharge is more expensive)")
	public void testCost_deeperDodCostsMore() {
		var cost20 = (REPLACEMENT_COST_INR / NAMEPLATE_KWH) * this.cycleAging.fadePerKwhDispatched(0.20, NAMEPLATE_KWH);
		var cost80 = (REPLACEMENT_COST_INR / NAMEPLATE_KWH) * this.cycleAging.fadePerKwhDispatched(0.80, NAMEPLATE_KWH);
		assertTrue(cost80 > cost20, "Deeper DoD must yield higher cost per kWh");
	}

	// ---- 6. Remaining life projection ----------------------------------------

	@Test
	@DisplayName("daysUntilFade: positive days projected at 0 existing fade")
	public void testDaysUntilFade_positiveProjection() {
		var days = this.calendarAging.daysUntilFade(0.20, 25.0);
		assertTrue(days > 0.0);
		assertFalse(Double.isNaN(days));
	}

	@Test
	@DisplayName("daysUntilFade: returns 0 when already past target")
	public void testDaysUntilFade_pastTarget() {
		this.calendarAging.setCumulativeFade(0.25);
		assertEquals(0.0, this.calendarAging.daysUntilFade(0.20, 25.0), 1e-9);
	}

	// ---- 7. EFC tracking -----------------------------------------------------

	@Test
	@DisplayName("EFC: two 50% DoD cycles = 1 equivalent full cycle")
	public void testEfc_twoHalfCycles() {
		this.cycleAging.recordCycle(0.5);
		this.cycleAging.recordCycle(0.5);
		assertEquals(1, this.cycleAging.getEquivalentFullCycles());
	}
}
