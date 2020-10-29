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

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.sux4j.scratch.EliasFanoMonotoneLongBigListTables;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class EliasFanoMonotoneLongBigList16Test {

	@Test
	public void testSmall() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(new long[][] { { 0, 1, 2 } });
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		l = new LongBigArrayBigList(new long[][] { { 0, 10, 20 } });
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));
	}

	@Test
	public void testMedium() {
		// No skip tables involved
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(Util.identity(1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM)));
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		l = new LongBigArrayBigList(Util.identity((1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM)) + 5));
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));
	}

	@Test
	public void testLarge() {
		// No skip tables involved
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(Util.identity(2 * (1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM))));
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		l = new LongBigArrayBigList(Util.identity(2 * (1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM)) + 5));
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));
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
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));
	}

	@Test
	public void testBulk() {
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom();
		for(final int base: new int[] { 0, 1, 10 }) {
			for(final int jump : new int[] { 1, 10, 100 }) {
				final long[] s = new long[100000];
				for(int i = 1; i < s.length; i++) s[i] = s[i - 1] + random.nextInt(jump) + base;
				final EliasFanoMonotoneLongBigList16 ef = new EliasFanoMonotoneLongBigList16(LongArrayList.wrap(s));
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
