package io.mec.edge.controller.degradation;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String batteryId = "battery0";
		private double arrheniusA = 1.4e-3;
		private double arrheniusEaEv = 0.60;
		private double nameplateKwh = 200.0;
		private double replacementCostInr = 7_000_000.0;
		private String commissionDate = "2024-01-15";
		private double persistedCalendarFade = 0.0;
		private double persistedEfc = 0.0;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setBatteryId(String batteryId) {
			this.batteryId = batteryId;
			return this;
		}

		public Builder setNameplateKwh(double nameplateKwh) {
			this.nameplateKwh = nameplateKwh;
			return this;
		}

		public Builder setReplacementCostInr(double replacementCostInr) {
			this.replacementCostInr = replacementCostInr;
			return this;
		}

		public Builder setCommissionDate(String commissionDate) {
			this.commissionDate = commissionDate;
			return this;
		}

		public Builder setPersistedCalendarFade(double fade) {
			this.persistedCalendarFade = fade;
			return this;
		}

		public Builder setPersistedEfc(double efc) {
			this.persistedEfc = efc;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String battery_id() {
		return this.builder.batteryId;
	}

	@Override
	public String battery_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.builder.batteryId);
	}

	@Override
	public double arrheniusA() {
		return this.builder.arrheniusA;
	}

	@Override
	public double arrheniusEaEv() {
		return this.builder.arrheniusEaEv;
	}

	@Override
	public double nameplateKwh() {
		return this.builder.nameplateKwh;
	}

	@Override
	public double replacementCostInr() {
		return this.builder.replacementCostInr;
	}

	@Override
	public String commissionDate() {
		return this.builder.commissionDate;
	}

	@Override
	public double persistedCalendarFade() {
		return this.builder.persistedCalendarFade;
	}

	@Override
	public double persistedEfc() {
		return this.builder.persistedEfc;
	}
}
