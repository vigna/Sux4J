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

package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeSet;

import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Test;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.sux4j.mph.Hashes;
import it.unimi.dsi.sux4j.util.ZFastTrie.InternalNode;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class ZFastTrieTest {


	public static String binary(final int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}

	private <T> ZFastTrie<T> testSerialization(final ZFastTrie<T> zft) throws IOException, ClassNotFoundException {
		final File temp = File.createTempFile(getClass().getSimpleName(), "test");
		temp.deleteOnExit();
		BinIO.storeObject(zft, temp);
		@SuppressWarnings("unchecked")
		final ZFastTrie<T> deserialized = (ZFastTrie<T>)BinIO.loadObject(temp);
		assertEquals(zft, deserialized);
		return deserialized;
	}

	@Test
	public void testEmpty() throws IOException, ClassNotFoundException {
		final String[] s = {};
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		assertFalse(zft.contains(""));
		zft = testSerialization(zft);
		assertFalse(zft.contains(""));
	}

	@Test
	public void testSingleton() throws IOException, ClassNotFoundException {
		final String[] s = { "a" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		zft.remove("a");
		assertFalse(zft.contains("a"));

		final ObjectBidirectionalIterator<String> iterator = zft.iterator();
		assertFalse(iterator.hasNext());
		assertFalse(iterator.hasPrevious());
	}

	@Test
	public void testDoubleton() throws IOException, ClassNotFoundException {
		final String[] s = { "a", "c" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testDoubleton2() throws IOException, ClassNotFoundException {
		final String[] s = { "c", "a" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testTriple() throws IOException, ClassNotFoundException {
		final String[] s = { "a", "b", "c" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testTriple2() throws IOException, ClassNotFoundException {
		final String[] s = { "c", "b", "a" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testExitNodeIsLeaf() throws IOException, ClassNotFoundException {
		final String[] s = { "a", "aa", "aaa" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testExitNodeIsLeaf3() throws IOException, ClassNotFoundException {
		final String[] s = { "a", "aa", "aaa" };
		ZFastTrie<String> zft = new ZFastTrie<>(TransformationStrategies.prefixFreeIso());
		for (final String t : s) zft.add(t);
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testRightJumps() throws IOException, ClassNotFoundException {
		final ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		zft.add(LongArrayBitVector.of(0));
		zft.add(LongArrayBitVector.of(1, 0));
		zft.add(LongArrayBitVector.of(1, 1, 0));
		testSerialization(zft);
	}


	@Test
	public void testSmallest() throws IOException, ClassNotFoundException {
		final String[] s = { "a", "b", "c", "d", "e", "f", "g" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testSmallest2() throws IOException, ClassNotFoundException {
		final String[] s = { "g", "f", "e", "d", "c", "b", "a" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}

	@Test
	public void testSmall() throws IOException, ClassNotFoundException {
		final String[] s = { "-", "0", "1", "4", "5", "a", "b", "c", "d", "e", "f", "g", "}" };
		ZFastTrie<String> zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());
		for (int i = s.length; i-- != 0;)
			assertTrue(s[i], zft.contains(s[i]));
		zft = testSerialization(zft);
		for (int i = s.length; i-- != 0;)
			assertTrue(zft.contains(s[i]));

		for (int i = s.length; i-- != 0;) {
			assertTrue(zft.remove(s[i]));
			assertFalse(zft.contains(s[i]));
		}
	}


	@Test
	public void testEmptyLcp() {
		final ZFastTrie<BitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		assertTrue(zft.add(LongArrayBitVector.of(0, 0)));
		assertTrue(zft.add(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.add(BitVectors.ONE));
		assertTrue(zft.contains(LongArrayBitVector.of(0, 0)));
		assertTrue(zft.contains(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.contains(BitVectors.ONE));
		assertTrue(zft.remove(BitVectors.ONE));
		assertTrue(zft.remove(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.remove(LongArrayBitVector.of(0, 0)));

		assertTrue(zft.add(LongArrayBitVector.of(0, 0)));
		assertTrue(zft.add(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.add(BitVectors.ONE));
		assertTrue(zft.remove(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.remove(BitVectors.ONE));
		assertTrue(zft.remove(LongArrayBitVector.of(0, 0)));

		assertTrue(zft.add(LongArrayBitVector.of(0, 0)));
		assertTrue(zft.add(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.add(BitVectors.ONE));
		assertTrue(zft.remove(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.remove(LongArrayBitVector.of(0, 0)));
		assertTrue(zft.remove(BitVectors.ONE));

		assertTrue(zft.add(LongArrayBitVector.of(1, 0)));
		assertTrue(zft.add(LongArrayBitVector.of(1, 1)));
		assertTrue(zft.add(BitVectors.ZERO));
		assertTrue(zft.contains(LongArrayBitVector.of(1, 0)));
		assertTrue(zft.contains(LongArrayBitVector.of(1, 1)));
		assertTrue(zft.contains(BitVectors.ZERO));
		assertTrue(zft.remove(BitVectors.ZERO));
		assertTrue(zft.remove(LongArrayBitVector.of(1, 1)));
		assertTrue(zft.remove(LongArrayBitVector.of(1, 0)));

		assertTrue(zft.add(LongArrayBitVector.of(1, 0)));
		assertTrue(zft.add(LongArrayBitVector.of(1, 1)));
		assertTrue(zft.add(BitVectors.ZERO));
		assertTrue(zft.remove(LongArrayBitVector.of(1, 1)));
		assertTrue(zft.remove(BitVectors.ZERO));
		assertTrue(zft.remove(LongArrayBitVector.of(1, 0)));

		assertTrue(zft.add(LongArrayBitVector.of(1, 0)));
		assertTrue(zft.add(LongArrayBitVector.of(1, 1)));
		assertTrue(zft.add(BitVectors.ZERO));
		assertTrue(zft.remove(LongArrayBitVector.of(1, 1)));
		assertTrue(zft.remove(LongArrayBitVector.of(1, 0)));
		assertTrue(zft.remove(BitVectors.ZERO));
	}

	@Test
	public void testManyBranches() throws ClassNotFoundException, IOException {
		ZFastTrie<BitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		for (int p = 0; p < 10; p++) {
			for (int i = 0; i < (1 << p); i++)
				assertTrue(zft.add(LongArrayBitVector.getInstance().append(i, p)));
			for (int i = 0; i < (1 << p); i++)
				assertTrue(zft.contains(LongArrayBitVector.getInstance().append(i, p)));
			zft = testSerialization(zft);
			for (int i = 0; i < (1 << p); i++)
				assertTrue(zft.remove(LongArrayBitVector.getInstance().append(i, p)));
			for (int i = 0; i < (1 << p); i++)
				assertTrue(zft.add(LongArrayBitVector.getInstance().append(i, p)));
			for (int i = (1 << p); i-- != 0;)
				assertTrue(zft.remove(LongArrayBitVector.getInstance().append(i, p)));
		}

		for (int p = 0; p < 10; p++) {
			for (int i = (1 << p); i-- != 0;)
				assertTrue(zft.add(LongArrayBitVector.getInstance().append(i, p)));
			for (int i = (1 << p); i-- != 0;)
				assertTrue(zft.contains(LongArrayBitVector.getInstance().append(i, p)));
			zft = testSerialization(zft);
			for (int i = (1 << p); i-- != 0;)
				assertTrue(zft.remove(LongArrayBitVector.getInstance().append(i, p)));
			for (int i = (1 << p); i-- != 0;)
				assertTrue(zft.add(LongArrayBitVector.getInstance().append(i, p)));
			for (int i = 0; i < (1 << p); i++)
				assertTrue(zft.remove(LongArrayBitVector.getInstance().append(i, p)));
		}
	}

	@Test
	public void testLinear() throws ClassNotFoundException, IOException {
		ZFastTrie<BitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		for (int p = 0; p < 20; p++)
			assertTrue(zft.add(LongArrayBitVector.getInstance().append(1 << p, p + 1)));
		for (int p = 0; p < 20; p++)
			assertTrue(zft.contains(LongArrayBitVector.getInstance().append(1 << p, p + 1)));
		zft = testSerialization(zft);
		for (int p = 0; p < 20; p++)
			assertTrue(zft.remove(LongArrayBitVector.getInstance().append(1 << p, p + 1)));
		for (int p = 0; p < 20; p++)
			assertTrue(zft.add(LongArrayBitVector.getInstance().append(1 << p, p + 1)));
		for (int p = 20; p-- != 0;)
			assertTrue(zft.remove(LongArrayBitVector.getInstance().append(1 << p, p + 1)));
	}

	@Test
	public void testPrefixExtension() throws ClassNotFoundException, IOException {
		ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		zft.add(LongArrayBitVector.of(0, 0));
		zft.add(LongArrayBitVector.of(0, 1));
		assertTrue(zft.add(LongArrayBitVector.of(1, 0, 0, 0, 1)));
		assertFalse(zft.add(LongArrayBitVector.of(0, 0)));
		assertFalse(zft.add(LongArrayBitVector.of(0, 1)));
		assertTrue(zft.contains(LongArrayBitVector.of(0, 0)));
		assertTrue(zft.contains(LongArrayBitVector.of(0, 1)));
		assertFalse(zft.contains(LongArrayBitVector.of()));
		assertFalse(zft.contains(LongArrayBitVector.of(0)));
		assertFalse(zft.contains(LongArrayBitVector.of(0, 0, 0)));
		zft = testSerialization(zft);
		assertFalse(zft.remove(LongArrayBitVector.of()));
		assertFalse(zft.remove(LongArrayBitVector.of(0)));
		assertFalse(zft.remove(LongArrayBitVector.of(0, 0, 0)));
		assertFalse(zft.remove(LongArrayBitVector.of(1, 0, 0, 1)));

	}

	@Test
	public void testCompletionLoop() throws ClassNotFoundException, IOException {
		final ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		zft.add(LongArrayBitVector.of(0, 0, 0, 0, 0, 0));
		zft.add(LongArrayBitVector.of(0, 0, 0, 0, 0, 1, 0, 0, 0));
		zft.add(LongArrayBitVector.of(0, 0, 0, 0, 0, 1, 0, 0, 1, 0));
		zft.add(LongArrayBitVector.of(0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0));
		final LongArrayBitVector l = LongArrayBitVector.of(0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 1);
		zft.add(l);
		final long[] state = Hashes.preprocessMurmur(l, 42);
		final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
		zft.getParentExitNode(l, state, stack);
		final InternalNode<LongArrayBitVector> parex = stack.pop();
		// This specific completion has an iteration in which the test (a & checkMask) != f fails
		zft.completeFatBinarySearchStack(l, state, stack, stack.top().extentLength, parex.nameLength - 1);
		assertSame(parex, stack.top().right);
		testSerialization(zft);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddingProperPrefix() {
		final ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		zft.add(LongArrayBitVector.of(0, 0));
		zft.add(LongArrayBitVector.of(0, 1));
		zft.contains(LongArrayBitVector.of(0, 0));
		zft.contains(LongArrayBitVector.of(0, 1));
		zft.add(LongArrayBitVector.of(0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void testAddingProperExtension() {
		final ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		zft.add(LongArrayBitVector.of(0, 0));
		zft.add(LongArrayBitVector.of(0, 1));
		zft.contains(LongArrayBitVector.of(0, 0));
		zft.contains(LongArrayBitVector.of(0, 1));
		zft.add(LongArrayBitVector.of(0, 0, 0));
	}

	@Test
	public void testCutsHighOnRoot() throws ClassNotFoundException, IOException {
		ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		zft.add(LongArrayBitVector.of(0, 0, 0, 0, 0, 0, 0));

		final LongArrayBitVector v = LongArrayBitVector.of(0, 0, 0, 0, 0, 0, 1);
		for (int i = 0; i < 100; i++) {
			final LongArrayBitVector w = v.copy();
			w.add(1);
			zft.add(w);
			v.add(0);
		}

		zft = testSerialization(zft);
		assertFalse(zft.contains(LongArrayBitVector.of(0)));
		assertFalse(zft.contains(LongArrayBitVector.of(0, 0)));
		assertFalse(zft.contains(LongArrayBitVector.of(0, 0, 0, 0)));
		assertFalse(zft.contains(LongArrayBitVector.of(0, 1)));
		assertFalse(zft.contains(LongArrayBitVector.of(1)));
		assertFalse(zft.contains(LongArrayBitVector.of(1, 0)));
		assertFalse(zft.contains(LongArrayBitVector.of(1, 1)));
	}

	@Test
	public void testCatchAllSearches() {
		final ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		final LongArrayBitVector v = LongArrayBitVector.of();
		for(int i = 0; i < 100; i++) {
			final LongArrayBitVector w = v.copy();
			w.add(1);
			zft.add(w);
			v.add(0);
		}

		v.length(50);
		v.add(1);
		zft.remove(v);
	}

	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {
		ZFastTrie<String> zft;
		final RandomGenerator random = new XoRoShiRo128PlusRandomGenerator(1);

		for (int d = 10; d < 1000; d *= 10) {
			final String[] s = new String[d];

			for (int rand = 0; rand < 2; rand++) {
				for (int i = s.length; i-- != 0;)
					s[i] = binary(i);

				for (int pass = 0; pass < 2; pass++) {

					zft = new ZFastTrie<>(Arrays.asList(s), TransformationStrategies.prefixFreeIso());

					for (int i = s.length; i-- != 0;)
						assertTrue(s[i], zft.contains(s[i]));

					// Exercise code for negative results
					for (int i = 1000; i-- != 0;)
						zft.contains(binary(i * i + d));

					zft = testSerialization(zft);
					for (int i = s.length; i-- != 0;)
						assertTrue(s[i], zft.contains(s[i]));

					zft = new ZFastTrie<>(Arrays.asList(s), new HuTuckerTransformationStrategy(Arrays.asList(s), true));

					for (int i = s.length; i-- != 0;)
						assertTrue(s[i], zft.contains(s[i]));

					zft = testSerialization(zft);
					for (int i = s.length; i-- != 0;)
						assertTrue(s[i], zft.contains(s[i]));

					Collections.sort(Arrays.asList(s));

					int p = 0;
					ObjectBidirectionalIterator<String> iterator;
					for (iterator = zft.iterator(); iterator.hasNext();)
						assertEquals(iterator.next(), s[p++]);
					while (iterator.hasPrevious())
						assertEquals(iterator.previous(), s[--p]);

					for (int i = 0; i < s.length / 100; i++) {
						p = i;
						for (iterator = zft.iterator(s[i]); iterator.hasNext();)
							assertEquals(iterator.next(), s[p++]);
						while (iterator.hasPrevious())
							assertEquals(iterator.previous(), s[--p]);
					}

					for (int i = s.length; i-- != 0;) {
						assertTrue(zft.remove(s[i]));
						assertFalse(zft.contains(s[i]));
					}

					Collections.shuffle(Arrays.asList(s), new XoRoShiRo128PlusRandom(1));
				}
			}

			for (int i = s.length; i-- != 0;)
				s[i] = binary(random.nextInt(Integer.MAX_VALUE));

		}
	}


	@SuppressWarnings("boxing")
	@Test
	public void testPredSuccRangeLongs() throws ClassNotFoundException, IOException {
		final TreeSet<Long> t = new TreeSet<>((x, y) -> Long.compareUnsigned(x, y));
		final long u = 1 << 12;
		final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
		for (int i = 0; i < u / 4; i++) t.add(r.nextLong() % u);

		ZFastTrie<Long> zft = new ZFastTrie<>(TransformationStrategies.fixedLong());
		zft.addAll(t);

		zft = testSerialization(zft);

		for (long i = -u - 1; i <= u + 1; i++) {
			assertEquals(t.ceiling(i), zft.successor(i));
			assertEquals(t.ceiling(i), zft.ceiling(i));
			assertEquals(t.higher(i), zft.strictSuccessor(i));
			assertEquals(t.higher(i), zft.higher(i));
			assertEquals(t.lower(i), zft.predecessor(i));
			assertEquals(t.lower(i), zft.lower(i));
			assertEquals(t.floor(i), zft.weakPredecessor(i));
			assertEquals(t.floor(i), zft.floor(i));
		}

		Long prev = null, prevPrev = null;
		for (final Long l : zft) {
			// Empty ranges from consecutive keys
			if (prev != null) {
				assertFalse(zft.nonemptyRange(prev + 1, l));
				assertTrue(zft.nonemptyRange(prev, l));
				assertTrue(zft.nonemptyRange(prev, l + 1));
			}
			// Nonempty ranges from keys two places apart
			if (prevPrev != null) {
				assertTrue(zft.nonemptyRange(prevPrev - 1, l));
				assertTrue(zft.nonemptyRange(prevPrev, l));
				assertTrue(zft.nonemptyRange(prevPrev, l + 1));
			}
			prevPrev = prev;
			prev = l;
		}

		for (int i = 0; i < 1000; i++) {
			long x = r.nextLong() % u;
			long y = r.nextLong() % u;
			if (Long.compareUnsigned(x, y) > 0) {
				final long tt = x;
				x = y;
				y = tt;
			}

			assertTrue(zft.nonemptyRange(x, y) == Long.compareUnsigned(t.ceiling(x), y) < 0);
			assertTrue(zft.nonemptyRange(x, x + 1) == Long.compareUnsigned(t.ceiling(x), x + 1) < 0);
		}

		for (final Long x : t) zft.remove(x);
	}

	private static LongArrayBitVector randomBitVector(final RandomGenerator r, final int maxLength) {
		final LongArrayBitVector v = LongArrayBitVector.ofLength(r.nextInt(maxLength));
		for (int i = 0; i < v.length(); i++) if (r.nextBoolean()) v.set(i);
		return v;
	}

	@Test
	public void testPredSuccRangeBits() throws ClassNotFoundException, IOException {
		final TreeSet<LongArrayBitVector> t = new TreeSet<>();
		final XoRoShiRo128PlusRandomGenerator r = new XoRoShiRo128PlusRandomGenerator(0);
		for (int i = 0; i < 1 << 12; i++) t.add(randomBitVector(r, 100));

		ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.prefixFree());
		zft.addAll(t);

		zft = testSerialization(zft);

		for (int i = 0; i < 10000; i++) {
			final LongArrayBitVector x = randomBitVector(r, 100);
			assertEquals(t.ceiling(x), zft.successor(x));
			assertEquals(t.ceiling(x), zft.ceiling(x));
			assertEquals(t.higher(x), zft.strictSuccessor(x));
			assertEquals(t.higher(x), zft.higher(x));
			assertEquals(t.lower(x), zft.predecessor(x));
			assertEquals(t.lower(x), zft.lower(x));
			assertEquals(t.floor(x), zft.weakPredecessor(x));
			assertEquals(t.floor(x), zft.floor(x));
		}

		for (int i = 0; i < 10000; i++) {
			LongArrayBitVector x = randomBitVector(r, 100);
			LongArrayBitVector y = randomBitVector(r, 100);
			if (x.compareTo(y) > 0) {
				final LongArrayBitVector tt = x;
				x = y;
				y = tt;
			}

			assertTrue(zft.nonemptyRange(x, y) == (t.ceiling(x) != null && t.ceiling(x).compareTo(y) < 0));
		}

		for (int i = 0; i < 10000; i++) {
			LongArrayBitVector x = randomBitVector(r, 100);
			if (x.length() == 0) continue;
			LongArrayBitVector y = x.copy();
			final long p = r.nextLong(y.length());
			y.flip(p);
			if (x.compareTo(y) > 0) {
				final LongArrayBitVector tt = x;
				x = y;
				y = tt;
			}

			assertTrue(zft.nonemptyRange(x, y) == (t.ceiling(x) != null && t.ceiling(x).compareTo(y) < 0));
		}

		for (final LongArrayBitVector x : t) zft.remove(x);
	}

	@Test
	public void testFatBinarySearchStackExact() {
		final ZFastTrie<LongArrayBitVector> z = new ZFastTrie<>(TransformationStrategies.identity());
		z.add(LongArrayBitVector.of(0, 0, 0, 0));
		z.add(LongArrayBitVector.of(0, 0, 0, 1));
		z.add(LongArrayBitVector.of(1, 0, 0, 0));
		z.add(LongArrayBitVector.of(1, 0, 1, 0, 1));
		z.add(LongArrayBitVector.of(1, 0, 1, 1, 1));

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of() }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			assertEquals(v.toString(), 0, stack.size());
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0),
				LongArrayBitVector.of(1), LongArrayBitVector.of(0), LongArrayBitVector.of(0, 1),
				LongArrayBitVector.of(0, 0), LongArrayBitVector.of(0, 0, 0), LongArrayBitVector.of(1, 1),
				LongArrayBitVector.of(0, 0, 1, 1),
				LongArrayBitVector.of(0, 0, 1, 1, 1, 1, 1, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			assertEquals(v.toString(), 1, stack.size());
		}

		// This test avoid the root and make the 2-fattest test fail at the first iteration
		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(0, 0, 1, 1, 1, 1, 1, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length() - 1);
			assertEquals(v.toString(), 1, stack.size());
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			assertEquals(v.toString(), 2, stack.size());
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] {
				LongArrayBitVector.of(1, 0, 1, 0), LongArrayBitVector.of(1, 0, 1, 0, 1),
				LongArrayBitVector.of(1, 0, 1, 1), LongArrayBitVector.of(1, 0, 1, 1, 1), }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			assertEquals(v.toString(), 3, stack.size());
		}
	}

	@Test
	public void testFatBinarySearchExact() {
		final ZFastTrie<LongArrayBitVector> z = new ZFastTrie<>(TransformationStrategies.identity());
		z.add(LongArrayBitVector.of(0, 0, 0, 0));
		z.add(LongArrayBitVector.of(0, 0, 0, 1));
		z.add(LongArrayBitVector.of(1, 0, 0, 0));
		z.add(LongArrayBitVector.of(1, 0, 1, 0, 1));
		z.add(LongArrayBitVector.of(1, 0, 1, 1, 1));

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of() }) {
			final long[] state = Hashes.preprocessMurmur(v, 42);
			final InternalNode<LongArrayBitVector> top = z.fatBinarySearchExact(v, state, v.length());
			assertTrue(top.extentLength == 0); // root
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0),
				LongArrayBitVector.of(1), LongArrayBitVector.of(0), LongArrayBitVector.of(0, 1),
				LongArrayBitVector.of(0, 0), LongArrayBitVector.of(0, 0, 0), LongArrayBitVector.of(1, 1),
				LongArrayBitVector.of(0, 0, 1, 1),
				LongArrayBitVector.of(0, 0, 1, 1, 1, 1, 1, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			final InternalNode<LongArrayBitVector> top = z.fatBinarySearchExact(v, state, v.length());
			assertTrue(stack.top() == top || stack.top().right == top || stack.top().left == top);
		}

		// This test avoid the root and make the 2-fattest test fail at the first iteration
		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(0, 0, 1, 1, 1, 1, 1, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			final InternalNode<LongArrayBitVector> top = z.fatBinarySearchExact(v, state, v.length());
			assertTrue(stack.top() == top || stack.top().right == top || stack.top().left == top);
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			final InternalNode<LongArrayBitVector> top = z.fatBinarySearchExact(v, state, v.length());
			assertTrue(stack.top() == top || stack.top().right == top || stack.top().left == top);
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0, 1, 0),
				LongArrayBitVector.of(1, 0, 1, 0, 1), LongArrayBitVector.of(1, 0, 1, 1),
				LongArrayBitVector.of(1, 0, 1, 1, 1), }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStackExact(v, state, stack, v.length());
			final InternalNode<LongArrayBitVector> top = z.fatBinarySearchExact(v, state, v.length());
			assertTrue(stack.top() == top || stack.top().right == top || stack.top().left == top);
		}
	}

	@Test
	public void testFatBinarySearchStack() {
		final ZFastTrie<LongArrayBitVector> z = new ZFastTrie<>(TransformationStrategies.identity());
		z.add(LongArrayBitVector.of(0, 0, 0, 0));
		z.add(LongArrayBitVector.of(0, 0, 0, 1));
		z.add(LongArrayBitVector.of(1, 0, 0, 0));
		z.add(LongArrayBitVector.of(1, 0, 1, 0, 1));
		z.add(LongArrayBitVector.of(1, 0, 1, 1, 1));

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of() }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			// Here we cannot use v.length() - 1 == -1
			z.fatBinarySearchStack(v, state, stack, v.length());
			assertEquals(v.toString(), 0, stack.size());
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0),
				LongArrayBitVector.of(1), LongArrayBitVector.of(0), LongArrayBitVector.of(0, 1),
				LongArrayBitVector.of(0, 0), LongArrayBitVector.of(1, 1), LongArrayBitVector.of(0, 0, 0),

		}) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStack(v, state, stack, v.length() - 1);
			assertEquals(v.toString(), 1, stack.size());
		}

		// This cuts low
		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(0, 0, 1, 1),
				LongArrayBitVector.of(0, 0, 1, 1, 1, 1, 1, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStack(v, state, stack, v.length() - 1);
			assertEquals(v.toString(), 2, stack.size());
		}

		// This test avoid the root and make the 2-fattest test fail at the first iteration
		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(0, 0, 1, 1, 1, 1, 1, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStack(v, state, stack, v.length());
			// Exit node
			assertEquals(v.toString(), 2, stack.size());
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0, 1) }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStack(v, state, stack, v.length() - 1);
			assertEquals(v.toString(), 2, stack.size());
		}

		for (final LongArrayBitVector v : new LongArrayBitVector[] { LongArrayBitVector.of(1, 0, 1, 0),
				LongArrayBitVector.of(1, 0, 1, 0, 1), LongArrayBitVector.of(1, 0, 1, 1),
				LongArrayBitVector.of(1, 0, 1, 1, 1), }) {
			final ObjectArrayList<ZFastTrie.InternalNode<LongArrayBitVector>> stack = new ObjectArrayList<>();
			final long[] state = Hashes.preprocessMurmur(v, 42);
			z.fatBinarySearchStack(v, state, stack, v.length() - 1);
			assertEquals(v.toString(), 3, stack.size());
		}
	}

	@SuppressWarnings("boxing")
	@Test
	// @Ignore
	public void testLarge() throws ClassNotFoundException, IOException {
		ZFastTrie<Long> zft = new ZFastTrie<>(TransformationStrategies.fixedLong());
		final RandomGenerator random = new XoRoShiRo128PlusRandomGenerator(0);
		for (int i = 0; i < 1000000; i++) assertTrue(zft.add(random.nextLong() >>> 1));
		zft = testSerialization(zft);
		random.setSeed(0);
		for (int i = 0; i < 1000000; i++) assertTrue(zft.remove(random.nextLong() >>> 1));
	}
	
	@Test
	public void testNonemptyRange() throws ClassNotFoundException, IOException {
		final ZFastTrie<LongArrayBitVector> zft = new ZFastTrie<>(TransformationStrategies.identity());
		zft.add(LongArrayBitVector.of(0, 1, 0, 0, 0, 0, 0, 0, 0));
		zft.add(LongArrayBitVector.of(0, 0, 0, 0, 1, 1, 0, 0));
		zft.add(LongArrayBitVector.of(0, 0, 0, 0, 1, 1, 0, 1));
		zft.add(LongArrayBitVector.of(0, 1, 0, 1, 1, 1, 0, 0, 0));
		zft.add(LongArrayBitVector.of(0, 1, 0, 1, 1, 1, 0, 1, 1));
		zft.add(LongArrayBitVector.of(1, 1, 1, 1, 1, 1, 1, 1, 1));
		final LongArrayBitVector x = LongArrayBitVector.of(0, 1, 0, 0, 0, 1, 0, 0, 0);
		final LongArrayBitVector y = LongArrayBitVector.of(0, 1, 0, 1, 0);		
	}

}
