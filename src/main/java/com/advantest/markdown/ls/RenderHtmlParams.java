/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * The parameters of the request {@value MarkdownTextDocumentService#RENDER_HTML}: the document to be
 * rendered.
 *
 * @param textDocument the document, which must be open
 */
record RenderHtmlParams(TextDocumentIdentifier textDocument) {
}
