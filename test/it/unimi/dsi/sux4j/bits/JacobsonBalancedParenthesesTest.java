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

package it.unimi.dsi.sux4j.bits;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;

public class JacobsonBalancedParenthesesTest extends BalancedParenthesesTestCase {

	@Test
	public void testCountFarOpen() {

		assertEquals(0, JacobsonBalancedParentheses.countFarOpen(0x5555555555555555L, Long.SIZE));
		assertEquals(1, JacobsonBalancedParentheses.countFarOpen(0xAAAAAAAAAAAAAAAAL, Long.SIZE));
		assertEquals(4, JacobsonBalancedParentheses.countFarOpen(Long.parseLong("110111", 2), 6));
		assertEquals(6, JacobsonBalancedParentheses.countFarOpen(Long.parseLong("111111", 2), 6));
		assertEquals(4, JacobsonBalancedParentheses.countFarOpen(Long.parseLong("101111", 2), 6));
		assertEquals(2, JacobsonBalancedParentheses.countFarOpen(Long.parseLong("001111", 2), 6));

		assertEquals(0, JacobsonBalancedParentheses.countFarClose(0x5555555555555555L, Long.SIZE));
		assertEquals(1, JacobsonBalancedParentheses.countFarClose(0xAAAAAAAAAAAAAAAAL, Long.SIZE));
		assertEquals(4, JacobsonBalancedParentheses.countFarClose(~Long.parseLong("110111", 2), 6));
		assertEquals(6, JacobsonBalancedParentheses.countFarClose(~Long.parseLong("111111", 2), 6));
		assertEquals(4, JacobsonBalancedParentheses.countFarClose(~Long.parseLong("101111", 2), 6));
		assertEquals(4, JacobsonBalancedParentheses.countFarClose(~Long.parseLong("001111", 2), 6));
	}


	private void recFindNearCloseTest(final LongArrayBitVector bv, final int e) {
		if (e == 0) {
			assertEquals(bv.toString(), bv.length() - 1, JacobsonBalancedParentheses.findNearClose(bv.getLong(0, bv.length())));
			return;
		}
		if (bv.length() + e == Long.SIZE) {
			assertEquals(63, JacobsonBalancedParentheses.findNearClose(bv.getLong(0, bv.length())));
			return;
		}

		if (e > 0) {
			bv.add(0);
			bv.add(0);
			recFindNearCloseTest(bv, e - 2);
			bv.set(bv.length() - 1);
			bv.set(bv.length() - 2);
			recFindNearCloseTest(bv, e + 2);
			bv.length(bv.length() - 2);
		}
	}

	@Test
	public void testFindNearCloseRec() {
		final LongArrayBitVector bv = LongArrayBitVector.getInstance();
		bv.add(1);
		bv.add(1);
		recFindNearCloseTest(bv, 2);
	}

	@Test
	public void testFindNearClose() {
		assertEquals(1, JacobsonBalancedParentheses.findNearClose(parseSmall("()")));

		assertEquals(3, JacobsonBalancedParentheses.findNearClose(parseSmall("(())")));

		assertEquals(5, JacobsonBalancedParentheses.findNearClose(parseSmall("(()())")));

		assertEquals(7, JacobsonBalancedParentheses.findNearClose(parseSmall("(((())))")));
		assertEquals(7, JacobsonBalancedParentheses.findNearClose(parseSmall("(()()())")));
		assertEquals(7, JacobsonBalancedParentheses.findNearClose(parseSmall("(()(()))")));

		assertEquals(9, JacobsonBalancedParentheses.findNearClose(parseSmall("((((()))))")));
		assertEquals(11, JacobsonBalancedParentheses.findNearClose(parseSmall("(((((())))))")));
		assertEquals(13, JacobsonBalancedParentheses.findNearClose(parseSmall("((((((()))))))")));
		assertEquals(15, JacobsonBalancedParentheses.findNearClose(parseSmall("(((((((())))))))")));

		assertEquals(9, JacobsonBalancedParentheses.findNearClose(parseSmall("(((()())))")));
		assertEquals(11, JacobsonBalancedParentheses.findNearClose(parseSmall("((((()()))))")));
		assertEquals(13, JacobsonBalancedParentheses.findNearClose(parseSmall("(((((()())))))")));
		assertEquals(15, JacobsonBalancedParentheses.findNearClose(parseSmall("((((((()()))))))")));

		assertEquals(9, JacobsonBalancedParentheses.findNearClose(parseSmall("(()()()())")));
		assertEquals(11, JacobsonBalancedParentheses.findNearClose(parseSmall("(()()()()())")));
		assertEquals(13, JacobsonBalancedParentheses.findNearClose(parseSmall("(()()()()()())")));
		assertEquals(15, JacobsonBalancedParentheses.findNearClose(parseSmall("(()()()()()()())")));

		assertEquals(17, JacobsonBalancedParentheses.findNearClose(parseSmall("((((((((()))))))))")));
		assertEquals(19, JacobsonBalancedParentheses.findNearClose(parseSmall("(((((((((())))))))))")));
		assertEquals(21, JacobsonBalancedParentheses.findNearClose(parseSmall("((((((((((()))))))))))")));
		assertEquals(23, JacobsonBalancedParentheses.findNearClose(parseSmall("(((((((((((())))))))))))")));
		assertEquals(25, JacobsonBalancedParentheses.findNearClose(parseSmall("((((((((((((()))))))))))))")));
		assertEquals(27, JacobsonBalancedParentheses.findNearClose(parseSmall("(((((((((((((())))))))))))))")));
		assertEquals(29, JacobsonBalancedParentheses.findNearClose(parseSmall("((((((((((((((()))))))))))))))")));
		assertEquals(31, JacobsonBalancedParentheses.findNearClose(parseSmall("((((((((((((((()()))))))))))))))")));

		assertEquals(63, JacobsonBalancedParentheses.findNearClose(parseSmall("(((((((((((((((((((((((((((((((())))))))))))))))))))))))))))))))")));
	}

	private void recFindFarCloseTest(final LongArrayBitVector bv) {
		if (bv.length() == Long.SIZE) {
			final long word = bv.getLong(0, Long.SIZE);
			for (int i = 0;; i++) {
				final int result = JacobsonBalancedParentheses.findFarClose2(word, i);
				if (result != -1) assertEquals(result, JacobsonBalancedParentheses.findFarClose(word, i));
				else {
					assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(word, i));
					break;
				}
			}
			return;
		}

		bv.add(0);
		bv.add(0);
		bv.add(0);
		bv.add(0);
		recFindFarCloseTest(bv);
		bv.set(bv.length() - 1);
		bv.set(bv.length() - 2);
		bv.set(bv.length() - 3);
		bv.set(bv.length() - 4);
		recFindFarCloseTest(bv);
		bv.length(bv.length() - 4);
	}

	@Test
	public void testFindFarCloseRec() {
		final LongArrayBitVector bv = LongArrayBitVector.getInstance();
		recFindFarCloseTest(bv);
	}


	@Test
	public void testFindFarClose() {
		assertEquals(2, JacobsonBalancedParentheses.findFarClose(parseSmall("()))(()(", false), 0));
		assertEquals(31, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false), 31));
		assertEquals(0, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false), 0));
		assertEquals(15, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false), 15));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false), 32));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false), 33));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false), 40));
		assertEquals(0, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false), 0));
		assertEquals(63, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false), 31));
		assertEquals(15, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false), 15));
		assertEquals(48, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false), 16));

		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("()((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 0));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("()((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 1));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("()((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 2));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 0));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 1));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 2));
		assertEquals(0, JacobsonBalancedParentheses.findFarClose(parseSmall(")(((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 0));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall(")(((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 1));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall(")(((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 2));
		assertEquals(0, JacobsonBalancedParentheses.findFarClose(parseSmall("))((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 0));
		assertEquals(1, JacobsonBalancedParentheses.findFarClose(parseSmall("))((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 1));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false), 2));

		assertEquals(62, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))", false), 62));
		assertEquals(63, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))", false), 63));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))", false), 64));

		assertEquals(61, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false), 61));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false), 62));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false), 63));

		assertEquals(61, JacobsonBalancedParentheses.findFarClose(parseSmall(")))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))(", false), 61));
		assertEquals(62, JacobsonBalancedParentheses.findFarClose(parseSmall(")))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))(", false), 62));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall(")))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))(", false), 63));

		assertEquals(61, JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((", false), 61));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((", false), 62));
		assertTrue(Long.SIZE <= JacobsonBalancedParentheses.findFarClose(parseSmall("))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))((", false), 63));
	}

	@Test
	public void testLong() {
		JacobsonBalancedParentheses bp = new JacobsonBalancedParentheses(new long[] { -1, -1, 0, 0 }, Long.SIZE * 4);
		assertBalancedParentheses(bp);

		bp = new JacobsonBalancedParentheses(new long[] { -1, 0xFFFFFFFFL, 0xFFFFFFFFL, 0 }, Long.SIZE * 4);
		assertBalancedParentheses(bp);

		bp = new JacobsonBalancedParentheses(new long[] { -1, 0xFFFFFFL, 0xFFFFFFFFFFL, 0 }, Long.SIZE * 4);
		assertBalancedParentheses(bp);

		bp = new JacobsonBalancedParentheses(new long[] { 0xFFFFFFFFFFFFFFL, 0xFFFFFFL, 0xFFFFFFFFFFL, 0xFF00L }, Long.SIZE * 4);
		assertBalancedParentheses(bp);

		bp = new JacobsonBalancedParentheses(new long[] { 0xFFFFFFFFFFFFFFFFL, 0x7FFFFFFFFFFFFFFFL, 0, 0 }, 256 - 2);
		assertBalancedParentheses(bp);
	}

	public void testSimple() {
		LongArrayBitVector bv = LongArrayBitVector.of(1, 0);
		JacobsonBalancedParentheses bp = new JacobsonBalancedParentheses(bv);
		assertEquals(1, bp.findClose(0));
		assertEquals(0, bp.findOpen(1));
		// assertEquals(0, bp.enclose(1));

		bv = LongArrayBitVector.of(1, 1, 0, 0);
		bp = new JacobsonBalancedParentheses(bv);
		assertEquals(3, bp.findClose(0));
		// assertEquals(0, bp.enclose(1));
		assertEquals(2, bp.findClose(1));
		assertEquals(1, bp.findOpen(2));
		// assertEquals(1, bp.enclose(2));
		assertEquals(0, bp.findOpen(3));
		// assertEquals(1, bp.enclose(3));

		bv = LongArrayBitVector.of(1, 1, 0, 1, 0, 0);
		bp = new JacobsonBalancedParentheses(bv);
		assertEquals(5, bp.findClose(0));
		assertEquals(2, bp.findClose(1));
		// assertEquals(0, bp.enclose(1));
		assertEquals(1, bp.findOpen(2));
		// assertEquals(1, bp.enclose(2));
		assertEquals(4, bp.findClose(3));
		// assertEquals(1, bp.enclose(3));
		assertEquals(3, bp.findOpen(4));
		// assertEquals(3, bp.enclose(4));
		assertEquals(0, bp.findOpen(5));
		// assertEquals(3, bp.enclose(5));

	}
}
