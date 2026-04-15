package io.mec.edge.controller.loadintelligence;

import io.openems.common.test.AbstractComponentConfig;
import io.openems.common.utils.ConfigUtils;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id = "ctrlLoadIntelligence0";
		private String batteryId = "battery0";
		private String siteId = "site0";
		private double shedThresholdSoc = 0.20;
		private double restoreThresholdSoc = 0.35;
		private int interRestoreDelaySeconds = 30;
		private double maxSiteInrushAmps = 200.0;
		private double nominalVoltageV = 230.0;

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

		public Builder setSiteId(String siteId) {
			this.siteId = siteId;
			return this;
		}

		public Builder setShedThresholdSoc(double shedThresholdSoc) {
			this.shedThresholdSoc = shedThresholdSoc;
			return this;
		}

		public Builder setRestoreThresholdSoc(double restoreThresholdSoc) {
			this.restoreThresholdSoc = restoreThresholdSoc;
			return this;
		}

		public Builder setInterRestoreDelaySeconds(int interRestoreDelaySeconds) {
			this.interRestoreDelaySeconds = interRestoreDelaySeconds;
			return this;
		}

		public Builder setMaxSiteInrushAmps(double maxSiteInrushAmps) {
			this.maxSiteInrushAmps = maxSiteInrushAmps;
			return this;
		}

		public Builder setNominalVoltageV(double nominalVoltageV) {
			this.nominalVoltageV = nominalVoltageV;
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
	public String battery_id() {
		return this.builder.batteryId;
	}

	@Override
	public String battery_target() {
		return ConfigUtils.generateReferenceTargetFilter(this.id(), this.battery_id());
	}

	@Override
	public String site_id() {
		return this.builder.siteId;
	}

	@Override
	public String loadConfigDir() {
		return "/etc/mec/loads";
	}

	@Override
	public double shedThresholdSoc() {
		return this.builder.shedThresholdSoc;
	}

	@Override
	public double restoreThresholdSoc() {
		return this.builder.restoreThresholdSoc;
	}

	@Override
	public int interRestoreDelaySeconds() {
		return this.builder.interRestoreDelaySeconds;
	}

	@Override
	public double maxSiteInrushAmps() {
		return this.builder.maxSiteInrushAmps;
	}

	@Override
	public double nominalVoltageV() {
		return this.builder.nominalVoltageV;
	}
}
