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

package it.unimi.dsi.sux4j.bits;

import static org.junit.Assert.assertEquals;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.lang.MutableString;

public abstract class BalancedParenthesesTestCase {

	public static String binary(long l, final boolean reverse) {
		if (reverse) l = Long.reverse(l);
		final MutableString s = new MutableString().append("0000000000000000000000000000000000000000000000000000000000000000000000000").append(Long.toBinaryString(l));
		s.delete(0, s.length() - 64);
		s.insert(0, '\n');
		s.append('\n');
		for(int i = 0; i < 32; i++) s.append(" ").append(Long.toHexString((l >>> (31 - i) * 2) & 0x3));
		s.append('\n');
		for(int i = 0; i < 16; i++) s.append("   ").append(Long.toHexString((l >>> (15 - i) * 4) & 0xF));
		s.append('\n');
		return s.toString();
	}


	public static LongArrayBitVector parse(final String s, final boolean check) {
		int e = 0;
		final LongArrayBitVector bv = LongArrayBitVector.getInstance();
		for(int i = 0; i < s.length(); i++) {
			if (s.charAt(i) == '(') {
				bv.add(1);
				e++;
			}
			else {
				if (check && e == 0) throw new IllegalArgumentException();
				bv.add(0);
				e--;
			}
		}

		if (check && e != 0) throw new IllegalArgumentException();

		return bv;
	}


	public static long parseSmall(final String s, final boolean check) {
		if (s.length() > Long.SIZE) throw new IllegalArgumentException();
		final LongArrayBitVector bv = parse(s, check);
		return bv.getLong(0, s.length());
	}

	public static long parseSmall(final String s) {
		return parseSmall(s, true);
	}

	public void assertBalancedParentheses(final BalancedParentheses balancedParentheses) {
		final long length = balancedParentheses.bitVector().length();
		final BitVector bits = balancedParentheses.bitVector();

		// Build matching

		final IntArrayList stack = new IntArrayList();
		final IntArrayList matches  = new IntArrayList();
		matches.size((int)length);

		for(int i = 0; i < length; i++) {
			if (bits.getBoolean(i)) stack.push(i);
			else {
				if (stack.isEmpty()) throw new AssertionError("The bit vector does not represent a correctly parenthesised string");
				final int pos = stack.popInt();
				matches.set(pos, i);
				matches.set(i, pos);
			}
		}

		if (! stack.isEmpty()) throw new AssertionError("The bit vector does not represent a correctly parenthesised string");

		for(int i = 0; i < length; i++) {
			if (bits.getBoolean(i)) assertEquals("Finding closing for position " + i, matches.getInt(i), balancedParentheses.findClose(i));
			// else assertEquals("Finding opening for position " + i, matches.getInt(i),
			// balancedParentheses.findOpen(i));
		}
	}

}
