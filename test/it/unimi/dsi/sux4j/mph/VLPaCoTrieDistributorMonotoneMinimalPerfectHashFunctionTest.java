/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2021 Sebastiano Vigna
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

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

public class VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunctionTest {


	public static String binary2(final int l) {
		final String s = "1" + Integer.toBinaryString(l) + "10000000000000000000000000000000000000000000000000000000000000000000000000";
		return s.substring(0, 32);
	}

	public static String binary(final int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}


	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {
		for (final int n : new int[] { 100, 1000, 100000 }) {
			for (int pass = 0; pass < 2; pass++) {
				final String[] s = new String[n];
				final int[] v = new int[s.length];
				for (int i = s.length; i-- != 0;)
					s[v[i] = i] = pass == 0 ? binary(i) : binary2(i);
				Arrays.sort(s);

				System.err.println(n);
				VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
						TransformationStrategies.prefixFreeUtf16());

				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));

				// Exercise code for negative results
				for (int i = n; i-- != 0;)
					mph.getLong(binary(i * i + n));

				File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));


				mph = new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s), new HuTuckerTransformationStrategy(Arrays.asList(s), true));

				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));

				temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
				for (int i = s.length; i-- != 0;)
					assertEquals(i, mph.getLong(s[i]));
			}
		}
	}

	@Test
	public void testManyLengths() throws IOException {
		final String[] s = new String[2051];
		for (int i = s.length; i-- != 0;) s[i] = binary(i);
		for (final int n: new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 15, 16, 17, 31, 32, 33, 127, 128, 129, 510, 511, 512, 513, 514, 1022, 1023, 1024, 1025, 1026, 2046, 2047, 2048, 2049, 2050 }) {
			System.err.println("Testing size " + n + "...");
			final VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s).subList(0, n),
						TransformationStrategies.prefixFreeUtf16());

			for (int i = n; i-- != 0;) assertEquals(i, mph.getLong(s[i]));
		}
	}
}
