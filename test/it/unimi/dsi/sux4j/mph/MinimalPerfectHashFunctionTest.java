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

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

@SuppressWarnings("deprecation")
public class MinimalPerfectHashFunctionTest {

	private void check(final int size, final String[] s, final MinimalPerfectHashFunction<CharSequence> mph, final int w) {
		final int[] check = new int[s.length];
		Arrays.fill(check, -1);
		for (int i = s.length; i-- != 0;) {
			assertEquals(Integer.toString(i), -1, check[(int)mph.getLong(s[i])]);
			check[(int)mph.getLong(s[i])] = i;
		}

		// Exercise code for negative results
		for (int i = 1000; i-- != 0;)
			if (w != 0) assertEquals(-1, mph.getLong(Integer.toString(i + size)));
			else mph.getLong(Integer.toString(i + size));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {

		for (final int size : new int[] { 0, 1, 4, 8, 20, 64, 100, 1000, 10000, 100000 }) {
			for(final int signatureWidth: new int[] { 0, 32, 64 }) {
				System.err.println("Size: " + size  + " w: " + signatureWidth);
				final String[] s = new String[size];
				for (int i = s.length; i-- != 0;)
					s[i] = Integer.toString(i);

				MinimalPerfectHashFunction<CharSequence> mph = new it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.Builder<CharSequence>().keys(Arrays.asList(s)).transform(TransformationStrategies.utf16()).signed(signatureWidth).build();

				check(size, s, mph, signatureWidth);

				final File temp = File.createTempFile(getClass().getSimpleName(), "test");
				temp.deleteOnExit();
				BinIO.storeObject(mph, temp);
				mph = (MinimalPerfectHashFunction<CharSequence>)BinIO.loadObject(temp);

				check(size, s, mph, signatureWidth);

				// From store
				final it.unimi.dsi.sux4j.io.ChunkedHashStore<CharSequence> chunkedHashStore = new it.unimi.dsi.sux4j.io.ChunkedHashStore<>(TransformationStrategies.utf16(), null, signatureWidth < 0 ? -signatureWidth : 0, null);
				chunkedHashStore.addAll(Arrays.asList(s).iterator());
				chunkedHashStore.checkAndRetry(Arrays.asList(s));
				mph = new it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.Builder<CharSequence>().store(chunkedHashStore).signed(signatureWidth).build();
				chunkedHashStore.close();

				check(size, s, mph, signatureWidth);
			}
		}
	}

	@Test
	public void testCountNonZeroPairs() {
		assertEquals(0, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(0));
		assertEquals(1, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(1));
		assertEquals(1, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(2));
		assertEquals(1, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(3));
		assertEquals(2, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(0xA));
		assertEquals(2, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(0x5));
		assertEquals(2, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(0xF));
		assertEquals(4, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(0x1111));
		assertEquals(4, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(0x3333));
		assertEquals(8, it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs(0xFFFF));
	}
}
