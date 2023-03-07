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

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

public class LcpMonotoneMinimalPerfectHashFunctionTest {


	public static String binary(final int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}

	private void check(final String[] s, final int size, final LcpMonotoneMinimalPerfectHashFunction<String> mph, final int signatureWidth) {
		for (int i = s.length; i-- != 0;) assertEquals(i, mph.getLong(s[i]));

		// Exercise code for negative results
		if (signatureWidth == 0) for (int i = size; i-- != 0;) mph.getLong(binary(i + size));
		else for (int i = size; i-- != 0;) assertEquals(-1, mph.getLong(binary(i + size)));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for (int size = 1000; size < 10000000; size *= 10) {
			for (final int signatureWidth: new int[] { 0, 32, 64 }) {
				System.err.println("Size: " + size + " Signature width: " + signatureWidth);
				final String[] s = new String[size];
				final int[] v = new int[s.length];
				for (int i = s.length; i-- != 0;)
					s[v[i] = i] = binary(i);

				LcpMonotoneMinimalPerfectHashFunction<String> mph = new LcpMonotoneMinimalPerfectHashFunction.Builder<String>().keys(Arrays.asList(s)).transform(TransformationStrategies.prefixFreeUtf16()).signed(signatureWidth).build();

				check(s, size, mph, signatureWidth);

				File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (LcpMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);

				check(s, size, mph, signatureWidth);

				mph = new LcpMonotoneMinimalPerfectHashFunction.Builder<String>().keys(Arrays.asList(s)).transform(new HuTuckerTransformationStrategy(Arrays.asList(s), true)).signed(signatureWidth).build();

				check(s, size, mph, signatureWidth);

				temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);

				check(s, size, mph, signatureWidth);
			}
		}
	}

	@Test
	public void testEmpty() throws IOException {
		final LcpMonotoneMinimalPerfectHashFunction<String> mph = new LcpMonotoneMinimalPerfectHashFunction.Builder<String>().keys(Arrays.asList(new String[] {})).transform(TransformationStrategies.prefixFreeUtf16()).build();
		assertEquals(-1, mph.getLong(""));
	}
}
