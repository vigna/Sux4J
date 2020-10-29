/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.bits;


import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;

public class Rank9SelectSlowTest {

	@Test
	public void testVeryLarge() {
		final LongArrayBitVector v = LongArrayBitVector.getInstance(2200000000L);
		for (int i = 0; i < 2200000000L / 64; i++)
			v.append(0x5555555555555555L, 64);
		Rank9 rank9;
		final Select9 select9 = new Select9(rank9 = new Rank9(v));
		for (int i = 0; i < 1100000000; i++)
			assertEquals(i * 2L, select9.select(i));
		for (int i = 0; i < 1100000000; i++)
			assertEquals(i, rank9.rank(i * 2L));
	}

}
