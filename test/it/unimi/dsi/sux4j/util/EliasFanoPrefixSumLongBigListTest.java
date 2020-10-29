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

public class EliasFanoPrefixSumLongBigListTest {

	@Test
	public void testSmall() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList(new long[][] { { 0, 0, 0 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 0, 1, 0 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 1, 1, 1 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 4, 3, 2 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));

		l = new LongBigArrayBigList(new long[][] { { 128, 2000, 50000000, 200, 10 } });
		assertEquals(l, new EliasFanoPrefixSumLongBigList(l));
	}
}
