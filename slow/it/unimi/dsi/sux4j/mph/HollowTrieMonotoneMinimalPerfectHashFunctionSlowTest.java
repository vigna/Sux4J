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

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;

public class HollowTrieMonotoneMinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() {
		final Iterable<Long> p = LargeLongCollection.getInstance();
		final HollowTrieMonotoneMinimalPerfectHashFunction<Long> f = new HollowTrieMonotoneMinimalPerfectHashFunction<>(p, TransformationStrategies.fixedLong());

		long j = 0;
		for (Long s : p) {
			assertEquals(j++, f.getLong(s));
		}
	}
}
