/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.advantest.markdown.service.MarkdownService;

/**
 * Serves a client connected by two pipes until it ends, and checks the exit code the server answers.
 */
class MarkdownLanguageServerServeTest {

	private static final long TIMEOUT_SECONDS = 10;

	private final AtomicBoolean serviceClosed = new AtomicBoolean();
	private CompletableFuture<Integer> exitCode;
	private PipedOutputStream clientOut;
	private Future<Void> clientListening;
	private LanguageServer server;

	@BeforeEach
	void serveClient() throws IOException {
		MarkdownService service = MarkdownService.builderNotCheckingUriReachability()
				.withPartToClose(() -> this.serviceClosed.set(true))
				.build();

		PipedInputStream serverIn = new PipedInputStream();
		this.clientOut = new PipedOutputStream(serverIn);
		PipedInputStream clientIn = new PipedInputStream();
		PipedOutputStream serverOut = new PipedOutputStream(clientIn);

		this.exitCode = CompletableFuture.supplyAsync(
				() -> MarkdownLanguageServer.serve(service, serverIn, serverOut));

		Launcher<LanguageServer> client = LSPLauncher.createClientLauncher(new SilentClient(), clientIn,
				this.clientOut);
		this.clientListening = client.startListening();
		this.server = client.getRemoteProxy();
	}

	@AfterEach
	void stopClient() {
		this.clientListening.cancel(true);
	}

	@Test
	void exitAfterShutdownAnswersExitCode0() throws Exception {
		initialize();
		this.server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		this.server.exit();

		assertEquals(0, this.exitCode.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertTrue(this.serviceClosed.get());
	}

	@Test
	void exitWithoutShutdownAnswersExitCode1AndClosesTheService() throws Exception {
		initialize();

		this.server.exit();

		assertEquals(1, this.exitCode.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertTrue(this.serviceClosed.get());
	}

	@Test
	void endOfInputAnswersExitCode1AndClosesTheService() throws Exception {
		initialize();

		this.clientOut.close();

		assertEquals(1, this.exitCode.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		assertTrue(this.serviceClosed.get());
	}

	private void initialize() throws Exception {
		this.server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());
	}
}
