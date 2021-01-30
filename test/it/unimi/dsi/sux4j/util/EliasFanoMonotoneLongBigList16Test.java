/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2021 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
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

		l = new LongBigArrayBigList(Util.identity(100L));
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigList16(l));
	}

	@Test
	public void testLarge() {
		// No skip tables involved
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(Util.identity(2 * (100L)));
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
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for(final int base: new int[] { 0, 1, 10 }) {
			for(final int jump : new int[] { 1, 10, 100 }) {
				final long[] s = new long[100000];
				for(int i = 1; i < s.length; i++) s[i] = s[i - 1] + random.nextInt(jump) + base;
				final EliasFanoMonotoneLongBigList16 ef = new EliasFanoMonotoneLongBigList16(LongArrayList.wrap(s));
				for(int i = 0; i < 1000; i++) {
					final int from = random.nextInt(s.length - 100);
					final int to = from + random.nextInt(100);
					final long[] dest = ef.get(from, new long[Math.max(1, to - from)]);
					for(int j = from; j < to; j++) assertEquals(s[j], dest[j - from]);
				}

				for(int i = 0; i < 1000; i++) {
					final int from = random.nextInt(s.length - 100);
					final int to = from + random.nextInt(100);
					final int offset = random.nextInt(10);
					final long[] dest = ef.get(from, new long[Math.max(offset + 1, to - from + offset + random.nextInt(10))], offset, to - from);
					for(int j = from; j < to; j++) assertEquals("From: " + from + " to: " + to + " j: " + j, s[j], dest[offset + j - from]);
				}
			}
		}
	}
}
