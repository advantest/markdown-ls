/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

/**
 * The answer to the request {@value MarkdownTextDocumentService#RENDER_HTML}: the HTML rendered from
 * a document and the version of the document it was rendered from.
 *
 * @param uri the document's URI
 * @param version the version of the document's text that was rendered, <code>null</code> if the
 *                client gave none
 * @param html the HTML, a fragment rather than a whole page
 */
record RenderHtmlResult(String uri, Integer version, String html) {
}
