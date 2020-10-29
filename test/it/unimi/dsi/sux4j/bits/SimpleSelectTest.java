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

public class SimpleSelectTest extends RankSelectTestCase {

	@Test
	public void testEmpty() {
		SimpleSelect select;

		select = new SimpleSelect(new long[1], 64);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[2], 128);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[1], 63);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[2], 65);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[3], 129);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

	}

	@Test
	public void testSingleton() {
		SimpleSelect select;

		select = new SimpleSelect(new long[] { 1L << 63, 0 }, 64);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[] { 1 }, 64);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[] { 1L << 63, 0 }, 128);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[] { 1L << 63, 0 }, 65);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SimpleSelect(new long[] { 1L << 63, 0, 0 }, 129);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));
	}

	@Test
	public void testDoubleton() {
		SimpleSelect select;

		select = new SimpleSelect(new long[] { 1 | 1L << 32 }, 64);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(32, select.select(1));
		assertEquals(-1, select.select(2));

		select = new SimpleSelect(new long[] { 1, 1 }, 128);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(64, select.select(1));
		assertEquals(-1, select.select(2));

		select = new SimpleSelect(new long[] { 1 | 1L << 32, 0 }, 63);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(32, select.select(1));
		assertEquals(-1, select.select(2));

		select = new SimpleSelect(new long[] { 1, 1, 0 }, 129);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(64, select.select(1));
		assertEquals(-1, select.select(2));
	}

	@Test
	public void testAlternating() {
		SimpleSelect select;
		int i;

		select = new SimpleSelect(new long[] { 0xAAAAAAAAAAAAAAAAL }, 64);
		assertSelect(select);
		for (i = 32; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleSelect(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128);
		assertSelect(select);
		for (i = 64; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleSelect(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5);
		assertSelect(select);
		for (i = 32 * 5; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleSelect(new long[] { 0xAAAAAAAAL }, 33);
		assertSelect(select);
		for (i = 16; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleSelect(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128);
		assertSelect(select);
		for (i = 56; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));
	}

	@Test
	public void testSelect() {
		SimpleSelect select;
		select = new SimpleSelect(LongArrayBitVector.of(1, 0, 1, 1, 0, 0, 0).bits(), 7);
		assertSelect(select);
		assertEquals(0, select.select(0));
	}

	@Test
	public void testSparse() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(256 * 1024);
		bitVector.set(1);
		bitVector.set(100000);
		bitVector.set(199999);
		SimpleSelect select;

		select = new SimpleSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(64 * 1024);
		bitVector.set(1);
		bitVector.set(40000);
		bitVector.set(49999);

		select = new SimpleSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		bitVector.set(1);
		bitVector.set(20000);
		bitVector.set(29999);

		select = new SimpleSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testAllOnes() {
		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(257);
		bitVector.fill(true);
		final SimpleSelect simpleSelect = new SimpleSelect(bitVector);
		assertEquals(0, simpleSelect.select(0));
	}

	@Test
	public void testAllZeroes() {
		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(257);
		final SimpleSelect simpleSelect = new SimpleSelect(bitVector);
		assertEquals(-1, simpleSelect.select(0));
	}

	@Test
	public void testDense() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(16 * 1024);

		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 2);
		SimpleSelect select;

		select = new SimpleSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 4);

		select = new SimpleSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 8);

		select = new SimpleSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 16);

		select = new SimpleSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 32);

		select = new SimpleSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testRandom() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance(1000);
		for (int i = 0; i < 1000; i++)
			bitVector.add(r.nextBoolean());
		SimpleSelect select;

		select = new SimpleSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		SimpleSelect r;
		for (int size = 0; size <= 4096; size++) {
			v = LongArrayBitVector.getInstance().length(size);
			for (int i = (size + 1) / 2; i-- != 0;)
				v.set(i * 2);
			r = new SimpleSelect(v);
			for (int i = size / 2; i-- != 0;)
				assertEquals(i * 2, r.select(i));

			v = LongArrayBitVector.getInstance().length(size);
			v.fill(true);
			r = new SimpleSelect(v);
			for (int i = size; i-- != 0;)
				assertEquals(i, r.select(i));
		}
	}

	@Test
	public void testBulk() {
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom();
		final long[] s = new long[100000];
		for(int i = s.length; i-- != 0;) s[i] = random.nextLong() & 0xF0F0F0F088884444L;
		final SimpleSelect ef = new SimpleSelect(s, s.length * Long.SIZE);

		for(int i = 0; i < 1000; i++) {
			final int from = random.nextInt(s.length - 100);
			final int to = from + random.nextInt(100);
			final int offset = random.nextInt(10);
			final long[] dest = ef.select(from, new long[to - from + offset + random.nextInt(10)], offset, to - from);
			for(int j = from; j < to; j++) assertEquals("From: " + from + " to: " + to + " j: " + j, ef.select(j), dest[offset + j - from]);
		}
	}
}
