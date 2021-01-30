/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2021 Sebastiano Vigna
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
