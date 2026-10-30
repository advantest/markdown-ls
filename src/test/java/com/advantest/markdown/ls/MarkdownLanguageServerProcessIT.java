/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Starts the server as a program of its own and talks to it over its standard input and output, as
 * an editor does.
 */
class MarkdownLanguageServerProcessIT {

	private static final long TIMEOUT_SECONDS = 30;
	private static final Pattern MESSAGE_HEADER = Pattern.compile("Content-Length: (\\d+)\r\n\r\n");

	private Process process;
	private RecordingInputStream serverOut;
	private Future<Void> clientListening;
	private LanguageServer server;

	@BeforeEach
	void startServer() throws IOException {
		String java = ProcessHandle.current().info().command().orElseThrow();
		this.process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
				MarkdownLanguageServer.class.getName())
				.redirectError(ProcessBuilder.Redirect.INHERIT)
				.start();

		this.serverOut = new RecordingInputStream(this.process.getInputStream());
		Launcher<LanguageServer> client = LSPLauncher.createClientLauncher(new SilentClient(), this.serverOut,
				this.process.getOutputStream());
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
