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

package it.unimi.dsi.sux4j.scratch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class EliasFanoMonotoneLongBigListTablesTest {

	@Test
	public void testSmall() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(new long[][] { { 0, 1, 2 } });
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));

		l = new LongBigArrayBigList(new long[][] { { 0, 10, 20 } });
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));
	}

	@Test
	public void testMedium() {
		// No skip tables involved
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(Util.identity(1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM)));
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));

		l = new LongBigArrayBigList(Util.identity((1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM)) + 5));
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));
	}

	@Test
	public void testLarge() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(Util.identity(2 * (1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM))));
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));

		l = new LongBigArrayBigList(Util.identity(2 * (1L << (EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM)) + 5));
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));

		for(int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));
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
		assertEquals(l, new EliasFanoMonotoneLongBigListTables(l));
	}
}
