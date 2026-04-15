package io.mec.edge.controller.tariff;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id = "ctrlTariff0";
		private String state = "karnataka";
		private String tariffConfigDir = "/etc/mec/tariff";
		private String meterId = "meter0";

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setState(String state) {
			this.state = state;
			return this;
		}

		public Builder setTariffConfigDir(String tariffConfigDir) {
			this.tariffConfigDir = tariffConfigDir;
			return this;
		}

		public Builder setMeterId(String meterId) {
			this.meterId = meterId;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Creates a new Config {@link Builder}.
	 *
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String state() {
		return this.builder.state;
	}

	@Override
	public String tariffConfigDir() {
		return this.builder.tariffConfigDir;
	}

	@Override
	public String meter_id() {
		return this.builder.meterId;
	}

	@Override
	public String meter_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.meter_id());
	}
}
