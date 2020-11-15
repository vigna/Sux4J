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
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.sux4j.util.EliasFanoIndexedMonotoneLongBigList.EliasFanoIndexedMonotoneLongBigListIterator;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class EliasFanoIndexedMonotoneLongBigListTest {

	private static void test(final EliasFanoIndexedMonotoneLongBigList l) {
		final long size = l.size64();
		final long u = l.getLong(size - 1);
		final long firstelement = l.getLong(0);

		long p = 0, q = -1, r = -1, s = 0;
		long succ = firstelement, ssucc = firstelement;
		long pred = -1;
		long wpred = -1;
		EliasFanoIndexedMonotoneLongBigListIterator listIterator;

		for (long i = -1; i <= u + 1; i++) {
			if (i < u + 1) while (i > succ) succ = l.getLong(++p);
			else {
				succ = Long.MAX_VALUE;
				p = size;
			}
			assertEquals(Long.toString(i), succ, l.successor(i));
			assertEquals(Long.toString(i), p, l.index());
			assertEquals(Long.toString(i), p, l.successorIndex(i));
			if (i >= 0) {
				if (i < u + 1) {
					assertEquals(Long.toString(i), succ, l.successorUnsafe(i));
					assertEquals(Long.toString(i), p, l.index());
					assertEquals(Long.toString(i), p, l.successorIndex(i));
					assertEquals(Long.toString(i), p, l.successorIndexUnsafe(i));
				} else {
					assertEquals(Long.toString(i), u + 1, l.successorUnsafe(i));
					assertEquals(Long.toString(i), size, l.index());
					assertEquals(Long.toString(i), size, l.successorIndex(i));
					assertEquals(Long.toString(i), size, l.successorIndexUnsafe(i));
				}

				listIterator = l.listIterator();
				assertEquals(succ, listIterator.skipTo(i));
				assertEquals(p, listIterator.nextIndex());
				if (i < u + 1) {
					assertEquals(succ, listIterator.nextLong());
					assertEquals(succ, listIterator.skipTo(i));
					assertEquals(p + 1, listIterator.nextIndex());
					assertEquals(succ, listIterator.previousLong());
					assertEquals(p, listIterator.nextIndex());
				}
				listIterator = l.listIterator();
				assertEquals(i == u + 1 ? u + 1 : succ, listIterator.skipToUnsafe(i));
				assertEquals(p, listIterator.nextIndex());
				if (i < u + 1) {
					assertEquals(succ, listIterator.nextLong());
					assertEquals(succ, listIterator.skipToUnsafe(i));
					assertEquals(p + 1, listIterator.nextIndex());
					assertEquals(succ, listIterator.previousLong());
					assertEquals(p, listIterator.nextIndex());
				}
			}
			if (i >= firstelement) {
				listIterator = l.listIterator();
				assertEquals(succ, listIterator.skipTo(i));
				assertEquals(p, listIterator.nextIndex());
				if (i < u + 1) assertEquals(succ, listIterator.nextLong());
			}
			if (i < u + 1) {
				listIterator = l.listIterator(p);
				assertEquals(succ, listIterator.nextLong());
			}

			listIterator = l.listIterator();
			for (int j = 0; j < p; j++) listIterator.nextLong();
			if (i < u + 1) assertEquals(succ, listIterator.nextLong());
			else assertFalse(listIterator.hasNext());

			while (i >= ssucc && s < size - 1) ssucc = l.getLong(++s);
			if (i >= ssucc && s == size - 1) {
				ssucc = Long.MAX_VALUE;
				s = size;
			}

			assertEquals(Long.toString(i), ssucc, l.strictSuccessor(i));
			assertEquals(Long.toString(i), s, l.index());

			if (ssucc != Long.MAX_VALUE) {
				assertEquals(Long.toString(i), s, l.index());
				assertEquals(Long.toString(i), s, l.strictSuccessorIndex(i));
			} else {
				assertEquals(Long.toString(i), size, l.index());
				assertEquals(Long.toString(i), size, l.strictSuccessorIndex(i));

			}

			if (i >= 0 && i < u) {
				assertEquals(Long.toString(i), ssucc, l.strictSuccessorUnsafe(i));
				assertEquals(Long.toString(i), s, l.strictSuccessorIndexUnsafe(i));
			}
			if (i == u) {
				assertEquals(Long.toString(i), u + 1, l.strictSuccessorUnsafe(i));
				assertEquals(Long.toString(i), size, l.strictSuccessorIndexUnsafe(i));
			}

			while (q < size - 1 && i > l.getLong(q + 1)) pred = l.getLong(++q);
			while (q < size - 1 && l.getLong(q + 1) == pred) q++;

			assertEquals(Long.toString(i), pred, l.predecessor(i));

			if (pred != -1) {
				assertEquals(Long.toString(i), q, l.index());
				assertEquals(Long.toString(i), q, l.predecessorIndex(i));
			} else {
				assertEquals(Long.toString(i), -1, l.index());
				assertEquals(Long.toString(i), -1, l.predecessorIndex(i));
			}

			if (i > firstelement) {
				assertEquals(Long.toString(i), pred, l.predecessorUnsafe(i));
				if (i == u + 1) {
					assertEquals(Long.toString(i), size - 1, l.index());
					assertEquals(Long.toString(i), size - 1, l.predecessorIndexUnsafe(i));
				} else if (pred != -1) {
					assertEquals(Long.toString(i), q, l.index());
					assertEquals(Long.toString(i), q, l.predecessorIndexUnsafe(i));
				} else {
					assertEquals(Long.toString(i), -1, l.index());
					assertEquals(Long.toString(i), -1, l.predecessorIndexUnsafe(i));
				}
			}

			while (r < size - 1 && i >= l.getLong(r + 1)) wpred = l.getLong(++r);
			while (r < size - 1 && l.getLong(r + 1) == wpred) r++;
			assertEquals(Long.toString(i), wpred, l.weakPredecessor(i));
			if (wpred != -1) {
				assertEquals(Long.toString(i), r, l.index());
				assertEquals(Long.toString(i), r, l.weakPredecessorIndex(i));
			} else {
				assertEquals(Long.toString(i), -1, l.index());
				assertEquals(Long.toString(i), -1, l.weakPredecessorIndex(i));
			}

			if (i >= firstelement && i <= u) {
				assertEquals(Long.toString(i), wpred, l.weakPredecessorUnsafe(i));
				if (wpred != -1) {
					assertEquals(Long.toString(i), r, l.index());
					assertEquals(Long.toString(i), r, l.weakPredecessorIndexUnsafe(i));
				} else {
					assertEquals(Long.toString(i), -1, l.index());
					assertEquals(Long.toString(i), -1, l.weakPredecessorIndexUnsafe(i));
				}
			}
		}

		assertEquals(-1, l.predecessor(firstelement));
		assertEquals(-1, l.predecessorIndex(firstelement));
		p = 1;
		while (p < size && l.getLong(p - 1) == l.getLong(p)) p++;
		assertEquals(firstelement, l.predecessorUnsafe(firstelement + 1));
		assertEquals(p - 1, l.index());
		assertEquals(p - 1, l.predecessorIndexUnsafe(firstelement + 1));

		assertEquals(l.getLong(size - 1), l.predecessor(Long.MAX_VALUE));
		assertEquals(size - 1, l.index());
		assertEquals(size - 1, l.predecessorIndex(Long.MAX_VALUE));

		assertEquals(l.getLong(size - 1), l.predecessorUnsafe(l.getLong(size - 1) + 1));
		assertEquals(size - 1, l.index());
		assertEquals(size - 1, l.predecessorIndexUnsafe(l.getLong(size - 1) + 1));

		if (firstelement > 0) assertEquals(-1, l.weakPredecessor(firstelement - 1));
		assertEquals(-1, l.predecessor(0));

		assertEquals(Long.MAX_VALUE, l.successor(Long.MAX_VALUE));
		assertEquals(Long.MAX_VALUE, l.listIterator().skipTo(l.getLong(size - 1) + 1));

		final LongOpenHashSet set = new LongOpenHashSet(l);
		for (long i = 0; i < size; i++) assertTrue(set.contains(l.getLong(i)) == l.contains(l.getLong(i)));
		for (long i = 0; i < size; i++) assertTrue(set.contains(l.getLong(i)) == (l.indexOf(l.getLong(i)) != -1));

		for (long i = 0; i < size; i++) {
			if (i != 0 && l.getLong(i) != l.getLong(i - 1)) assertEquals(i, l.indexOf(l.getLong(i)));
		}
		for (long i = 1; i < size; i++) {
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

		for (int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);
		test(e);

	}

	@Test
	public void testRandom() {
		// Weird skips
		LongBigArrayBigList l = new LongBigArrayBigList();
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for (long i = 10000000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros(random.nextLong());
			l.add(c);
		}
		EliasFanoIndexedMonotoneLongBigList e = new EliasFanoIndexedMonotoneLongBigList(l);
		assertEquals(l, e);

		l = new LongBigArrayBigList();
		for (long i = 1000, c = 0; i-- != 0;) {
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
		for (final int base : new int[] { 0, 1, 10 }) {
			for (final int jump : new int[] { 1, 10, 100 }) {
				final long[] s = new long[1000];
				for (int i = 1; i < s.length; i++) s[i] = s[i - 1] + random.nextInt(jump) + base;
				final EliasFanoIndexedMonotoneLongBigList ef = new EliasFanoIndexedMonotoneLongBigList(LongArrayList.wrap(s));
				test(ef);
				for (int i = 0; i < 1000; i++) {
					final int from = random.nextInt(s.length - 100);
					final int to = from + random.nextInt(100);
					final long[] dest = ef.get(from, new long[Math.max(1, to - from)]);
					for (int j = from; j < to; j++) assertEquals(s[j], dest[j - from]);
				}

				for (int i = 0; i < 1000; i++) {
					final int from = random.nextInt(s.length - 100);
					final int to = from + random.nextInt(100);
					final int offset = random.nextInt(10);
					final long[] dest = ef.get(from, new long[Math.max(offset + 1, to - from + offset + random.nextInt(10))], offset, to - from);
					for (int j = from; j < to; j++) assertEquals("From: " + from + " to: " + to + " j: " + j, s[j], dest[offset + j - from]);
				}
			}
		}
	}

	@Test
	public void testUnsafeExtremal() {
		final EliasFanoIndexedMonotoneLongBigList l = new EliasFanoIndexedMonotoneLongBigList(3, 10, LongIterators.fromTo(0, 3));
		assertEquals(2, l.successorUnsafe(2));
		assertEquals(2, l.index());
		assertEquals(2, l.successorIndexUnsafe(2));
		assertEquals(10, l.successorUnsafe(3));
		assertEquals(3, l.index());
		assertEquals(3, l.successorIndexUnsafe(3));
		assertEquals(10, l.successorUnsafe(10));
		assertEquals(3, l.index());
		assertEquals(3, l.successorIndexUnsafe(10));

		assertEquals(2, l.strictSuccessorUnsafe(1));
		assertEquals(2, l.index());
		assertEquals(2, l.strictSuccessorIndexUnsafe(1));
		assertEquals(10, l.strictSuccessorUnsafe(2));
		assertEquals(3, l.index());
		assertEquals(3, l.strictSuccessorIndexUnsafe(2));
		assertEquals(10, l.strictSuccessorUnsafe(3));
		assertEquals(3, l.index());
		assertEquals(3, l.strictSuccessorIndexUnsafe(3));
		assertEquals(10, l.strictSuccessorUnsafe(9));
		assertEquals(3, l.index());
		assertEquals(3, l.strictSuccessorIndexUnsafe(9));
	}
}
