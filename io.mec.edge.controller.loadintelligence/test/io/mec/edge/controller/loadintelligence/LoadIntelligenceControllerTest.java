package io.mec.edge.controller.loadintelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic unit tests for {@link SheddingSequencer}, {@link Circuit}, and
 * {@link LoadConfigLoader}. No OSGi framework or real battery is required.
 */
class LoadIntelligenceControllerTest {

	// ── shared inter-restore delay: 30 seconds ────────────────────────────────
	private static final long DELAY_MS = 30_000L;
	private static final double MAX_INRUSH = 50.0;

	private SheddingSequencer sequencer;

	@BeforeEach
	void setUp() {
		// shed at 20 %, restore at 35 %, 30 s inter-restore delay, 50 A inrush limit
		this.sequencer = new SheddingSequencer(0.20, 0.35, DELAY_MS, MAX_INRUSH);
	}

	// ── helper ────────────────────────────────────────────────────────────────

	private static Circuit nonCritical(String id, int priority, double currentAmps, double inrushFactor) {
		Circuit c = new Circuit(id, id, 0, priority, CircuitClassification.NON_CRITICAL, inrushFactor);
		c.updateMeasurement(currentAmps, 230.0, 0);
		return c;
	}

	private static Circuit critical(String id, int priority) {
		return new Circuit(id, id, 0, priority, CircuitClassification.CRITICAL, 1.0);
	}

	// ── test 1: normal operation — 60 % SoC, no shedding ─────────────────────

	@Test
	void testNormalOperation_noShedding() {
		var circuits = new ArrayList<Circuit>();
		circuits.add(nonCritical("nc1", 1, 10.0, 2.5));
		circuits.add(nonCritical("nc2", 2, 5.0, 2.5));

		this.sequencer.evaluate(0.60, circuits);

		assertTrue(circuits.get(0).isConnected(), "nc1 should remain connected at 60% SoC");
		assertTrue(circuits.get(1).isConnected(), "nc2 should remain connected at 60% SoC");
		assertEquals(0, this.sequencer.activeShedCount(circuits));
	}

	// ── test 2: shed sequence — 15 % SoC, all NON_CRITICAL shed ──────────────

	@Test
	void testShedSequence_allNonCriticalDisconnected() {
		var circuits = new ArrayList<Circuit>();
		circuits.add(nonCritical("nc1", 1, 10.0, 2.5));
		circuits.add(nonCritical("nc2", 2, 5.0, 2.5));
		circuits.add(nonCritical("nc3", 3, 8.0, 2.5));

		this.sequencer.evaluate(0.15, circuits);

		assertFalse(circuits.get(0).isConnected(), "nc1 should be shed");
		assertFalse(circuits.get(1).isConnected(), "nc2 should be shed");
		assertFalse(circuits.get(2).isConnected(), "nc3 should be shed");
		assertEquals(3, this.sequencer.activeShedCount(circuits));
	}

	// ── test 3: CRITICAL circuit never shed ───────────────────────────────────

	@Test
	void testCriticalCircuit_neverShed() {
		var circuits = new ArrayList<Circuit>();
		circuits.add(critical("crit1", 0));
		circuits.add(nonCritical("nc1", 1, 10.0, 2.5));

		this.sequencer.evaluate(0.10, circuits);

		assertTrue(circuits.get(0).isConnected(), "CRITICAL circuit must never be shed");
		assertFalse(circuits.get(1).isConnected(), "NON_CRITICAL should be shed");
	}

	// ── test 4: restore — highest priority (lowest number) restored first ─────

	@Test
	void testRestore_highestPriorityRestoredFirst() {
		// Use a sequencer with no inrush limit so the inrush guard doesn't block
		var seq = new SheddingSequencer(0.20, 0.35, 0, 10_000.0);

		// Three circuits already shed; currentAmps = 0 so fallback inrush (10A) applies
		Circuit nc1 = nonCritical("nc1", 1, 0.0, 2.5);
		Circuit nc2 = nonCritical("nc2", 2, 0.0, 2.5);
		Circuit nc3 = nonCritical("nc3", 3, 0.0, 2.5);
		nc1.setConnected(false);
		nc2.setConnected(false);
		nc3.setConnected(false);

		var circuits = List.of(nc1, nc2, nc3);

		Optional<Circuit> restored = seq.restoreNext(circuits.stream()
				.filter(c -> c.getClassification() == CircuitClassification.NON_CRITICAL)
				.toList());

		assertTrue(restored.isPresent(), "A circuit should have been restored");
		assertEquals("nc1", restored.get().getId(), "Priority-1 circuit should be restored first");
		assertTrue(nc1.isConnected());
		assertFalse(nc2.isConnected());
		assertFalse(nc3.isConnected());
	}

	// ── test 5: inter-restore delay — second restore blocked within window ────

	@Test
	void testInterRestoreDelay_blockedWithinWindow() {
		var seq = new SheddingSequencer(0.20, 0.35, DELAY_MS, 10_000.0);

		Circuit nc1 = nonCritical("nc1", 1, 0.0, 2.5);
		Circuit nc2 = nonCritical("nc2", 2, 0.0, 2.5);
		nc1.setConnected(false);
		nc2.setConnected(false);

		var nonCriticals = List.of(nc1, nc2);

		// First restore should succeed
		Optional<Circuit> first = seq.restoreNext(nonCriticals);
		assertTrue(first.isPresent(), "First restore should succeed");

		// Reset nc1 to disconnected to attempt restoring nc2 immediately
		nc1.setConnected(false);

		// Second restore immediately after should be blocked
		Optional<Circuit> second = seq.restoreNext(nonCriticals);
		assertFalse(second.isPresent(), "Second restore should be blocked by inter-restore delay");
	}

	// ── test 6: inter-restore delay — proceeds after delay elapsed ────────────

	@Test
	void testInterRestoreDelay_proceedsAfterDelay() {
		var seq = new SheddingSequencer(0.20, 0.35, DELAY_MS, 10_000.0);

		Circuit nc1 = nonCritical("nc1", 1, 0.0, 2.5);
		nc1.setConnected(false);

		// Simulate that the last restore happened well in the past
		seq.setLastRestoreEpochMs(System.currentTimeMillis() - (DELAY_MS + 5_000L));

		Optional<Circuit> restored = seq.restoreNext(List.of(nc1));
		assertTrue(restored.isPresent(), "Restore should proceed after delay has elapsed");
	}

	// ── test 7: inrush guard — blocked when 30A * 2.5 = 75A > 50A ───────────

	@Test
	void testInrushGuard_blockedWhenExceedsLimit() {
		// maxInrushAmps = 50; circuit has 30A * 2.5 factor = 75A estimated inrush
		var seq = new SheddingSequencer(0.20, 0.35, 0, 50.0);

		Circuit nc1 = nonCritical("nc1", 1, 30.0, 2.5);
		nc1.setConnected(false);

		Optional<Circuit> result = seq.restoreNext(List.of(nc1));
		assertFalse(result.isPresent(), "Restore should be blocked: 30A*2.5=75A > 50A limit");
	}

	// ── test 8: inrush guard — allowed when 20A * 2.5 = 50A < 300A ──────────

	@Test
	void testInrushGuard_allowedWhenBelowLimit() {
		// maxInrushAmps = 300; circuit has 20A * 2.5 factor = 50A estimated inrush
		var seq = new SheddingSequencer(0.20, 0.35, 0, 300.0);

		Circuit nc1 = nonCritical("nc1", 1, 20.0, 2.5);
		nc1.setConnected(false);

		Optional<Circuit> result = seq.restoreNext(List.of(nc1));
		assertTrue(result.isPresent(), "Restore should be allowed: 20A*2.5=50A < 300A limit");
	}

	// ── test 9: LoadConfigLoader.parseCircuits — parses a Map correctly ───────

	@Test
	void testParseCircuits_parsesMapCorrectly() {
		var circuitEntry = new HashMap<String, Object>();
		circuitEntry.put("id", "test-circuit");
		circuitEntry.put("name", "Test Circuit");
		circuitEntry.put("ct_register", 42);
		circuitEntry.put("priority", 3);
		circuitEntry.put("classification", "CRITICAL");
		circuitEntry.put("max_inrush_factor", 2.0);

		var root = new HashMap<String, Object>();
		root.put("circuits", List.of(circuitEntry));

		List<Circuit> circuits = LoadConfigLoader.parseCircuits(root);

		assertEquals(1, circuits.size());
		Circuit c = circuits.get(0);
		assertEquals("test-circuit", c.getId());
		assertEquals("Test Circuit", c.getName());
		assertEquals(42, c.getCtRegister());
		assertEquals(3, c.getPriority());
		assertEquals(CircuitClassification.CRITICAL, c.getClassification());
		assertEquals(2.0, c.getMaxInrushFactor(), 1e-9);
	}

	// ── test 10: LoadConfigLoader.parseCircuits — returns empty list for null ─

	@Test
	void testParseCircuits_returnsEmptyListForNull() {
		List<Circuit> circuits = LoadConfigLoader.parseCircuits(null);
		assertTrue(circuits.isEmpty(), "Should return empty list for null input");
	}
}
