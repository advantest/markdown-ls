/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;

/**
 * The server as a client sees it: a language server that also answers the requests markdown-ls
 * adds to the protocol.
 */
interface MarkdownLanguageServerProxy extends LanguageServer {

	@JsonRequest(MarkdownTextDocumentService.RENDER_HTML)
	CompletableFuture<RenderHtmlResult> renderHtml(RenderHtmlParams params);

	@JsonRequest(MarkdownLanguageServer.ENVIRONMENT)
	CompletableFuture<Environment> environment();

	/**
	 * Connects the given client to a server reading from <code>in</code> and writing to
	 * <code>out</code>.
	 */
	static Launcher<MarkdownLanguageServerProxy> connect(LanguageClient client, InputStream in, OutputStream out) {
		return new LSPLauncher.Builder<MarkdownLanguageServerProxy>()
				.setLocalService(client)
				.setRemoteInterface(MarkdownLanguageServerProxy.class)
				.setInput(in)
				.setOutput(out)
				.create();
	}
}
