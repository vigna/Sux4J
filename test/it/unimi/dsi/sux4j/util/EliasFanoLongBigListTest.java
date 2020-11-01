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

package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class EliasFanoLongBigListTest {

	@Test
	public void testSmall() {
		for (final boolean offline : new boolean[] { false, true }) {
			LongBigArrayBigList l;
			l = new LongBigArrayBigList(new long[][] { { 0, 0, 0 } });
			assertEquals(l, new EliasFanoLongBigList(l.iterator(), 0, offline));

			l = new LongBigArrayBigList(new long[][] { { 0, 1, 0 } });
			assertEquals(l, new EliasFanoLongBigList(l.iterator(), 0, offline));

			l = new LongBigArrayBigList(new long[][] { { 1, 1, 1 } });
			assertEquals(l, new EliasFanoLongBigList(l.iterator(), 0, offline));

			l = new LongBigArrayBigList(new long[][] { { 4, 3, 2 } });
			assertEquals(l, new EliasFanoLongBigList(l.iterator(), 0, offline));

			l = new LongBigArrayBigList(new long[][] { { 128, 2000, 50000000, 200, 10 } });
			assertEquals(l, new EliasFanoLongBigList(l.iterator(), 0, offline));
		}
	}

	@Test
	public void testBulk() {
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for(final int base: new int[] { 0, 1, 10 }) {
			final long[] s = new long[100000];
			for(int i = s.length; i-- != 0;) s[i] = random.nextInt(100) + base;
			final EliasFanoLongBigList ef = new EliasFanoLongBigList(LongIterators.wrap(s));
			for(int i = 0; i < 1000; i++) {
				final int from = random.nextInt(s.length - 100);
				final int to = from + random.nextInt(100);
				final long[] dest = ef.get(from, new long[to - from]);
				for(int j = from; j < to; j++) assertEquals(s[j], dest[j - from]);
			}

			for(int i = 0; i < 1000; i++) {
				final int from = random.nextInt(s.length - 100);
				final int to = from + random.nextInt(100);
				final int offset = random.nextInt(10);
				final long[] dest = ef.get(from, new long[to - from + offset + random.nextInt(10)], offset, to - from);
				for(int j = from; j < to; j++) assertEquals(s[j], dest[offset + j - from]);
			}
		}
	}
}
