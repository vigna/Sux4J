package it.unimi.dsi.sux4j.util;
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



import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.NoSuchElementException;

import org.junit.Test;

import com.google.common.collect.Iterators;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class EliasFanoMonotoneBigLongBigListSlowTest {

	private final class Elements implements LongIterator {
		private final XoRoShiRo128PlusRandomGenerator random = new XoRoShiRo128PlusRandomGenerator(0);
		private final int k;
		private final long n;
		long i, d;

		private Elements(final long n, final int k) {
			this.n = n;
			this.k = k;
		}

		@Override
		public boolean hasNext() {
			return i < n;
		}

		@Override
		public long nextLong() {
			if (!hasNext()) throw new NoSuchElementException();
			i++;
			return d += k * Long.numberOfTrailingZeros(random.nextLong());
		}
	}

	public void testRandom(final long n, final int k) {
		long m = 0;
		LongIterator iterator = new Elements(n, k);
		for (long i = 0; i < n; i++) m = iterator.nextLong();
		m++;

		final EliasFanoMonotoneBigLongBigList ef = new EliasFanoMonotoneBigLongBigList(n, m, new Elements(n, k));

		assertTrue(Iterators.elementsEqual(new Elements(n, k), ef.iterator()));
		iterator = new Elements(n, k);
		for (long i = 0; i < n; i++) assertEquals(iterator.nextLong(), ef.getLong(i));
	}

	@Test
	public void test1Mi() {
		testRandom(1 << 20, 5);
	}

	@Test
	public void test100Mi() {
		testRandom(100 * (1 << 20), 5);
	}

	@Test
	public void test2Gi() {
		testRandom(2 * (1L << 30), 5);
	}

	@Test
	public void test64Gi() {
		testRandom(64 * (1L << 30), 5);
	}

	@Test
	public void test1MiNoLower() {
		testRandom(1 << 20, 1);
	}

	@Test
	public void test100MiNoLower() {
		testRandom(100 * (1 << 20), 1);
	}

	@Test
	public void test2GiNoLower() {
		testRandom(2 * (1L << 30), 1);
	}

	@Test
	public void test64GiNoLower() {
		testRandom(64 * (1L << 30), 1);
	}
}
