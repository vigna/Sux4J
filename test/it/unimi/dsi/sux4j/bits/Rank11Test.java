/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2020 Sebastiano Vigna
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
			System.err.println(size);
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
