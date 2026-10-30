/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.advantest.markdown.service.MarkdownService;
import com.advantest.markdown.service.validation.ValidationIssue;
import com.advantest.resources.LocalFileSystemResource;

/**
 * Checks that the open documents are kept and that the service reads a file as the text the client
 * holds for it until the client closes it.
 */
class MarkdownTextDocumentServiceTest {

	private static final String LINKING_TEXT = "[Target](target.md#new-heading)\n";

	@TempDir
	Path folder;

	private final AtomicBoolean shutDown = new AtomicBoolean();
	private final PublishedDiagnostics published = new PublishedDiagnostics();
	private final ExecutorService renderThread = Executors.newSingleThreadExecutor();
	private final AtomicInteger rendered = new AtomicInteger();
	private MarkdownService service;
	private DocumentValidator validator;
	private MarkdownTextDocumentService documents;
	private Path target;
	private Path linking;

	@BeforeEach
	void createServiceAndFiles() throws IOException {
		this.service = MarkdownService.builderNotCheckingUriReachability().build();
		this.validator = new DocumentValidator(this.service, this.published, Duration.ofMillis(50));
		Executor renderer = task -> {
			this.rendered.incrementAndGet();
			this.renderThread.execute(task);
		};
		this.documents = new MarkdownTextDocumentService(this.service, this.shutDown::get, this.validator, renderer);
		this.target = Files.writeString(this.folder.resolve("target.md"), "# Old heading\n");
		this.linking = Files.writeString(this.folder.resolve("linking.md"), LINKING_TEXT);
	}

	@AfterEach
	void closeService() {
		this.validator.close();
		this.renderThread.shutdownNow();
		this.service.close();
	}

	@Test
	void syncOptionsAskForTheWholeTextOnChangeAndForSavingWithoutText() {
		TextDocumentSyncOptions options = MarkdownTextDocumentService.syncOptions();

		assertTrue(options.getOpenClose());
		assertEquals(TextDocumentSyncKind.Full, options.getChange());
		assertFalse(options.getSave().getRight().getIncludeText());
	}

	@Test
	void openKeepsTheText() {
		open(uriOf(this.target), "# New heading {#new-heading}\n");

		assertEquals(Optional.of("# New heading {#new-heading}\n"), this.documents.textOf(uriOf(this.target)));
	}

	@Test
	void changeReplacesTheTextWithTheLastChange() {
		open(uriOf(this.target), "# One\n");

		change(uriOf(this.target), "# Two\n", "# Three\n");

		assertEquals(Optional.of("# Three\n"), this.documents.textOf(uriOf(this.target)));
	}

	@Test
	void changeWithoutTextChangesNothing() {
		open(uriOf(this.target), "# One\n");

		change(uriOf(this.target));

		assertEquals(Optional.of("# One\n"), this.documents.textOf(uriOf(this.target)));
	}

	@Test
	void changeOfADocumentNotOpenIsIgnored() {
		change(uriOf(this.target), "# Two\n");

		assertEquals(Optional.empty(), this.documents.textOf(uriOf(this.target)));
	}

	@Test
	void closeForgetsTheText() {
		open(uriOf(this.target), "# One\n");

		close(uriOf(this.target));

		assertEquals(Optional.empty(), this.documents.textOf(uriOf(this.target)));
	}

	@Test
	void closeOfADocumentNotOpenIsIgnored() {
		assertDoesNotThrow(() -> close(uriOf(this.target)));
	}

	@Test
	void saveKeepsTheText() {
		open(uriOf(this.target), "# One\n");

		this.documents.didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uriOf(this.target))));

		assertEquals(Optional.of("# One\n"), this.documents.textOf(uriOf(this.target)));
	}

	@Test
	void theServiceReadsAnOpenFileAsTheTextOfTheClient() {
		assertEquals(1, issuesOfLinkingDocument().size(), "The saved file has no such heading.");

		open(uriOf(this.target), "# New heading {#new-heading}\n");

		assertEquals(List.of(), issuesOfLinkingDocument());
	}

	@Test
	void theServiceReadsAChangedFileAsTheLatestTextOfTheClient() {
		open(uriOf(this.target), "# Old heading\n");

		change(uriOf(this.target), "# New heading {#new-heading}\n");

		assertEquals(List.of(), issuesOfLinkingDocument());
	}

	@Test
	void theServiceReadsAClosedFileAsSavedAgain() {
		open(uriOf(this.target), "# New heading {#new-heading}\n");

		close(uriOf(this.target));

		assertEquals(1, issuesOfLinkingDocument().size());
	}

	@Test
	void aDocumentThatIsNoFileIsKeptWithoutTellingTheService() {
		open("untitled:Untitled-1", "# New heading {#new-heading}\n");
		change("untitled:Untitled-1", "# Newer heading\n");

		assertEquals(Optional.of("# Newer heading\n"), this.documents.textOf("untitled:Untitled-1"));
		close("untitled:Untitled-1");
		assertEquals(Optional.empty(), this.documents.textOf("untitled:Untitled-1"));
	}

	@Test
	void aDocumentWithAMalformedUriIsKeptWithoutTellingTheService() {
		open("file:not a uri", "# New heading {#new-heading}\n");

		assertEquals(Optional.of("# New heading {#new-heading}\n"), this.documents.textOf("file:not a uri"));
	}

	@Test
	void openValidatesTheDocument() throws InterruptedException {
		open(uriOf(this.linking), LINKING_TEXT);

		PublishDiagnosticsParams diagnostics = this.published.next();
		assertEquals(uriOf(this.linking), diagnostics.getUri());
		assertEquals(1, diagnostics.getVersion());
		assertEquals(1, diagnostics.getDiagnostics().size());
	}

	@Test
	void changeValidatesTheLatestText() throws InterruptedException {
		open(uriOf(this.linking), LINKING_TEXT);
		this.published.next();

		change(uriOf(this.linking), "No link\n");

		PublishDiagnosticsParams diagnostics = this.published.next();
		assertEquals(2, diagnostics.getVersion());
		assertEquals(List.of(), diagnostics.getDiagnostics());
	}

	@Test
	void saveValidatesTheDocumentAgainstFilesSavedMeanwhile() throws InterruptedException, IOException {
		open(uriOf(this.linking), LINKING_TEXT);
		assertEquals(1, this.published.next().getDiagnostics().size());
		Files.writeString(this.target, "# New heading {#new-heading}\n");

		save(uriOf(this.linking));

		assertEquals(List.of(), this.published.next().getDiagnostics());
	}

	@Test
	void saveOfADocumentNotOpenValidatesNothing() throws InterruptedException {
		save(uriOf(this.linking));

		this.published.assertNoneWithin(Duration.ofMillis(200));
	}

	@Test
	void closeRemovesTheDiagnostics() throws InterruptedException {
		open(uriOf(this.linking), LINKING_TEXT);
		this.published.next();

		close(uriOf(this.linking));

		PublishDiagnosticsParams diagnostics = this.published.next();
		assertEquals(uriOf(this.linking), diagnostics.getUri());
		assertEquals(List.of(), diagnostics.getDiagnostics());
	}

	@Test
	void aDocumentThatIsNoFileIsValidatedAsWell() throws InterruptedException {
		open("untitled:Untitled-1", "[Missing](#nowhere)\n");

		assertEquals(1, this.published.next().getDiagnostics().size());
	}

	@Test
	void notificationsAfterShutdownAreIgnored() {
		open(uriOf(this.target), "# One\n");
		this.shutDown.set(true);
		this.validator.close();
		this.service.close();

		open(uriOf(this.linking), LINKING_TEXT);
		change(uriOf(this.target), "# Two\n");
		save(uriOf(this.target));
		close(uriOf(this.target));

		assertEquals(Optional.of("# One\n"), this.documents.textOf(uriOf(this.target)));
		assertEquals(Optional.empty(), this.documents.textOf(uriOf(this.linking)));
	}

	@Test
	void renderHtmlRendersTheLatestTextOfTheClientOnTheRenderer() throws Exception {
		open(uriOf(this.target), "# One\n");
		change(uriOf(this.target), "# Two {#two}\n\nUnsaved *text*\n");

		RenderHtmlResult result = renderHtml(uriOf(this.target));

		assertEquals(uriOf(this.target), result.uri());
		assertEquals(2, result.version());
		assertTrue(result.html().contains("<em"), result.html());
		assertTrue(result.html().contains("Unsaved"), result.html());
		assertTrue(result.html().contains("source-offset"), "Source positions are kept: " + result.html());
		assertEquals(1, this.rendered.get());
	}

	@Test
	void renderHtmlRendersADocumentThatIsNoFile() throws Exception {
		open("untitled:Untitled-1", "Some *text*\n");

		RenderHtmlResult result = renderHtml("untitled:Untitled-1");

		assertEquals(1, result.version());
		assertTrue(result.html().contains("<em"), result.html());
	}

	@Test
	void renderHtmlOfADocumentNotOpenFails() {
		assertError(ResponseErrorCode.RequestFailed, this.documents.renderHtml(paramsFor(uriOf(this.target))));
		assertEquals(0, this.rendered.get());
	}

	@Test
	void renderHtmlOfAClosedDocumentFails() {
		open(uriOf(this.target), "# One\n");
		close(uriOf(this.target));

		assertError(ResponseErrorCode.RequestFailed, this.documents.renderHtml(paramsFor(uriOf(this.target))));
	}

	@Test
	void renderHtmlWithoutADocumentFails() {
		assertError(ResponseErrorCode.InvalidParams, this.documents.renderHtml(null));
		assertError(ResponseErrorCode.InvalidParams, this.documents.renderHtml(new RenderHtmlParams(null)));
		assertError(ResponseErrorCode.InvalidParams,
				this.documents.renderHtml(new RenderHtmlParams(new TextDocumentIdentifier())));
	}

	@Test
	void renderHtmlAfterShutdownFails() {
		open(uriOf(this.target), "# One\n");
		this.shutDown.set(true);

		assertError(ResponseErrorCode.InvalidRequest, this.documents.renderHtml(paramsFor(uriOf(this.target))));
	}

	@Test
	void renderHtmlFailsIfTheServiceIsClosedBeforeTheRenderingStarts() {
		open(uriOf(this.target), "# One\n");
		this.service.close();

		assertError(ResponseErrorCode.InvalidRequest, this.documents.renderHtml(paramsFor(uriOf(this.target))));
	}

	private RenderHtmlResult renderHtml(String uri) throws Exception {
		return this.documents.renderHtml(paramsFor(uri)).get(10, TimeUnit.SECONDS);
	}

	private static RenderHtmlParams paramsFor(String uri) {
		return new RenderHtmlParams(new TextDocumentIdentifier(uri));
	}

	private static void assertError(ResponseErrorCode expected, CompletableFuture<RenderHtmlResult> answer) {
		ExecutionException failure = assertThrows(ExecutionException.class, () -> answer.get(10, TimeUnit.SECONDS));
		ResponseErrorException error = assertInstanceOf(ResponseErrorException.class, failure.getCause());
		assertEquals(expected.getValue(), error.getResponseError().getCode());
	}

	private List<ValidationIssue> issuesOfLinkingDocument() {
		return this.service.validateMarkdown(LINKING_TEXT, LocalFileSystemResource.of(this.linking));
	}

	private static String uriOf(Path file) {
		return file.toUri().toString();
	}

	private void open(String uri, String text) {
		this.documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, "markdown", 1, text)));
	}

	private void change(String uri, String... texts) {
		List<TextDocumentContentChangeEvent> changes = Arrays.stream(texts)
				.map(TextDocumentContentChangeEvent::new)
				.toList();
		this.documents.didChange(
				new DidChangeTextDocumentParams(new VersionedTextDocumentIdentifier(uri, 2), changes));
	}

	private void close(String uri) {
		this.documents.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)));
	}

	private void save(String uri) {
		this.documents.didSave(new DidSaveTextDocumentParams(new TextDocumentIdentifier(uri)));
	}
}
