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

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertFalse;

import java.io.IOException;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;

public class MinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		final Iterable<Long> p = LargeLongCollection.getInstance();

		final LongArrayBitVector b = LongArrayBitVector.ofLength(LargeLongCollection.SIZE);
		final GOVMinimalPerfectHashFunction<Long> mph = new GOVMinimalPerfectHashFunction.Builder<Long>().keys(p).transform(TransformationStrategies.fixedLong()).build();

		for (final Long long1 : p) {
			final long pos = mph.getLong(long1);
			assertFalse(b.getBoolean(pos));
			b.set(pos);
		}
	}
}
