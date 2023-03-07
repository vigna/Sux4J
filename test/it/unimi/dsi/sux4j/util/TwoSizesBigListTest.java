/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2023 Sebastiano Vigna
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
