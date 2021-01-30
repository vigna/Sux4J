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
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigListIterator;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class EliasFanoPrefixSumLongBigListTest {

	private static void test(final EliasFanoPrefixSumLongBigList l) {
		for (long i = 0; i < l.size64(); i++) {
			LongBigListIterator iterator = l.listIterator(i);
			for (long j = i; j < l.size64(); j++) {
				assertEquals("nextIndex() " + j + " from " + i, j, iterator.nextIndex());
				assertEquals("next() " + j + " from " + i, l.getLong(j), iterator.nextLong());
				assertEquals("previous() " + j + " from " + i, l.getLong(j), iterator.previousLong());
				assertEquals("nextIndex() " + j + " from " + i, j, iterator.nextIndex());
				assertEquals("next() " + j + " from " + i, l.getLong(j), iterator.nextLong());
				assertEquals("nextIndex() " + j + " from " + i, j + 1, iterator.nextIndex());
			}
			assertFalse(iterator.hasNext());
			iterator = l.listIterator(i);
			for (long j = i; j-- != 0;) {
				assertEquals("previousIndex() " + j + " from " + i, j, iterator.previousIndex());
				assertEquals("previous() " + j + " from " + i, l.getLong(j), iterator.previousLong());
				assertEquals("next() " + j + " from " + i, l.getLong(j), iterator.nextLong());
				assertEquals("previous() " + j + " from " + i, l.getLong(j), iterator.previousLong());
			}
			assertFalse(iterator.hasPrevious());
		}
	}

	@Test
	public void testSmall() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(new long[][] { { 0, 0, 0 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 0, 1, 0 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 1, 1, 1 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 4, 3, 2 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 128, 2000, 50000000, 200, 10 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));
	}

	@Test
	public void testMedium() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(Util.identity(100L));
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));

		for (int i = (int)l.size64(); i-- != 0;) l.set(i, l.getLong(i) * 1000);
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));
	}

	@Test
	public void testRandom() {
		LongBigArrayBigList l = new LongBigArrayBigList();
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for (long i = 1000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros(random.nextLong());
			l.add(c);
		}
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
		test(new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList();
		for (long i = 10000000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros(random.nextLong());
			l.add(c);
		}
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
	}
}
