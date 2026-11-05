/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.util.Objects;

/**
 * A library the server runs on, as the request <code>markdown/environment</code> names it.
 *
 * @param name the library's name, not <code>null</code>
 * @param version the library's version, not <code>null</code>
 */
public record Library(String name, String version) {

	/**
	 * Creates a library.
	 *
	 * @throws NullPointerException if the name or the version is <code>null</code>
	 */
	public Library {
		Objects.requireNonNull(name, "A library needs a name.");
		Objects.requireNonNull(version, "A library needs a version.");
	}
}
