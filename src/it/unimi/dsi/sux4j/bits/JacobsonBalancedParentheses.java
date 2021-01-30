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

import static it.unimi.dsi.bits.LongArrayBitVector.bit;
import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;
import static it.unimi.dsi.bits.LongArrayBitVector.words;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Collections;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.mph.HollowTrieMonotoneMinimalPerfectHashFunction;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;


/** An implementation of Jacobson's balanced parentheses data structure.
 *
 * <strong>Warning</strong>: this class is a stub implementing just those method needed by a {@link HollowTrieMonotoneMinimalPerfectHashFunction}.
 *
 * @author Sebastiano Vigna
 */

public class JacobsonBalancedParentheses implements BalancedParentheses {
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

	private static final long serialVersionUID = 1L;
	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;
	private static final boolean DDEBUG = false;
	private transient long[] bits;
	protected final BitVector bitVector;
	private final SparseSelect openingPioneers;
	private final SparseRank openingPioneersRank;
	private final EliasFanoLongBigList openingPioneerMatches;
	private final SparseSelect closingPioneers;
	private final SparseRank closingPioneersRank;
	private final EliasFanoLongBigList closingPioneerMatches;

	public final static int countFarOpen(final long word, int l) {
		int c = 0, e = 0;
		while(l-- != 0) {
			if ((word & 1L << l) != 0) {
				if (++e > 0) c++;
			}
			else {
				if (e > 0) e = -1;
				else --e;
			}
		}

		return c;
	}

	public final static int findFarOpen(final long word, int l, int k) {
		int e = 0;
		while(l-- != 0) {
			if ((word & 1L << l) != 0) {
				if (++e > 0 && k-- == 0) return l;
			}
			else {
				if (e > 0) e = -1;
				else --e;
			}
		}

		return -1;
	}

	public final static int countFarClose(final long word, final int l) {
		int c = 0, e = 0;
		for(int i = 0; i < l; i++) {
			if ((word & 1L << i) != 0) {
				if (e > 0) e = -1;
				else --e;
			}
			else {
				if (++e > 0) c++;
			}
		}

		return c;
	}

	public final static int findFarClose2(final long word, int k) {
		int e = 0;
		for(int i = 0; i < Long.SIZE; i++) {
			if ((word & 1L << i) != 0) {
				if (e > 0) e = -1;
				else --e;
			}
			else {
				if (++e > 0 && k-- == 0) return i;
			}
		}

		return -1;
	}


	public static final long ONES_STEP_4 = 0x1111111111111111L;
	public static final long MSBS_STEP_4 = 0x8L * ONES_STEP_4;
	public static final long ONES_STEP_8 = 0x0101010101010101L;
	public static final long MSBS_STEP_8 = 0x80L * ONES_STEP_8;
	private static final long ONES_STEP_16 = 0x0001000100010001L;
	private static final long MSBS_STEP_16 = 0x8000800080008000L;
	private static final long ONES_STEP_32 = 0x0000000100000001L;
	private static final long MSBS_STEP_32 = 0x8000000080000000L;

	public final static int findFarClose(final long word, int k) {
		// 00 -> 00 01 -> 01 11 -> 10 10 -> 00
		if (DDEBUG) System.err.println("Before: " + binary(word, true));
		final long b1 = (word & (0xA * ONES_STEP_4)) >>> 1;
		final long b0 = word & (0x5 * ONES_STEP_4);
		final long lsb = (b1 ^ b0) & b1;
		//System.err.println("b0:" + binary(b0, true));
		//System.err.println("b1:" + binary(b1, true));
		//System.err.println("lsb:" + binary(lsb, true));

		final long open2 = (b1 & b0) << 1 | lsb;
		if (DDEBUG) System.err.println("Open:" + binary(open2, false));
		// 00 -> 10 01 -> 01 11 -> 00 10 -> 00
		final long closed2 = ((b1 | b0) ^ (0x5 * ONES_STEP_4)) << 1 | lsb;
		if (DDEBUG) System.err.println("Closed:" + binary(closed2, false));

		final long open4eccess = (open2 & (0x3 * ONES_STEP_4));
		final long closed4eccess = (closed2 & (0xC * ONES_STEP_4)) >>> 2;

		//if (DDEBUG) System.err.println("Open e:  " + binary(open4eccess, false));
		//if (DDEBUG) System.err.println("Closed e:" + binary(closed4eccess, false));

		long open4 = ((open4eccess | MSBS_STEP_4) - closed4eccess) ^ MSBS_STEP_4;
		if (DDEBUG) System.err.println("Diff (open4):" + binary(open4, false));

		final long open4mask = ((((open4 & MSBS_STEP_4) >>> 3) | MSBS_STEP_4) - ONES_STEP_4) ^ MSBS_STEP_4;

		if (DDEBUG) System.err.println("Mask (open4)  : " + binary(open4mask, false));

		//open4eccess = (open & (0xC * ONES_STEP_4)) >>> 2;
		//closed4eccess = closed & (0x3 * ONES_STEP_4);

		long closed4 = ((closed4eccess | MSBS_STEP_4) - open4eccess) ^ MSBS_STEP_4;
		if (DDEBUG) System.err.println("Diff (closed4):" + binary(closed4, false));
		final long closed4mask = ((((closed4 & MSBS_STEP_4) >>> 3) | MSBS_STEP_4) - ONES_STEP_4) ^ MSBS_STEP_4;
		if (DDEBUG) System.err.println("Mask (closed4): " + binary(closed4mask, false));

		open4 = ((open2 & (0xC * ONES_STEP_4)) >>> 2) + (open4mask & open4);
		closed4 = (closed2 & (0x3 * ONES_STEP_4)) + (closed4mask & closed4);

		if (DDEBUG) System.err.println("Open 4:  " + binary(open4, false));
		if (DDEBUG) System.err.println("Closed 4:" + binary(closed4, false));

		final long open8eccess = (open4 & (0xF * ONES_STEP_8));
		final long closed8eccess = (closed4 & (0xF0 * ONES_STEP_8)) >>> 4;

		long open8  = ((open8eccess | MSBS_STEP_8) - closed8eccess) ^ MSBS_STEP_8;
		final long open8mask =  ((((open8 & MSBS_STEP_8) >>> 7) | MSBS_STEP_8) - ONES_STEP_8) ^ MSBS_STEP_8;

		long closed8 = ((closed8eccess | MSBS_STEP_8) - open8eccess) ^ MSBS_STEP_8;
		final long closed8mask = ((((closed8 & MSBS_STEP_8) >>> 7) | MSBS_STEP_8) - ONES_STEP_8) ^ MSBS_STEP_8;

		open8 = ((open4 & (0xF0 * ONES_STEP_8)) >>> 4) + (open8mask & open8);
		closed8 = (closed4 & (0xF * ONES_STEP_8)) + (closed8mask & closed8);

		if (DDEBUG) System.err.println("Open 8:  " + binary(open8, false));
		if (DDEBUG) System.err.println("Closed 8:" + binary(closed8, false));

		final long open16eccess = (open8 & (0xFF * ONES_STEP_16));
		final long closed16eccess = (closed8 & (0xFF00 * ONES_STEP_16)) >>> 8;

		long open16  = ((open16eccess | MSBS_STEP_16) - closed16eccess) ^ MSBS_STEP_16;
		final long open16mask =  ((((open16 & MSBS_STEP_16) >>> 15) | MSBS_STEP_16) - ONES_STEP_16) ^ MSBS_STEP_16;

		long closed16 = ((closed16eccess | MSBS_STEP_16) - open16eccess) ^ MSBS_STEP_16;
		final long closed16mask = ((((closed16 & MSBS_STEP_16) >>> 15) | MSBS_STEP_16) - ONES_STEP_16) ^ MSBS_STEP_16;

		open16 = ((open8 & (0xFF00 * ONES_STEP_16)) >>> 8) + (open16mask & open16);
		closed16 = (closed8 & (0xFF * ONES_STEP_16)) + (closed16mask & closed16);

		if (DDEBUG) System.err.println("Open 16:  " + binary(open16, false));
		if (DDEBUG) System.err.println("Closed 16:" + binary(closed16, false));

		final long open32eccess = (open16 & 0xFFFF * ONES_STEP_32);
		final long closed32eccess = (closed16 & (0xFFFF0000L * ONES_STEP_32)) >>> 16;

		long open32  = ((open32eccess | MSBS_STEP_32) - closed32eccess) ^ MSBS_STEP_32;
		final long open32mask =  ((((open32 & MSBS_STEP_32) >>> 31) | MSBS_STEP_32) - ONES_STEP_32) ^ MSBS_STEP_32;

		long closed32 = ((closed32eccess | MSBS_STEP_32) - open32eccess) ^ MSBS_STEP_32;
		final long closed32mask = ((((closed32 & MSBS_STEP_32) >>> 31) | MSBS_STEP_32) - ONES_STEP_32) ^ MSBS_STEP_32;

		open32 = ((open16 & (0xFFFF0000L * ONES_STEP_32)) >>> 16)+ (open32mask & open32);
		closed32 = (closed16 & (0xFFFF * ONES_STEP_32)) + (closed32mask & closed32);

		if (DDEBUG) System.err.println("Open 32:  " + binary(open32, false));
		if (DDEBUG) System.err.println("Closed 32:" + binary(closed32, false));

		final long check32 = ((k - (closed32 & 0xFFFFFFFFL)) >>> Long.SIZE - 1) - 1;
		long mask = check32 & 0xFFFFFFFFL;
		k -= closed32 & mask;
		k += open32 & mask;
		int shift = (int)(32 & check32);

		final long check16 = ((k - (closed16 >>> shift & 0xFFFF)) >>> Long.SIZE - 1) - 1;
		mask = check16 & 0xFFFF;
		k -= closed16 >>> shift & mask;
		k += open16 >>> shift & mask;
		shift += 16 & check16;

		final long check8 = ((k - (closed8 >>> shift & 0xFF)) >>> Long.SIZE - 1) - 1;
		mask = check8 & 0xFF;
		k -= closed8 >>> shift & mask;
		k += open8 >>> shift & mask;
		shift += 8 & check8;

		final long check4 = ((k - (closed4 >>> shift & 0xF)) >>> Long.SIZE - 1) - 1;
		mask = check4 & 0xF;
		k -= closed4 >>> shift & mask;
		k += open4 >>> shift & mask;
		shift += 4 & check4;

		final long check2 = ((k - (closed2 >>> shift & 0x3)) >>> Long.SIZE - 1) - 1;
		mask = check2 & 0x3;
		k -= closed2 >>> shift & mask;
		k += open2 >>> shift & mask;
		shift += 2 & check2;

		return (int)(shift + k + ((word >>> shift & ((k << 1) | 1)) << 1));

	}

	public final static int findNearClose2(final long word) {
		int c = 1;
		for(int i = 1; i < 64; i++) {
			if ((word & 1L << i) != 0) c++;
			else c--;
			if (c == 0) return i;
		}
		return Long.SIZE;
	}


	private final static long L = 0x4038302820181008L;

	public final static int findNearClose(final long word) {
		long byteSums = word - ((word & 0xa * ONES_STEP_4) >>> 1);
		long zeroes, update;
		byteSums = (byteSums & 3 * ONES_STEP_4) + ((byteSums >>> 2) & 3 * ONES_STEP_4);
		//System.err.print("**** "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		byteSums = ((byteSums + (byteSums >>> 4)) & 0x0f * ONES_STEP_8) * (ONES_STEP_8 << 1); // Twice the number of open parentheses (cumulative by byte)

		//System.err.print("**** "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
        // TODO: this can be simplified
		byteSums = ((L | MSBS_STEP_8) - byteSums) ^ ((L ^ ~byteSums) & MSBS_STEP_8); // Closed excess per byte
		//System.err.print("Closed excess: "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();

		// Set up flags for excess values that are already zero
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		//System.err.print("Updates: "); for(int i = 0; i < 8; i++) System.err.print((byte)(update >>> i * 8 & 0xFF) + " "); System.err.println();
		zeroes = (MSBS_STEP_8 | ONES_STEP_8 * 7) & update;
		//System.err.print("Zeroes: "); for(int i = 0; i < 8; i++) System.err.print((byte)(zeroes >>> i * 8 & 0xFF) + " "); System.err.println();

		byteSums += (word >>> 7 & ONES_STEP_8);
		byteSums = ((byteSums | MSBS_STEP_8) - (~(word >>> 7) & ONES_STEP_8)) ^ ((byteSums ^ MSBS_STEP_8) & MSBS_STEP_8);
		//System.err.print("Sums: "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		//System.err.print("Updates: "); for(int i = 0; i < 8; i++) System.err.print((byte)(update >>> i * 8 & 0xFF) + " "); System.err.println();
		zeroes = zeroes & ~update | (MSBS_STEP_8 | ONES_STEP_8 * 6) & update;
		//System.err.print("Zeroes: "); for(int i = 0; i < 8; i++) System.err.print((byte)(zeroes >>> i * 8 & 0xFF) + " "); System.err.println();

		byteSums += (word >>> 6 & ONES_STEP_8);
		byteSums = ((byteSums | MSBS_STEP_8) - (~(word >>> 6) & ONES_STEP_8)) ^ ((byteSums ^ MSBS_STEP_8) & MSBS_STEP_8);
		//System.err.print("Sums: "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		//System.err.print("Updates: "); for(int i = 0; i < 8; i++) System.err.print((byte)(update >>> i * 8 & 0xFF) + " "); System.err.println();
		zeroes = zeroes & ~update | (MSBS_STEP_8 | ONES_STEP_8 * 5) & update;
		//System.err.print("Zeroes: "); for(int i = 0; i < 8; i++) System.err.print((byte)(zeroes >>> i * 8 & 0xFF) + " "); System.err.println();

		byteSums += (word >>> 5 & ONES_STEP_8);
		byteSums = ((byteSums | MSBS_STEP_8) - (~(word >>> 5) & ONES_STEP_8)) ^ ((byteSums ^ MSBS_STEP_8) & MSBS_STEP_8);
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		zeroes = zeroes & ~update | (MSBS_STEP_8 | ONES_STEP_8 * 4) & update;

		byteSums += (word >>> 4 & ONES_STEP_8);
		byteSums = ((byteSums | MSBS_STEP_8) - (~(word >>> 4) & ONES_STEP_8)) ^ ((byteSums ^ MSBS_STEP_8) & MSBS_STEP_8);
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		zeroes = zeroes & ~update | (MSBS_STEP_8 | ONES_STEP_8 * 3) & update;

		byteSums += (word >>> 3 & ONES_STEP_8);
		byteSums = ((byteSums | MSBS_STEP_8) - (~(word >>> 3) & ONES_STEP_8)) ^ ((byteSums ^ MSBS_STEP_8) & MSBS_STEP_8);
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		zeroes = zeroes & ~update | (MSBS_STEP_8 | ONES_STEP_8 * 2) & update;

		byteSums += (word >>> 2 & ONES_STEP_8);
		byteSums = ((byteSums | MSBS_STEP_8) - (~(word >>> 2) & ONES_STEP_8)) ^ ((byteSums ^ MSBS_STEP_8) & MSBS_STEP_8);
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		zeroes = zeroes & ~update | (MSBS_STEP_8 | ONES_STEP_8 * 1) & update;

		byteSums += (word >>> 1 & ONES_STEP_8);
		byteSums = ((byteSums | MSBS_STEP_8) - (~(word >>> 1) & ONES_STEP_8)) ^ ((byteSums ^ MSBS_STEP_8) & MSBS_STEP_8);
		update = (~(byteSums | ((byteSums | MSBS_STEP_8) - ONES_STEP_8)) & MSBS_STEP_8) >>> 7;
		update = ((update | MSBS_STEP_8) - ONES_STEP_8) ^ ((update ^ ONES_STEP_8) | ~MSBS_STEP_8);
		zeroes = zeroes & ~update | MSBS_STEP_8 & update;


		//for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		//for(int i = 0; i < 8; i++) System.err.print((byte)(zeroes >>> i * 8 & 0xFF) + " "); System.err.println();

		// TODO: check that in this case MSB(x&-x) isn't better.
		final int block = Long.numberOfTrailingZeros(zeroes >>> 7 & ONES_STEP_8);
		// A simple trick to return 127 if block < 0 (i.e., no match)
		return ((int)(block + (zeroes >>> block & 0x7F)) | (block >> 8)) & 0x7F;

		/*		//assert block != -1;
		//block = block == -1 ? 0 : block / 8;
		assert block >= 7;
		excess = excess >>> block - 7 & 0xFF;
		System.out.println("LSB: " + Fast.leastSignificantBit(zeroes & MSBS_STEP_8) + " Block: " + block);
		System.out.println("Excess: " + excess);

		if (excess != 0) {
			for(int i = 0; i < 8; i++) {
				if ((origWord & 1L << block - 7 + i) != 0) excess++;
				else excess--;
				if (excess == 0) {
					assert (zeroes >>> block -7 & 0x7F) - 1 == i : ((zeroes >>> block -7 & 0x7F) - 1)+ " != " + i;

					return block -7 + i;
				}
			}
		}
		else {
			//assert (zeroes >>> block -7 & 0x7F) - 1 == 1 : ((zeroes >>> block -7 & 0x7F) - 1) + " != " + 1;
			return (int)(block - 7 + (zeroes >>> block -7 & 0x7F) - 1);
		}

        return -1;*/

	}

	private final static long L_ALT = 0x3830282018100800L + 0x0202020202020202L;

	public final static int findNearCloseAlt(long word) {
		long byteSums = (word << 6) - (((word << 6) & 0xa * ONES_STEP_4) >>> 1);
		long zeroes, update;
		byteSums = (byteSums & 3 * ONES_STEP_4) + ((byteSums >>> 2) & 3 * ONES_STEP_4);
		//System.err.print("**** "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		byteSums = ((byteSums + (byteSums >>> 4)) & 0x0f * ONES_STEP_8) * (ONES_STEP_8 << 1); // Twice the number of open parentheses (cumulative by byte)

		//System.err.print("**** "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		byteSums = ((MSBS_STEP_8 | byteSums) - L_ALT) ^ MSBS_STEP_8; // Closed excess per byte
		//System.err.print("Closed excess: "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();

		// Set up flags for excess values that are already zero
		update = (~(((byteSums - ONES_STEP_8) & MSBS_STEP_8) >>> 7) & ONES_STEP_8) - ONES_STEP_8;
		//System.err.print("Updates: "); for(int i = 0; i < 8; i++) System.err.print((byte)(update >>> i * 8 & 0xFF) + " "); System.err.println();
		zeroes = (MSBS_STEP_8 | ONES_STEP_8 * 1) & update;
		//System.err.print("Zeroes: "); for(int i = 0; i < 8; i++) System.err.print((byte)(zeroes >>> i * 8 & 0xFF) + " "); System.err.println();

		word >>= 2;
		byteSums -= ONES_STEP_8 * 2 - ((word & (ONES_STEP_8 << 1)) + (word << 1 & (ONES_STEP_8 << 1)));
		//System.err.print("Sums: "); for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		update = (~((((byteSums | zeroes) - ONES_STEP_8) ^ byteSums) >>> 7) & ONES_STEP_8) - ONES_STEP_8;
		//System.err.print("Updates: "); for(int i = 0; i < 8; i++) System.err.print((byte)(update >>> i * 8 & 0xFF) + " "); System.err.println();
		zeroes |= (MSBS_STEP_8 | ONES_STEP_8 * 3) & update;
		//System.err.print("Zeroes: "); for(int i = 0; i < 8; i++) System.err.print((byte)(zeroes >>> i * 8 & 0xFF) + " "); System.err.println();

		word >>= 2;
		byteSums -= ONES_STEP_8 * 2 - ((word & (ONES_STEP_8 << 1)) + (word << 1 & (ONES_STEP_8 << 1)));
		update = (~((((byteSums | zeroes)- ONES_STEP_8)  ^ byteSums) >>> 7) & ONES_STEP_8) - ONES_STEP_8;
		zeroes |= (MSBS_STEP_8 | ONES_STEP_8 * 5) & update;

		word >>= 2;
		byteSums -= ONES_STEP_8 * 2 - ((word & (ONES_STEP_8 << 1)) + (word << 1 & (ONES_STEP_8 << 1)));
		update = (~((((byteSums | zeroes)- ONES_STEP_8)  ^ byteSums) >>> 7) & ONES_STEP_8) - ONES_STEP_8;
		zeroes |= (MSBS_STEP_8 | ONES_STEP_8 * 7) & update;

		//for(int i = 0; i < 8; i++) System.err.print((byte)(byteSums >>> i * 8 & 0xFF) + " "); System.err.println();
		//for(int i = 0; i < 8; i++) System.err.print((byte)(zeroes >>> i * 8 & 0xFF) + " "); System.err.println();

		// TODO: check that in this case MSB(x&-x) isn't better.
		final int block = Long.numberOfTrailingZeros(zeroes >>> 7 & ONES_STEP_8);
		// A simple trick to return 127 if block < 0 (i.e., no match)
		return ((int)(block + (zeroes >>> block & 0x3F)) | (block >> 8)) & 0x7F;

		/*		//assert block != -1;
		//block = block == -1 ? 0 : block / 8;
		assert block >= 7;
		excess = excess >>> block - 7 & 0xFF;
		System.out.println("LSB: " + Fast.leastSignificantBit(zeroes & MSBS_STEP_8) + " Block: " + block);
		System.out.println("Excess: " + excess);

		if (excess != 0) {
			for(int i = 0; i < 8; i++) {
				if ((origWord & 1L << block - 7 + i) != 0) excess++;
				else excess--;
				if (excess == 0) {
					assert (zeroes >>> block -7 & 0x7F) - 1 == i : ((zeroes >>> block -7 & 0x7F) - 1)+ " != " + i;

					return block -7 + i;
				}
			}
		}
		else {
			//assert (zeroes >>> block -7 & 0x7F) - 1 == 1 : ((zeroes >>> block -7 & 0x7F) - 1) + " != " + 1;
			return (int)(block - 7 + (zeroes >>> block -7 & 0x7F) - 1);
		}

        return -1;*/

	}

	public JacobsonBalancedParentheses(final BitVector bv) {
		this(bv, true, true, true);
	}

	public JacobsonBalancedParentheses(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	public JacobsonBalancedParentheses(final BitVector bitVector, final boolean findOpen, final boolean findClose, final boolean enclose) {
		if (! findOpen && ! findClose && ! enclose) throw new IllegalArgumentException("You must specify at least one implemented method");
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();
		final int numWords = words(length);

		final byte count[] = new byte[numWords];
		final byte residual[] = new byte[numWords];

		if (DEBUG) System.err.println("Expression: " + bitVector);

		LongArrayList closingPioneers = null, closingPioneerMatches = null, openingPioneers = null, openingPioneerMatches = null;

		if (findOpen) {
			closingPioneers = new LongArrayList();
			closingPioneerMatches = new LongArrayList();
			for(int block = 0; block < numWords; block++) {
				if (DEBUG) System.err.println("Scanning word " + block + " (" + LongArrayBitVector.wrap(new long[] { bits[block] }) + ")");
				final int l = (int)Math.min(Long.SIZE, length - bits(block));

				if (block > 0) {
					int excess = 0;
					int countFarClosing = countFarClose(bits[block], l);

					for(int j = 0; j < l; j++) {
						if ((bits[block] & 1L << j) != 0) {
							if (excess > 0) excess = -1;
							else --excess;
						}
						else {
							if (++excess > 0) {
								// Find block containing matching far open parenthesis
								int matchingBlock = block;
								while(count[--matchingBlock] == 0);
								countFarClosing--;
								/* Note that we always consider the first far parenthesis in a block a
								 * pioneer, so in principle we might add to the standard construction
								 * one pioneer per block. However, this approach voids the need of
								 * keeping track of the eccess at the start of each block.
								 */
								if (--count[matchingBlock] == 0 || countFarClosing == 0) {
									// This is a closing pioneer
									if (DEBUG) System.err.println("+) " + (block * Long.SIZE + j) + " " + Arrays.toString(count));
									closingPioneers.add(bits(block) + j);
									closingPioneerMatches.add((bits(block) + j) - (bits(matchingBlock) + findFarOpen(bits[matchingBlock], Long.SIZE, residual[matchingBlock])));
								}
								residual[matchingBlock]++;
							}
						}
					}
				}
				count[block] = (byte)countFarOpen(bits[block], l);
				if (DEBUG) System.err.println("Stack updated: " + Arrays.toString(count));
			}

			for(int i = count.length; i-- != 0;) if (count[i] != 0) throw new IllegalArgumentException("Unbalanced parentheses");
			if (DEBUG) System.err.println("):" + closingPioneers);
			if (DEBUG) System.err.println("):" + closingPioneerMatches);
		}

		if (findClose) {
			Arrays.fill(residual, (byte)0);

			openingPioneers = new LongArrayList();
			openingPioneerMatches = new LongArrayList();

			for(int block = numWords; block-- != 0;) {
				if (DEBUG) System.err.println("Scanning word " + block + " (" + LongArrayBitVector.wrap(new long[] { bits[block] }) + ")");
				final int l = (int)Math.min(Long.SIZE, length - bits(block));

				if (block != numWords -1) {
					int excess = 0;
					int countFarOpening = countFarOpen(bits[block], l);
					boolean somethingAdded = false;

					for(int j = l; j-- != 0;) {
						if ((bits[block] & 1L << j) == 0) {
							if (excess > 0) excess = -1;
							else --excess;
						}
						else {
							if (++excess > 0) {
								// Find block containing matching far close parenthesis
								int matchingBlock = block;
								while(count[++matchingBlock] == 0);
								countFarOpening--;
								/* Note that we always consider the first far parenthesis in a block a
								 * pioneer, so in principle we might add to the standard construction
								 * one pioneer per block. However, this approach voids the need of
								 * keeping track of the eccess at the start of each block.
								 */
								if (--count[matchingBlock] == 0 || countFarOpening == 0) {
									// This is an opening pioneer
									if (DEBUG) System.err.println("+(" + (block * (long)Long.SIZE + j) + " " + Arrays.toString(count));
									openingPioneers.add(bits(block) + j);
									openingPioneerMatches.add(-(bits(block) + j) + (bits(matchingBlock) + findFarClose(bits[matchingBlock], residual[matchingBlock])));
									if (ASSERTS) somethingAdded = true;
								}
								residual[matchingBlock]++;
							}
						}
					}
					if (ASSERTS) assert somethingAdded || countFarOpen(bits[block], l) == 0 : "No pioneers for block " + block + " " + LongArrayBitVector.wrap(new long[] { bits[block] }, l) + " (" + l + ") " + countFarOpen(bits[block], l);
				}
				count[block] = (byte)countFarClose(bits[block], l);
				if (DEBUG) System.err.println("Stack updated: " + Arrays.toString(count));
			}

			for(int i = count.length; i-- != 0;) if (count[i] != 0) throw new IllegalArgumentException("Unbalanced parentheses");
			if (DEBUG) System.err.println("(:" + openingPioneers);
			if (DEBUG) System.err.println("(:" + openingPioneerMatches);

			Collections.reverse(openingPioneers);
			Collections.reverse(openingPioneerMatches);
		}

		this.closingPioneers = closingPioneers != null ? new SparseSelect(bitVector.length(), closingPioneers.size(), closingPioneers.iterator()) : null;
		this.closingPioneersRank = closingPioneers != null ? this.closingPioneers.getRank() : null;
		this.closingPioneerMatches = closingPioneers != null ? new EliasFanoLongBigList(closingPioneerMatches) : null;
		this.openingPioneers = openingPioneers != null ? new SparseSelect(bitVector.length(), openingPioneers.size(), openingPioneers.iterator()) : null;
		this.openingPioneersRank = openingPioneers != null ? this.openingPioneers.getRank() : null;
		this.openingPioneerMatches = openingPioneers != null ? new EliasFanoLongBigList(openingPioneerMatches) : null;
}

	@Override
	public long enclose(final long pos) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long findClose(final long pos) {
		if (DEBUG) System.err.println("findClose(" + pos + ")...");
		final int word = word(pos);
		final int bit = bit(pos);
		if ((bits[word] & 1L << bit) == 0) throw new IllegalArgumentException();

		final int result = findNearClose(bits[word] >>> bit);

		if (ASSERTS) {
			int c = 1;
			int b = bit;
			while(++b < Long.SIZE) {
				if ((bits[word] & 1L << b) == 0) c--;
				else c++;
				if (c == 0) break;
			}

			if (ASSERTS) assert (c != 0) == (result < 0 || result >= Long.SIZE - bit) : "c: " + c + " bit: " + (b - bit) + " result:" + result + " " + LongArrayBitVector.wrap(new long[] { bits[word] >>> bit }, Long.SIZE - bit) + " (" +(Long.SIZE -bit)+ ")";
			if (ASSERTS) assert (c != 0) || (b == bit + result) : b + " != " + (bit + result) + " (bit:" + bit + ")" + LongArrayBitVector.wrap(new long[] { bits[word] >>> bit });
		}
		if (result < Long.SIZE - bit) {
			if (DEBUG) System.err.println("Returning in-word value: " + (bits(word) + bit + result));
			return bits(word) + bit + result;
		}

		final long pioneerIndex = openingPioneersRank.rank(pos + 1) - 1;
		final long pioneer = openingPioneers.select(pioneerIndex);
		final long match = pioneer + openingPioneerMatches.getLong(pioneerIndex);

		if (pos == pioneer) {
			if (DEBUG) System.err.println("Returning exact pioneer match: " + match);
			return match;
		}

		if (DEBUG) System.err.println("pioneer: " + pioneer + "; match: " + match);
		final int dist = (int)(pos - pioneer);

		if (ASSERTS) assert word == pioneer / Long.SIZE : "pos: " + pos + " word:" + word + " pioneer: " + pioneer + " word:" + pioneer / Long.SIZE  + " result:" + result;
		if (ASSERTS) assert word != match / Long.SIZE;
		if (ASSERTS) assert pioneer < pos;

		final int e = 2 * Long.bitCount((bits[word] >>> pioneer) & (1L << dist) - 1) - dist;
		if (ASSERTS) {
			assert e >= 1;
			int ee = 0;
			for(long p = pioneer; p < pos; p++) if ((bits[(int)(p / Long.SIZE)] & 1L << p) != 0) ee++;
			else ee--;
			assert ee == e: ee + " != " + e;
		}
		if (DEBUG) System.err.println("eccess: " + e);

		final int matchWord = word(match);
		final int matchBit = bit(match);

		final int numFarClose = matchBit - (Long.bitCount(bits[matchWord] & (1L << matchBit) - 1) << 1);

		if (DEBUG) System.err.println("far close before match: " + numFarClose);
		return bits(matchWord) + findFarClose(bits[matchWord], numFarClose - e);
	}

	@Override
	public long findOpen(final long pos) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long numBits() {
		return
			(openingPioneers != null ? (openingPioneers.numBits() + openingPioneersRank.numBits() + openingPioneerMatches.numBits()) : 0) +
			(closingPioneers != null ? closingPioneers.numBits() + closingPioneersRank.numBits() + closingPioneerMatches.numBits() : 0);
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		bits = bitVector.bits();
	}

	@Override
	public BitVector bitVector() {
		return bitVector;
	}

}
