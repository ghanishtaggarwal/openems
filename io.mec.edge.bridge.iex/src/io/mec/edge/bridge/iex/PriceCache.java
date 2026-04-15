package io.mec.edge.bridge.iex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

/**
 * Thread-safe local file cache for the last-known IEX RTM price.
 *
 * <p>JSON format:
 *
 * <pre>
 * {
 *   "price_inr_kwh": 4.25,
 *   "timestamp_ms": 1712300000000
 * }
 * </pre>
 */
public class PriceCache {

	private static final Logger LOG = LoggerFactory.getLogger(PriceCache.class);

	private final Path cacheFile;
	private final Gson gson = new Gson();

	/** In-memory copy — avoids disk I/O on every read when entry is fresh. */
	private volatile CacheEntry memoryEntry = null;

	/**
	 * Constructs a {@link PriceCache}.
	 *
	 * @param cacheFilePath absolute path to the JSON cache file
	 */
	public PriceCache(String cacheFilePath) {
		this.cacheFile = Paths.get(cacheFilePath);
	}

	/**
	 * Persists a new price to disk and updates the in-memory copy.
	 *
	 * @param priceInrKwh market clearing price in ₹/kWh
	 * @param timestampMs epoch milliseconds of this price point
	 */
	public synchronized void write(double priceInrKwh, long timestampMs) {
		var obj = new JsonObject();
		obj.addProperty("price_inr_kwh", priceInrKwh);
		obj.addProperty("timestamp_ms", timestampMs);
		try {
			Files.createDirectories(this.cacheFile.getParent());
			Files.writeString(this.cacheFile, this.gson.toJson(obj), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			this.memoryEntry = new CacheEntry(priceInrKwh, timestampMs);
		} catch (IOException e) {
			LOG.warn("[IEX Cache] Failed to write cache: {}", e.getMessage());
		}
	}

	/**
	 * Returns a fresh cache entry, or {@code null} if unavailable or stale.
	 *
	 * @param maxAgeMs maximum acceptable age in milliseconds
	 * @return {@link CacheEntry}, or {@code null}
	 */
	public synchronized CacheEntry read(long maxAgeMs) {
		var now = System.currentTimeMillis();

		// Return in-memory copy if still fresh
		if (this.memoryEntry != null && (now - this.memoryEntry.timestampMs) <= maxAgeMs) {
			return this.memoryEntry;
		}

		// Try disk
		if (!Files.exists(this.cacheFile)) {
			return null;
		}
		try {
			var json = Files.readString(this.cacheFile, StandardCharsets.UTF_8);
			var obj = this.gson.fromJson(json, JsonObject.class);
			var price = obj.get("price_inr_kwh").getAsDouble();
			var ts = obj.get("timestamp_ms").getAsLong();
			if ((now - ts) > maxAgeMs) {
				return null; // stale
			}
			this.memoryEntry = new CacheEntry(price, ts);
			return this.memoryEntry;
		} catch (IOException | JsonParseException | NullPointerException e) {
			LOG.warn("[IEX Cache] Failed to read cache: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Invalidates the in-memory entry (forces disk re-read on next access).
	 */
	public synchronized void invalidateMemory() {
		this.memoryEntry = null;
	}

	/**
	 * Immutable cache entry.
	 */
	public record CacheEntry(double priceInrKwh, long timestampMs) {
	}
}
