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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class EliasFanoIndexedMonotoneLongBigListTest {

	private static void test(final EliasFanoIndexedMonotoneLongBigList l) {
		final long u = l.getLong(l.size64() - 1);
		long p = 0, q = 0;
		long succ = l.getLong(p);
		long pred = -1;
		for (long i = 0; i <= u; i++) {
			while (i > succ) succ = l.getLong(++p);
			if (i > l.getLong(q)) {
				pred = l.getLong(q);
				do q++; while (q < l.size64() - 1 && l.getLong(q) == l.getLong(q + 1));
			}
			assertEquals(succ, l.successor(i));
			assertEquals(p, l.index());
			assertEquals(pred, l.predecessor(i));
			if (pred != -1) assertEquals(p - 1, l.index());
		}

		assertEquals(-1, l.predecessor(l.getLong(0)));
		assertEquals(-1, l.predecessor(0));
		assertEquals(Long.MAX_VALUE, l.successor(u + 1));
		assertEquals(Long.MAX_VALUE, l.successor(Long.MAX_VALUE));

		for (long i = 0; i < l.size64(); i++) {
			assertTrue(l.contains(l.getLong(i)));
			if (i == 0 || l.getLong(i) != l.getLong(i - 1)) assertEquals(i, l.index());
		}
		for (long i = 0; i < l.size64(); i++) {
			if (i != 0 && l.getLong(i) != l.getLong(i - 1)) assertEquals(i, l.indexOf(l.getLong(i)));
		}
		for (long i = 1; i < l.size64(); i++) {
			final long start = l.getLong(i - 1);
			final long end = l.getLong(i);
			for (long x = start + 1; x < end; x++) {
				assertFalse(l.contains(x));
				assertEquals(-1, l.indexOf(x));
			}
		}
	}

	@Test
	public void testSmall() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(new long[][] { { 0, 1, 2 } });
		EliasFanoIndexedMonotoneLongBigList e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);
		test(e);

		l = new LongBigArrayBigList(new long[][] { { 0, 10, 20 } });
		e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);
		test(e);
	}

	@Test
	public void testMedium() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(Util.identity(100L));
		EliasFanoIndexedMonotoneLongBigList e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);
		test(e);

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);
		test(e);

	}

	@Test
	public void testRandom() {
		// Weird skips
		final LongBigArrayBigList l = new LongBigArrayBigList();
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for(long i = 10000000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros(random.nextLong());
			l.add(c);
		}
		final EliasFanoIndexedMonotoneLongBigList e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);
		test(e);
	}

	@Test
	public void testBulk() {
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for(final int base: new int[] { 0, 1, 10 }) {
			for(final int jump : new int[] { 1, 10, 100 }) {
				final long[] s = new long[100000];
				for(int i = 1; i < s.length; i++) s[i] = s[i - 1] + random.nextInt(jump) + base;
				final EliasFanoIndexedMonotoneLongBigList ef = new EliasFanoIndexedMonotoneLongBigList(LongArrayList.wrap(s));
				test(ef);
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
					for(int j = from; j < to; j++) assertEquals("From: " + from + " to: " + to + " j: " + j, s[j], dest[offset + j - from]);
				}
			}
		}
	}
}
