package io.mec.edge.controller.tariff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for {@link TariffSlot}, {@link TariffConfig}, and
 * {@link TariffConfigLoader}. No OSGi framework is required.
 */
class TariffControllerTest {

	private static final double DELTA = 1e-9;

	// ── helper ────────────────────────────────────────────────────────────────

	/**
	 * Loads the bundled YAML resource for the given state name via the classpath.
	 */
	private static TariffConfig loadBundled(String stateName) throws IOException {
		InputStream is = TariffControllerTest.class.getClassLoader()
				.getResourceAsStream("tariff/" + stateName + ".yaml");
		assertNotNull(is, "Bundled YAML resource not found for state: " + stateName);
		try (is) {
			return TariffConfigLoader.parse(is);
		}
	}

	// ── test 1: load each bundled config and verify discom name ───────────────

	@Test
	void testLoadPunjab_discomName() throws IOException {
		assertEquals("PSPCL", loadBundled("punjab").getDiscom());
	}

	@Test
	void testLoadMaharashtra_discomName() throws IOException {
		assertEquals("MSEDCL", loadBundled("maharashtra").getDiscom());
	}

	@Test
	void testLoadKarnataka_discomName() throws IOException {
		assertEquals("BESCOM", loadBundled("karnataka").getDiscom());
	}

	@Test
	void testLoadDelhi_discomName() throws IOException {
		assertEquals("TPDDL", loadBundled("delhi").getDiscom());
	}

	// ── test 2: PSPCL ToD slot transitions ───────────────────────────────────
	// rate_kwh + fuel_adj_component (0.42)
	// off-peak (22–06): 5.85 + 0.42 = 6.27
	// morning (06–09): 7.20 + 0.42 = 7.62
	// day     (09–18): 8.65 + 0.42 = 9.07
	// evening (18–22): 9.10 + 0.42 = 9.52

	static Stream<Arguments> pspcl_slots() {
		return Stream.of(
				Arguments.of(LocalTime.of(23, 0), 6.27),
				Arguments.of(LocalTime.of(6, 0), 7.62),
				Arguments.of(LocalTime.of(9, 0), 9.07),
				Arguments.of(LocalTime.of(18, 0), 9.52),
				Arguments.of(LocalTime.of(22, 0), 6.27)
		);
	}

	@ParameterizedTest(name = "PSPCL rate at {0} = {1}")
	@MethodSource("pspcl_slots")
	void testPspcl_rateAtSlotBoundaries(LocalTime time, double expectedRate) throws IOException {
		TariffConfig cfg = loadBundled("punjab");
		assertEquals(expectedRate, cfg.rateAt(time), DELTA);
	}

	// ── test 3: MSEDCL ToD slot transitions ──────────────────────────────────
	// off-peak (22–06): 5.50 + 0.38 = 5.88
	// morning  (06–09): 7.40 + 0.38 = 7.78
	// peak1    (09–12): 9.20 + 0.38 = 9.58
	// shoulder (12–18): 7.40 + 0.38 = 7.78
	// evening  (18–22): 9.80 + 0.38 = 10.18
	// 22:00 wraps back to off-peak: 5.88

	static Stream<Arguments> msedcl_slots() {
		return Stream.of(
				Arguments.of(LocalTime.of(0, 0), 5.88),
				Arguments.of(LocalTime.of(6, 0), 7.78),
				Arguments.of(LocalTime.of(9, 0), 9.58),
				Arguments.of(LocalTime.of(12, 0), 7.78),
				Arguments.of(LocalTime.of(18, 0), 10.18),
				Arguments.of(LocalTime.of(22, 0), 5.88)
		);
	}

	@ParameterizedTest(name = "MSEDCL rate at {0} = {1}")
	@MethodSource("msedcl_slots")
	void testMsedcl_rateAtSlotBoundaries(LocalTime time, double expectedRate) throws IOException {
		TariffConfig cfg = loadBundled("maharashtra");
		assertEquals(expectedRate, cfg.rateAt(time), DELTA);
	}

	// ── test 4: BESCOM ToD slot transitions ──────────────────────────────────
	// off-peak (22–06): 5.35 + 0.30 = 5.65
	// morning  (06–10): 7.10 + 0.30 = 7.40
	// peak1    (10–14): 8.85 + 0.30 = 9.15
	// shoulder (14–18): 7.10 + 0.30 = 7.40
	// evening  (18–22): 9.30 + 0.30 = 9.60
	// 23:00 in off-peak: 5.65

	static Stream<Arguments> bescom_slots() {
		return Stream.of(
				Arguments.of(LocalTime.of(23, 0), 5.65),
				Arguments.of(LocalTime.of(6, 0), 7.40),
				Arguments.of(LocalTime.of(10, 0), 9.15),
				Arguments.of(LocalTime.of(14, 0), 7.40),
				Arguments.of(LocalTime.of(18, 0), 9.60),
				Arguments.of(LocalTime.of(22, 0), 5.65)
		);
	}

	@ParameterizedTest(name = "BESCOM rate at {0} = {1}")
	@MethodSource("bescom_slots")
	void testBescom_rateAtSlotBoundaries(LocalTime time, double expectedRate) throws IOException {
		TariffConfig cfg = loadBundled("karnataka");
		assertEquals(expectedRate, cfg.rateAt(time), DELTA);
	}

	// ── test 5: TPDDL ToD slot transitions ───────────────────────────────────
	// off-peak (23–06): 5.70 + 0.45 = 6.15
	// morning  (06–09): 7.55 + 0.45 = 8.00
	// day      (09–17): 8.40 + 0.45 = 8.85
	// peak     (17–21): 10.20 + 0.45 = 10.65
	// shoulder (21–23): 7.55 + 0.45 = 8.00
	// 00:00 in off-peak: 6.15

	static Stream<Arguments> tpddl_slots() {
		return Stream.of(
				Arguments.of(LocalTime.of(0, 0), 6.15),
				Arguments.of(LocalTime.of(6, 0), 8.00),
				Arguments.of(LocalTime.of(9, 0), 8.85),
				Arguments.of(LocalTime.of(17, 0), 10.65),
				Arguments.of(LocalTime.of(21, 0), 8.00),
				Arguments.of(LocalTime.of(23, 0), 6.15)
		);
	}

	@ParameterizedTest(name = "TPDDL rate at {0} = {1}")
	@MethodSource("tpddl_slots")
	void testTpddl_rateAtSlotBoundaries(LocalTime time, double expectedRate) throws IOException {
		TariffConfig cfg = loadBundled("delhi");
		assertEquals(expectedRate, cfg.rateAt(time), DELTA);
	}

	// ── test 6: TariffSlot midnight wrap-around ───────────────────────────────

	@Test
	void testTariffSlot_wrapAround_containsMidnightRange() {
		TariffSlot slot = new TariffSlot(LocalTime.of(22, 0), LocalTime.of(6, 0), 5.85, 280.0);

		assertTrue(slot.contains(LocalTime.of(23, 0)), "23:00 should be in [22:00–06:00]");
		assertTrue(slot.contains(LocalTime.of(0, 0)), "00:00 should be in [22:00–06:00]");
		assertTrue(slot.contains(LocalTime.of(5, 59)), "05:59 should be in [22:00–06:00]");
		assertFalse(slot.contains(LocalTime.of(6, 0)), "06:00 should NOT be in [22:00–06:00]");
	}

	// ── test 7: TariffSlot normal (non-wrap) slot ─────────────────────────────

	@Test
	void testTariffSlot_normalSlot_halfOpen() {
		TariffSlot slot = new TariffSlot(LocalTime.of(6, 0), LocalTime.of(22, 0), 8.65, 450.0);

		assertTrue(slot.contains(LocalTime.of(6, 0)), "06:00 should be in [06:00–22:00]");
		assertTrue(slot.contains(LocalTime.of(12, 0)), "12:00 should be in [06:00–22:00]");
		assertTrue(slot.contains(LocalTime.of(21, 59)), "21:59 should be in [06:00–22:00]");
		assertFalse(slot.contains(LocalTime.of(22, 0)), "22:00 should NOT be in [06:00–22:00]");
	}

	// ── test 8: TPDDL peak slot has highest demand charge ─────────────────────

	@Test
	void testTpddl_peakSlotHighestDemandCharge() throws IOException {
		TariffConfig cfg = loadBundled("delhi");
		double maxDemand = cfg.getTouSlots().stream()
				.mapToDouble(TariffSlot::getDemandChargeKva)
				.max()
				.orElse(0.0);
		assertEquals(520.0, maxDemand, DELTA, "TPDDL peak demand charge should be 520.0 ₹/kVA");
	}

	// ── test 9: inline YAML parse using ByteArrayInputStream ─────────────────

	@Test
	void testInlineYamlParse() {
		String yaml = """
				discom: TEST_DISCOM
				fuel_adj_component: 0.10
				power_factor_penalty_threshold: 0.92
				tou_slots:
				  - from: "00:00"
				    to: "12:00"
				    rate_kwh: 4.00
				    demand_charge_kva: 200.0
				  - from: "12:00"
				    to: "00:00"
				    rate_kwh: 8.00
				    demand_charge_kva: 400.0
				""";

		TariffConfig cfg = TariffConfigLoader.parse(
				new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

		assertEquals("TEST_DISCOM", cfg.getDiscom());
		assertEquals(0.10, cfg.getFuelAdjComponent(), DELTA);
		assertEquals(2, cfg.getTouSlots().size());

		// Off-peak rate at 06:00: 4.00 + 0.10 = 4.10
		assertEquals(4.10, cfg.rateAt(LocalTime.of(6, 0)), DELTA);

		// Peak rate at 14:00: 8.00 + 0.10 = 8.10
		assertEquals(8.10, cfg.rateAt(LocalTime.of(14, 0)), DELTA);
	}
}
