/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.junit.jupiter.api.Test;

/**
 * Reads the version of the server from the JAR the build made, which the integration tests run on
 * rather than on the class folder.
 */
class MarkdownLsInfoIT {

	private static String expectedVersion() {
		String expected = System.getProperty("markdown-ls.version");
		assertNotNull(expected, "The build names the version it expects.");
		return expected;
	}

	@Test
	void readsTheVersionFromTheJar() {
		URL resource = MarkdownLsInfo.class.getResource("/" + MarkdownLsInfo.VERSION_RESOURCE);
		assertNotNull(resource);
		assertEquals("jar", resource.getProtocol());

		assertEquals(expectedVersion(), MarkdownLsInfo.getVersion());
	}

	@Test
	void namesTheVersionInTheManifestOfTheJar() throws Exception {
		File jarFile = new File(MarkdownLsInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI());

		try (JarFile jar = new JarFile(jarFile)) {
			assertEquals(expectedVersion(),
					jar.getManifest().getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION));
		}
	}
}