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

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;

public class TrivialBalancedParenthesesTest extends BalancedParenthesesTestCase {

	@Test
	public void testSimple() {
		LongArrayBitVector bv = LongArrayBitVector.of(1, 0);
		TrivialBalancedParentheses bp = new TrivialBalancedParentheses(bv);
		assertBalancedParentheses(bp);
		assertEquals(1, bp.findClose(0));
		assertEquals(0, bp.findOpen(1));
		// assertEquals(0, bp.enclose(1));

		bv = LongArrayBitVector.of(1, 1, 0, 0);
		bp = new TrivialBalancedParentheses(bv);
		assertBalancedParentheses(bp);
		assertEquals(3, bp.findClose(0));
		// assertEquals(0, bp.enclose(1));
		assertEquals(2, bp.findClose(1));
		assertEquals(1, bp.findOpen(2));
		// assertEquals(1, bp.enclose(2));
		assertEquals(0, bp.findOpen(3));
		// assertEquals(1, bp.enclose(3));

		bv = LongArrayBitVector.of(1, 1, 0, 1, 0, 0);
		bp = new TrivialBalancedParentheses(bv);
		assertBalancedParentheses(bp);
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
