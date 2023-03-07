/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2023 Sebastiano Vigna
 *
 * This program and the accompanying materials are made available under the
 * terms of the GNU Lesser General Public License v2.1 or later,
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html,
 * or the Apache Software License 2.0, which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later OR Apache-2.0
 */

package it.unimi.dsi.sux4j.io;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.junit.Test;

public class FileLinesListTest {

	@Test
	public void test() throws IOException {
		final File t = File.createTempFile(FileLinesListTest.class.getName(), "tmp");
		t.deleteOnExit();

		FileWriter fw = new FileWriter(t);
		fw.write("\naa\naaaa\n\naa\n".toCharArray());
		fw.close();

		FileLinesList fll = new FileLinesList(t.toString(), "ASCII");
		assertEquals("", fll.get(0).toString());
		assertEquals("aa", fll.get(1).toString());
		assertEquals("aaaa", fll.get(2).toString());
		assertEquals("", fll.get(3).toString());
		assertEquals("aa", fll.get(4).toString());

		fw = new FileWriter(t);
		fw.write("\n\n\n".toCharArray());
		fw.close();

		fll = new FileLinesList(t.toString(), "ASCII");
		assertEquals("", fll.get(0).toString());
		assertEquals("", fll.get(1).toString());
		assertEquals("", fll.get(2).toString());

		fw = new FileWriter(t);
		fw.write("\n\na".toCharArray());
		fw.close();

		fll = new FileLinesList(t.toString(), "ASCII");
		assertEquals("", fll.get(0).toString());
		assertEquals("", fll.get(1).toString());
		assertEquals("a", fll.get(2).toString());

		/*
		 * fw = new FileWriter(t); fw.write("".toCharArray()); fw.close();
		 *
		 * fll = new FileLinesList(t.toString(), "ASCII"); assertEquals("", fll.get(0
		 *).toString());
		 */
	}
}
