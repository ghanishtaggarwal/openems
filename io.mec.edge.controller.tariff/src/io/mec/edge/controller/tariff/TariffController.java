package io.mec.edge.controller.tariff;

import io.openems.common.types.OpenemsType;
import io.openems.edge.common.channel.Channel;
import io.openems.edge.common.channel.Doc;
import io.openems.edge.common.channel.value.Value;
import io.openems.edge.common.component.OpenemsComponent;
import io.openems.edge.timeofusetariff.api.TimeOfUseTariff;

/**
 * OpenEMS service interface for the MEC Tariff Controller.
 *
 * <p>
 * This component acts as a {@link TimeOfUseTariff} provider — it does not
 * implement {@code Controller} because it is a background service that
 * maintains price data rather than a dispatcher that acts on every cycle.
 */
public interface TariffController extends OpenemsComponent, TimeOfUseTariff {

	public enum ChannelId implements io.openems.edge.common.channel.ChannelId {

		/**
		 * Current effective tariff rate including fuel-adjustment component.
		 *
		 * <ul>
		 * <li>Type: FLOAT
		 * <li>Unit: ₹/kWh
		 * </ul>
		 */
		CURRENT_TARIFF_RATE(Doc.of(OpenemsType.FLOAT)),

		/**
		 * Rolling 15-minute peak apparent power demand.
		 *
		 * <ul>
		 * <li>Type: FLOAT
		 * <li>Unit: kVA
		 * </ul>
		 */
		PEAK_DEMAND_KVA(Doc.of(OpenemsType.FLOAT));

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
	 * Gets the Channel for {@link ChannelId#CURRENT_TARIFF_RATE}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getCurrentTariffRateChannel() {
		return this.channel(ChannelId.CURRENT_TARIFF_RATE);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#CURRENT_TARIFF_RATE}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getCurrentTariffRate() {
		return this.getCurrentTariffRateChannel().value();
	}

	/**
	 * Gets the Channel for {@link ChannelId#PEAK_DEMAND_KVA}.
	 *
	 * @return the Channel
	 */
	default Channel<Float> getPeakDemandKvaChannel() {
		return this.channel(ChannelId.PEAK_DEMAND_KVA);
	}

	/**
	 * Gets the {@link Value} for {@link ChannelId#PEAK_DEMAND_KVA}.
	 *
	 * @return the Channel {@link Value}
	 */
	default Value<Float> getPeakDemandKva() {
		return this.getPeakDemandKvaChannel().value();
	}
}
