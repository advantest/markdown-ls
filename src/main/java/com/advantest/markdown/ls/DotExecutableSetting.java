/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import java.util.Optional;

import com.advantest.markdown.service.MarkdownService;
import com.google.gson.JsonElement;

/**
 * The setting <code>markdownSupport.plantUml.dotExecutable</code>: the Graphviz <code>dot</code>
 * executable PlantUML shall run, or an empty text where the user names none.
 *
 * <p>PlantUML keeps the executable once for the whole process. Where the user names none, the one in
 * effect when the setting was created applies, which is what <code>GRAPHVIZ_DOT</code> or the place
 * Graphviz is usually installed at named when the service was built.</p>
 */
final class DotExecutableSetting {

	private final String executableNobodyNamed = MarkdownService.getDotExecutable().orElse(null);

	/**
	 * Names the executable PlantUML runs from now on.
	 *
	 * @param path the executable's path, empty or <code>null</code> for the one in effect when this
	 *        setting was created
	 * @throws IllegalArgumentException if the path names nothing that can be run, in which case the
	 *         executable in effect stays
	 */
	void apply(String path) {
		MarkdownService.setDotExecutable(path == null || path.isBlank() ? this.executableNobodyNamed : path);
	}

	/**
	 * Reads the setting from the <code>initializationOptions</code> of <code>initialize</code>:
	 * <code>{ "plantUml": { "dotExecutable": "..." } }</code>.
	 *
	 * @param options the options as JSON
	 * @return the path, empty text if the setting says none, or {@link Optional#empty()} if the options
	 *         hold no setting
	 */
	static Optional<String> fromInitializationOptions(Object options) {
		return valueAt(options, "plantUml", "dotExecutable");
	}

	/**
	 * Reads the setting from the <code>settings</code> of <code>workspace/didChangeConfiguration</code>:
	 * <code>{ "markdownSupport": { "plantUml": { "dotExecutable": "..." } } }</code>.
	 *
	 * @param settings the settings as JSON
	 * @return the path, empty text if the setting says none, or {@link Optional#empty()} if the settings
	 *         hold no setting
	 */
	static Optional<String> fromSettings(Object settings) {
		return valueAt(settings, "markdownSupport", "plantUml", "dotExecutable");
	}

	private static Optional<String> valueAt(Object json, String... keys) {
		if (!(json instanceof JsonElement element)) {
			return Optional.empty();
		}
		for (String key : keys) {
			if (!element.isJsonObject() || !element.getAsJsonObject().has(key)) {
				return Optional.empty();
			}
			element = element.getAsJsonObject().get(key);
		}
		if (element.isJsonNull()) {
			return Optional.of("");
		}
		if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			return Optional.of(element.getAsString());
		}
		return Optional.empty();
	}
}
