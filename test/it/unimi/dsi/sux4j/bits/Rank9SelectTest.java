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

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class Rank9SelectTest extends RankSelectTestCase {


	@Test
	public void testSingleton() {
		Rank9 rank9;
		Select9 select9;
		int i;

		select9 = new Select9(rank9 = new Rank9(new long[] { 1L << 63, 0 }, 64));
		assertRankAndSelect(rank9, select9);
		assertEquals(1, rank9.rank(64));
		assertEquals(0, rank9.rank(63));
		for (i = 63; i-- != 0;)
			assertEquals(0, rank9.rank(i));
		assertEquals(63, select9.select(0));
		assertEquals(63, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 1 }, 64));
		assertRankAndSelect(rank9, select9);
		for (i = 64; i-- != 2;)
			assertEquals(1, rank9.rank(i));
		assertEquals(0, rank9.rank(0));
		assertEquals(0, select9.select(0));
		assertEquals(0, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 1L << 63, 0 }, 128));
		assertRankAndSelect(rank9, select9);
		for (i = 128; i-- != 64;)
			assertEquals(1, rank9.rank(i));
		while (i-- != 0)
			assertEquals(0, rank9.rank(i));
		assertEquals(63, select9.select(0));
		assertEquals(63, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 1L << 63, 0 }, 65));
		assertRankAndSelect(rank9, select9);
		for (i = 65; i-- != 64;)
			assertEquals(1, rank9.rank(i));
		while (i-- != 0)
			assertEquals(0, rank9.rank(i));
		assertEquals(63, select9.select(0));
		assertEquals(63, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 1L << 63, 0, 0 }, 129));
		assertRankAndSelect(rank9, select9);
		for (i = 128; i-- != 64;)
			assertEquals(1, rank9.rank(i));
		while (i-- != 0)
			assertEquals(0, rank9.rank(i));
		assertEquals(63, select9.select(0));
		assertEquals(63, select9.select(rank9.count() - 1));
	}

	@Test
	public void testDoubleton() {
		Rank9 rank9;
		Select9 select9;
		int i;

		select9 = new Select9(rank9 = new Rank9(new long[] { 1 | 1L << 32 }, 64));
		assertRankAndSelect(rank9, select9);
		for (i = 64; i-- != 33;)
			assertEquals(2, rank9.rank(i));
		while (i-- != 1)
			assertEquals(1, rank9.rank(i));
		assertEquals(0, rank9.rank(0));
		assertEquals(0, select9.select(0));
		assertEquals(32, select9.select(1));
		assertEquals(32, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 1, 1 }, 128));
		assertRankAndSelect(rank9, select9);
		for (i = 128; i-- != 65;)
			assertEquals(2, rank9.rank(i));
		while (i-- != 1)
			assertEquals(1, rank9.rank(i));
		assertEquals(0, rank9.rank(0));
		assertEquals(0, select9.select(0));
		assertEquals(64, select9.select(1));
		assertEquals(64, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 1 | 1L << 32, 0 }, 63));
		assertRankAndSelect(rank9, select9);
		for (i = 63; i-- != 33;)
			assertEquals(2, rank9.rank(i));
		while (i-- != 1)
			assertEquals(1, rank9.rank(i));
		assertEquals(0, rank9.rank(0));
		assertEquals(0, select9.select(0));
		assertEquals(32, select9.select(1));
		assertEquals(32, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 1, 1, 0 }, 129));
		assertRankAndSelect(rank9, select9);
		for (i = 129; i-- != 65;)
			assertEquals(2, rank9.rank(i));
		while (i-- != 1)
			assertEquals(1, rank9.rank(i));
		assertEquals(0, rank9.rank(0));
		assertEquals(0, select9.select(0));
		assertEquals(64, select9.select(1));
		assertEquals(64, select9.select(rank9.count() - 1));
	}

	@Test
	public void testAlternating() {
		Rank9 rank9;
		Select9 select9;
		int i;

		select9 = new Select9(rank9 = new Rank9(new long[] { 0xAAAAAAAAAAAAAAAAL }, 64));
		assertRankAndSelect(rank9, select9);
		for (i = 64; i-- != 0;)
			assertEquals(i / 2, rank9.rank(i));
		for (i = 32; i-- != 1;)
			assertEquals(i * 2 + 1, select9.select(i));
		assertEquals(63, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128));
		assertRankAndSelect(rank9, select9);
		for (i = 128; i-- != 0;)
			assertEquals(i / 2, rank9.rank(i));
		for (i = 64; i-- != 1;)
			assertEquals(i * 2 + 1, select9.select(i));
		assertEquals(127, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5));
		assertRankAndSelect(rank9, select9);
		for (i = 64 * 5; i-- != 0;)
			assertEquals(i / 2, rank9.rank(i));
		for (i = 32 * 5; i-- != 1;)
			assertEquals(i * 2 + 1, select9.select(i));
		assertEquals(64 * 5 - 1, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 0xAAAAAAAAL }, 33));
		assertRankAndSelect(rank9, select9);
		for (i = 33; i-- != 0;)
			assertEquals(i / 2, rank9.rank(i));
		for (i = 16; i-- != 1;)
			assertEquals(i * 2 + 1, select9.select(i));
		assertEquals(31, select9.select(rank9.count() - 1));

		select9 = new Select9(rank9 = new Rank9(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128));
		assertRankAndSelect(rank9, select9);
		for (i = 128; i-- != 113;)
			assertEquals(56, rank9.rank(i));
		while (i-- != 0)
			assertEquals(i / 2, rank9.rank(i));
		for (i = 56; i-- != 1;)
			assertEquals(i * 2 + 1, select9.select(i));
		assertEquals(111, select9.select(rank9.count() - 1));
	}

	@Test
	public void testSelect() {
		Rank9 rank9;
		Select9 select9;
		select9 = new Select9(rank9 = new Rank9(LongArrayBitVector.of(1, 0, 1, 1, 0, 0, 0).bits(), 7));
		assertRankAndSelect(rank9, select9);
		assertEquals(0, select9.select(0));
		assertEquals(3, select9.select(rank9.count() - 1));
	}

	@Test
	public void testRandom() {
		for (int size = 10; size <= 10000000; size *= 10) {
			final Random r = new XoRoShiRo128PlusRandom(1);
			final LongArrayBitVector bitVector = LongArrayBitVector.getInstance(size);
			for (int i = 0; i < size; i++)
				bitVector.add(r.nextBoolean());
			Rank9 rank9;
			Select9 select9;

			select9 = new Select9(rank9 = new Rank9(bitVector));
			assertRankAndSelect(rank9, select9);
		}
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		Rank9 rank9;
		Select9 select9;
		for (int size = 0; size <= 4096; size++) {
			v = LongArrayBitVector.getInstance().length(size);
			for (int i = (size + 1) / 2; i-- != 0;)
				v.set(i * 2);
			select9 = new Select9(rank9 = new Rank9(v));
			for (int i = size + 1; i-- != 0;)
				assertEquals((i + 1) / 2, rank9.rank(i));
			for (int i = size / 2; i-- != 0;)
				assertEquals(i * 2, select9.select(i));

		}
	}

	@Test
	public void testSparse() {
		LongArrayBitVector v;
		Rank9 rank9;
		Select9 select9;
		v = LongArrayBitVector.getInstance().length(1000000);
		for (int i = 0; i < 1000; i++) v.set(i * 100);

		select9 = new Select9(rank9 = new Rank9(v));
		assertRankAndSelect(rank9, select9);

		v.fill(0);
		for (int i = 0; i < 1000; i++) v.set(i * 75);

		select9 = new Select9(rank9 = new Rank9(v));
		assertRankAndSelect(rank9, select9);

		v.fill(0);
		for (int i = 0; i < 1000; i++) v.set(i * 50);

		select9 = new Select9(rank9 = new Rank9(v));
		assertRankAndSelect(rank9, select9);

		v.fill(0);
		for (int i = 0; i < 1000; i++) v.set(i * 25);

		select9 = new Select9(rank9 = new Rank9(v));
		assertRankAndSelect(rank9, select9);

		v.fill(0);
		for (int i = 0; i < 1000; i++) v.set(i * 200);

		select9 = new Select9(rank9 = new Rank9(v));
		assertRankAndSelect(rank9, select9);
	}

	@Test
	public void testUnsafe() {
		final Rank9 rank9 = new Rank9(new long[2], 127);
		assertEquals(0, rank9.rankStrict(127));
	}
}
