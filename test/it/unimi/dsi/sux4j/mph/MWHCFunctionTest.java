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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLists;

@SuppressWarnings("deprecation")
public class MWHCFunctionTest {

	private void check(final int size, final String[] s, final MWHCFunction<CharSequence> mph, final int signatureWidth) {
		if (signatureWidth < 0) for (int i = s.length; i-- != 0;) assertEquals(1, mph.getLong(s[i]));
		else for (int i = s.length; i-- != 0;) assertEquals(i, mph.getLong(s[i]));

		// Exercise code for negative results
		if (signatureWidth == 0) for (int i = size; i-- != 0;) mph.getLong(Integer.toString(i + size));
		else if (signatureWidth < 0) for (int i = size; i-- != 0;) assertEquals(0, mph.getLong(Integer.toString(i + size)));
		else for (int i = size; i-- != 0;) assertEquals(-1, mph.getLong(Integer.toString(i + size)));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {

		for (int outputWidth = 20; outputWidth < Long.SIZE; outputWidth += 8) {
			for (final int signatureWidth: new int[] { -32, 0, 32, 64 }) {
				for (int size = 1000; size < 10000000; size *= 10) {
					final String[] s = new String[size];
					for (int i = s.length; i-- != 0;)
						s[i] = Integer.toString(i);

					MWHCFunction<CharSequence> mph = new MWHCFunction.Builder<CharSequence>().keys(Arrays.asList(s)).transform(TransformationStrategies.utf16()).signed(signatureWidth).build();

					check(size, s, mph, signatureWidth);

					final File temp = File.createTempFile(getClass().getSimpleName(), "test");
					temp.deleteOnExit();
					BinIO.storeObject(mph, temp);
					mph = (MWHCFunction<CharSequence>)BinIO.loadObject(temp);

					check(size, s, mph, signatureWidth);

					// From store
					final it.unimi.dsi.sux4j.io.ChunkedHashStore<CharSequence> chunkedHashStore = new it.unimi.dsi.sux4j.io.ChunkedHashStore<>(TransformationStrategies.utf16(), null, signatureWidth < 0 ? -signatureWidth : 0, null);
					chunkedHashStore.addAll(Arrays.asList(s).iterator());
					chunkedHashStore.checkAndRetry(Arrays.asList(s));
					mph = new MWHCFunction.Builder<CharSequence>().store(chunkedHashStore).signed(signatureWidth).build();

					check(size, s, mph, signatureWidth);
				}
			}
		}
	}

	@Test
	public void testLongNumbers() throws IOException {
		final LongArrayList l = new LongArrayList(new long[] { 0x234904309830498L, 0xae049345e9eeeeeL, 0x23445234959234L, 0x239234eaeaeaeL });
		MWHCFunction<CharSequence> mph = new MWHCFunction.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		mph = new MWHCFunction.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l, Long.SIZE).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		mph = new MWHCFunction.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).values(l, Long.SIZE).indirect().build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
	}

	@Test
	public void testDictionary() throws IOException {
		final MWHCFunction<CharSequence> mph = new MWHCFunction.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d" })).transform(TransformationStrategies.utf16()).dictionary(8).build();
		assertEquals(1, mph.getLong("a"));
		assertEquals(1, mph.getLong("b"));
		assertEquals(1, mph.getLong("c"));
		assertEquals(1, mph.getLong("d"));
		assertEquals(0, mph.getLong("e"));
	}

	@Test
	public void testDuplicates() throws IOException {
		final MWHCFunction<String> mph = new MWHCFunction.Builder<String>().keys(
				new Iterable<String>() {
					int iteration;

					@Override
					public Iterator<String> iterator() {
						if (iteration++ > 2) return Arrays.asList(new String[] { "a", "b", "c" }).iterator();
						return Arrays.asList(new String[] { "a", "b", "a" }).iterator();
					}
				}).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, mph.getLong("a"));
		assertEquals(1, mph.getLong("b"));
		assertEquals(2, mph.getLong("c"));
	}

	@Test
	public void testEmpty() throws IOException {
		final List<String> emptyList = Collections.emptyList();
		MWHCFunction<String> mph = new MWHCFunction.Builder<String>().keys(emptyList).transform(TransformationStrategies.utf16()).build();
		assertEquals(-1, mph.getLong("a"));
		mph = new MWHCFunction.Builder<String>().keys(emptyList).dictionary(10).transform(TransformationStrategies.utf16()).build();
		assertEquals(0, mph.getLong("a"));
		mph = new MWHCFunction.Builder<String>().keys(emptyList).values(LongLists.EMPTY_LIST, 10).transform(TransformationStrategies.utf16()).build();
		assertEquals(-1, mph.getLong("a"));

	}
}
