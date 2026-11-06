# Protocol

markdown-ls speaks the [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
3.17. This page describes what it offers of it and the requests it adds.

## Versioning

markdown-ls is versioned semantically. Its contract is what it adds to standard LSP: its own requests,
notifications and settings, such as `markdown/renderHtml`. A change that breaks this contract raises the
major version, an addition raises the minor version, and anything else only the patch version. A client
can therefore tell from the version alone whether it can use a server.

The server names its version in the `Implementation-Version` of its JAR's manifest and in the
`serverInfo` of its answer to `initialize`. It reads the version from the resource
`META-INF/com.advantest.markdown/markdown-ls/version.properties`, which the build writes into its class
folder as well, so the server names its version when run from classes, too. Where the resource cannot be
read, it names no version.

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

## `markdown/environment`

A request from the client for the libraries the server runs on and for how PlantUML is set up, e.g. to show
them to the user. It has no parameters.

Result:

```json
{
  "libraries": [
    { "name": "markdown-ls", "version": "1.0.0" },
    { "name": "markdown-service", "version": "1.0.0" },
    { "name": "markdown-core", "version": "2.0.0" },
    { "name": "flexmark-extensions", "version": "2.0.0" },
    { "name": "flexmark", "version": "0.65.3" },
    { "name": "PlantUML", "version": "1.2026.3" }
  ],
  "plantUml": {
    "version": "1.2026.3",
    "securityProfile": "ALLOWLIST",
    "urlAllowList": [ "https://example.com/" ],
    "dotExecutable": "C:\\Program Files\\Graphviz\\bin\\dot.exe",
    "graphvizVersion": "12.2.1"
  }
}
```

| Field                      | Meaning                                                                         |
|----------------------------|---------------------------------------------------------------------------------|
| `libraries`                | the libraries in this order, followed by those a launcher adds, e.g. the ones   |
|                            | its service is built from                                                       |
| `libraries[].version`      | the library's version, `unknown` where the library does not tell it             |
| `plantUml.version`         | the version of PlantUML                                                         |
| `plantUml.securityProfile` | PlantUML's security profile; the server shows it, it never changes it           |
| `plantUml.urlAllowList`    | the web addresses PlantUML may read from, empty if none                         |
| `plantUml.dotExecutable`   | the Graphviz `dot` executable PlantUML runs, missing or `null` if none is found |
| `plantUml.graphvizVersion` | the version of that Graphviz, missing or `null` if it cannot be run             |

The server runs Graphviz to learn its version, so it answers on its rendering thread, after the renderings
requested before. The answer reflects the `dot` executable in effect at that time.

Errors:

| Code                      | When                                         |
|---------------------------|----------------------------------------------|
| `InvalidRequest` (-32600) | the client has asked the server to shut down |

## Settings

The client may send the settings below in the `initializationOptions` of `initialize`, without the
section `markdownSupport`, and on a change with `workspace/didChangeConfiguration`, inside it:

```json
{ "plantUml": { "dotExecutable": "C:\\Program Files\\Graphviz\\bin\\dot.exe" } }
```

```json
{ "settings": { "markdownSupport": { "plantUml": { "dotExecutable": "" } } } }
```

The server ignores any other key in the section.

| Setting                                    | Meaning                                                           |
|--------------------------------------------|-------------------------------------------------------------------|
| `markdownSupport.plantUml.dotExecutable`   | the Graphviz `dot` executable PlantUML runs; an empty string, or  |
|                                            | `null`, means none is set, in which case the server runs the one  |
|                                            | the environment variable `GRAPHVIZ_DOT` names, else the one it    |
|                                            | finds where Graphviz is usually installed                         |

A message lacking the setting leaves the executable as it is. If the server cannot run anything at the
path given, it keeps the executable it ran before and tells the user with `window/showMessage`, a warning
naming the path.
