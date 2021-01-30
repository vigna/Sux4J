/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2019-2021 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.io;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;

public class BucketedHashStoreTest {

	@Test
	public void test() throws IOException {
		for(final int s: new int[] { 0, 1, 10, 100, 1000, 1000000 }) {
			final BucketedHashStore<Long> b = new BucketedHashStore<>(TransformationStrategies.fixedLong());
			for(int i = 0; i < s; i++) b.add(Long.valueOf(i));
			b.bucketSize(35);
			long t = 0;
			for(final BucketedHashStore.Bucket bucket: b) t += bucket.size();
			assertEquals(s, t);
			b.close();
		}
	}

}
