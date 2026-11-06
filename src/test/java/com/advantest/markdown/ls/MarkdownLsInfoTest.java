/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Reads the version of the server from the class folder.
 */
class MarkdownLsInfoTest {

	@Test
	void readsTheVersionOfTheBuild() {
		String expected = System.getProperty("markdown-ls.version");
		assertNotNull(expected, "The build names the version it expects.");

		assertEquals(expected, MarkdownLsInfo.getVersion());
		assertFalse(MarkdownLsInfo.getVersion().contains("${"));
	}

	@Test
	void answersNoVersionWhereTheResourceIsMissing() {
		assertNull(MarkdownLsInfo.readVersion("META-INF/com.advantest.markdown/no-such-library/version.properties"));
	}

	@Test
	void answersNoVersionWhereTheResourceNamesNone() {
		assertNull(MarkdownLsInfo.readVersion("com/advantest/markdown/ls/version-resources/no-version.properties"));
	}

	@Test
	void answersNoVersionWhereTheResourceWasNotFilledIn() {
		assertNull(MarkdownLsInfo.readVersion(
				"com/advantest/markdown/ls/version-resources/unfiltered-version.properties"));
	}
}