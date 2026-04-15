package io.mec.edge.controller.loadintelligence;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements a hysteresis-based load-shedding and restoration sequencer.
 *
 * <p>
 * When battery SoC falls to or below {@code shedThresholdSoc} all
 * NON_CRITICAL circuits are disconnected (shed) in ascending priority order
 * (lowest priority number first). When SoC climbs back to or above
 * {@code restoreThresholdSoc} the sequencer restores one NON_CRITICAL circuit
 * per evaluation cycle, honouring an inter-restore delay and an inrush-current
 * guard.
 */
public class SheddingSequencer {

	private static final double FALLBACK_INRUSH_AMPS = 10.0;

	private final Logger log = LoggerFactory.getLogger(SheddingSequencer.class);

	private final double shedThresholdSoc;
	private final double restoreThresholdSoc;
	private final long interRestoreDelayMs;
	private final double maxInrushAmps;

	/** Epoch-millisecond timestamp of the last successful restore (0 = never). */
	private long lastRestoreEpochMs = 0;

	/**
	 * Constructs a new SheddingSequencer.
	 *
	 * @param shedThresholdSoc     SoC fraction (0.0–1.0) at or below which all
	 *                             NON_CRITICAL loads are shed
	 * @param restoreThresholdSoc  SoC fraction at or above which restoration
	 *                             resumes
	 * @param interRestoreDelayMs  minimum milliseconds to wait between successive
	 *                             restore events
	 * @param maxInrushAmps        maximum allowable inrush current for a restore
	 *                             event (amps)
	 */
	public SheddingSequencer(double shedThresholdSoc, double restoreThresholdSoc,
			long interRestoreDelayMs, double maxInrushAmps) {
		this.shedThresholdSoc = shedThresholdSoc;
		this.restoreThresholdSoc = restoreThresholdSoc;
		this.interRestoreDelayMs = interRestoreDelayMs;
		this.maxInrushAmps = maxInrushAmps;
	}

	// ── public API ────────────────────────────────────────────────────────────

	/**
	 * Evaluates the current SoC and adjusts circuit connections accordingly.
	 *
	 * @param socFraction SoC expressed as a fraction in [0.0, 1.0]
	 * @param circuits    all circuits managed by the controller
	 */
	public void evaluate(double socFraction, List<Circuit> circuits) {
		var nonCritical = circuits.stream()
				.filter(c -> c.getClassification() == CircuitClassification.NON_CRITICAL)
				.toList();

		if (socFraction <= this.shedThresholdSoc) {
			shedAll(nonCritical);
		} else if (socFraction >= this.restoreThresholdSoc) {
			restoreNext(nonCritical);
		}
	}

	/**
	 * Disconnects every connected NON_CRITICAL circuit in ascending priority order
	 * (lowest priority number = highest urgency = shed first).
	 *
	 * @param nonCritical list of NON_CRITICAL circuits
	 */
	public void shedAll(List<Circuit> nonCritical) {
		nonCritical.stream()
				.filter(Circuit::isConnected)
				.sorted(Comparator.comparingInt(Circuit::getPriority))
				.forEach(c -> {
					c.setConnected(false);
					this.log.info("Shedding circuit '{}' (priority {}, {:.1f} A)",
							c.getId(), c.getPriority(), c.getCurrentAmps());
				});
	}

	/**
	 * Attempts to restore the highest-priority disconnected NON_CRITICAL circuit
	 * (lowest priority number = restore first), subject to inter-restore delay and
	 * inrush guard.
	 *
	 * @param nonCritical list of NON_CRITICAL circuits
	 * @return the restored {@link Circuit}, or {@link Optional#empty()} if no
	 *         restore was possible
	 */
	public Optional<Circuit> restoreNext(List<Circuit> nonCritical) {
		// Find the disconnected circuit with the lowest priority number
		Optional<Circuit> candidate = nonCritical.stream()
				.filter(c -> !c.isConnected())
				.min(Comparator.comparingInt(Circuit::getPriority));

		if (candidate.isEmpty()) {
			return Optional.empty();
		}

		// Inter-restore delay guard
		long nowMs = System.currentTimeMillis();
		if (this.lastRestoreEpochMs != 0
				&& (nowMs - this.lastRestoreEpochMs) < this.interRestoreDelayMs) {
			this.log.debug("Restore of '{}' deferred: inter-restore delay not elapsed.",
					candidate.get().getId());
			return Optional.empty();
		}

		// Inrush guard
		Circuit c = candidate.get();
		double effectiveInrushAmps = c.getCurrentAmps() == 0.0
				? FALLBACK_INRUSH_AMPS
				: c.getCurrentAmps() * c.getMaxInrushFactor();

		if (effectiveInrushAmps > this.maxInrushAmps) {
			this.log.warn(
					"Restore of '{}' blocked: estimated inrush {:.1f} A exceeds limit {:.1f} A.",
					c.getId(), effectiveInrushAmps, this.maxInrushAmps);
			return Optional.empty();
		}

		// All guards passed — restore
		c.setConnected(true);
		this.lastRestoreEpochMs = nowMs;
		this.log.info("Restored circuit '{}' (priority {}, estimated inrush {:.1f} A).",
				c.getId(), c.getPriority(), effectiveInrushAmps);
		return Optional.of(c);
	}

	/**
	 * Returns the number of NON_CRITICAL circuits that are currently disconnected
	 * (i.e. actively shed).
	 *
	 * @param circuits all circuits managed by the controller
	 * @return active shed count
	 */
	public int activeShedCount(List<Circuit> circuits) {
		return (int) circuits.stream()
				.filter(c -> c.getClassification() == CircuitClassification.NON_CRITICAL
						&& !c.isConnected())
				.count();
	}

	// ── package-private accessors for testing ─────────────────────────────────

	long getLastRestoreEpochMs() {
		return this.lastRestoreEpochMs;
	}

	void setLastRestoreEpochMs(long lastRestoreEpochMs) {
		this.lastRestoreEpochMs = lastRestoreEpochMs;
	}
}
