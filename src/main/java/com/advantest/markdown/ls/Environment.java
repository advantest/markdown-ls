/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import java.util.ArrayList;
import java.util.List;

import com.advantest.MarkdownCoreInfo;
import com.advantest.flexmark.FlexmarkExtensionsInfo;
import com.advantest.markdown.service.MarkdownService;
import com.advantest.markdown.service.MarkdownServiceInfo;
import com.vladsch.flexmark.FlexmarkInfo;

/**
 * The answer to <code>markdown/environment</code>: the libraries the server runs on and how PlantUML
 * is set up.
 *
 * @param libraries the libraries in the order they are shown: the server's own, then further ones
 * @param plantUml how PlantUML is set up
 */
record Environment(List<Library> libraries, PlantUmlEnvironment plantUml) {

	/**
	 * The version of a library that does not say which one it is.
	 */
	static final String UNKNOWN_VERSION = "unknown";

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
				library("markdown-service", MarkdownServiceInfo.getVersion()),
				library("markdown-core", MarkdownCoreInfo.getVersion()),
				library("flexmark-extensions", FlexmarkExtensionsInfo.getVersion()),
				library("flexmark", FlexmarkInfo.getVersion()),
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

	private static String nullIfEmpty(String text) {
		return text == null || text.isEmpty() ? null : text;
	}
}
