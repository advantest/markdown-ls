/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.advantest.markdown.service.MarkdownService;
import com.advantest.resources.LocalFileSystemResource;
import com.advantest.resources.Resource;

/**
 * Checks when documents are validated and that only what is up to date is published.
 */
class DocumentValidatorTest {

	private static final Duration DELAY = Duration.ofMillis(300);
	private static final Duration QUIET = Duration.ofMillis(500);
	private static final String BROKEN = "[Target](target.md#missing)\n";
	private static final String FIXED = "[Target](target.md)\n";

	@TempDir
	Path folder;

	private final PublishedDiagnostics published = new PublishedDiagnostics();
	private MarkdownService service;
	private DocumentValidator validator;
	private String uri;
	private Resource document;

	@BeforeEach
	void createValidator() throws IOException {
		Files.writeString(this.folder.resolve("target.md"), "# Target\n");
		Path file = Files.writeString(this.folder.resolve("document.md"), BROKEN);
		this.uri = file.toUri().toString();
		this.document = LocalFileSystemResource.of(file);
		this.service = MarkdownService.builderNotCheckingUriReachability().build();
		this.validator = new DocumentValidator(this.service, this.published, DELAY);
	}

	@AfterEach
	void close() {
		this.validator.close();
		this.service.close();
	}

	@Test
	void validateNowPublishesTheIssuesOfTheText() throws InterruptedException {
		this.validator.validateNow(this.uri, this.document, BROKEN, 3);

		PublishDiagnosticsParams diagnostics = this.published.next();
		assertEquals(this.uri, diagnostics.getUri());
		assertEquals(3, diagnostics.getVersion());
		assertEquals(1, diagnostics.getDiagnostics().size());
		Diagnostic diagnostic = diagnostics.getDiagnostics().get(0);
		assertEquals(Diagnostics.SOURCE, diagnostic.getSource());
		assertEquals(0, diagnostic.getRange().getStart().getLine());
	}

	@Test
	void validateNowPublishesNoIssuesForAValidText() throws InterruptedException {
		this.validator.validateNow(this.uri, this.document, FIXED, null);

		PublishDiagnosticsParams diagnostics = this.published.next();
		assertEquals(List.of(), diagnostics.getDiagnostics());
		assertNull(diagnostics.getVersion());
	}

	@Test
	void aDocumentThatIsNoFileIsValidatedAsAnUnknownDocument() throws InterruptedException {
		this.validator.validateNow("untitled:Untitled-1", null, "# Heading\n", 1);

		assertEquals("untitled:Untitled-1", this.published.next().getUri());
	}

	@Test
	void validateAfterChangeWaitsForTheDelay() throws InterruptedException {
		long start = System.nanoTime();

		this.validator.validateAfterChange(this.uri, this.document, BROKEN, 2);

		this.published.next();
		assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(DELAY) >= 0,
				"The document was validated before the delay passed.");
	}

	@Test
	void changesInQuickSuccessionAreValidatedOnceWithTheLastText() throws InterruptedException {
		this.validator.validateAfterChange(this.uri, this.document, BROKEN, 2);
		this.validator.validateAfterChange(this.uri, this.document, BROKEN + BROKEN, 3);
		this.validator.validateAfterChange(this.uri, this.document, FIXED, 4);

		PublishDiagnosticsParams diagnostics = this.published.next();
		assertEquals(4, diagnostics.getVersion());
		assertEquals(List.of(), diagnostics.getDiagnostics());
		this.published.assertNoneWithin(QUIET);
	}

	@Test
	void validateNowReplacesAValidationAfterChangeNotStartedYet() throws InterruptedException {
		this.validator.validateAfterChange(this.uri, this.document, FIXED, 2);
		this.validator.validateNow(this.uri, this.document, BROKEN, 3);

		assertEquals(3, this.published.next().getVersion());
		this.published.assertNoneWithin(QUIET);
	}

	@Test
	void documentsAreValidatedIndependently() throws InterruptedException {
		this.validator.validateNow(this.uri, this.document, BROKEN, 1);
		this.validator.validateNow("untitled:Untitled-1", null, "# Heading\n", 1);

		List<String> uris = List.of(this.published.next().getUri(), this.published.next().getUri());
		assertTrue(uris.contains(this.uri));
		assertTrue(uris.contains("untitled:Untitled-1"));
	}

	@Test
	void forgetPublishesNoDiagnosticsAndDropsAPendingValidation() throws InterruptedException {
		this.validator.validateAfterChange(this.uri, this.document, BROKEN, 2);

		this.validator.forget(this.uri);

		PublishDiagnosticsParams diagnostics = this.published.next();
		assertEquals(this.uri, diagnostics.getUri());
		assertEquals(List.of(), diagnostics.getDiagnostics());
		this.published.assertNoneWithin(QUIET);
	}

	@Test
	void closeDropsPendingValidationsAndIgnoresLaterOnes() throws InterruptedException {
		this.validator.validateAfterChange(this.uri, this.document, BROKEN, 2);

		this.validator.close();
		this.validator.validateNow(this.uri, this.document, BROKEN, 3);
		this.validator.forget(this.uri);

		this.published.assertNoneWithin(QUIET);
	}

	@Test
	void nothingIsPublishedOnceTheServiceIsClosed() throws InterruptedException {
		this.service.close();

		this.validator.validateNow(this.uri, this.document, BROKEN, 1);

		this.published.assertNoneWithin(QUIET);
	}
}
