/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.advantest.MarkdownCoreInfo;
import com.advantest.markdown.service.MarkdownService;

/**
 * The answer to <code>markdown/environment</code>: the libraries the server runs on and how PlantUML
 * is set up.
 *
 * @param libraries the libraries in the order they are shown: the server's own, then further ones
 * @param plantUml how PlantUML is set up
 */
record Environment(List<Library> libraries, PlantUmlEnvironment plantUml) {

	/**
	 * The version of a library that does not say which one it is, e.g. the server run from classes
	 * without a manifest.
	 */
	static final String UNKNOWN_VERSION = "unknown";

	private static final Logger LOG = LoggerFactory.getLogger(Environment.class);

	private static final String SETTINGS = "settings.properties";

	/**
	 * Describes the environment as it is now: Graphviz is asked which version it is, so this is no
	 * call for the thread synchronising the documents.
	 *
	 * @param furtherLibraries libraries to list after the server's own, not <code>null</code>
	 * @return the environment, never <code>null</code>
	 */
	static Environment describe(List<Library> furtherLibraries) {
		List<Library> libraries = new ArrayList<>(List.of(
				library(MarkdownLanguageServer.NAME, MarkdownLanguageServer.version()),
				library("markdown-service", setting("markdownServiceVersion")),
				library("markdown-core", orSetting(MarkdownCoreInfo.getVersion(), "markdownCoreVersion")),
				library("flexmark-extensions",
						orSetting(MarkdownCoreInfo.getFlexmarkExtensionsVersion(), "flexmarkExtensionsVersion")),
				library("flexmark", orSetting(MarkdownCoreInfo.getFlexmarkVersion(), "flexmarkVersion")),
				library("PlantUML", MarkdownCoreInfo.getPlantUmlVersion())));
		libraries.addAll(furtherLibraries);

		PlantUmlEnvironment plantUml = new PlantUmlEnvironment(MarkdownCoreInfo.getPlantUmlVersion(),
				MarkdownService.getPlantUmlSecurityProfile().name(),
				MarkdownService.getPlantUmlUrlAllowList(),
				nullIfEmpty(MarkdownCoreInfo.getGraphvizExecutable()),
				nullIfEmpty(MarkdownCoreInfo.getGraphvizVersion()));
		return new Environment(List.copyOf(libraries), plantUml);
	}

	private static Library library(String name, String version) {
		return new Library(name, version != null ? version : UNKNOWN_VERSION);
	}

	private static String orSetting(String version, String key) {
		return version != null ? version : setting(key);
	}

	/**
	 * Answers a value of the settings the Markdown libraries carry. Each of them carries its settings
	 * under the same name, so <code>MarkdownCoreInfo</code> reads whichever comes first on the class path
	 * and may miss its own; this looks at all of them.
	 *
	 * @return the first value found for the key, <code>null</code> if none is
	 */
	private static String setting(String key) {
		try {
			Enumeration<URL> resources = Environment.class.getClassLoader().getResources(SETTINGS);
			while (resources.hasMoreElements()) {
				Properties settings = new Properties();
				try (InputStream in = resources.nextElement().openStream()) {
					settings.load(in);
				}
				String value = settings.getProperty(key);
				if (value != null) {
					return value;
				}
			}
		} catch (IOException e) {
			LOG.warn("The settings of the libraries could not be read.", e);
		}
		return null;
	}

	private static String nullIfEmpty(String text) {
		return text == null || text.isEmpty() ? null : text;
	}
}
