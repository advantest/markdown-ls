/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.advantest.markdown.service.MarkdownService;

/**
 * Asks for the HTML of a document, as a client connected to the server by two pipes would.
 */
class MarkdownLanguageServerRenderHtmlTest {

	private static final long TIMEOUT_SECONDS = 10;

	@TempDir
	Path folder;

	private Future<Void> serverListening;
	private Future<Void> clientListening;
	private MarkdownLanguageServerProxy server;
	private String uri;

	@BeforeEach
	void launchAndInitialize() throws Exception {
		this.uri = Files.writeString(this.folder.resolve("document.md"), "# Saved\n").toUri().toString();

		PipedInputStream serverIn = new PipedInputStream();
		PipedOutputStream clientOut = new PipedOutputStream(serverIn);
		PipedInputStream clientIn = new PipedInputStream();
		PipedOutputStream serverOut = new PipedOutputStream(clientIn);
		this.serverListening = MarkdownLanguageServer.launch(
				MarkdownService.builderNotCheckingUriReachability().build(), serverIn, serverOut);
		Launcher<MarkdownLanguageServerProxy> launcher = MarkdownLanguageServerProxy.connect(new SilentClient(),
				clientIn, clientOut);
		this.clientListening = launcher.startListening();
		this.server = launcher.getRemoteProxy();

		this.server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());
	}

	@AfterEach
	void stop() {
		this.clientListening.cancel(true);
		this.serverListening.cancel(true);
	}

	@Test
	void answersTheHtmlOfTheUnsavedTextWithItsVersion() throws Exception {
		open("# Opened\n");
		this.server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
				new VersionedTextDocumentIdentifier(this.uri, 7),
				List.of(new TextDocumentContentChangeEvent("# Changed\n\nSome *text*\n"))));

		RenderHtmlResult result = renderHtml().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertEquals(this.uri, result.uri());
		assertEquals(7, result.version());
		assertTrue(result.html().contains("Changed") && !result.html().contains("Saved"), result.html());
		assertTrue(result.html().contains("source-offset=\""), result.html());
	}

	@Test
	void answersADocumentNotOpenWithRequestFailed() {
		assertErrorCode(ResponseErrorCode.RequestFailed);
	}

	@Test
	void answersARequestAfterShutdownWithInvalidRequest() throws Exception {
		open("# Opened\n");
		this.server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertErrorCode(ResponseErrorCode.InvalidRequest);
	}

	private void open(String text) {
		this.server.getTextDocumentService()
				.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(this.uri, "markdown", 1, text)));
	}

	private CompletableFuture<RenderHtmlResult> renderHtml() {
		return this.server.renderHtml(new RenderHtmlParams(new TextDocumentIdentifier(this.uri)));
	}

	private void assertErrorCode(ResponseErrorCode expected) {
		ExecutionException failure = assertThrows(ExecutionException.class,
				() -> renderHtml().get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		ResponseErrorException error = assertInstanceOf(ResponseErrorException.class, failure.getCause());
		assertEquals(expected.getValue(), error.getResponseError().getCode());
	}
}
