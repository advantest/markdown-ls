# Protocol

markdown-ls speaks the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
3.17. This page describes what it offers of it and the requests it adds.

## Versioning

markdown-ls is versioned semantically. Its contract is what it adds to standard LSP: its own requests,
notifications and settings, such as `markdown/renderHtml`. A change that breaks this contract raises the
major version, an addition raises the minor version, and anything else only the patch version. A client
can therefore tell from the version alone whether it can use a server.

The server names its version in the `Implementation-Version` of its JAR's manifest and in the
`serverInfo` of its answer to `initialize`. Run from classes without a manifest, as in its own tests, it
names no version there.

## Documents

The server keeps the whole text of every document the client has open (`textDocumentSync`: open and close,
`Full` changes, save without text). Until a document is closed, the server reads the file as the text the
client holds rather than as what is saved, wherever another document refers to it.

## Diagnostics

The server publishes the issues of an open document with `textDocument/publishDiagnostics`. A document is
validated when it is opened and saved, and 300 ms after the last change. Closing a document removes its
diagnostics. Each diagnostic has the source `markdown-ls` and the id of the issue type as its code.

## `markdown/renderHtml`

A request from the client for the HTML of a document open in the editor.

Parameters:

```json
{ "textDocument": { "uri": "file:///path/to/document.md" } }
```

Result:

```json
{ "uri": "file:///path/to/document.md", "version": 7, "html": "<h1 source-offset=\"0\" ...>...</h1>" }
```

| Field     | Meaning                                                                        |
|-----------|--------------------------------------------------------------------------------|
| `uri`     | the document's URI, as given in the request                                    |
| `version` | the version of the document's text that was rendered, unsaved changes included |
| `html`    | the HTML `markdown-service` renders, unchanged: a fragment, not a whole page   |

The elements of the HTML carry the attributes `source-offset` and `source-length`, which name the part
of the document they were rendered from.

The server renders the text it holds when the request arrives, on a thread of its own, one request after
the other. When the document changes while a rendering is under way, the result carries the older version,
so a client can drop it once it has shown a newer one.

Errors:

| Code                      | When                                         |
|---------------------------|----------------------------------------------|
| `RequestFailed` (-32803)  | the document is not open                     |
| `InvalidParams` (-32602)  | the request names no document                |
| `InvalidRequest` (-32600) | the client has asked the server to shut down |
