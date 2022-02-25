/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2022 Sebastiano Vigna
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
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class SimpleSelectZeroTest extends RankSelectTestCase {


	@Test
	public void testSingleton() {
		SimpleSelectZero select;

		select = new SimpleSelectZero(new long[] { -1L >>> 1, 0 }, 64);
		assertSelectZero(select);
		assertEquals(63, select.selectZero(0));

		select = new SimpleSelectZero(new long[] { -1L << 1 }, 64);
		assertSelectZero(select);
		assertEquals(0, select.selectZero(0));

		select = new SimpleSelectZero(new long[] { -1L >>> 1, -1L }, 128);
		assertSelectZero(select);
		assertEquals(63, select.selectZero(0));

		select = new SimpleSelectZero(new long[] { -1L >>> 1, 1 }, 65);
		assertSelectZero(select);
		assertEquals(63, select.selectZero(0));

		select = new SimpleSelectZero(new long[] { -1L >>> 1, -1, 1 }, 129);
		assertSelectZero(select);
		assertEquals(63, select.selectZero(0));
	}

	@Test
	public void testDoubleton() {
		SimpleSelectZero select;

		select = new SimpleSelectZero(new long[] { 0xFFFFFFFEFFFFFFFEL }, 64);
		assertSelectZero(select);
		assertEquals(0, select.selectZero(0));
		assertEquals(32, select.selectZero(1));

		select = new SimpleSelectZero(new long[] { -1L << 1, -1L << 1 }, 128);
		assertSelectZero(select);
		assertEquals(0, select.selectZero(0));
		assertEquals(64, select.selectZero(1));

		select = new SimpleSelectZero(new long[] { 0x7FFFFFFEFFFFFFFEL, 0 }, 63);
		assertSelectZero(select);
		assertEquals(0, select.selectZero(0));
		assertEquals(32, select.selectZero(1));

		select = new SimpleSelectZero(new long[] { -1L << 1, -1L << 1, 1 }, 129);
		assertSelectZero(select);
		assertEquals(0, select.selectZero(0));
		assertEquals(64, select.selectZero(1));
	}

	@Test
	public void testAlternating() {
		SimpleSelectZero select;
		int i;

		select = new SimpleSelectZero(new long[] { 0x5555555555555555L }, 64);
		assertSelectZero(select);
		for (i = 32; i-- != 1;)
			assertEquals(i * 2 + 1, select.selectZero(i));

		select = new SimpleSelectZero(new long[] { 0x5555555555555555L, 0x5555555555555555L }, 128);
		assertSelectZero(select);
		for (i = 64; i-- != 1;)
			assertEquals(i * 2 + 1, select.selectZero(i));

		select = new SimpleSelectZero(new long[] { 0x5555555555555555L, 0x5555555555555555L, 0x5555555555555555L, 0x5555555555555555L, 0x5555555555555555L }, 64 * 5);
		assertSelectZero(select);
		for (i = 32 * 5; i-- != 1;)
			assertEquals(i * 2 + 1, select.selectZero(i));

		select = new SimpleSelectZero(new long[] { 0x55555555L }, 33);
		assertSelectZero(select);
		for (i = 16; i-- != 1;)
			assertEquals(i * 2 + 1, select.selectZero(i));

		select = new SimpleSelectZero(new long[] { 0x5555555555555555L, 0x555555555555L }, 128);
		assertSelectZero(select);
		for (i = 56; i-- != 1;)
			assertEquals(i * 2 + 1, select.selectZero(i));
	}

	@Test
	public void testSparse() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(256 * 1024);
		for (int i = 0; i < 100; i++) bitVector.set(i);
		bitVector.set(100000);
		bitVector.set(199999);
		SimpleSelectZero select;

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);

		bitVector = LongArrayBitVector.getInstance().length(64 * 1024);
		bitVector.set(1);
		bitVector.set(40000);
		bitVector.set(49999);

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		bitVector.set(1);
		bitVector.set(20000);
		bitVector.set(29999);

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);
	}

	@Test
	public void testDense() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(16 * 1024);

		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 2);
		SimpleSelectZero select;

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 4);

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 8);

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 16);

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 32);

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);
	}

	@Test
	public void testRandom() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance(1000);
		for (int i = 0; i < 1000; i++)
			bitVector.add(r.nextBoolean());
		SimpleSelectZero select;

		select = new SimpleSelectZero(bitVector);
		assertSelectZero(select);
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		SimpleSelectZero r;
		for (int size = 0; size <= 4096; size++) {
			v = LongArrayBitVector.getInstance().length(size);
			v.fill(true);
			for (int i = (size + 1) / 2; i-- != 0;)
				v.set(i * 2, false);
			r = new SimpleSelectZero(v);
			for (int i = size / 2; i-- != 0;)
				assertEquals(i * 2, r.selectZero(i));

			v = LongArrayBitVector.getInstance().length(size);
			v.fill(false);
			r = new SimpleSelectZero(v);
			for (int i = size; i-- != 0;)
				assertEquals(i, r.selectZero(i));
		}
	}
}
