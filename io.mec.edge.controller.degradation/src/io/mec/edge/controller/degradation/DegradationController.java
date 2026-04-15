package io.mec.edge.controller.degradation;

import io.openems.common.channel.Unit;
import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.controller.api.Controller;

/**
 * MEC Degradation Controller — public API.
 *
 * <p>Exposes real-time battery SoH and marginal degradation cost so that
 * dispatch controllers can factor lifecycle cost into charge/discharge decisions.
 */
public interface DegradationController extends Controller, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * State of Health: 1.0 = new, 0.8 = end-of-life threshold.
		 *
		 * <ul>
		 * <li>Interface: {@link DegradationController}
		 * <li>Type: Float
		 * <li>Range: 0.0–1.0
		 * </ul>
		 */
		STATE_OF_HEALTH(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Battery State of Health (0.0 = dead, 1.0 = new)")),

		/**
		 * Marginal cost charged to each dispatched kWh (₹/kWh).
		 *
		 * <ul>
		 * <li>Interface: {@link DegradationController}
		 * <li>Type: Float
		 * <li>Unit: INR/kWh
		 * </ul>
		 */
		COST_PER_KWH_DISPATCHED(Doc.of(OpenemsType.FLOAT) //
				.unit(Unit.NONE) //
				.text("Marginal degradation cost per kWh dispatched (INR/kWh)")),

		/**
		 * Calendar age in whole days since commissioning.
		 *
		 * <ul>
		 * <li>Interface: {@link DegradationController}
		 * <li>Type: Integer
		 * </ul>
		 */
		CALENDAR_AGE_DAYS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.NONE) //
				.text("Calendar age in days since commissioning")),

		/**
		 * Cumulative equivalent full cycles (EFC).
		 *
		 * <ul>
		 * <li>Interface: {@link DegradationController}
		 * <li>Type: Integer
		 * </ul>
		 */
		CYCLE_COUNT(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.NONE) //
				.text("Equivalent full cycle count")),

		/**
		 * Estimated months of useful life remaining before SoH drops below 0.80.
		 *
		 * <ul>
		 * <li>Interface: {@link DegradationController}
		 * <li>Type: Integer
		 * </ul>
		 */
		ESTIMATED_REMAINING_LIFE_MONTHS(Doc.of(OpenemsType.INTEGER) //
				.unit(Unit.NONE) //
				.text("Estimated remaining useful life in months (SoH > 0.80)"));

		private final Doc doc;

		private ChannelId(Doc doc) {
			this.doc = doc;
		}

		@Override
		public Doc doc() {
			return this.doc;
		}
	}

	/**
	 * Gets the Channel for {@link ChannelId#STATE_OF_HEALTH}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getStateOfHealthChannel() {
		return this.channel(ChannelId.STATE_OF_HEALTH);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#STATE_OF_HEALTH}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getStateOfHealth() {
		return this.getStateOfHealthChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#STATE_OF_HEALTH}.
	 *
	 * @param value the next value
	 */
	default void _setStateOfHealth(float value) {
		this.getStateOfHealthChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#COST_PER_KWH_DISPATCHED}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getCostPerKwhDispatchedChannel() {
		return this.channel(ChannelId.COST_PER_KWH_DISPATCHED);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#COST_PER_KWH_DISPATCHED}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getCostPerKwhDispatched() {
		return this.getCostPerKwhDispatchedChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on
	 * {@link ChannelId#COST_PER_KWH_DISPATCHED}.
	 *
	 * @param value the next value
	 */
	default void _setCostPerKwhDispatched(float value) {
		this.getCostPerKwhDispatchedChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CALENDAR_AGE_DAYS}.
	 *
	 * @return the Channel
	 */
	default Channel<Integer> getCalendarAgeDaysChannel() {
		return this.channel(ChannelId.CALENDAR_AGE_DAYS);
	}

	/**
	 * Gets the Channel for {@link ChannelId#CYCLE_COUNT}.
	 *
	 * @return the Channel
	 */
	default Channel<Integer> getCycleCountChannel() {
		return this.channel(ChannelId.CYCLE_COUNT);
	}

	/**
	 * Gets the Channel for {@link ChannelId#ESTIMATED_REMAINING_LIFE_MONTHS}.
	 *
	 * @return the Channel
	 */
	default Channel<Integer> getEstimatedRemainingLifeMonthsChannel() {
		return this.channel(ChannelId.ESTIMATED_REMAINING_LIFE_MONTHS);
	}
}
