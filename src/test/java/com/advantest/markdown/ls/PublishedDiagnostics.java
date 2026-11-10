/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.lsp4j.PublishDiagnosticsParams;

/**
 * Keeps the diagnostics published, in the order they arrive, for a test to take them one by one.
 */
final class PublishedDiagnostics implements Consumer<PublishDiagnosticsParams> {

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final BlockingQueue<PublishDiagnosticsParams> published = new LinkedBlockingQueue<>();

	@Override
	public void accept(PublishDiagnosticsParams diagnostics) {
		this.published.add(diagnostics);
	}

	/**
	 * Takes the diagnostics published next, waiting for them if they have not arrived yet.
	 */
	PublishDiagnosticsParams next() throws InterruptedException {
		PublishDiagnosticsParams next = this.published.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		assertNotNull(next, "No diagnostics were published.");
		return next;
	}

	/**
	 * Checks that nothing more is published within the given time.
	 */
	void assertNoneWithin(Duration time) throws InterruptedException {
		PublishDiagnosticsParams next = this.published.poll(time.toMillis(), TimeUnit.MILLISECONDS);
		assertNull(next, () -> "Unexpected diagnostics were published: " + next);
	}
}
