/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.advantest.MarkdownCoreInfo;
import com.advantest.markdown.service.MarkdownService;

/**
 * Checks what the server tells about the libraries it runs on and about PlantUML.
 */
class EnvironmentTest {

	@Test
	void theLibrariesAreListedInTheOrderTheyAreShownIn() {
		List<String> names = Environment.describe(List.of()).libraries().stream().map(Library::name).toList();

		assertEquals(List.of("markdown-ls", "markdown-service", "markdown-core", "flexmark-extensions", "flexmark",
				"PlantUML"), names);
	}

	@Test
	void furtherLibrariesFollowTheServersOwn() {
		List<Library> libraries = Environment.describe(List.of(new Library("further", "1.2.3"))).libraries();

		assertEquals(new Library("further", "1.2.3"), libraries.get(libraries.size() - 1));
		assertEquals(7, libraries.size());
	}

	@Test
	void theServerNamesTheVersionOfItsBuild() {
		assertEquals(new Library("markdown-ls", expected("markdown-ls.version")), library("markdown-ls"));
	}

	/**
	 * markdown-core and markdown-service once carried their versions in resources of the same name, so
	 * on one class path one hid the other; each library now answers the version of its own.
	 */
	@Test
	void everyLibraryNamesTheVersionThatRuns() {
		assertEquals(expected("markdown-service.version"), library("markdown-service").version());
		assertEquals(expected("markdown-core.version"), library("markdown-core").version());
		assertEquals(expected("flexmark-extensions.version"), library("flexmark-extensions").version());
		assertEquals(expected("flexmark.version"), library("flexmark").version());
		assertEquals(MarkdownCoreInfo.getPlantUmlVersion(), library("PlantUML").version());
		assertTrue(library("PlantUML").version().matches("\\d+\\.\\d+.*"));
	}

	@Test
	void plantUmlIsDescribedAsItIsSetUp() {
		PlantUmlEnvironment plantUml = Environment.describe(List.of()).plantUml();

		assertEquals(MarkdownCoreInfo.getPlantUmlVersion(), plantUml.version());
		assertEquals(MarkdownService.getPlantUmlSecurityProfile().name(), plantUml.securityProfile());
		assertEquals(MarkdownService.getPlantUmlUrlAllowList(), plantUml.urlAllowList());
		assertEquals(orNull(MarkdownCoreInfo.getGraphvizExecutable()), plantUml.dotExecutable());
		assertEquals(orNull(MarkdownCoreInfo.getGraphvizVersion()), plantUml.graphvizVersion());
		assertNotEquals("", plantUml.dotExecutable());
		assertNotEquals("", plantUml.graphvizVersion());
	}

	@Test
	void aLibraryNeedsANameAndAVersion() {
		assertThrows(NullPointerException.class, () -> new Library(null, "1.0"));
		assertThrows(NullPointerException.class, () -> new Library("name", null));
	}

	private static Library library(String name) {
		return Environment.describe(List.of()).libraries().stream()
				.filter(library -> library.name().equals(name))
				.findFirst()
				.orElseThrow();
	}

	private static String expected(String property) {
		String version = System.getProperty(property);
		assertNotNull(version, "The build names the version it expects in " + property + ".");
		return version;
	}

	private static String orNull(String text) {
		return text.isEmpty() ? null : text;
	}
}
