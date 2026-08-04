# markdown-ls

A language server for Markdown. It speaks the Language Server Protocol (LSP) over standard input and
output, so that every editor with an LSP client can offer the Markdown features it provides.

## How it works

The server is a thin layer built with [LSP4J](https://github.com/eclipse-lsp4j/lsp4j). It translates
between the protocol and the Java library `markdown-service`, which holds the language logic itself:
parsing, validation, rendering and the like. The server adds what the protocol needs on top of that —
the lifecycle, the synchronisation of the open documents, and the mapping of positions, ranges and
document URIs.
