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
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class EliasFanoIndexedMonotoneLongBigListTest {

	private static void test(final EliasFanoIndexedMonotoneLongBigList l) {
		final long u = l.getLong(l.size64() - 1);
		long p = 0, q = -1, r = -1, s = 0;
		long succ = l.getLong(0), ssucc = l.getLong(0);
		long pred = Long.MIN_VALUE;
		long wpred = Long.MIN_VALUE;
		for (long i = -1; i <= u; i++) {

			while (i > succ) succ = l.getLong(++p);
			assertEquals(Long.toString(i), succ, l.successor(i));
			assertEquals(Long.toString(i), p, l.index());
			assertEquals(Long.toString(i), p, l.successorIndex(i));

			while (i >= ssucc && s < l.size64() - 1) ssucc = l.getLong(++s);
			if (i >= ssucc && s == l.size64() - 1) ssucc = Long.MAX_VALUE;
			assertEquals(Long.toString(i), ssucc, l.strictSuccessor(i));
			if (ssucc != Long.MAX_VALUE) {
				assertEquals(Long.toString(i), s, l.index());
				assertEquals(Long.toString(i), s, l.strictSuccessorIndex(i));
			}

			while (q < l.size64() - 1 && i > l.getLong(q + 1)) pred = l.getLong(++q);
			while (q < l.size64() - 1 && l.getLong(q + 1) == pred) q++;
			assertEquals(Long.toString(i), pred, l.predecessor(i));
			if (pred != Long.MIN_VALUE) {
				assertEquals(Long.toString(i), q, l.index());
				assertEquals(Long.toString(i), q, l.predecessorIndex(i));
			}

			while (r < l.size64() - 1 && i >= l.getLong(r + 1)) wpred = l.getLong(++r);
			while (r < l.size64() - 1 && l.getLong(r + 1) == wpred) r++;
			assertEquals(Long.toString(i), wpred, l.weakPredecessor(i));
			if (wpred != Long.MIN_VALUE) {
				assertEquals(Long.toString(i), r, l.index());
				assertEquals(Long.toString(i), r, l.weakPredecessorIndex(i));
			}
		}

		assertEquals(Long.MIN_VALUE, l.predecessor(l.getLong(0)));
		assertEquals(Long.MIN_VALUE, l.predecessorIndex(l.getLong(0)));
		assertEquals(l.size64() - 1, l.predecessorIndex(Long.MAX_VALUE));
		assertEquals(l.getLong(l.size64() - 1), l.predecessor(Long.MAX_VALUE));
		assertEquals(l.size64() - 1, l.index());
		if (l.getLong(0) > 0) assertEquals(Long.MIN_VALUE, l.weakPredecessor(l.getLong(0) - 1));
		assertEquals(Long.MIN_VALUE, l.predecessor(0));
		assertEquals(Long.MAX_VALUE, l.strictSuccessor(u));
		assertEquals(Long.MAX_VALUE, l.successorIndex(Long.MAX_VALUE));
		assertEquals(Long.MAX_VALUE, l.successor(u + 1));
		assertEquals(Long.MAX_VALUE, l.successor(Long.MAX_VALUE));

		final LongOpenHashSet set = new LongOpenHashSet(l);
		for (long i = 0; i < l.size64(); i++) assertTrue(set.contains(l.getLong(i)) == l.contains(l.getLong(i)));
		for (long i = 0; i < l.size64(); i++) assertTrue(set.contains(l.getLong(i)) == (l.indexOf(l.getLong(i)) != -1));

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
	public void testUneven() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(new long[][] { { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 18, 19, 20 } });
		final EliasFanoIndexedMonotoneLongBigList e = new EliasFanoIndexedMonotoneLongBigList(l);
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
		LongBigArrayBigList l = new LongBigArrayBigList();
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for(long i = 10000000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros(random.nextLong());
			l.add(c);
		}
		EliasFanoIndexedMonotoneLongBigList e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);
		test(e);

		l = new LongBigArrayBigList();
		for (long i = 10000, c = 0; i-- != 0;) {
			c += random.nextInt(1000);
			l.add(c);
		}
		e = new EliasFanoIndexedMonotoneLongBigList(l);
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
