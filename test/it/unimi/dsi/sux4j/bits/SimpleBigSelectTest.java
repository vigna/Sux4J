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

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.LongBigArrayBitVector;
import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class SimpleBigSelectTest extends RankSelectTestCase {

	@Test
	public void testSingleton() {
		SimpleBigSelect select;

		select = new SimpleBigSelect(new long[][] { { 1L << 63, 0 } }, 64);
		assertSelect(select);
		assertEquals(63, select.select(0));

		select = new SimpleBigSelect(new long[][] { { 1 } }, 64);
		assertSelect(select);
		assertEquals(0, select.select(0));

		select = new SimpleBigSelect(new long[][] { { 1L << 63, 0 } }, 128);
		assertSelect(select);
		assertEquals(63, select.select(0));

		select = new SimpleBigSelect(new long[][] { { 1L << 63, 0 } }, 65);
		assertSelect(select);
		assertEquals(63, select.select(0));

		select = new SimpleBigSelect(new long[][] { { 1L << 63, 0, 0 } }, 129);
		assertSelect(select);
		assertEquals(63, select.select(0));
	}

	@Test
	public void testDoubleton() {
		SimpleBigSelect select;

		select = new SimpleBigSelect(new long[][] { { 1 | 1L << 32 } }, 64);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(32, select.select(1));

		select = new SimpleBigSelect(new long[][] { { 1, 1 } }, 128);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(64, select.select(1));

		select = new SimpleBigSelect(new long[][] { { 1 | 1L << 32, 0 } }, 63);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(32, select.select(1));

		select = new SimpleBigSelect(new long[][] { { 1, 1, 0 } }, 129);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(64, select.select(1));
	}

	@Test
	public void testAlternating() {
		SimpleBigSelect select;
		int i;

		select = new SimpleBigSelect(new long[][] { { 0xAAAAAAAAAAAAAAAAL } }, 64);
		assertSelect(select);
		for (i = 32; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleBigSelect(new long[][] { { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL } }, 128);
		assertSelect(select);
		for (i = 64; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleBigSelect(new long[][] { { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL,
				0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL } }, 64 * 5);
		assertSelect(select);
		for (i = 32 * 5; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleBigSelect(new long[][] { { 0xAAAAAAAAL } }, 33);
		assertSelect(select);
		for (i = 16; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SimpleBigSelect(new long[][] { { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL } }, 128);
		assertSelect(select);
		for (i = 56; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));
	}

	@Test
	public void testSelect() {
		SimpleBigSelect select;
		select = new SimpleBigSelect(BigArrays.wrap(LongBigArrayBitVector.of(1, 0, 1, 1, 0, 0, 0).bits()), 7);
		assertSelect(select);
		assertEquals(0, select.select(0));
	}

	@Test
	public void testSparse() {
		LongBigArrayBitVector bitVector = LongBigArrayBitVector.getInstance().length(256 * 1024);
		for (int i = 0; i < 100; i++) bitVector.set(i);
		bitVector.set(100000);
		bitVector.set(199999);
		SimpleBigSelect select;

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);

		bitVector = LongBigArrayBitVector.getInstance().length(1024 * 1024);
		for (int i = 0; i <= 10; i++) bitVector.set(100000 * i);

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);

		bitVector = LongBigArrayBitVector.getInstance().length(64 * 1024);
		bitVector.set(1);
		bitVector.set(40000);
		bitVector.set(49999);

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);

		bitVector = LongBigArrayBitVector.getInstance().length(32 * 1024);
		bitVector.set(1);
		bitVector.set(20000);
		bitVector.set(29999);

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testAllOnes() {
		final LongBigArrayBitVector bitVector = LongBigArrayBitVector.getInstance().length(257);
		bitVector.fill(true);
		final SimpleBigSelect simpleSelect = new SimpleBigSelect(bitVector);
		assertEquals(0, simpleSelect.select(0));
	}

	@Test
	public void testDense() {
		LongBigArrayBitVector bitVector = LongBigArrayBitVector.getInstance().length(16 * 1024);

		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 2);
		SimpleBigSelect select;

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);

		bitVector = LongBigArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 4);

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);

		bitVector = LongBigArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 8);

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);

		bitVector = LongBigArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 16);

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);

		bitVector = LongBigArrayBitVector.getInstance().length(32 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 32);

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testRandom() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		final LongBigArrayBitVector bitVector = LongBigArrayBitVector.getInstance(1000);
		for (int i = 0; i < 1000; i++)
			bitVector.add(r.nextBoolean());
		SimpleBigSelect select;

		select = new SimpleBigSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testAllSizes() {
		LongBigArrayBitVector v;
		SimpleBigSelect r;
		for (int size = 0; size <= 4096; size++) {
			v = LongBigArrayBitVector.getInstance().length(size);
			for (int i = (size + 1) / 2; i-- != 0;)
				v.set(i * 2);
			r = new SimpleBigSelect(v);
			for (int i = size / 2; i-- != 0;)
				assertEquals(i * 2, r.select(i));

			v = LongBigArrayBitVector.getInstance().length(size);
			v.fill(true);
			r = new SimpleBigSelect(v);
			for (int i = size; i-- != 0;)
				assertEquals(i, r.select(i));
		}
	}

	@Test
	public void testBulk() {
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom();
		final long[] s = new long[100000];
		for(int i = s.length; i-- != 0;) s[i] = random.nextLong() & 0xF0F0F0F088884444L;
		final SimpleBigSelect ef = new SimpleBigSelect(BigArrays.wrap(s), s.length * Long.SIZE);

		for(int i = 0; i < 1000; i++) {
			final int from = random.nextInt(s.length - 100);
			final int to = from + random.nextInt(100);
			final int offset = random.nextInt(10);
			final long[] dest = ef.select(from, new long[Math.max(offset + 1, to - from + offset + random.nextInt(10))], offset, to - from);
			for(int j = from; j < to; j++) assertEquals("From: " + from + " to: " + to + " j: " + j, ef.select(j), dest[offset + j - from]);
		}
	}
}
