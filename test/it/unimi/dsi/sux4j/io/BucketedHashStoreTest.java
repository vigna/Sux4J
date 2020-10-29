/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2019-2020 Sebastiano Vigna
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
