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

package it.unimi.dsi.sux4j.bits;

import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class Rank11Test extends RankSelectTestCase {

	@Test
	public void testEmpty() {
		Rank11 rank11b;
		rank11b = new Rank11(new long[1], 64);
		assertRank(rank11b);
		rank11b = new Rank11(new long[2], 128);
		assertRank(rank11b);
		rank11b = new Rank11(new long[1], 63);
		assertRank(rank11b);
		rank11b = new Rank11(new long[2], 65);
		assertRank(rank11b);
		rank11b = new Rank11(new long[3], 129);
		assertRank(rank11b);
	}

	@Test
	public void testSingleton() {
		Rank11 rank11b;

		rank11b = new Rank11(new long[] { 1L << 63, 0 }, 64);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 1 }, 64);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 1L << 63, 0 }, 128);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 1L << 63, 0 }, 65);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 1L << 63, 0, 0 }, 129);
		assertRank(rank11b);
	}

	@Test
	public void testDoubleton() {
		Rank11 rank11b;

		rank11b = new Rank11(new long[] { 1 | 1L << 32 }, 64);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 1, 1 }, 128);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 1 | 1L << 32, 0 }, 63);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 1, 1, 0 }, 129);
		assertRank(rank11b);
	}

	@Test
	public void testAlternating() {
		Rank11 rank11b;

		rank11b = new Rank11(new long[] { 0xAAAAAAAAAAAAAAAAL }, 64);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 0xAAAAAAAAL }, 33);
		assertRank(rank11b);

		rank11b = new Rank11(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128);
		assertRank(rank11b);
	}

	@Test
	public void testSelect() {
		Rank11 rank11b;
		rank11b = new Rank11(LongArrayBitVector.of(1, 0, 1, 1, 0, 0, 0).bits(), 7);
		assertRank(rank11b);
	}

	@Test
	public void testRandom() {
		for (int size = 10; size <= 100000000; size *= 10) {
			final Random r = new XoRoShiRo128PlusRandom(1);
			final LongArrayBitVector bitVector = LongArrayBitVector.getInstance(size);
			for (int i = 0; i < size; i++)
				bitVector.add(r.nextBoolean());
			Rank11 rank11b;

			rank11b = new Rank11(bitVector);
			assertRank(rank11b);
		}
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		Rank11 rank11b;
		for (int size = 0; size <= 4096; size++) {
			v = LongArrayBitVector.getInstance().length(size);
			for (int i = (size + 1) / 2; i-- != 0;)
				v.set(i * 2);
			rank11b = new Rank11(v);
			assertRank(rank11b);
		}
	}
}
