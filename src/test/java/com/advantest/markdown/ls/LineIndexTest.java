/*
 * Copyright © 2026 Advantest Europe GmbH. All rights reserved.
 */
package com.advantest.markdown.ls;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.Test;

/**
 * Checks the positions of offsets in texts with the different ends of a line.
 */
class LineIndexTest {

	@Test
	void anOffsetInTheFirstLineIsItsCharacter() {
		assertEquals(new Position(0, 3), new LineIndex("# Title\n").positionOf(3));
	}

	@Test
	void linesEndWithALineFeed() {
		LineIndex lines = new LineIndex("one\ntwo\nthree");

		assertEquals(new Position(1, 0), lines.positionOf(4));
		assertEquals(new Position(2, 2), lines.positionOf(10));
	}

	@Test
	void linesEndWithACarriageReturnAndALineFeed() {
		LineIndex lines = new LineIndex("one\r\ntwo\r\nthree");

		assertEquals(new Position(1, 0), lines.positionOf(5));
		assertEquals(new Position(2, 2), lines.positionOf(12));
	}

	@Test
	void linesEndWithACarriageReturnAlone() {
		LineIndex lines = new LineIndex("one\rtwo\r\rthree");

		assertEquals(new Position(1, 1), lines.positionOf(5));
		assertEquals(new Position(2, 0), lines.positionOf(8));
		assertEquals(new Position(3, 0), lines.positionOf(9));
	}

	@Test
	void anOffsetAtTheEndOfALineIsTheLengthOfTheLine() {
		LineIndex lines = new LineIndex("one\r\ntwo");

		assertEquals(new Position(0, 3), lines.positionOf(3));
	}

	@Test
	void anOffsetBetweenCarriageReturnAndLineFeedIsTheEndOfTheLine() {
		LineIndex lines = new LineIndex("one\r\ntwo");

		assertEquals(new Position(0, 3), lines.positionOf(4));
	}

	@Test
	void anOffsetAtTheEndOfTheTextIsInTheLastLine() {
		assertEquals(new Position(1, 0), new LineIndex("one\n").positionOf(4));
		assertEquals(new Position(0, 3), new LineIndex("one").positionOf(3));
	}

	@Test
	void anOffsetOutsideTheTextIsMovedIntoIt() {
		LineIndex lines = new LineIndex("one\ntwo");

		assertEquals(new Position(0, 0), lines.positionOf(-1));
		assertEquals(new Position(1, 3), lines.positionOf(100));
	}

	@Test
	void anEmptyTextHasOneEmptyLine() {
		assertEquals(new Position(0, 0), new LineIndex("").positionOf(0));
	}

	@Test
	void charactersAreCountedInUtf16CodeUnits() {
		// the emoji takes two code units, as the protocol counts by default
		LineIndex lines = new LineIndex("a\uD83D\uDE00b\nc");

		assertEquals(new Position(0, 3), lines.positionOf(3));
	}

	@Test
	void manyLinesAreIndexed() {
		LineIndex lines = new LineIndex("x\n".repeat(100));

		assertEquals(new Position(99, 1), lines.positionOf(199));
		assertEquals(new Position(100, 0), lines.positionOf(200));
	}
}
