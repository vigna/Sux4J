/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2020 Sebastiano Vigna
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
import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class HollowTrieDistributorMinimalPerfectMonotoneHashFunctionTest {


	public static String binary2(final int l) {
		final String s = "1" + Integer.toBinaryString(l) + "10000000000000000000000000000000000000000000000000000000000000000000000000";
		return s.substring(0, 32);
	}


	public static String binary(final int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}

	@Test
	public void testEmpty() throws IOException {
		final String[] s = {};
		final HollowTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new HollowTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
				TransformationStrategies.prefixFreeIso());
		mph.size64();
		mph.getLong("0");
	}


	@Test
	public void testSmall() throws IOException {
		final String[] s = { "a", "b", "c", "d", "e", "f", "g", "h" };
		for(int l = s.length; l-- != 0;) {
			final String[] a = Arrays.copyOf(s, l);
			final HollowTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new HollowTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(a),
					TransformationStrategies.prefixFreeIso());

			for (int i = a.length; i-- != 0;)
				assertEquals(i, mph.getLong(a[i]));

			if (l == 0) assertEquals(-1, mph.getLong(s[0]));
		}
	}

	@Test
	public void testPrefix() throws IOException {

		final String[] s = { "0", "00", "000", "0000", "00000", "000000", "00000001", "0000000101", "00000002" };
		final HollowTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new HollowTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
				TransformationStrategies.prefixFreeUtf16());

		for (int i = s.length; i-- != 0;)
			assertEquals(i, mph.getLong(s[i]));

	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {
		final Random r = new XoRoShiRo128PlusRandom(1);

		for (int pass = 0; pass < 2; pass++)
			for (int d = 100; d < 100000; d *= 10) {
				final String[] s = new String[d];
				final int[] v = new int[s.length];
				for (int i = s.length; i-- != 0;)
					s[v[i] = i] = pass == 0 ? binary(r.nextInt()) : binary2(r.nextInt());
				Arrays.sort(s);

				HollowTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new HollowTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
						TransformationStrategies.prefixFreeUtf16());

				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));

				// Exercise code for negative results
				// for(int i = 1000; i-- != 0;) mph.getLong(binary(i * i + d));

				File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (HollowTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));


				mph = new HollowTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s), new HuTuckerTransformationStrategy(Arrays.asList(s), true));

				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));

				temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (HollowTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));
			}
	}
}
