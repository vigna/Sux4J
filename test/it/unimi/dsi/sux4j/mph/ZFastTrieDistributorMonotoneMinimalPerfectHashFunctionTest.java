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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

public class ZFastTrieDistributorMonotoneMinimalPerfectHashFunctionTest {


	public static String binary(final int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}

	@Test
	public void testEmpty() throws IOException {
		final String[] s = {};
		for (int b = -1; b < 3; b++) {
			final ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
					TransformationStrategies.prefixFreeIso(), b, 0, null);
			assertEquals("Bucket size: " + (1 << b), 0, mph.size64());
			mph.numBits();
		}
	}

	@Test
	public void testSingleton() throws IOException {
		final String[] s = { "a" };
		for (int b = -1; b < 3; b++) {
			final ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
					TransformationStrategies.prefixFreeIso(), b, 0, null);
			for (int i = s.length; i-- != 0;)
				assertEquals("Bucket size: " + (1 << b), i, mph.getLong(s[i]));
			mph.numBits();
		}
	}

	@Test
	public void testDoubleton() throws IOException {
		final String[] s = { "a", "b" };
		for (int b = -1; b < 3; b++) {
			final ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
					TransformationStrategies.prefixFreeIso(), b, 0, null);
			for (int i = s.length; i-- != 0;)
				assertEquals("Bucket size: " + (1 << b), i, mph.getLong(s[i]));
			mph.numBits();
		}
	}


	@Test
	public void testSmallest() throws IOException {
		final String[] s = { "a", "b", "c", "d", "e", "f", "g" };
		for (int b = 1; b < 2; b++) {
			final ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
					TransformationStrategies.prefixFreeIso(), b, 0, null);
			for (int i = s.length; i-- != 0;)
				assertEquals("Bucket size: " + (1 << b), i, mph.getLong(s[i]));
			mph.numBits();
		}
	}

	@Test
	public void testSmall() throws IOException {
		final String[] s = { "-", "0", "1", "4", "5", "a", "b", "c", "d", "e", "f", "g", "}" };
		for (int b = -1; b < 5; b++) {
			final ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
					TransformationStrategies.prefixFreeIso(), b, 0, null);
			for (int i = s.length; i-- != 0;)
				assertEquals("Bucket size: " + (1 << b), i, mph.getLong(s[i]));
		}
	}

	private void check(final String[] s, final int d, final ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph, final int signatureWidth) {
		for (int i = s.length; i-- != 0;) assertEquals(i, mph.getLong(s[i]));

		// Exercise code for negative results
		if (signatureWidth == 0) for (int i = d; i-- != 0;) mph.getLong(binary(i + d));
		else for (int i = d; i-- != 0;) assertEquals(-1, mph.getLong(binary(i + d)));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for (int b = -1; b < 6; b++) {
			for (int d = 100; d < 10000; d *= 10) {
				for (final int signatureWidth: new int[] { 0, 32, 64 }) {
					// System.err.println("Size: " + d + " Bucket: " + b + " Signature width: " + signatureWidth);
					final String[] s = new String[d];
					final int[] v = new int[s.length];
					for (int i = s.length; i-- != 0;)
						s[v[i] = i] = binary(i);

					ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso(), b, signatureWidth, null);
					if (d >= 10000) assertTrue((double)mph.numBits() / d + " >= 10 ", mph.numBits() / d < 10);

					check(s, d, mph, signatureWidth);

					File temp = File.createTempFile(getClass().getSimpleName(), "test");
					temp.deleteOnExit();
					BinIO.storeObject(mph, temp);
					mph = (ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);

					check(s, d, mph, signatureWidth);

					mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s), new HuTuckerTransformationStrategy(Arrays.asList(s), true), b, signatureWidth, null);
					mph.numBits();

					check(s, d, mph, signatureWidth);

					temp = File.createTempFile(getClass().getSimpleName(), "test");
					temp.deleteOnExit();
					BinIO.storeObject(mph, temp);
					mph = (ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);

					check(s, d, mph, signatureWidth);
				}
			}
		}
	}
}
