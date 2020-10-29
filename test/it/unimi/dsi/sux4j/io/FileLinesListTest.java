/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2020 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
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
