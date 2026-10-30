# markdown-ls

A language server for Markdown. It speaks the Language Server Protocol (LSP) over standard input and
output, so that every editor with an LSP client can offer the Markdown features it provides.

## How it works

The server is a thin layer built with [LSP4J](https://github.com/eclipse-lsp4j/lsp4j). It translates
between the protocol and the Java library `markdown-service`, which holds the language logic itself:
parsing, validation, rendering and the like. The server adds what the protocol needs on top of that —
the lifecycle, the synchronisation of the open documents, and the mapping of positions, ranges and
document URIs.

## Requirements

A Java 21 or newer. The start scripts take it from the environment variable `MARKDOWN_LS_JAVA`, the
path of a `java` executable, else from `JAVA_HOME`, else from the `PATH`. Options for the Java VM can be
given in `MARKDOWN_LS_OPTS`.

## Building

```shell
./mvnw verify
```

The build needs Java 21 and Maven 3.9, which the Maven wrapper fetches. It takes `markdown-service` from
the local Maven repository as long as that library is not published, so install it there first with
`./mvnw install` in its own checkout. The build produces `target/markdown-ls-<version>-distribution.zip`
and `.tar.gz`, which hold:

```text
markdown-ls-<version>/
├── bin/
│   ├── markdown-ls         start script for Linux, macOS and Git Bash
│   └── markdown-ls.cmd     start script for Windows
├── lib/                    the libraries the server uses
└── markdown-ls.jar         the server itself, also runnable with "java -jar"
```

## Running

The server is not started by hand. An editor's LSP client starts `bin/markdown-ls`, or
`bin\markdown-ls.cmd` on Windows, and talks to it over standard input and output.
