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
