/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
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

import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;
import static it.unimi.dsi.bits.LongArrayBitVector.words;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;


/** A <code>rank16</code> implementation.
 *
 * <p><code>rank16</code> is a ranking structure using just 18.75% additional space
 * and providing fast ranking. It is the natural ranking structure for 128-bit processors. */

public class Rank16 extends AbstractRank implements Rank {
	private static final long serialVersionUID = 1L;
	private static final int LOG2_BLOCK_LENGTH = 10;
	private static final int BLOCK_LENGTH = 1 << LOG2_BLOCK_LENGTH;

	protected transient long[] bits;
	protected final long[] superCount;
	protected final short[] count;
	protected final int numWords;
	protected final long numOnes;
	protected final long lastOne;
	protected final BitVector bitVector;

	public Rank16(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	public Rank16(final BitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		numWords = words(bitVector.length());

		final int numSuperCounts = (int)((bitVector.length() + BLOCK_LENGTH - 1) >>> LOG2_BLOCK_LENGTH);
		final int numCounts = (numWords + 1) >> 1;
		// Init rank/select structure
		count = new short[numCounts];
		superCount = new long[numSuperCounts];

		final int numWords = this.numWords;
		final long[] superCount = this.superCount;
		final short[] count = this.count;
		final long[] bits = this.bits;

		long c = 0, l = -1;
		for(int i = 0; i < numWords; i++) {
			if ((i & ~-BLOCK_LENGTH) == 0) superCount[i >>> LOG2_BLOCK_LENGTH] = c;
			if (i % 2 == 0) count[i >> 1] = (short)(c - superCount[i >>> LOG2_BLOCK_LENGTH]);
			c += Long.bitCount(bits[i]);
			if (bits[i] != 0) l = bits(i) + Fast.mostSignificantBit(bits[i]);
		}

		numOnes = c;
		lastOne = l;
	}


	@Override
	public long rank(final long pos) {
		assert pos >= 0;
		assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if (pos > lastOne) return numOnes;

		final int word = word(pos);
		final int block = word >>> LOG2_BLOCK_LENGTH;
		final int offset = word >> 1;

		return (word & 1) == 0 ?
				superCount[block] + (count[offset] & 0xFFFF) + Long.bitCount(bits[word] & ((1L << pos) - 1)) : superCount[block] + (count[offset] & 0xFFFF) + Long.bitCount(bits[word - 1]) + Long.bitCount(bits[word] & (1L << pos) - 1);
	}

	@Override
	public long numBits() {
		return bits(count.length) + bits(superCount.length);
	}

	@Override
	public long count() {
		return numOnes;
	}

	@Override
	public long rank(final long from, final long to) {
		return rank(to) - rank(from);
	}

	public long lastOne() {
		return lastOne;
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
