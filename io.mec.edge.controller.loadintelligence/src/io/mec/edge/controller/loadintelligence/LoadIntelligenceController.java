package io.mec.edge.controller.loadintelligence;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

/**
 * OpenEMS Controller interface for MEC Load Intelligence.
 *
 * <p>
 * Exposes four read-only channels that summarise the current state of the
 * site's load portfolio.
 */
public interface LoadIntelligenceController extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Aggregated active power of all CRITICAL circuits.
		 *
		 * <ul>
		 * <li>Type: FLOAT
		 * <li>Unit: kW
		 * </ul>
		 */
		CRITICAL_LOAD_KW(Doc.of(OpenemsType.FLOAT)),

		/**
		 * Aggregated active power of all NON_CRITICAL circuits.
		 *
		 * <ul>
		 * <li>Type: FLOAT
		 * <li>Unit: kW
		 * </ul>
		 */
		NON_CRITICAL_LOAD_KW(Doc.of(OpenemsType.FLOAT)),

		/**
		 * Number of NON_CRITICAL circuits currently in the shed (disconnected) state.
		 *
		 * <ul>
		 * <li>Type: INTEGER
		 * </ul>
		 */
		ACTIVE_SHED_COUNT(Doc.of(OpenemsType.INTEGER)),

		/**
		 * Weighted-average availability of NON_CRITICAL circuits across the current
		 * reporting window (percentage).
		 *
		 * <ul>
		 * <li>Type: FLOAT
		 * <li>Unit: %
		 * <li>Range: 0..100
		 * </ul>
		 */
		SITE_AVAILABILITY_PERCENT(Doc.of(OpenemsType.FLOAT));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	// ── channel accessors ─────────────────────────────────────────────────────

	/**
	 * Gets the Channel for {@link ChannelId#CRITICAL_LOAD_KW}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getCriticalLoadKwChannel() {
		return this.channel(ChannelId.CRITICAL_LOAD_KW);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#CRITICAL_LOAD_KW}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getCriticalLoadKw() {
		return this.getCriticalLoadKwChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#NON_CRITICAL_LOAD_KW}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getNonCriticalLoadKwChannel() {
		return this.channel(ChannelId.NON_CRITICAL_LOAD_KW);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#NON_CRITICAL_LOAD_KW}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getNonCriticalLoadKw() {
		return this.getNonCriticalLoadKwChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#ACTIVE_SHED_COUNT}.
	 *
	 * @return the Channel
	 */
	default Channel<Integer> getActiveShedCountChannel() {
		return this.channel(ChannelId.ACTIVE_SHED_COUNT);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#ACTIVE_SHED_COUNT}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Integer> getActiveShedCount() {
		return this.getActiveShedCountChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#SITE_AVAILABILITY_PERCENT}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getSiteAvailabilityPercentChannel() {
		return this.channel(ChannelId.SITE_AVAILABILITY_PERCENT);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#SITE_AVAILABILITY_PERCENT}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getSiteAvailabilityPercent() {
		return this.getSiteAvailabilityPercentChannel().value();
	}
}
