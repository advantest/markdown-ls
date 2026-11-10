/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.advantest.markdown.service.MarkdownService;
import com.advantest.resources.LocalFileSystemResource;
import com.advantest.resources.Resource;
import com.advantest.resources.UnresolvedResource;

/**
 * Keeps the documents the client has open, tells the service what they contain and has them
 * validated.
 *
 * <p>The client sends the whole text of a document whenever it changes. Until the document is
 * closed, the service reads the file as that text rather than as what is saved, wherever a document
 * refers to it. Only documents whose URI names a file are told to the service; the others are kept
 * all the same. A document is validated when it is opened and saved, and once its edits have paused.
 * An open document is rendered to HTML on request. Notifications arriving after the client asked the
 * server to shut down are ignored, since the service is closed by then.</p>
 */
final class MarkdownTextDocumentService implements TextDocumentService {

	/**
	 * The method of the request for the HTML of an open document.
	 */
	static final String RENDER_HTML = "markdown/renderHtml";

	private static final Logger LOG = LoggerFactory.getLogger(MarkdownTextDocumentService.class);

	private final MarkdownService service;
	private final BooleanSupplier shutDown;
	private final DocumentValidator validator;
	private final Executor renderer;
	private final Map<String, OpenDocument> openDocuments = new ConcurrentHashMap<>();

	/**
	 * @param service the service to tell the contents of the open documents, not <code>null</code>
	 * @param shutDown says whether the client asked the server to shut down, not <code>null</code>
	 * @param validator validates the open documents, not <code>null</code>
	 * @param renderer renders the documents, off the thread the notifications arrive on, not
	 *                 <code>null</code>
	 */
	MarkdownTextDocumentService(MarkdownService service, BooleanSupplier shutDown, DocumentValidator validator,
			Executor renderer) {
		this.service = Objects.requireNonNull(service);
		this.shutDown = Objects.requireNonNull(shutDown);
		this.validator = Objects.requireNonNull(validator);
		this.renderer = Objects.requireNonNull(renderer);
	}

	/**
	 * Describes what the client is to send about its documents: their opening and closing, their
	 * whole text on every change, and their saving without the text.
	 */
	static TextDocumentSyncOptions syncOptions() {
		TextDocumentSyncOptions options = new TextDocumentSyncOptions();
		options.setOpenClose(true);
		options.setChange(TextDocumentSyncKind.Full);
		options.setSave(new SaveOptions(false));
		return options;
	}

	/**
	 * Answers the text the client holds for the document with the given URI.
	 *
	 * @return the text, empty if the document is not open
	 */
	Optional<String> textOf(String uri) {
		return Optional.ofNullable(this.openDocuments.get(uri)).map(OpenDocument::text);
	}

	@Override
	public void didOpen(DidOpenTextDocumentParams params) {
		if (this.shutDown.getAsBoolean()) {
			return;
		}
		String uri = params.getTextDocument().getUri();
		OpenDocument document = new OpenDocument(resourceOf(uri), params.getTextDocument().getText(),
				params.getTextDocument().getVersion());
		this.openDocuments.put(uri, document);
		document.tell(this.service);
		this.validator.validateNow(uri, document.resource(), document.text(), document.version());
	}

	@Override
	public void didChange(DidChangeTextDocumentParams params) {
		if (this.shutDown.getAsBoolean()) {
			return;
		}
		String uri = params.getTextDocument().getUri();
		OpenDocument opened = this.openDocuments.get(uri);
		List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
		if (opened == null || changes.isEmpty()) {
			return;
		}
		// with full synchronisation each change holds the whole text, so the last one counts
		OpenDocument changed = new OpenDocument(opened.resource(), changes.get(changes.size() - 1).getText(),
				params.getTextDocument().getVersion());
		this.openDocuments.put(uri, changed);
		changed.tell(this.service);
		this.validator.validateAfterChange(uri, changed.resource(), changed.text(), changed.version());
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		if (this.shutDown.getAsBoolean()) {
			return;
		}
		String uri = params.getTextDocument().getUri();
		OpenDocument closed = this.openDocuments.remove(uri);
		if (closed == null) {
			return;
		}
		if (closed.resource() != null) {
			this.service.dropUnsavedContents(closed.resource());
		}
		this.validator.forget(uri);
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		if (this.shutDown.getAsBoolean()) {
			return;
		}
		String uri = params.getTextDocument().getUri();
		OpenDocument saved = this.openDocuments.get(uri);
		if (saved != null) {
			// the files the document refers to may have been saved along with it
			this.validator.validateNow(uri, saved.resource(), saved.text(), saved.version());
		}
	}

	/**
	 * Renders an open document to HTML, from the text the client holds for it at the time the
	 * request arrives.
	 *
	 * @return the HTML and the version of the text it was rendered from; fails with
	 *         {@link ResponseErrorCode#RequestFailed} if the document is not open and with
	 *         {@link ResponseErrorCode#InvalidRequest} once the client asked the server to shut down
	 */
	CompletableFuture<RenderHtmlResult> renderHtml(RenderHtmlParams params) {
		if (this.shutDown.getAsBoolean()) {
			return failure(ResponseErrorCode.InvalidRequest, "The server is shutting down.");
		}
		if (params == null || params.textDocument() == null || params.textDocument().getUri() == null) {
			return failure(ResponseErrorCode.InvalidParams, "The document to be rendered is missing.");
		}
		String uri = params.textDocument().getUri();
		OpenDocument document = this.openDocuments.get(uri);
		if (document == null) {
			return failure(ResponseErrorCode.RequestFailed, "The document " + uri + " is not open.");
		}
		return CompletableFuture.supplyAsync(() -> render(uri, document), this.renderer);
	}

	private RenderHtmlResult render(String uri, OpenDocument document) {
		Resource resource = document.resource() != null ? document.resource() : UnresolvedResource.UNKNOWN_DOCUMENT;
		try {
			return new RenderHtmlResult(uri, document.version(),
					this.service.parseMarkdownAndRenderHtml(document.text(), resource));
		} catch (IllegalStateException serviceClosed) {
			throw new ResponseErrorException(
					new ResponseError(ResponseErrorCode.InvalidRequest, "The server is shutting down.", null));
		}
	}

	private static <T> CompletableFuture<T> failure(ResponseErrorCode code, String message) {
		return CompletableFuture.failedFuture(new ResponseErrorException(new ResponseError(code, message, null)));
	}

	/**
	 * Answers the resource of the file the given URI names.
	 *
	 * @return the resource, <code>null</code> if the URI names no file
	 */
	private static Resource resourceOf(String uri) {
		try {
			URI parsed = URI.create(uri);
			if (!"file".equalsIgnoreCase(parsed.getScheme())) {
				LOG.debug("The document {} is no file, so the service is not told about it.", uri);
				return null;
			}
			return LocalFileSystemResource.of(withUpperCaseDriveLetter(Path.of(parsed)));
		} catch (IllegalArgumentException e) {
			LOG.warn("The document {} has a URI that names no file.", uri, e);
			return null;
		}
	}

	/**
	 * Answers the given path with its Windows drive letter in upper case, as the service sees a file it
	 * is given directly. Clients such as VS Code write the drive letter of a URI in lower case, which
	 * would otherwise show in every message naming a path resolved from the document.
	 *
	 * @param path a path, not <code>null</code>
	 * @return the path with an upper case drive letter, or the path itself if it starts with none
	 */
	static Path withUpperCaseDriveLetter(Path path) {
		String text = path.toString();
		if (text.length() < 2 || text.charAt(1) != ':' || text.charAt(0) < 'a' || text.charAt(0) > 'z') {
			return path;
		}
		return path.getFileSystem().getPath(Character.toUpperCase(text.charAt(0)) + text.substring(1));
	}

	/**
	 * A document the client has open: the file it is, if it is one, and the text the client holds
	 * in the given version.
	 */
	private record OpenDocument(Resource resource, String text, Integer version) {

		void tell(MarkdownService service) {
			if (this.resource != null) {
				service.putUnsavedContents(this.resource, this.text);
			}
		}
	}
}
