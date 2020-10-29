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

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;

public class TwoSizesBigListTest {
	@Test
	public void testConstruction() {
		final LongBigList l = LongArrayBitVector.getInstance().asLongBigList(10);
		for (int i = 0; i < 1024; i++)
			l.add(i);
		TwoSizesLongBigList ts = new TwoSizesLongBigList(l);
		assertEquals(ts, l);

		l.clear();
		for (int i = 0; i < 512; i++)
			l.add(2);
		for (int i = 0; i < 512; i++)
			l.add(i);
		ts = new TwoSizesLongBigList(l);
		assertEquals(ts, l);
	}
}
