package io.mec.edge.bridge.iex;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

/**
 * MEC IEX RTM Bridge — public API.
 *
 * <p>Publishes IEX Real-Time Market clearing prices so that dispatch
 * controllers can compare grid tariff vs. market price for arbitrage.
 * Also implements {@link TimeOfUseTariff} so the OpenEMS Energy Optimizer
 * can use live IEX prices for dispatch scheduling.
 */
public interface IexBridge extends TimeOfUseTariff, OpenemsComponent {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Current IEX RTM market clearing price (₹/kWh).
		 *
		 * <ul>
		 * <li>Type: Float
		 * <li>Unit: INR/kWh
		 * </ul>
		 */
		CURRENT_RTM_PRICE(Doc.of(OpenemsType.FLOAT) //
				.text("IEX RTM market clearing price (INR/kWh)")),

		/**
		 * Epoch milliseconds of the last successful or cached price update.
		 *
		 * <ul>
		 * <li>Type: Long
		 * </ul>
		 */
		LAST_FETCH_TIMESTAMP(Doc.of(OpenemsType.LONG) //
				.text("Epoch milliseconds of last price update")),

		/**
		 * IEX API fetch status: SUCCESS | CACHED | FALLBACK.
		 *
		 * <ul>
		 * <li>Type: String
		 * </ul>
		 */
		API_FETCH_STATUS(Doc.of(OpenemsType.STRING) //
				.text("IEX API fetch status: SUCCESS | CACHED | FALLBACK"));

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
	 * Gets the Channel for {@link ChannelId#CURRENT_RTM_PRICE}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getCurrentRtmPriceChannel() {
		return this.channel(ChannelId.CURRENT_RTM_PRICE);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#CURRENT_RTM_PRICE}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getCurrentRtmPrice() {
		return this.getCurrentRtmPriceChannel().value();
	}

	/**
	 * Internal method to set the 'nextValue' on {@link ChannelId#CURRENT_RTM_PRICE}.
	 *
	 * @param value the next value
	 */
	default void _setCurrentRtmPrice(float value) {
		this.getCurrentRtmPriceChannel().setNextValue(value);
	}

	/**
	 * Gets the Channel for {@link ChannelId#LAST_FETCH_TIMESTAMP}.
	 *
	 * @return the Channel
	 */
	default Channel<Long> getLastFetchTimestampChannel() {
		return this.channel(ChannelId.LAST_FETCH_TIMESTAMP);
	}

	/**
	 * Gets the Channel for {@link ChannelId#API_FETCH_STATUS}.
	 *
	 * @return the Channel
	 */
	default Channel<String> getApiFetchStatusChannel() {
		return this.channel(ChannelId.API_FETCH_STATUS);
	}
}
