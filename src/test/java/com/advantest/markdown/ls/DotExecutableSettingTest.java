/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.advantest.markdown.service.MarkdownService;
import com.google.gson.JsonParser;

/**
 * Checks how the setting naming the Graphviz <code>dot</code> executable is read and applied.
 */
class DotExecutableSettingTest {

	@TempDir
	Path folder;

	private String executableBefore;
	private String dot;

	@BeforeEach
	void rememberTheExecutableAndCreateAnother() throws IOException {
		this.executableBefore = MarkdownService.getDotExecutable().orElse(null);
		this.dot = createExecutable(this.folder.resolve("dot.exe"));
	}

	@AfterEach
	void restoreTheExecutable() {
		MarkdownService.setDotExecutable(this.executableBefore);
	}

	@Test
	void theInitializationOptionsNameTheExecutable() {
		assertEquals(Optional.of("/opt/dot"), DotExecutableSetting.fromInitializationOptions(
				JsonParser.parseString("{\"plantUml\":{\"dotExecutable\":\"/opt/dot\"},\"other\":1}")));
	}

	@Test
	void theSettingsNameTheExecutableInTheirSection() {
		assertEquals(Optional.of(""), DotExecutableSetting.fromSettings(JsonParser.parseString(
				"{\"markdownSupport\":{\"plantUml\":{\"dotExecutable\":\"\",\"other\":true},\"x\":2}}")));
	}

	@Test
	void aNullValueSaysNobodyNamedOne() {
		assertEquals(Optional.of(""), DotExecutableSetting.fromInitializationOptions(
				JsonParser.parseString("{\"plantUml\":{\"dotExecutable\":null}}")));
	}

	@Test
	void anythingElseNamesNoExecutable() {
		for (String json : new String[] { "{}", "[]", "\"text\"", "{\"plantUml\":3}", "{\"plantUml\":{}}",
				"{\"plantUml\":{\"dotExecutable\":7}}", "{\"plantUml\":{\"dotExecutable\":{}}}" }) {
			assertEquals(Optional.empty(), DotExecutableSetting.fromInitializationOptions(JsonParser.parseString(json)),
					json);
		}
		assertEquals(Optional.empty(), DotExecutableSetting.fromInitializationOptions(null));
		assertEquals(Optional.empty(), DotExecutableSetting.fromSettings("not JSON"));
	}

	@Test
	void applyingAPathNamesTheExecutable() {
		new DotExecutableSetting().apply(this.dot);

		assertEquals(Optional.of(this.dot), MarkdownService.getDotExecutable());
	}

	@Test
	void applyingAnEmptyPathRestoresTheExecutableInEffectWhenTheSettingWasCreated() {
		DotExecutableSetting setting = new DotExecutableSetting();
		setting.apply(this.dot);

		setting.apply("");

		assertEquals(Optional.ofNullable(this.executableBefore), MarkdownService.getDotExecutable());
	}

	@Test
	void applyingAPathWithNothingToRunFailsAndKeepsTheExecutable() {
		DotExecutableSetting setting = new DotExecutableSetting();
		setting.apply(this.dot);

		assertThrows(IllegalArgumentException.class,
				() -> setting.apply(this.folder.resolve("missing.exe").toString()));

		assertEquals(Optional.of(this.dot), MarkdownService.getDotExecutable());
	}

	/**
	 * Creates a file the operating system would run, which is not a Graphviz, though.
	 */
	static String createExecutable(Path file) throws IOException {
		Files.writeString(file, "");
		file.toFile().setExecutable(true);
		return file.toAbsolutePath().toString();
	}
}
