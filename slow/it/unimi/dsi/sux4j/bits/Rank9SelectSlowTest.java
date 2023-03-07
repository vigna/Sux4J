/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2023 Sebastiano Vigna
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
