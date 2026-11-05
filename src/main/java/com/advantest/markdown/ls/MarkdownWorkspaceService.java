/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.services.WorkspaceService;

/**
 * Takes the settings the client sends when they change: so far the Graphviz <code>dot</code>
 * executable.
 */
final class MarkdownWorkspaceService implements WorkspaceService {

	private final Consumer<String> dotExecutable;

	/**
	 * @param dotExecutable takes the path of the <code>dot</code> executable, empty if the user names
	 *        none
	 */
	MarkdownWorkspaceService(Consumer<String> dotExecutable) {
		this.dotExecutable = Objects.requireNonNull(dotExecutable);
	}

	@Override
	public void didChangeConfiguration(DidChangeConfigurationParams params) {
		DotExecutableSetting.fromSettings(params.getSettings()).ifPresent(this.dotExecutable);
	}

	/**
	 * The server watches no files, so nothing is to be done.
	 */
	@Override
	public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
	}
}
