package io.mec.edge.bridge.iex;

import java.io.IOException;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * HTTP client for the IEX (Indian Energy Exchange) Real-Time Market API.
 *
 * <p>Uses OkHttp3 (already available in the OpenEMS workspace via
 * {@code com.squareup.okhttp3}). Credentials are read from the environment
 * variables {@code IEX_USER} and {@code IEX_PASS}.
 *
 * <p><b>Note:</b> IEX's public API endpoints are not formally documented.
 * The URLs and response schema below match the patterns observed from the
 * IEX web portal. Adjust {@link #LOGIN_URL}, {@link #RTM_PRICE_URL}, and
 * {@link #parseRtmResponse(String)} if the API changes.
 */
public class IexApiClient {

	private static final Logger LOG = LoggerFactory.getLogger(IexApiClient.class);

	private static final String LOGIN_URL = "https://www.iexindia.com/api/login";
	private static final String RTM_PRICE_URL = "https://www.iexindia.com/api/rtm/clearing-price/latest";
	private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
	private static final Duration TIMEOUT = Duration.ofSeconds(30);

	private final String username;
	private final String password;
	private final OkHttpClient httpClient;
	private final Gson gson = new Gson();

	/** Session cookie or Bearer token obtained after login. */
	private volatile String sessionCookie = null;

	/**
	 * Constructs an {@link IexApiClient}.
	 *
	 * @param username IEX portal username (from env {@code IEX_USER})
	 * @param password IEX portal password (from env {@code IEX_PASS})
	 */
	public IexApiClient(String username, String password) {
		this.username = username;
		this.password = password;
		this.httpClient = new OkHttpClient.Builder()
				.connectTimeout(TIMEOUT)
				.readTimeout(TIMEOUT)
				.build();
	}

	/**
	 * Fetches the latest RTM clearing price in ₹/kWh.
	 *
	 * @return clearing price in ₹/kWh
	 * @throws IexApiException if the request fails or the response is malformed
	 */
	public double fetchRtmPriceInrKwh() throws IexApiException {
		this.ensureLoggedIn();
		var body = this.get(RTM_PRICE_URL);
		return this.parseRtmResponse(body);
	}

	// ---- Private helpers -----------------------------------------------------

	private void ensureLoggedIn() throws IexApiException {
		if (this.sessionCookie != null) {
			return;
		}
		this.login();
	}

	private void login() throws IexApiException {
		var payload = new JsonObject();
		payload.addProperty("username", this.username);
		payload.addProperty("password", this.password);

		var requestBody = RequestBody.create(this.gson.toJson(payload), JSON_MEDIA);
		var request = new Request.Builder()
				.url(LOGIN_URL)
				.post(requestBody)
				.header("Accept", "application/json")
				.build();

		try (var response = this.httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) {
				throw new IexApiException("Login failed with HTTP " + response.code());
			}
			// Try Set-Cookie header first
			var setCookie = response.header("Set-Cookie");
			if (setCookie != null) {
				this.sessionCookie = setCookie.split(";")[0];
				LOG.info("[IEX] Login successful (cookie-based session)");
				return;
			}
			// Fall back to body token
			var responseBody = response.body();
			if (responseBody != null) {
				var bodyStr = responseBody.string();
				var bodyJson = this.gson.fromJson(bodyStr, JsonObject.class);
				if (bodyJson != null && bodyJson.has("token")) {
					this.sessionCookie = "Authorization=Bearer " + bodyJson.get("token").getAsString();
					LOG.info("[IEX] Login successful (token-based session)");
					return;
				}
			}
			throw new IexApiException("Login succeeded but no session cookie or token found in response");
		} catch (IOException e) {
			this.sessionCookie = null;
			throw new IexApiException("Login HTTP call failed: " + e.getMessage(), e);
		}
	}

	private String get(String url) throws IexApiException {
		var builder = new Request.Builder().url(url).get();
		if (this.sessionCookie != null) {
			builder.header("Cookie", this.sessionCookie);
		}
		try (var response = this.httpClient.newCall(builder.build()).execute()) {
			if (response.code() == 401 || response.code() == 403) {
				this.sessionCookie = null;
				throw new IexApiException("Session expired (HTTP " + response.code() + ")");
			}
			if (!response.isSuccessful()) {
				throw new IexApiException("GET " + url + " returned HTTP " + response.code());
			}
			var body = response.body();
			return (body != null) ? body.string() : "";
		} catch (IOException e) {
			throw new IexApiException("HTTP GET failed: " + e.getMessage(), e);
		}
	}

	/**
	 * Parses the RTM clearing price from the IEX API JSON response.
	 *
	 * <p>Expected response shapes:
	 *
	 * <pre>
	 * // Shape A — array under "data":
	 * { "data": [ { "mcp": 4250.0, "mcv": 1200.0, "interval": "15:45" } ] }
	 *
	 * // Shape B — direct field:
	 * { "mcp": 5000.0 }
	 * </pre>
	 *
	 * <p>MCP (Market Clearing Price) is in ₹/MWh; divided by 1000 → ₹/kWh.
	 *
	 * @param json raw response body
	 * @return clearing price in ₹/kWh
	 * @throws IexApiException on parse error or missing MCP field
	 */
	double parseRtmResponse(String json) throws IexApiException {
		if (json == null || json.isBlank()) {
			throw new IexApiException("Empty or null response body");
		}
		try {
			var root = this.gson.fromJson(json, JsonObject.class);
			if (root == null) {
				throw new IexApiException("Response parsed to null");
			}
			// Shape A: data array
			if (root.has("data") && root.get("data").isJsonArray()) {
				var data = root.getAsJsonArray("data");
				if (!data.isEmpty()) {
					var latest = data.get(data.size() - 1).getAsJsonObject();
					return latest.get("mcp").getAsDouble() / 1000.0;
				}
			}
			// Shape B: direct mcp field
			if (root.has("mcp")) {
				return root.get("mcp").getAsDouble() / 1000.0;
			}
			throw new IexApiException("Cannot find MCP in response: " + json);
		} catch (JsonParseException | NullPointerException e) {
			throw new IexApiException("Malformed JSON response: " + e.getMessage(), e);
		}
	}

	/**
	 * Resets the session, forcing re-authentication on the next call.
	 */
	public void resetSession() {
		this.sessionCookie = null;
	}

	// ---- Exception -----------------------------------------------------------

	/**
	 * Thrown when the IEX API call fails or returns an unexpected response.
	 */
	public static class IexApiException extends Exception {

		private static final long serialVersionUID = 1L;

		/**
		 * Constructs an {@link IexApiException}.
		 *
		 * @param message error message
		 */
		public IexApiException(String message) {
			super(message);
		}

		/**
		 * Constructs an {@link IexApiException} with a cause.
		 *
		 * @param message error message
		 * @param cause   underlying exception
		 */
		public IexApiException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
