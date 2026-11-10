/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import com.advantest.markdown.service.validation.IssueSeverity;
import com.advantest.markdown.service.validation.ValidationIssue;

/**
 * Turns the problems the service found in a document into the diagnostics of the protocol.
 */
final class Diagnostics {

	/**
	 * Names the server as the source of its diagnostics, so that an editor can tell them from those
	 * of other tools.
	 */
	static final String SOURCE = MarkdownLanguageServer.NAME;

	private Diagnostics() {
	}

	/**
	 * Answers the diagnostics of the given problems.
	 *
	 * @param issues the problems found in the given text, not <code>null</code>
	 * @param text the text that was validated, which the offsets of the problems count into, not
	 *             <code>null</code>
	 * @return a diagnostic for each problem, in the same order
	 */
	static List<Diagnostic> of(List<ValidationIssue> issues, String text) {
		LineIndex lines = new LineIndex(text);
		return issues.stream().map(issue -> of(issue, lines)).toList();
	}

	private static Diagnostic of(ValidationIssue issue, LineIndex lines) {
		Range range = new Range(lines.positionOf(issue.startOffset()), lines.positionOf(issue.endOffset()));
		return new Diagnostic(range, issue.message(), severityOf(issue.severity()), SOURCE, issue.issueTypeId());
	}

	private static DiagnosticSeverity severityOf(IssueSeverity severity) {
		return switch (severity) {
			case ERROR -> DiagnosticSeverity.Error;
			case WARNING -> DiagnosticSeverity.Warning;
			case INFO -> DiagnosticSeverity.Information;
		};
	}
}
