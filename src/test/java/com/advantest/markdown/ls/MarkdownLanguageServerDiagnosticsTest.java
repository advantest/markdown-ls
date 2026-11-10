/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.advantest.markdown.service.MarkdownService;

/**
 * Edits a document, as a client connected to the server by two pipes would, and checks the
 * diagnostics the server publishes for it.
 */
class MarkdownLanguageServerDiagnosticsTest {

	private static final long TIMEOUT_SECONDS = 10;
	private static final String BROKEN = "# Title\n\nSee [the target](target.md#missing).\n";

	@TempDir
	Path folder;

	private final RecordingClient client = new RecordingClient();
	private Future<Void> serverListening;
	private Future<Void> clientListening;
	private LanguageServer server;
	private String uri;

	@BeforeEach
	void launchAndInitialize() throws Exception {
		Files.writeString(this.folder.resolve("target.md"), "# Target\n");
		this.uri = this.folder.resolve("document.md").toUri().toString();

		PipedInputStream serverIn = new PipedInputStream();
		PipedOutputStream clientOut = new PipedOutputStream(serverIn);
		PipedInputStream clientIn = new PipedInputStream();
		PipedOutputStream serverOut = new PipedOutputStream(clientIn);
		this.serverListening = MarkdownLanguageServer.launch(
				MarkdownService.builderNotCheckingUriReachability().build(), serverIn, serverOut);
		Launcher<LanguageServer> launcher = LSPLauncher.createClientLauncher(this.client, clientIn, clientOut);
		this.clientListening = launcher.startListening();
		this.server = launcher.getRemoteProxy();

		this.server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());
	}

	@AfterEach
	void shutDownAndExit() throws Exception {
		this.server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.exit();
		this.clientListening.cancel(true);
		this.serverListening.cancel(true);
	}

	@Test
	void openingADocumentPublishesItsIssues() throws InterruptedException {
		open(BROKEN);

		PublishDiagnosticsParams published = this.client.diagnostics.next();
		assertEquals(this.uri, published.getUri());
		assertEquals(1, published.getVersion());
		assertEquals(1, published.getDiagnostics().size());
		Diagnostic diagnostic = published.getDiagnostics().get(0);
		assertEquals(DiagnosticSeverity.Error, diagnostic.getSeverity());
		assertEquals(Diagnostics.SOURCE, diagnostic.getSource());
		assertEquals(2, diagnostic.getRange().getStart().getLine());
	}

	@Test
	void fixingTheIssueRemovesItOnceTheEditsPause() throws InterruptedException {
		open(BROKEN);
		this.client.diagnostics.next();

		change(2, "# Title\n\nSee [the target](target.md#mis).\n");
		change(3, "# Title\n\nSee [the target](target.md).\n");

		PublishDiagnosticsParams published = this.client.diagnostics.next();
		assertEquals(3, published.getVersion());
		assertEquals(List.of(), published.getDiagnostics());
		this.client.diagnostics.assertNoneWithin(Duration.ofMillis(500));
	}

	@Test
	void closingTheDocumentRemovesItsIssues() throws InterruptedException {
		open(BROKEN);
		this.client.diagnostics.next();

		this.server.getTextDocumentService()
				.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(this.uri)));

		PublishDiagnosticsParams published = this.client.diagnostics.next();
		assertEquals(this.uri, published.getUri());
		assertEquals(List.of(), published.getDiagnostics());
	}

	private void open(String text) {
		this.server.getTextDocumentService()
				.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(this.uri, "markdown", 1, text)));
	}

	private void change(int version, String text) {
		this.server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
				new VersionedTextDocumentIdentifier(this.uri, version),
				List.of(new TextDocumentContentChangeEvent(text))));
	}
}
