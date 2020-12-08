/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;

public class GOV3FunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		final Iterable<Long> p = LargeLongCollection.getInstance();
		final GOV3Function<Long> f = new GOV3Function.Builder<Long>().keys(p).transform(TransformationStrategies.fixedLong()).values(new AbstractLongBigList() {

			@Override
			public long getLong(final long index) {
				return index % 7;
			}

			@Override
			public long size64() {
				return LargeLongCollection.SIZE;
			}
		}, 3).build();

		long j = 0;
		for (final Long s : p) {
			assertEquals(j++ % 7, f.getLong(s));
		}
	}
}
