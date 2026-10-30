/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.advantest.markdown.service.MarkdownService;

/**
 * The Markdown language server: offers a {@link MarkdownService} to an editor through the Language
 * Server Protocol.
 *
 * <p>The server is given its service and owns it from then on: it closes the service when the
 * client asks it to shut down, or when the client tells it to exit without having asked that.
 * {@link #launch(MarkdownService, InputStream, OutputStream)} starts a server on any two streams;
 * {@link #main(String[])} starts one as a program of its own.</p>
 */
public final class MarkdownLanguageServer implements LanguageServer, LanguageClientAware {

	static final String NAME = "markdown-ls";

	private static final Logger LOG = LoggerFactory.getLogger(MarkdownLanguageServer.class);

	private final MarkdownService service;
	private final MarkdownTextDocumentService documents;
	private final CompletableFuture<Future<Void>> listening = new CompletableFuture<>();
	private volatile LanguageClient client;
	private volatile boolean shutdownRequested;

	private MarkdownLanguageServer(MarkdownService service) {
		this.service = Objects.requireNonNull(service, "A server needs a service.");
		this.documents = new MarkdownTextDocumentService(service, () -> this.shutdownRequested);
	}

	/**
	 * Starts a server, which reads the messages of its client from one stream and writes its own to
	 * the other, and returns once it listens.
	 *
	 * <p>The server owns the given service from now on and closes it. It listens until the client
	 * tells it to exit, which cancels the returned future, or until the input ends, which completes
	 * it. The streams are not closed by the server, except that the input is when the client tells
	 * the server to exit.</p>
	 *
	 * @param service the service the server offers, not <code>null</code>
	 * @param in the stream the client's messages arrive on, not <code>null</code>
	 * @param out the stream the server's messages are written to, not <code>null</code>
	 * @return the server's listening, which ends when the server does
	 */
	public static Future<Void> launch(MarkdownService service, InputStream in, OutputStream out) {
		return start(service, in, out).listening.join();
	}

	private static MarkdownLanguageServer start(MarkdownService service, InputStream in, OutputStream out) {
		Objects.requireNonNull(in, "A server needs a stream to read from.");
		Objects.requireNonNull(out, "A server needs a stream to write to.");
		MarkdownLanguageServer server = new MarkdownLanguageServer(service);
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);
		server.connect(launcher.getRemoteProxy());
		server.listening.complete(launcher.startListening());
		return server;
	}

	/**
	 * Starts the server as a program of its own, which speaks the protocol on standard input and
	 * output and offers a service asking every web address a document names whether it is there.
	 *
	 * <p>Standard output belongs to the protocol, so everything else written to
	 * {@link System#out} goes to standard error instead. The program ends when the client tells
	 * the server to exit or when standard input ends, with exit code 0 if the client asked the
	 * server to shut down before, and 1 otherwise.</p>
	 *
	 * @param args the command line arguments, none so far
	 */
	public static void main(String[] args) {
		PrintStream protocolOut = System.out;
		System.setOut(System.err);

		MarkdownLanguageServer server = start(MarkdownService.builderCheckingUriReachability().build(),
				System.in, protocolOut);
		try {
			server.listening.join().get();
		} catch (CancellationException exitRequested) {
			// the client told the server to exit
		} catch (ExecutionException e) {
			LOG.error("The server stopped listening to its client.", e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		server.service.close();
		System.exit(server.shutdownRequested ? 0 : 1);
	}

	@Override
	public void connect(LanguageClient client) {
		this.client = client;
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		ServerCapabilities capabilities = new ServerCapabilities();
		capabilities.setTextDocumentSync(MarkdownTextDocumentService.syncOptions());
		return CompletableFuture.completedFuture(new InitializeResult(capabilities, new ServerInfo(NAME)));
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		this.shutdownRequested = true;
		this.service.close();
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void exit() {
		this.service.close();
		this.listening.thenAccept(future -> future.cancel(true));
	}

	@Override
	public TextDocumentService getTextDocumentService() {
		return this.documents;
	}

	/**
	 * The server offers no capability on the workspace yet, so it has no service for it.
	 */
	@Override
	public WorkspaceService getWorkspaceService() {
		return null;
	}
}
