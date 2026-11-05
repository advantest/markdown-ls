/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
	void theVersionOfTheServerIsUnknownWhenItRunsFromClassesWithoutManifest() {
		assertEquals(new Library("markdown-ls", Environment.UNKNOWN_VERSION), library("markdown-ls"));
	}

	@Test
	void everyLibraryTheServerRunsOnNamesItsVersion() {
		for (String name : List.of("markdown-service", "markdown-core", "flexmark-extensions", "flexmark",
				"PlantUML")) {
			String version = library(name).version();
			assertTrue(version.matches("\\d+\\.\\d+.*"), name + " names no version: " + version);
		}
		assertEquals(MarkdownCoreInfo.getPlantUmlVersion(), library("PlantUML").version());
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

	private static String orNull(String text) {
		return text.isEmpty() ? null : text;
	}
}
