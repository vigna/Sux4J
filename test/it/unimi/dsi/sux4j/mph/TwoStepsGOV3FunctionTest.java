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

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongBigLists;

public class TwoStepsGOV3FunctionTest {

	@Test
	public void testSimpleList() throws IOException {
		final LongBigList l = LongBigLists.asBigList(new LongArrayList(new long[] { 4, 4, 4, 0, 1 }));
		final TwoStepsGOV3Function<CharSequence> mph = new TwoStepsGOV3Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d", "e" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		assertEquals(l.getLong(4), mph.getLong("e"));
	}

	@Test
	public void testSimpleCompressedList() throws IOException {
		final LongBigList l = LongBigLists.asBigList(new LongArrayList(new long[] { 4, 4, 4, 4, 4, 4, 0, 10000 }));
		final TwoStepsGOV3Function<CharSequence> mph = new TwoStepsGOV3Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d", "e", "f", "g", "h" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		assertEquals(l.getLong(4), mph.getLong("e"));
		assertEquals(l.getLong(5), mph.getLong("f"));
		assertEquals(l.getLong(6), mph.getLong("g"));
		assertEquals(l.getLong(7), mph.getLong("h"));
	}

	@Test
	public void testCompressedList() throws IOException {
		final LongBigList l = LongBigLists.asBigList(new LongArrayList(new long[] { 4, 4, 3, 3, 3, 4, 0, 10000 }));
		final TwoStepsGOV3Function<CharSequence> mph = new TwoStepsGOV3Function.Builder<CharSequence>().keys(Arrays.asList(new String[] { "a", "b", "c", "d", "e", "f", "g", "h" })).transform(TransformationStrategies.utf16()).values(l).build();
		assertEquals(l.getLong(0), mph.getLong("a"));
		assertEquals(l.getLong(1), mph.getLong("b"));
		assertEquals(l.getLong(2), mph.getLong("c"));
		assertEquals(l.getLong(3), mph.getLong("d"));
		assertEquals(l.getLong(4), mph.getLong("e"));
		assertEquals(l.getLong(5), mph.getLong("f"));
		assertEquals(l.getLong(6), mph.getLong("g"));
		assertEquals(l.getLong(7), mph.getLong("h"));
	}
}
