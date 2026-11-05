/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.ServerInfo;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
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
 * {@link #serve(MarkdownService, InputStream, OutputStream)} serves a client on them until it ends;
 * {@link #main(String[])} starts one as a program of its own.</p>
 */
public final class MarkdownLanguageServer implements LanguageServer, LanguageClientAware {

	static final String NAME = "markdown-ls";

	static final String ENVIRONMENT = "markdown/environment";

	private static final Logger LOG = LoggerFactory.getLogger(MarkdownLanguageServer.class);

	private final MarkdownService service;
	private final DocumentValidator validator;
	private final ExecutorService renderer = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "markdown-ls rendering");
		thread.setDaemon(true);
		return thread;
	});
	private final MarkdownTextDocumentService documents;
	private final List<Library> furtherLibraries;
	private final DotExecutableSetting dotExecutable = new DotExecutableSetting();
	private final MarkdownWorkspaceService workspace = new MarkdownWorkspaceService(this::useDotExecutable);
	private final CompletableFuture<Future<Void>> listening = new CompletableFuture<>();
	private volatile LanguageClient client;
	private volatile boolean shutdownRequested;

	private MarkdownLanguageServer(MarkdownService service, List<Library> furtherLibraries) {
		this.service = Objects.requireNonNull(service, "A server needs a service.");
		this.furtherLibraries = List.copyOf(furtherLibraries);
		this.validator = new DocumentValidator(service, this::publish, DocumentValidator.DELAY_AFTER_CHANGE);
		this.documents = new MarkdownTextDocumentService(service, () -> this.shutdownRequested, this.validator,
				this.renderer);
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
		return start(service, List.of(), in, out).listening.join();
	}

	private static MarkdownLanguageServer start(MarkdownService service, List<Library> furtherLibraries,
			InputStream in, OutputStream out) {
		Objects.requireNonNull(furtherLibraries, "A server needs a list of further libraries, if an empty one.");
		Objects.requireNonNull(in, "A server needs a stream to read from.");
		Objects.requireNonNull(out, "A server needs a stream to write to.");
		MarkdownLanguageServer server = new MarkdownLanguageServer(service, furtherLibraries);
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, in, out);
		server.connect(launcher.getRemoteProxy());
		server.listening.complete(launcher.startListening());
		return server;
	}

	/**
	 * Serves a client, which writes its messages to one stream and reads the server's from the other,
	 * until it tells the server to exit or the input ends, then closes the service and answers the
	 * exit code: 0 if the client asked the server to shut down before, 1 otherwise.
	 *
	 * <p>The server owns the given service from now on and closes it. The streams are not closed by
	 * the server, except that the input is when the client tells the server to exit. If the thread
	 * serving is interrupted, the server stops serving, closes the service and keeps the thread's
	 * interrupt status set.</p>
	 *
	 * @param service the service the server offers, not <code>null</code>
	 * @param in the stream the client's messages arrive on, not <code>null</code>
	 * @param out the stream the server's messages are written to, not <code>null</code>
	 * @return the exit code, 0 if the client asked the server to shut down before it ended, 1 otherwise
	 * @see #serve(MarkdownService, List, InputStream, OutputStream)
	 */
	public static int serve(MarkdownService service, InputStream in, OutputStream out) {
		return serve(service, List.of(), in, out);
	}

	/**
	 * Serves a client as {@link #serve(MarkdownService, InputStream, OutputStream)} does, and names
	 * further libraries in the answer to <code>markdown/environment</code>, after the server's own: those
	 * a launcher adds, e.g. the ones its service is built from.
	 *
	 * @param service the service the server offers, not <code>null</code>
	 * @param furtherLibraries the libraries to name after the server's own, in this order, not
	 *        <code>null</code>
	 * @param in the stream the client's messages arrive on, not <code>null</code>
	 * @param out the stream the server's messages are written to, not <code>null</code>
	 * @return the exit code, 0 if the client asked the server to shut down before it ended, 1 otherwise
	 */
	public static int serve(MarkdownService service, List<Library> furtherLibraries, InputStream in,
			OutputStream out) {
		MarkdownLanguageServer server = start(service, furtherLibraries, in, out);
		try {
			server.listening.join().get();
		} catch (CancellationException exitRequested) {
			// the client told the server to exit
		} catch (ExecutionException e) {
			LOG.error("The server stopped listening to its client.", e.getCause());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		server.closeService();
		return server.shutdownRequested ? 0 : 1;
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
	 * @see #serve(MarkdownService, InputStream, OutputStream)
	 */
	public static void main(String[] args) {
		PrintStream protocolOut = System.out;
		System.setOut(System.err);
		System.exit(serve(MarkdownService.builderCheckingUriReachability().build(), System.in, protocolOut));
	}

	@Override
	public void connect(LanguageClient client) {
		this.client = client;
	}

	private void publish(PublishDiagnosticsParams diagnostics) {
		this.client.publishDiagnostics(diagnostics);
	}

	@Override
	public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		DotExecutableSetting.fromInitializationOptions(params.getInitializationOptions())
				.ifPresent(this::useDotExecutable);
		ServerCapabilities capabilities = new ServerCapabilities();
		capabilities.setTextDocumentSync(MarkdownTextDocumentService.syncOptions());
		return CompletableFuture.completedFuture(new InitializeResult(capabilities, new ServerInfo(NAME, version())));
	}

	/**
	 * Answers the server's version, which its JAR's manifest names.
	 *
	 * @return the version, <code>null</code> if the server runs from classes without a manifest, e.g.
	 *         in a test, in which case the client is told no version
	 */
	static String version() {
		return MarkdownLanguageServer.class.getPackage().getImplementationVersion();
	}

	@Override
	public CompletableFuture<Object> shutdown() {
		this.shutdownRequested = true;
		closeService();
		return CompletableFuture.completedFuture(null);
	}

	@Override
	public void exit() {
		closeService();
		this.listening.thenAccept(future -> future.cancel(true));
	}

	private void closeService() {
		this.validator.close();
		// renderings already requested still end, with an error once the service is closed
		this.renderer.shutdown();
		this.service.close();
	}

	@Override
	public TextDocumentService getTextDocumentService() {
		return this.documents;
	}

	/**
	 * Answers the request {@value MarkdownTextDocumentService#RENDER_HTML}: the HTML of an open document.
	 *
	 * <p>The request is declared here rather than on the document service, since LSP4J learns the
	 * parameter types of the requests from the server's class.</p>
	 *
	 * @see MarkdownTextDocumentService#renderHtml(RenderHtmlParams)
	 */
	@JsonRequest(MarkdownTextDocumentService.RENDER_HTML)
	public CompletableFuture<RenderHtmlResult> renderHtml(RenderHtmlParams params) {
		return this.documents.renderHtml(params);
	}

	/**
	 * Answers the request {@value #ENVIRONMENT}: the libraries the server runs on and how PlantUML is
	 * set up, asked on the thread rendering, since Graphviz is run to learn its version.
	 *
	 * @return the environment, or an error with the code <code>InvalidRequest</code> once the client
	 *         asked the server to shut down
	 */
	@JsonRequest(ENVIRONMENT)
	public CompletableFuture<Environment> environment() {
		try {
			if (!this.shutdownRequested) {
				return CompletableFuture.supplyAsync(() -> Environment.describe(this.furtherLibraries), this.renderer);
			}
		} catch (RejectedExecutionException shutDownMeanwhile) {
			// answered below like a request arriving after shutdown
		}
		return CompletableFuture.failedFuture(new ResponseErrorException(new ResponseError(
				ResponseErrorCode.InvalidRequest, "The server is shut down.", null)));
	}

	/**
	 * Names the Graphviz <code>dot</code> executable PlantUML runs, as the user set it, and tells the
	 * user if nothing can be run there, in which case the executable in effect stays.
	 */
	private void useDotExecutable(String path) {
		try {
			this.dotExecutable.apply(path);
		} catch (IllegalArgumentException e) {
			LOG.warn("The Graphviz dot executable was not changed.", e);
			this.client.showMessage(new MessageParams(MessageType.Warning, e.getMessage()
					+ " PlantUML keeps running the Graphviz it ran before."));
		}
	}

	@Override
	public WorkspaceService getWorkspaceService() {
		return this.workspace;
	}
}
