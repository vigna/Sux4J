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

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class HollowTrieMonotoneMinimalPerfectHashFunctionTest {

	public static ObjectArrayList<BitVector> listOf(final int[]... bit) {
		final ObjectArrayList<BitVector> vectors = new ObjectArrayList<>();
		for (final int[] v : bit)
			vectors.add(LongArrayBitVector.of(v));
		return vectors;
	}

	@Test
	public void testEmpty() {
		final HollowTrieMonotoneMinimalPerfectHashFunction<BitVector> hollowTrie = new HollowTrieMonotoneMinimalPerfectHashFunction<>(listOf(new int[][] {}), TransformationStrategies.identity());
		assertEquals(-1, hollowTrie.getLong(LongArrayBitVector.of(0)));
		assertEquals(-1, hollowTrie.getLong(LongArrayBitVector.of(1)));
		assertEquals(0, hollowTrie.size64());
	}

	@Test
	public void testSingleton() {
		final HollowTrieMonotoneMinimalPerfectHashFunction<BitVector> hollowTrie = new HollowTrieMonotoneMinimalPerfectHashFunction<>(
				listOf(new int[][] { { 0 } }).iterator(), TransformationStrategies.identity());

		assertEquals(0, hollowTrie.getLong(LongArrayBitVector.of(0)));
		assertEquals(1, hollowTrie.size64());
	}

	@Test
	public void testSimple() {
		HollowTrieMonotoneMinimalPerfectHashFunction<BitVector> hollowTrie = new HollowTrieMonotoneMinimalPerfectHashFunction<>(
				listOf(new int[][] { { 0 }, { 1, 0, 0, 0, 0 }, { 1, 0, 0, 0, 1 }, { 1, 0, 0, 1 } }).iterator(), TransformationStrategies.identity());

		assertEquals(0, hollowTrie.getLong(LongArrayBitVector.of(0)));
		assertEquals(1, hollowTrie.getLong(LongArrayBitVector.of(1, 0, 0, 0, 0)));
		assertEquals(2, hollowTrie.getLong(LongArrayBitVector.of(1, 0, 0, 0, 1)));
		assertEquals(3, hollowTrie.getLong(LongArrayBitVector.of(1, 0, 0, 1)));
		assertEquals(4, hollowTrie.size64());


		hollowTrie = new HollowTrieMonotoneMinimalPerfectHashFunction<>(
				listOf(new int[][] { { 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0 }, { 0, 1, 0, 1, 0, 0 }, { 0, 1, 0, 1, 0, 1 }, { 0, 1, 1, 1, 0 } }).iterator(), TransformationStrategies.identity());

		assertEquals(0, hollowTrie.getLong(LongArrayBitVector.of(0, 0, 0, 0, 0)));
		assertEquals(1, hollowTrie.getLong(LongArrayBitVector.of(0, 1, 0, 0, 0)));
		assertEquals(2, hollowTrie.getLong(LongArrayBitVector.of(0, 1, 0, 1, 0, 0)));
		assertEquals(3, hollowTrie.getLong(LongArrayBitVector.of(0, 1, 0, 1, 0, 1)));
		assertEquals(4, hollowTrie.getLong(LongArrayBitVector.of(0, 1, 1, 1, 0)));
		assertEquals(5, hollowTrie.size64());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testRandom() throws IOException, ClassNotFoundException {
		final Random r = new XoRoShiRo128PlusRandom(3);
		final int n = 10;
		final LongArrayBitVector[] bitVector = new LongArrayBitVector[n];
		for (int i = 0; i < n; i++) {
			bitVector[i] = LongArrayBitVector.getInstance();
			int l = 12;
			while (l-- != 0)
				bitVector[i].add(r.nextBoolean());
		}

		// Sort lexicographically
		Arrays.sort(bitVector);

		HollowTrieMonotoneMinimalPerfectHashFunction<LongArrayBitVector> hollowTrie = new HollowTrieMonotoneMinimalPerfectHashFunction<>(Arrays.asList(bitVector),
				TransformationStrategies.identity());

		for (int i = 0; i < n; i++)
			assertEquals(i, hollowTrie.getLong(bitVector[i]));
		assertEquals(n, hollowTrie.size64());

		// Exercise code for negative results
		for (int i = 1000; i-- != 0;) {
			int l = r.nextInt(30);
			final BitVector bv = LongArrayBitVector.getInstance(l);
			while (l-- != 0)
				bv.add(r.nextBoolean());
			hollowTrie.getLong(bv);
		}

		// Test serialisation
		final File temp = File.createTempFile(getClass().getSimpleName(), "test");
		temp.deleteOnExit();
		BinIO.storeObject(hollowTrie, temp);
		hollowTrie = (HollowTrieMonotoneMinimalPerfectHashFunction<LongArrayBitVector>)BinIO.loadObject(temp);

		for (int i = 0; i < n; i++)
			assertEquals(i, hollowTrie.getLong(bitVector[i]));

		// Test that random inquiries do not break the trie
		for (int i = 0; i < 10; i++) {
			bitVector[i] = LongArrayBitVector.getInstance();
			int l = 8;
			while (l-- != 0)
				bitVector[i].add(r.nextBoolean());
		}
		assertEquals(n, hollowTrie.size64());

	}
}
