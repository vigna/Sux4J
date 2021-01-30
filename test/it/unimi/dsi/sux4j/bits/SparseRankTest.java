/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2021 Sebastiano Vigna
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

import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class SparseRankTest extends RankSelectTestCase {

	@Test
	public void testEmpty() {
		SparseRank rank;

		rank = new SparseRank(new long[1], 64);
		assertEquals(0, rank.rank(0));
		assertEquals(0, rank.rank(1));

		rank = new SparseRank(new long[2], 128);
		assertEquals(0, rank.rank(0));
		assertEquals(0, rank.rank(1));

		rank = new SparseRank(new long[1], 63);
		assertEquals(0, rank.rank(0));
		assertEquals(0, rank.rank(1));

		rank = new SparseRank(new long[2], 65);
		assertEquals(0, rank.rank(0));
		assertEquals(0, rank.rank(1));

		rank = new SparseRank(new long[3], 129);
		assertEquals(0, rank.rank(0));
		assertEquals(0, rank.rank(1));

	}

	@Test
	public void testSingleton() {
		SparseRank rank;

		rank = new SparseRank(new long[] { 1L << 63, 0 }, 64);
		assertRank(rank);
		assertEquals(0, rank.rank(0));
		assertEquals(1, rank.rank(64));

		rank = new SparseRank(new long[] { 1 }, 64);
		assertRank(rank);
		assertEquals(0, rank.rank(0));
		assertEquals(1, rank.rank(1));
	}

	@Test
	public void testDoubleton() {
		SparseRank rank;

		rank = new SparseRank(new long[] { 1 | 1L << 32 }, 64);
		assertRank(rank);
		assertEquals(0, rank.rank(0));
		assertEquals(1, rank.rank(32));
		assertEquals(2, rank.rank(64));

		rank = new SparseRank(new long[] { 1, 1 }, 128);
		assertRank(rank);
		assertEquals(0, rank.rank(0));
		assertEquals(1, rank.rank(1));
		assertEquals(2, rank.rank(65));

	}

	@Test
	public void testAlternating() {
		SparseRank rank;
		int i;

		rank = new SparseRank(new long[] { 0xAAAAAAAAAAAAAAAAL }, 64);
		assertRank(rank);
		for (i = 64; i-- != 1;)
			assertEquals(i / 2, rank.rank(i));

		rank = new SparseRank(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128);
		assertRank(rank);
		for (i = 128; i-- != 1;)
			assertEquals(i / 2, rank.rank(i));

		rank = new SparseRank(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5);
		assertRank(rank);
		for (i = 64 * 5; i-- != 1;)
			assertEquals(i / 2, rank.rank(i));

		rank = new SparseRank(new long[] { 0xAAAAAAAAL }, 33);
		assertRank(rank);
		for (i = 33; i-- != 1;)
			assertEquals(i / 2, rank.rank(i));

		rank = new SparseRank(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128);
		assertRank(rank);
		for (i = 112; i-- != 1;)
			assertEquals(i / 2, rank.rank(i));
	}

	@Test
	public void testrank() {
		SparseRank rank;
		rank = new SparseRank(LongArrayBitVector.of(1, 0, 1, 1, 0, 0, 0).bits(), 7);
		assertRank(rank);
		assertEquals(0, rank.rank(0));
	}

	@Test
	public void testSparse() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(256 * 1024);
		bitVector.set(1);
		bitVector.set(100000);
		bitVector.set(199999);
		SparseRank rank;

		rank = new SparseRank(bitVector);
		assertRank(rank);

		bitVector = LongArrayBitVector.getInstance().length(64 * 1024);
		bitVector.set(1);
		bitVector.set(40000);
		bitVector.set(49999);

		rank = new SparseRank(bitVector);
		assertRank(rank);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		bitVector.set(1);
		bitVector.set(20000);
		bitVector.set(29999);

		rank = new SparseRank(bitVector);
		assertRank(rank);
	}

	@Test
	public void testDense() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(16 * 1024);

		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 2);
		SparseRank rank;

		rank = new SparseRank(bitVector);
		assertRank(rank);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 4);

		rank = new SparseRank(bitVector);
		assertRank(rank);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 8);

		rank = new SparseRank(bitVector);
		assertRank(rank);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 16);

		rank = new SparseRank(bitVector);
		assertRank(rank);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 32);

		rank = new SparseRank(bitVector);
		assertRank(rank);
	}

	@Test
	public void testRandom() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance(1000);
		for (int i = 0; i < 1000; i++)
			bitVector.add(r.nextBoolean());
		SparseRank rank;

		rank = new SparseRank(bitVector);
		assertRank(rank);
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		SparseRank r;
		for (int size = 0; size <= 4096; size++) {
			v = LongArrayBitVector.getInstance().length(size);
			for (int i = (size + 1) / 2; i-- != 0;)
				v.set(i * 2);
			r = new SparseRank(v);
			for (int i = size; i-- != 0;)
				assertEquals((i + 1) / 2, r.rank(i));

			v = LongArrayBitVector.getInstance().length(size);
			v.fill(true);
			r = new SparseRank(v);
			for (int i = size; i-- != 0;)
				assertEquals(i, r.rank(i));
		}
	}

	@Test
	public void testGetRank() {
		final SparseSelect select = new SparseSelect(LongArrayList.wrap(new long[] { 0, 48, 128 }));
		final SparseRank rank = select.getRank();
		assertRankAndSelect(rank, select);
	}

	@Test
	public void testMaxInt() {
		final LongArrayBitVector bv = LongArrayBitVector.ofLength(Integer.MAX_VALUE + 2L);
		bv.set(Integer.MAX_VALUE + 1L);
		final SparseSelect select = new SparseSelect(bv);
        assertEquals(Integer.MAX_VALUE + 1L, select.select(0));
	}
}
