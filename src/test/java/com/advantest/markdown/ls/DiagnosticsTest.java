/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import com.advantest.markdown.service.MarkdownService;
import com.advantest.markdown.service.validation.IssueSeverity;
import com.advantest.markdown.service.validation.ValidationIssue;

/**
 * Checks how the problems of the service become diagnostics.
 */
class DiagnosticsTest {

	private static final String TEXT = "# Title\r\n\r\nSee [there](#nowhere).\r\n";

	@Test
	void aDiagnosticSpansTheRangeOfTheProblem() {
		ValidationIssue issue = new ValidationIssue("type", IssueSeverity.ERROR, "Broken.", 3, 15, 23);

		Diagnostic diagnostic = Diagnostics.of(List.of(issue), TEXT).get(0);

		assertEquals(new Range(new Position(2, 4), new Position(2, 12)), diagnostic.getRange());
	}

	@Test
	void aDiagnosticNamesTheProblemAndTheServer() {
		ValidationIssue issue = new ValidationIssue("some.type", IssueSeverity.ERROR, "Broken.", 1, 0, 1);

		Diagnostic diagnostic = Diagnostics.of(List.of(issue), TEXT).get(0);

		assertEquals("Broken.", diagnostic.getMessage().getLeft());
		assertEquals("some.type", diagnostic.getCode().getLeft());
		assertEquals("markdown-ls", diagnostic.getSource());
	}

	@Test
	void theSeveritiesCorrespond() {
		List<ValidationIssue> issues = List.of(
				new ValidationIssue("type", IssueSeverity.ERROR, "Error.", 1, 0, 1),
				new ValidationIssue("type", IssueSeverity.WARNING, "Warning.", 1, 0, 1),
				new ValidationIssue("type", IssueSeverity.INFO, "Information.", 1, 0, 1));

		List<DiagnosticSeverity> severities = Diagnostics.of(issues, TEXT).stream()
				.map(Diagnostic::getSeverity)
				.toList();

		assertEquals(List.of(DiagnosticSeverity.Error, DiagnosticSeverity.Warning, DiagnosticSeverity.Information),
				severities);
	}

	@Test
	void noProblemsAreNoDiagnostics() {
		assertEquals(List.of(), Diagnostics.of(List.of(), TEXT));
	}

	@Test
	void theDiagnosticOfABrokenAnchorMarksTheLinkTargetInATextWithCarriageReturns() {
		try (MarkdownService service = MarkdownService.builderNotCheckingUriReachability().build()) {
			List<ValidationIssue> issues = service.validateMarkdown(TEXT);

			List<Diagnostic> diagnostics = Diagnostics.of(issues, TEXT);

			assertEquals(1, diagnostics.size(), diagnostics.toString());
			Range range = diagnostics.get(0).getRange();
			assertEquals(2, range.getStart().getLine());
			assertEquals(2, range.getEnd().getLine());
			String line = TEXT.split("\r\n")[2];
			assertEquals("#nowhere", line.substring(range.getStart().getCharacter(), range.getEnd().getCharacter()));
		}
	}
}
