/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.advantest.markdown.service.MarkdownService;
import com.google.gson.JsonParser;

/**
 * Asks a server served to a client by two pipes about its environment, and names the Graphviz
 * <code>dot</code> executable it shall run, as a client would.
 */
class MarkdownLanguageServerEnvironmentTest {

	private static final long TIMEOUT_SECONDS = 10;

	private static final Library FURTHER = new Library("further-library", "4.5.6");

	@TempDir
	Path folder;

	private final BlockingQueue<MessageParams> messages = new LinkedBlockingQueue<>();
	private String executableBefore;
	private String dot;
	private CompletableFuture<Integer> exitCode;
	private Future<Void> clientListening;
	private MarkdownLanguageServerProxy server;

	@BeforeEach
	void serveClient() throws Exception {
		this.executableBefore = MarkdownService.getDotExecutable().orElse(null);
		this.dot = DotExecutableSettingTest.createExecutable(this.folder.resolve("dot.exe"));

		PipedInputStream serverIn = new PipedInputStream();
		PipedOutputStream clientOut = new PipedOutputStream(serverIn);
		PipedInputStream clientIn = new PipedInputStream();
		PipedOutputStream serverOut = new PipedOutputStream(clientIn);
		MarkdownService service = MarkdownService.builderNotCheckingUriReachability().build();
		this.exitCode = CompletableFuture.supplyAsync(
				() -> MarkdownLanguageServer.serve(service, List.of(FURTHER), serverIn, serverOut));

		SilentClient client = new SilentClient() {
			@Override
			public void showMessage(MessageParams message) {
				MarkdownLanguageServerEnvironmentTest.this.messages.add(message);
			}
		};
		Launcher<MarkdownLanguageServerProxy> launcher = MarkdownLanguageServerProxy.connect(client, clientIn,
				clientOut);
		this.clientListening = launcher.startListening();
		this.server = launcher.getRemoteProxy();
	}

	@AfterEach
	void stop() throws Exception {
		this.server.exit();
		this.exitCode.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.clientListening.cancel(true);
		MarkdownService.setDotExecutable(this.executableBefore);
	}

	@Test
	void environmentListsTheServersLibrariesAndThenTheFurtherOnes() throws Exception {
		initialize(null);

		Environment environment = environment();

		List<String> names = environment.libraries().stream().map(Library::name).toList();
		assertEquals(List.of("markdown-ls", "markdown-service", "markdown-core", "flexmark-extensions", "flexmark",
				"PlantUML", FURTHER.name()), names);
		assertEquals(FURTHER, environment.libraries().get(6));
		assertEquals(MarkdownService.getPlantUmlSecurityProfile().name(), environment.plantUml().securityProfile());
	}

	@Test
	void theInitializationOptionsNameTheDotExecutable() throws Exception {
		initialize(JsonParser.parseString("{\"plantUml\":{\"dotExecutable\":" + quoted(this.dot) + "}}"));

		Environment environment = environment();

		assertEquals(Optional.of(this.dot), MarkdownService.getDotExecutable());
		assertEquals(this.dot, environment.plantUml().dotExecutable());
		assertNull(environment.plantUml().graphvizVersion(), "The executable is no Graphviz.");
	}

	@Test
	void aChangedConfigurationNamesTheDotExecutableAndAnEmptyOneRestoresTheUsualOne() throws Exception {
		initialize(null);

		changeConfiguration(this.dot);
		assertEquals(this.dot, environment().plantUml().dotExecutable());

		changeConfiguration("");
		environment();
		assertEquals(Optional.ofNullable(this.executableBefore), MarkdownService.getDotExecutable());
	}

	@Test
	void aChangedConfigurationWithoutTheSettingChangesNothing() throws Exception {
		initialize(null);
		changeConfiguration(this.dot);

		this.server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(
				JsonParser.parseString("{\"markdownSupport\":{\"other\":\"value\"}}")));

		assertEquals(this.dot, environment().plantUml().dotExecutable());
	}

	@Test
	void aDotExecutableThatCannotBeRunIsReportedAndKeepsTheOneInEffect() throws Exception {
		initialize(null);
		changeConfiguration(this.dot);

		changeConfiguration(this.folder.resolve("missing.exe").toString());

		MessageParams message = this.messages.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertEquals(MessageType.Warning, message.getType());
		assertTrue(message.getMessage().contains("missing.exe"), message.getMessage());
		assertEquals(this.dot, environment().plantUml().dotExecutable());
	}

	@Test
	void environmentAfterShutdownFails() throws Exception {
		initialize(null);
		this.server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		ExecutionException failure = assertThrows(ExecutionException.class, this::environment);

		ResponseErrorException error = assertInstanceOf(ResponseErrorException.class, failure.getCause());
		assertEquals(ResponseErrorCode.InvalidRequest.getValue(), error.getResponseError().getCode());
	}

	private void initialize(Object initializationOptions) throws Exception {
		InitializeParams params = new InitializeParams();
		params.setInitializationOptions(initializationOptions);
		this.server.initialize(params).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());
	}

	private void changeConfiguration(String dotExecutable) {
		this.server.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(
				JsonParser.parseString("{\"markdownSupport\":{\"plantUml\":{\"dotExecutable\":"
						+ quoted(dotExecutable) + "}}}")));
	}

	private Environment environment() throws Exception {
		return this.server.environment().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private static String quoted(String text) {
		return "\"" + text.replace("\\", "\\\\") + "\"";
	}
}
