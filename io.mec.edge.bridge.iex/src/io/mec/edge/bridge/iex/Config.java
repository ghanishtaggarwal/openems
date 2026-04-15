package io.mec.edge.bridge.iex;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * OSGi ConfigAdmin interface for the MEC IEX RTM Bridge.
 */
@ObjectClassDefinition(//
		name = "MEC Bridge IEX RTM", //
		description = "Fetches IEX Real-Time Market clearing price every 15 minutes")
@interface Config {

	@AttributeDefinition(name = "Component-ID", description = "Unique ID of this Component")
	String id() default "mecIex0";

	@AttributeDefinition(name = "Alias", description = "Human-readable name; defaults to Component-ID")
	String alias() default "";

	@AttributeDefinition(name = "Is enabled?", description = "Is this Component enabled?")
	boolean enabled() default true;

	@AttributeDefinition(name = "Fallback price (INR/kWh)", //
			description = "Used when both API and cache are unavailable")
	double fallbackPriceInrKwh() default 4.50;

	@AttributeDefinition(name = "Cache max age (minutes)", //
			description = "Cached price is considered stale after this many minutes")
	int cacheMaxAgeMinutes() default 60;

	@AttributeDefinition(name = "Cache file path", //
			description = "Absolute path for the local price cache JSON file")
	String cacheFilePath() default "/var/mec/iex_cache.json";

	@AttributeDefinition(name = "Fetch interval (minutes)", //
			description = "How often to poll the IEX RTM API")
	int fetchIntervalMinutes() default 15;

	String webconsole_configurationFactory_nameHint() default "MEC Bridge IEX RTM [{id}]";
}
