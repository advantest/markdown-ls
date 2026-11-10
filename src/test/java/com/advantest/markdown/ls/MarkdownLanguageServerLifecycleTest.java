/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.advantest.markdown.service.MarkdownService;

/**
 * Drives a server through its lifecycle, as a client connected to it by two pipes would.
 */
class MarkdownLanguageServerLifecycleTest {

	private static final long TIMEOUT_SECONDS = 10;

	private final AtomicBoolean serviceClosed = new AtomicBoolean();
	private Future<Void> serverListening;
	private Future<Void> clientListening;
	private LanguageServer server;

	@BeforeEach
	void launchServerAndClient() throws IOException {
		MarkdownService service = MarkdownService.builderNotCheckingUriReachability()
				.withPartToClose(() -> this.serviceClosed.set(true))
				.build();

		PipedInputStream serverIn = new PipedInputStream();
		PipedOutputStream clientOut = new PipedOutputStream(serverIn);
		PipedInputStream clientIn = new PipedInputStream();
		PipedOutputStream serverOut = new PipedOutputStream(clientIn);

		this.serverListening = MarkdownLanguageServer.launch(service, serverIn, serverOut);

		Launcher<LanguageServer> client = LSPLauncher.createClientLauncher(new SilentClient(), clientIn, clientOut);
		this.clientListening = client.startListening();
		this.server = client.getRemoteProxy();
	}

	@AfterEach
	void stopClientAndServer() {
		this.clientListening.cancel(true);
		this.serverListening.cancel(true);
	}

	@Test
	void initializeNamesTheServer() throws Exception {
		InitializeResult result = initialize();

		assertEquals(MarkdownLanguageServer.NAME, result.getServerInfo().getName());
	}

	@Test
	void initializeNamesTheVersionOfTheBuild() throws Exception {
		InitializeResult result = initialize();

		assertEquals(System.getProperty("markdown-ls.version"), result.getServerInfo().getVersion());
	}

	@Test
	void initializeOffersToKeepTheWholeTextOfTheDocuments() throws Exception {
		InitializeResult result = initialize();

		TextDocumentSyncOptions sync = result.getCapabilities().getTextDocumentSync().getRight();
		assertTrue(sync.getOpenClose());
		assertEquals(TextDocumentSyncKind.Full, sync.getChange());
		assertFalse(sync.getSave().getRight().getIncludeText());
	}

	@Test
	void shutdownClosesTheServiceButKeepsListening() throws Exception {
		initialize();

		this.server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertTrue(this.serviceClosed.get());
		assertFalse(this.serverListening.isDone());
	}

	@Test
	void exitAfterShutdownEndsListening() throws Exception {
		initialize();
		this.server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		this.server.exit();

		assertThrows(CancellationException.class,
				() -> this.serverListening.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
	}

	@Test
	void exitWithoutShutdownClosesTheServiceAndEndsListening() throws Exception {
		initialize();

		this.server.exit();

		assertThrows(CancellationException.class,
				() -> this.serverListening.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertTrue(this.serviceClosed.get());
	}

	private InitializeResult initialize() throws Exception {
		InitializeResult result = this.server.initialize(new InitializeParams())
				.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());
		return result;
	}
}
