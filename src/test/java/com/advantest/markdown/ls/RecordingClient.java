/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import org.eclipse.lsp4j.PublishDiagnosticsParams;

/**
 * A client that keeps the diagnostics the server publishes and ignores everything else.
 */
final class RecordingClient extends SilentClient {

	final PublishedDiagnostics diagnostics = new PublishedDiagnostics();

	@Override
	public void publishDiagnostics(PublishDiagnosticsParams published) {
		this.diagnostics.accept(published);
	}
}
