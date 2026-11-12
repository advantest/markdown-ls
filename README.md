# markdown-ls

A language server for Markdown. It speaks the Language Server Protocol (LSP) over standard input and
output, so that every editor with an LSP client can offer the Markdown features it provides.

## How it works

The server is a thin layer built with [LSP4J](https://github.com/eclipse-lsp4j/lsp4j). It translates
between the protocol and the Java library `markdown-service`, which holds the language logic itself:
parsing, validation, rendering and the like. The server adds what the protocol needs on top of that —
the lifecycle, the synchronisation of the open documents, and the mapping of positions, ranges and
document URIs. [docs/protocol.md](docs/protocol.md) describes what the server offers of the protocol and
the requests it adds to it, such as `markdown/renderHtml` for the HTML of a document.

## Requirements

A Java 21 or newer. The start scripts take it from the environment variable `MARKDOWN_LS_JAVA`, the
path of a `java` executable, else from `JAVA_HOME`, else from the `PATH`. Options for the Java VM can be
given in `MARKDOWN_LS_OPTS`.

## Building

```shell
./mvnw verify
```

The build needs Java 21 and Maven 3.9, which the Maven wrapper fetches. It takes `markdown-service` and the
libraries below it from GitHub Packages, which asks for a GitHub token for each `github-packages-*` repository of
the `pom.xml` in the Maven settings. The build produces `target/markdown-ls-<version>-distribution.zip`
and `.tar.gz`, which hold:

```text
markdown-ls-<version>/
├── bin/
│   ├── markdown-ls         start script for Linux, macOS and Git Bash
│   └── markdown-ls.cmd     start script for Windows
├── lib/                    the libraries the server uses
├── licenses/               the licence texts of the third-party libraries
├── notices/                their copyright and licence notices
├── sources/                the source code of the libraries under the GPL
├── bom.json                the software bill of materials, in CycloneDX format
├── LICENSE                 the licence of the server
├── markdown-ls.jar         the server itself, also runnable with "java -jar"
├── README.md               this file
└── THIRD-PARTY.txt         the third-party libraries and their licences
```

Generating `bom.json` needs the network, so an offline build (`-o`) fails when packaging.

## Running

The server is not started by hand. An editor's LSP client starts `bin/markdown-ls`, or
`bin\markdown-ls.cmd` on Windows, and talks to it over standard input and output.

## License

This work is made available under the terms of the BSD 3-Clause License.
The licence accompanies this distribution: [LICENSE](LICENSE).

## Copyright

Copyright (c) 2026, Advantest Europe GmbH.
All rights reserved.
