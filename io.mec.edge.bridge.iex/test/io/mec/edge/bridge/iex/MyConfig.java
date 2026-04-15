package io.mec.edge.bridge.iex;

import io.openems.common.test.AbstractComponentConfig;

@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	private final Builder builder;

	@Override
	public String id() {
		return this.builder.id;
	}

	@Override
	public String alias() {
		return this.builder.alias;
	}

	@Override
	public boolean enabled() {
		return this.builder.enabled;
	}

	@Override
	public double fallbackPriceInrKwh() {
		return this.builder.fallbackPriceInrKwh;
	}

	@Override
	public int cacheMaxAgeMinutes() {
		return this.builder.cacheMaxAgeMinutes;
	}

	@Override
	public String cacheFilePath() {
		return this.builder.cacheFilePath;
	}

	@Override
	public int fetchIntervalMinutes() {
		return this.builder.fetchIntervalMinutes;
	}

	@Override
	public String webconsole_configurationFactory_nameHint() {
		return "MEC Bridge IEX RTM [" + this.builder.id + "]";
	}

	public static class Builder {
		private String id = "mecIex0";
		private String alias = "";
		private boolean enabled = true;
		private double fallbackPriceInrKwh = 4.50;
		private int cacheMaxAgeMinutes = 60;
		private String cacheFilePath = "/tmp/iex_test_cache.json";
		private int fetchIntervalMinutes = 15;

		public Builder setId(String id) {
			this.id = id;
			return this;
		}

		public Builder setAlias(String alias) {
			this.alias = alias;
			return this;
		}

		public Builder setEnabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public Builder setFallbackPriceInrKwh(double fallbackPriceInrKwh) {
			this.fallbackPriceInrKwh = fallbackPriceInrKwh;
			return this;
		}

		public Builder setCacheMaxAgeMinutes(int cacheMaxAgeMinutes) {
			this.cacheMaxAgeMinutes = cacheMaxAgeMinutes;
			return this;
		}

		public Builder setCacheFilePath(String cacheFilePath) {
			this.cacheFilePath = cacheFilePath;
			return this;
		}

		public Builder setFetchIntervalMinutes(int fetchIntervalMinutes) {
			this.fetchIntervalMinutes = fetchIntervalMinutes;
			return this;
		}

		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	public static Builder create() {
		return new Builder();
	}
}
