/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * A client that ignores everything the server tells it.
 */
final class SilentClient implements LanguageClient {

	@Override
	public void telemetryEvent(Object object) {
	}

	@Override
	public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
	}

	@Override
	public void showMessage(MessageParams messageParams) {
	}

	@Override
	public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void logMessage(MessageParams message) {
	}
}
