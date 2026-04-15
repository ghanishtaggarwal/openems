package io.mec.edge.controller.tariff;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Loads a {@link TariffConfig} from YAML, trying an external file first and
 * falling back to a classpath resource for bundled DISCOM configurations.
 *
 * <p>
 * YAML schema example:
 *
 * <pre>
 * discom: BESCOM
 * fuel_adj_component: 0.30
 * power_factor_penalty_threshold: 0.85
 * tou_slots:
 *   - from: "22:00"
 *     to: "06:00"
 *     rate_kwh: 5.35
 *     demand_charge_kva: 290.0
 * </pre>
 */
public class TariffConfigLoader {

	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("H:mm");

	private TariffConfigLoader() {
		// utility class
	}

	/**
	 * Loads a {@link TariffConfig} for the given state.
	 *
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>External file: {@code <configDir>/<state>.yaml}
	 * <li>Classpath resource: {@code tariff/<state>.yaml}
	 * </ol>
	 *
	 * @param configDir directory on the filesystem to try first
	 * @param state     state/DISCOM identifier (e.g. "karnataka"); matched to YAML
	 *                  filename
	 * @return a parsed {@link TariffConfig}
	 * @throws IOException if neither source can be read
	 */
	public static TariffConfig load(String configDir, String state) throws IOException {
		// 1. Try external file
		var externalPath = Paths.get(configDir, state + ".yaml");
		if (Files.isReadable(externalPath)) {
			try (InputStream is = new FileInputStream(externalPath.toFile())) {
				return parse(is);
			}
		}

		// 2. Fall back to bundled classpath resource
		String resourcePath = "tariff/" + state + ".yaml";
		InputStream classpathIs = TariffConfigLoader.class.getClassLoader()
				.getResourceAsStream(resourcePath);
		if (classpathIs == null) {
			throw new IOException("Tariff config not found on filesystem ('"
					+ externalPath + "') or classpath ('" + resourcePath + "').");
		}
		try (InputStream is = classpathIs) {
			return parse(is);
		}
	}

	/**
	 * Parses a {@link TariffConfig} from the given {@link InputStream}.
	 *
	 * @param is the YAML input stream
	 * @return a parsed {@link TariffConfig}
	 */
	@SuppressWarnings("unchecked")
	public static TariffConfig parse(InputStream is) {
		Yaml yaml = new Yaml();
		Map<String, Object> root = (Map<String, Object>) yaml.load(is);
		return parseMap(root);
	}

	/**
	 * Parses a {@link TariffConfig} from a pre-loaded map (root of a YAML
	 * document).
	 *
	 * @param root the top-level YAML map
	 * @return a parsed {@link TariffConfig}
	 */
	@SuppressWarnings("unchecked")
	public static TariffConfig parseMap(Map<String, Object> root) {
		String discom = (String) root.getOrDefault("discom", "UNKNOWN");
		double fac = toDouble(root.get("fuel_adj_component"), 0.0);
		double pfThreshold = toDouble(root.get("power_factor_penalty_threshold"), 0.90);

		List<TariffSlot> slots = new ArrayList<>();
		Object slotsObj = root.get("tou_slots");
		if (slotsObj instanceof List<?> rawList) {
			for (Object item : rawList) {
				if (!(item instanceof Map<?, ?> entry)) {
					continue;
				}
				Map<String, Object> m = (Map<String, Object>) entry;
				LocalTime from = LocalTime.parse((String) m.get("from"), TIME_FMT);
				LocalTime to = LocalTime.parse((String) m.get("to"), TIME_FMT);
				double rateKwh = toDouble(m.get("rate_kwh"), 0.0);
				double demandKva = toDouble(m.get("demand_charge_kva"), 0.0);
				slots.add(new TariffSlot(from, to, rateKwh, demandKva));
			}
		}

		return new TariffConfig(discom, slots, fac, pfThreshold);
	}

	// ── helpers ───────────────────────────────────────────────────────────────

	private static double toDouble(Object value, double fallback) {
		if (value instanceof Number n) {
			return n.doubleValue();
		}
		return fallback;
	}
}
