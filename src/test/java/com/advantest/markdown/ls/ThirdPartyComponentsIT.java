/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks that lib/ leaves out exactly the components PlantUML's JAR holds in compiled form, which
 * the property plantuml-bundled-group-ids of the POM names by their groups. A newer PlantUML may
 * hold other components; the property then has to follow.
 */
class ThirdPartyComponentsIT {

	private static final Pattern MAVEN_PURL = Pattern
			.compile("\"purl\"\\s*:\\s*\"pkg:maven/([^/]+)/([^@]+)@([^?\"]+)\\?type=jar\"");

	private static final Pattern LISTED_COMPONENT = Pattern.compile("^  - .* \\(([^:]+):([^:]+):([^,)]+)[,)].*$");

	private record Component(String groupId, String artifactId, String version) {

		String coordinates() {
			return this.groupId + ":" + this.artifactId + ":" + this.version;
		}

		String fileName() {
			return this.artifactId + "-" + this.version + ".jar";
		}
	}

	private static Path buildDirectory;

	private static Path lib;

	private static Path plantUmlJar;

	@BeforeAll
	static void locate() throws IOException {
		buildDirectory = Path.of(System.getProperty("markdown-ls.build-directory", "target"));
		lib = buildDirectory.resolve("lib");
		try (Stream<Path> jars = Files.list(lib)) {
			plantUmlJar = jars.filter(p -> p.getFileName().toString().startsWith("plantuml")).findFirst()
					.orElseThrow();
		}
	}

	@Test
	void plantUmlIsTheEditionUnderTheEclipsePublicLicense() {
		assertTrue(plantUmlJar.getFileName().toString().startsWith("plantuml-epl-"), plantUmlJar.toString());
	}

	@Test
	void noClassIsInTwoJarsOfTheDistribution() throws IOException {
		Map<String, Path> jarOfClass = new HashMap<>();
		List<String> duplicates = new ArrayList<>();
		List<Path> jars = new ArrayList<>(jarsIn(lib));
		jars.add(buildDirectory.resolve("markdown-ls-" + System.getProperty("markdown-ls.version") + ".jar"));
		for (Path path : jars) {
			try (JarFile jar = new JarFile(path.toFile())) {
				for (String name : classesIn(jar)) {
					Path other = jarOfClass.putIfAbsent(name, path);
					if (other != null) {
						duplicates.add(name + " in " + other.getFileName() + " and " + path.getFileName());
					}
				}
			}
		}

		assertEquals(List.of(), duplicates.stream().limit(10).toList());
	}

	@Test
	void eachComponentLeftOutOfLibIsInsideThePlantUmlJar() throws IOException {
		List<Component> leftOut = componentsLeftOutOfLib();
		Path repository = Path.of(System.getProperty("markdown-ls.local-repository"));

		assertFalse(leftOut.isEmpty());
		try (JarFile plantUml = new JarFile(plantUmlJar.toFile())) {
			for (Component component : leftOut) {
				Path path = repository.resolve(component.groupId().replace('.', '/')).resolve(component.artifactId())
						.resolve(component.version()).resolve(component.fileName());
				try (JarFile jar = new JarFile(path.toFile())) {
					for (String name : classesIn(jar)) {
						JarEntry inPlantUml = plantUml.getJarEntry(name);
						assertTrue(inPlantUml != null, component.coordinates() + ": " + name);
						assertEquals(jar.getJarEntry(name).getCrc(), inPlantUml.getCrc(),
								component.coordinates() + ": " + name);
					}
				}
			}
		}
	}

	@Test
	void thirdPartyTxtNamesEachComponentLeftOutOfLib() throws IOException {
		Set<String> listed = new TreeSet<>();
		listedInThirdPartyTxt().values().forEach(components -> components.forEach(c -> listed.add(c.coordinates())));

		for (Component component : componentsLeftOutOfLib()) {
			assertTrue(listed.contains(component.coordinates()), component.coordinates());
		}
	}

	@Test
	void theSourceOfEachComponentUnderTheGplIsShipped() throws IOException {
		List<Component> underGpl = listedInThirdPartyTxt().entrySet().stream()
				.filter(e -> e.getKey().startsWith("GPL")).flatMap(e -> e.getValue().stream()).toList();

		assertFalse(underGpl.isEmpty());
		for (Component component : underGpl) {
			Path source = buildDirectory.resolve("third-party/sources")
					.resolve(component.artifactId() + "-" + component.version() + "-sources.jar");
			assertTrue(Files.isRegularFile(source), source.toString());
		}
	}

	/** The components bom.json lists whose JARs lib/ does not hold, the server itself aside. */
	private static List<Component> componentsLeftOutOfLib() throws IOException {
		String bom = Files.readString(buildDirectory.resolve("bom.json"), StandardCharsets.UTF_8);
		Set<String> inLib = new TreeSet<>();
		jarsIn(lib).forEach(p -> inLib.add(p.getFileName().toString()));

		List<Component> leftOut = new ArrayList<>();
		Matcher purl = MAVEN_PURL.matcher(bom);
		while (purl.find()) {
			Component component = new Component(purl.group(1), purl.group(2), purl.group(3));
			if (!component.artifactId().equals("markdown-ls") && !inLib.contains(component.fileName())
					&& !leftOut.contains(component)) {
				leftOut.add(component);
			}
		}
		return leftOut;
	}

	/** The components THIRD-PARTY.txt lists, by the licences it lists them under. */
	private static Map<String, List<Component>> listedInThirdPartyTxt() throws IOException {
		Map<String, List<Component>> listed = new HashMap<>();
		String licence = null;
		for (String line : Files.readAllLines(buildDirectory.resolve("generated-sources/license/THIRD-PARTY.txt"),
				StandardCharsets.UTF_8)) {
			Matcher component = LISTED_COMPONENT.matcher(line);
			if (component.matches() && licence != null) {
				listed.computeIfAbsent(licence, l -> new ArrayList<>())
						.add(new Component(component.group(1), component.group(2), component.group(3)));
			} else if (!line.startsWith(" ") && line.endsWith(":")) {
				licence = line.substring(0, line.length() - 1);
			}
		}
		return listed;
	}

	private static List<Path> jarsIn(Path directory) throws IOException {
		try (Stream<Path> files = Files.list(directory)) {
			return files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted().toList();
		}
	}

	/** The classes of a JAR, leaving out the module descriptors and the classes for other Java versions. */
	private static List<String> classesIn(JarFile jar) {
		return jar.stream().map(JarEntry::getName)
				.filter(n -> n.endsWith(".class") && !n.endsWith("module-info.class") && !n.startsWith("META-INF/"))
				.toList();
	}
}
