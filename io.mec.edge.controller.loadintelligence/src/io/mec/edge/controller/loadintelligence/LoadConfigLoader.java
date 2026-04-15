package io.mec.edge.controller.loadintelligence;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * Loads circuit configuration from a YAML file.
 *
 * <p>
 * Expected YAML schema:
 *
 * <pre>
 * circuits:
 *   - id: "circuit-1"
 *     name: "HVAC Main"
 *     ct_register: 100
 *     priority: 1
 *     classification: NON_CRITICAL
 *     max_inrush_factor: 2.5
 * </pre>
 */
public class LoadConfigLoader {

	private LoadConfigLoader() {
		// utility class — no instances
	}

	/**
	 * Loads circuits from a YAML file at the given path.
	 *
	 * @param yamlPath absolute path to the YAML configuration file
	 * @return list of {@link Circuit} objects; never {@code null}
	 * @throws IOException if the file cannot be read or parsed
	 */
	@SuppressWarnings("unchecked")
	public static List<Circuit> load(String yamlPath) throws IOException {
		try (InputStream is = new FileInputStream(yamlPath)) {
			Yaml yaml = new Yaml();
			Map<String, Object> root = (Map<String, Object>) yaml.load(is);
			return parseCircuits(root);
		}
	}

	/**
	 * Parses a map (typically the root of a loaded YAML document) into a list of
	 * {@link Circuit} objects.
	 *
	 * @param root the top-level map; may be {@code null}
	 * @return list of {@link Circuit} objects; empty list if {@code root} is
	 *         {@code null} or contains no {@code circuits} key
	 */
	@SuppressWarnings("unchecked")
	public static List<Circuit> parseCircuits(Map<String, Object> root) {
		if (root == null) {
			return Collections.emptyList();
		}

		Object circuitsObj = root.get("circuits");
		if (!(circuitsObj instanceof List<?> rawList)) {
			return Collections.emptyList();
		}

		List<Circuit> result = new ArrayList<>();
		for (Object item : rawList) {
			if (!(item instanceof Map<?, ?> entry)) {
				continue;
			}
			Map<String, Object> m = (Map<String, Object>) entry;

			String id = (String) m.get("id");
			String name = (String) m.get("name");
			int ctRegister = toInt(m.get("ct_register"), 0);
			int priority = toInt(m.get("priority"), 0);
			String classStr = (String) m.getOrDefault("classification", "NON_CRITICAL");
			CircuitClassification classification;
			try {
				classification = CircuitClassification.valueOf(classStr.toUpperCase());
			} catch (IllegalArgumentException e) {
				classification = CircuitClassification.NON_CRITICAL;
			}
			double maxInrushFactor = toDouble(m.get("max_inrush_factor"), 1.0);

			result.add(new Circuit(id, name, ctRegister, priority, classification, maxInrushFactor));
		}
		return result;
	}

	// ── helpers ───────────────────────────────────────────────────────────────

	private static int toInt(Object value, int fallback) {
		if (value instanceof Number n) {
			return n.intValue();
		}
		return fallback;
	}

	private static double toDouble(Object value, double fallback) {
		if (value instanceof Number n) {
			return n.doubleValue();
		}
		return fallback;
	}
}
