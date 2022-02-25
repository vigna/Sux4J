/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2022 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;

public class HypergraphFunctionTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {
		for (final int size : new int[] { 0, 1, 4, 8, 20, 64, 100, 1000 }) {
			System.err.println("Size: " + size);

			final String[] s = new String[size];
			final long[] v = new long[s.length];
			for (int i = s.length; i-- != 0;)
				s[(int)(v[i] = i)] = Integer.toString(i);

			GOV3Function<String> function = new GOV3Function.Builder<String>().keys(Arrays.asList(s)).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(v), 12).build();

			for (int i = s.length; i-- != 0;)
				assertEquals(i, function.getLong(s[i]));

			final File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(function, temp);
			function = (GOV3Function<String>)BinIO.loadObject(temp);
			for (int i = s.length; i-- != 0;)
				assertEquals(i, function.getLong(s[i]));

			function = new GOV3Function.Builder<String>().keys(Arrays.asList(s)).transform(TransformationStrategies.utf16()).build();
			for (int i = s.length; i-- != 0;)
				assertEquals(i, function.getLong(s[i]));
		}
	}

	public static String binary(final int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		final String[] s = new String[10];
		final long[] v = new long[s.length];
		for (int i = s.length; i-- != 0;)
			s[(int)(v[i] = i)] = binary(i);

		GOV3Function<String> function = new GOV3Function.Builder<String>().keys(Arrays.asList(s)).transform(TransformationStrategies.utf16()).values(LongArrayList.wrap(v), 12).build();

		final int[] check = new int[s.length];
		Arrays.fill(check, -1);
		for (int i = s.length; i-- != 0;)
			assertEquals(i, function.getLong(s[i]));

		final File temp = File.createTempFile(getClass().getSimpleName(), "test");
		temp.deleteOnExit();
		BinIO.storeObject(function, temp);
		function = (GOV3Function<String>)BinIO.loadObject(temp);
		for (int i = s.length; i-- != 0;)
			assertEquals(i, function.getLong(s[i]));
	}

}
