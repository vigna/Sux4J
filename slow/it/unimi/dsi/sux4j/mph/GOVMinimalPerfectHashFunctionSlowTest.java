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

import static org.junit.Assert.assertFalse;

import java.io.IOException;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;

public class GOVMinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		final LargeLongCollection p = LargeLongCollection.getInstance();
		final GOVMinimalPerfectHashFunction<Long> f = new GOVMinimalPerfectHashFunction.Builder<Long>().keys(p).transform(TransformationStrategies.fixedLong()).build();

		final LongArrayBitVector b = LongArrayBitVector.ofLength(p.size64());
		for (long i = 0; i < p.size64(); i++) {
			final long t = f.getLong(Long.valueOf(i));
			assertFalse(b.getBoolean(t));
			b.set(t);
		}
	}
}
