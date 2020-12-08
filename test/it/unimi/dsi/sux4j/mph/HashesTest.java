/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2020 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class HashesTest {


	@Test
	public void testJenkinsPreprocessing() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		for (int l = 0; l < 1000; l++) {
			final LongArrayBitVector bv = LongArrayBitVector.getInstance();
			for (int i = 0; i < l; i++)
				bv.add(r.nextBoolean());
			final long[][] state = Hashes.preprocessJenkins(bv, 0);
			for (int i = 0; i < l; i++) {
				final long[] h = new long[3];
				Hashes.jenkins(bv, i, state[0], state[1], state[2], h);
				assertEquals("Prefix length " + i, Hashes.jenkins(bv.subVector(0, i)), h[2]);
				assertEquals("Prefix length " + i, Hashes.jenkins(bv.subVector(0, i)), Hashes.jenkins(bv, i, state[0], state[1], state[2]));
				final long[] k = new long[3];
				Hashes.jenkins(bv.subVector(0, i), 0, k);
				assertArrayEquals("Prefix length " + i, k, h);
			}
		}
	}

	@Test
	public void testMurmurPreprocessing() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		for (int l = 0; l < 1000; l++) {
			final LongArrayBitVector bv = LongArrayBitVector.getInstance();
			for (int i = 0; i < l; i++)
				bv.add(r.nextBoolean());
			final long[] state = Hashes.preprocessMurmur(bv, 0);
			for (int i = 0; i < l; i++) {
				assertEquals("Prefix length " + i, Hashes.murmur(bv.subVector(0, i), 0), Hashes.murmur(bv, i, state));
				for (int p = 0; p < l; p += 16)
					assertEquals("Prefix length " + i + ", lcp " + p, Hashes.murmur(bv.subVector(0, i), 0), Hashes.murmur(bv, i, state, p));
			}
		}
	}

	@Test
	public void testMurmur3Preprocessing() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		final long[] h = new long[2];
		for (int l = 0; l < 1000; l++) {
			final LongArrayBitVector bv = LongArrayBitVector.getInstance();
			for (int i = 0; i < l; i++)
				bv.add(r.nextBoolean());
			final long[][] state = Hashes.preprocessMurmur3(bv, 0);
			long m;
			m = Hashes.murmur3(bv, 0);
			Hashes.murmur3(bv, 0, h);
			assertEquals(m, h[0]);

			for (int i = 0; i < l; i++) {
				m = Hashes.murmur3(bv.subVector(0, i), 0);
				assertEquals("Prefix length " + i, m, Hashes.murmur3(bv, i, state[0], state[1], state[2], state[3]));
				Hashes.murmur3(bv, i, state[0], state[1], state[2], state[3], h);
				assertEquals(m, h[0]);
				for (int p = 0; p < l; p += 16) {
					m = Hashes.murmur3(bv.subVector(0, i), 0);
					assertEquals("Prefix length " + i + ", lcp " + p, m, Hashes.murmur3(bv, i, state[0], state[1], state[2], state[3], p));
					Hashes.murmur3(bv, i, state[0], state[1], state[2], state[3], p, h);
					assertEquals(m, h[0]);
				}
			}
		}
	}

	@Test
	public void test0() {
		final long[] h = new long[3];
		Hashes.jenkins(BitVectors.EMPTY_VECTOR, 0, h);
		assertEquals(Hashes.jenkins(BitVectors.EMPTY_VECTOR, 0), h[2]);
		assertEquals(Hashes.jenkins(BitVectors.EMPTY_VECTOR), h[2]);
	}

	@Test
	public void testSpooky4Preprocessing() {
		final Random r = new XoRoShiRo128PlusRandom(1);
		for(final int size: new int[] { 0, 10, 16, 100, 12 * 64 - 1, 12 * 64, 12 * 64 + 1, 1000, 2000, 2048 }) {
			for (int l = 0; l < size; l++) {
				final LongArrayBitVector bv = LongArrayBitVector.getInstance(size);
				for (int i = 0; i < l; i++) bv.add(r.nextBoolean());
				final long[] state = Hashes.preprocessSpooky4(bv, 0);
				for (int i = 0; i < l; i++) {
					final long[] h = new long[4];
					Hashes.spooky4(bv, i, 0, state, h);
					final long[] k = new long[4];
					Hashes.spooky4(bv.subVector(0, i), 0, k);
					assertArrayEquals("Prefix length " + i, k, h);
				}
			}
		}
	}
}
