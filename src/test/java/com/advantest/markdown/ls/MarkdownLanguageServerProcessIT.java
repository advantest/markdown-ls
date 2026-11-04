/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Starts the server as a program of its own and talks to it over its standard input and output, as
 * an editor does.
 */
class MarkdownLanguageServerProcessIT {

	private static final long TIMEOUT_SECONDS = 30;
	private static final Pattern MESSAGE_HEADER = Pattern.compile("Content-Length: (\\d+)\r\n\r\n");

	@TempDir
	Path folder;

	private final RecordingClient client = new RecordingClient();
	private Process process;
	private RecordingInputStream serverOut;
	private Future<Void> clientListening;
	private MarkdownLanguageServerProxy server;

	@BeforeEach
	void startServer() throws IOException {
		List<String> command = new ArrayList<>();
		command.add(ProcessHandle.current().info().command().orElseThrow());
		// The build hands in the coverage agent here; started from elsewhere the option is absent.
		String jvmOption = System.getProperty("markdown-ls.server-jvm-option", "");
		if (!jvmOption.isBlank()) {
			command.add(jvmOption);
		}
		command.addAll(List.of("-cp", System.getProperty("java.class.path"),
				MarkdownLanguageServer.class.getName()));
		this.process = new ProcessBuilder(command)
				.redirectError(ProcessBuilder.Redirect.INHERIT)
				.start();

		this.serverOut = new RecordingInputStream(this.process.getInputStream());
		Launcher<MarkdownLanguageServerProxy> client = MarkdownLanguageServerProxy.connect(this.client,
				this.serverOut, this.process.getOutputStream());
		this.clientListening = client.startListening();
		this.server = client.getRemoteProxy();
	}

	@AfterEach
	void stopServer() {
		this.clientListening.cancel(true);
		this.process.destroyForcibly();
	}

	@Test
	void exitsWithZeroAfterShutdownAndWritesOnlyProtocolMessages() throws Exception {
		this.server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());
		this.server.shutdown().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		this.server.exit();

		assertEquals(0, awaitExitCode());
		assertOnlyProtocolMessages(this.serverOut.recorded());
	}

	@Test
	void exitsWithOneWithoutShutdown() throws Exception {
		this.server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());

		this.server.exit();

		assertEquals(1, awaitExitCode());
	}

	@Test
	void namesTheVersionOfItsJarOnInitialize() throws Exception {
		String expected = System.getProperty("markdown-ls.version");
		assertNotNull(expected, "The build hands in the version the JAR is expected to name.");

		InitializeResult result = this.server.initialize(new InitializeParams())
				.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertEquals(MarkdownLanguageServer.NAME, result.getServerInfo().getName());
		assertEquals(expected, result.getServerInfo().getVersion());
	}

	@Test
	void publishesTheIssuesOfAnOpenedDocument() throws Exception {
		Files.writeString(this.folder.resolve("target.md"), "# Target\n");
		String uri = this.folder.resolve("document.md").toUri().toString();
		this.server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());

		this.server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri, "markdown", 1, "[Target](target.md#missing)\n")));

		PublishDiagnosticsParams published = this.client.diagnostics.next();
		assertEquals(uri, published.getUri());
		assertEquals(1, published.getDiagnostics().size());
	}

	@Test
	void answersTheHtmlOfAnOpenedDocument() throws Exception {
		String uri = this.folder.resolve("document.md").toUri().toString();
		this.server.initialize(new InitializeParams()).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		this.server.initialized(new InitializedParams());
		this.server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(
				new TextDocumentItem(uri, "markdown", 4, "# Title\n\nSome *text*\n")));

		RenderHtmlResult result = this.server.renderHtml(new RenderHtmlParams(new TextDocumentIdentifier(uri)))
				.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		assertEquals(uri, result.uri());
		assertEquals(4, result.version());
		assertTrue(result.html().contains("<em"), result.html());
	}

	private int awaitExitCode() throws InterruptedException {
		assertTrue(this.process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS), "The server did not end.");
		return this.process.exitValue();
	}

	private static void assertOnlyProtocolMessages(byte[] output) {
		String text = new String(output, StandardCharsets.UTF_8);
		int position = 0;
		int messages = 0;
		while (position < output.length) {
			Matcher header = MESSAGE_HEADER.matcher(text).region(position, text.length());
			if (!header.lookingAt()) {
				fail("Standard output holds something else than a protocol message at byte " + position
						+ ": " + text.substring(position));
			}
			int contentStart = header.end();
			int contentLength = Integer.parseInt(header.group(1));
			String content = new String(output, contentStart, contentLength, StandardCharsets.UTF_8);
			assertTrue(content.startsWith("{") && content.endsWith("}"), "Not a JSON object: " + content);
			position = contentStart + contentLength;
			messages++;
		}
		assertEquals(2, messages, "The answers to initialize and shutdown were expected.");
	}

	/**
	 * Keeps a copy of everything read from the stream it wraps.
	 */
	private static final class RecordingInputStream extends FilterInputStream {

		private final ByteArrayOutputStream recorded = new ByteArrayOutputStream();

		RecordingInputStream(InputStream in) {
			super(in);
		}

		@Override
		public synchronized int read() throws IOException {
			int read = super.read();
			if (read >= 0) {
				this.recorded.write(read);
			}
			return read;
		}

		@Override
		public synchronized int read(byte[] buffer, int offset, int length) throws IOException {
			int read = super.read(buffer, offset, length);
			if (read > 0) {
				this.recorded.write(buffer, offset, read);
			}
			return read;
		}

		synchronized byte[] recorded() {
			return this.recorded.toByteArray();
		}
	}
}
