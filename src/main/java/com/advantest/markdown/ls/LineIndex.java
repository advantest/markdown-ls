/*
 * Copyright © 2026 Advantest Europe GmbH
 * SPDX-License-Identifier: BSD-3-Clause
 */
package com.advantest.markdown.ls;

import java.util.Arrays;
import java.util.Objects;

import org.eclipse.lsp4j.Position;

/**
 * Turns offsets into a text into the line and character positions of the protocol.
 *
 * <p>An offset counts the characters of a Java string, i.e. UTF-16 code units, which is also how
 * the protocol counts the characters in a line unless client and server agree on another
 * encoding. A line ends with a line feed, a carriage return or both together. A position never
 * points into the end of a line: an offset between a carriage return and its line feed, or beyond
 * the text, is moved back to the end of its line.</p>
 */
final class LineIndex {

	private final String text;
	private final int[] lineStarts;

	LineIndex(String text) {
		this.text = Objects.requireNonNull(text);
		int[] starts = new int[16];
		int count = 1;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			boolean crlf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
			if ((c == '\n' || c == '\r') && !crlf) {
				if (count == starts.length) {
					starts = Arrays.copyOf(starts, count * 2);
				}
				starts[count++] = i + 1;
			}
		}
		this.lineStarts = Arrays.copyOf(starts, count);
	}

	/**
	 * Answers the position of the given offset.
	 *
	 * @param offset the number of characters before the position, moved into the text if it lies
	 *               outside
	 * @return the position, with line and character counted from 0
	 */
	Position positionOf(int offset) {
		int clamped = Math.max(0, Math.min(offset, this.text.length()));
		int line = Arrays.binarySearch(this.lineStarts, clamped);
		if (line < 0) {
			line = -line - 2;
		}
		int start = this.lineStarts[line];
		return new Position(line, Math.min(clamped, endOfContent(line)) - start);
	}

	private int endOfContent(int line) {
		int end = line + 1 < this.lineStarts.length ? this.lineStarts[line + 1] : this.text.length();
		while (end > this.lineStarts[line] && isLineBreak(this.text.charAt(end - 1))) {
			end--;
		}
		return end;
	}

	private static boolean isLineBreak(char c) {
		return c == '\n' || c == '\r';
	}
}
