/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.SaveOptions;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextDocumentSyncOptions;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.advantest.markdown.service.MarkdownService;
import com.advantest.resources.LocalFileSystemResource;
import com.advantest.resources.Resource;

/**
 * Keeps the documents the client has open and tells the service what they contain.
 *
 * <p>The client sends the whole text of a document whenever it changes. Until the document is
 * closed, the service reads the file as that text rather than as what is saved, wherever a document
 * refers to it. Only documents whose URI names a file are told to the service; the others are kept
 * all the same. Notifications arriving after the client asked the server to shut down are ignored,
 * since the service is closed by then.</p>
 */
final class MarkdownTextDocumentService implements TextDocumentService {

	private static final Logger LOG = LoggerFactory.getLogger(MarkdownTextDocumentService.class);

	private final MarkdownService service;
	private final BooleanSupplier shutDown;
	private final Map<String, OpenDocument> openDocuments = new ConcurrentHashMap<>();

	/**
	 * @param service the service to tell the contents of the open documents, not <code>null</code>
	 * @param shutDown says whether the client asked the server to shut down, not <code>null</code>
	 */
	MarkdownTextDocumentService(MarkdownService service, BooleanSupplier shutDown) {
		this.service = Objects.requireNonNull(service);
		this.shutDown = Objects.requireNonNull(shutDown);
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
		OpenDocument document = new OpenDocument(resourceOf(uri), params.getTextDocument().getText());
		this.openDocuments.put(uri, document);
		document.tell(this.service);
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
		OpenDocument changed = new OpenDocument(opened.resource(), changes.get(changes.size() - 1).getText());
		this.openDocuments.put(uri, changed);
		changed.tell(this.service);
	}

	@Override
	public void didClose(DidCloseTextDocumentParams params) {
		if (this.shutDown.getAsBoolean()) {
			return;
		}
		OpenDocument closed = this.openDocuments.remove(params.getTextDocument().getUri());
		if (closed != null && closed.resource() != null) {
			this.service.dropUnsavedContents(closed.resource());
		}
	}

	@Override
	public void didSave(DidSaveTextDocumentParams params) {
		// the client still holds the text, which now equals what is saved
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
			return LocalFileSystemResource.of(Path.of(parsed));
		} catch (IllegalArgumentException e) {
			LOG.warn("The document {} has a URI that names no file.", uri, e);
			return null;
		}
	}

	/**
	 * A document the client has open: the file it is, if it is one, and the text the client holds.
	 */
	private record OpenDocument(Resource resource, String text) {

		void tell(MarkdownService service) {
			if (this.resource != null) {
				service.putUnsavedContents(this.resource, this.text);
			}
		}
	}
}
