/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Tells which version of the server runs.
 */
public final class MarkdownLsInfo {

	/**
	 * The resource the build writes the version of the server into. It is named after the coordinates
	 * of the server, so that no library on the class path can hide it.
	 */
	static final String VERSION_RESOURCE = "META-INF/com.advantest.markdown/markdown-ls/version.properties";

	private static final String VERSION_KEY = "version";

	private static final String VERSION = readVersion(VERSION_RESOURCE);

	private MarkdownLsInfo() {
	}

	/**
	 * @return the version of the server, <code>null</code> if it cannot be read
	 */
	public static String getVersion() {
		return VERSION;
	}

	/**
	 * Reads the version a resource of the server names.
	 *
	 * @return the version, <code>null</code> if the resource cannot be read, names no version or was not
	 *         filled in by the build
	 */
	static String readVersion(String resource) {
		try (InputStream stream = MarkdownLsInfo.class.getClassLoader().getResourceAsStream(resource)) {
			if (stream == null) {
				return null;
			}
			Properties properties = new Properties();
			properties.load(stream);
			String version = properties.getProperty(VERSION_KEY);
			if (version == null || version.isBlank() || version.contains("${")) {
				return null;
			}
			return version.strip();
		} catch (IOException | IllegalArgumentException e) {
			return null;
		}
	}
}