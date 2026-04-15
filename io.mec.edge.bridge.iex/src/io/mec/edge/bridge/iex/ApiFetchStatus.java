package io.mec.edge.bridge.iex;

/**
 * Status of the most recent IEX RTM price fetch attempt.
 */
public enum ApiFetchStatus {

	/** Live price retrieved successfully from IEX API. */
	SUCCESS,

	/** API call failed; serving a cached value within max-age. */
	CACHED,

	/** Both API and cache are unavailable; using the configured fallback price. */
	FALLBACK
}
