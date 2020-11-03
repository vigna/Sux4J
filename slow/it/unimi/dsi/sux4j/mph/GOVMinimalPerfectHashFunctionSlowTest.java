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

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.longs.LongOpenHashBigSet;

public class GOVMinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		final LargeLongCollection p = LargeLongCollection.getInstance();
		final GOVMinimalPerfectHashFunction<Long> f = new GOVMinimalPerfectHashFunction.Builder<Long>().keys(p).transform(TransformationStrategies.fixedLong()).build();
		final LongOpenHashBigSet s = new LongOpenHashBigSet();
		for (final Long l : p) assertTrue(s.add(f.getLong(l)));
	}
}
