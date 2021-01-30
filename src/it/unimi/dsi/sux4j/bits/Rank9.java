/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2021 Sebastiano Vigna
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

import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;
import static it.unimi.dsi.bits.LongArrayBitVector.words;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;

/** A <code>rank9</code> implementation.
 *
 * <p><code>rank9</code> is a ranking structure using 25% additional space and providing exceptionally fast ranking.
 */

public class Rank9 extends AbstractRank implements Rank {
	private static final long serialVersionUID = 1L;

	protected transient long[] bits;
	protected final BitVector bitVector;
	protected final long[] count;
	protected final int numWords;
	protected final long numOnes;
	protected final long lastOne;

	public Rank9(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	public Rank9(final BitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();

		numWords = words(length);

		final int numCounts = (int)((length + 8 * Long.SIZE - 1) / (8 * Long.SIZE)) * 2;
		// Init rank/select structure
		count = new long[numCounts + 1];

		final int numWords = this.numWords;
		final long[] bits = this.bits;
		final long[] count = this.count;

		long c = 0, l = -1;
		int pos = 0;
		for(int i = 0; i < numWords; i += 8, pos += 2) {
			count[pos] = c;
			c += Long.bitCount(bits[i]);
			if (bits[i] != 0) l = bits(i) + Fast.mostSignificantBit(bits[i]);
			for(int j = 1;  j < 8; j++) {
				count[pos + 1] |= (i + j <= numWords ? c - count[pos] : 0x1FFL) << 9 * (j - 1);
				if (i + j < numWords) {
					c += Long.bitCount(bits[i + j]);
					if (bits[i + j] != 0) l = bits(i + j) + Fast.mostSignificantBit(bits[i + j]);
				}
			}
		}

		numOnes = c;
		lastOne = l;
		count[numCounts] = c;
	}


	@Override
	public long rank(final long pos) {
		assert pos >= 0;
		assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if (pos > lastOne) return numOnes;

		final int word = word(pos);
		final int block = (word >>> 2) & ~1;
		final int offset = (word & 0x7) - 1;

		return count[block] + (count[block + 1] >>> (offset + (offset >>> 32 - 4 & 0x8)) * 9 & 0x1FF) + Long.bitCount(bits[word] & ((1L << pos) - 1));
	}

	/**
	 * Returns the rank at the given position assuming that the argument is less than the length of the
	 * bit vector.
	 *
	 * <p>
	 * This method is slightly faster than {@link #rank(long)}, as it avoids a check, but its behavior
	 * when the argument is equal to the length of the underlying bit vector is undefined.
	 *
	 * <p>
	 * If the array of longs representing the bit vector has a free bit at the end, this method will
	 * work correctly even when {@code pos} is equal to the length of the bit vector.
	 *
	 * @param pos a position in the bit vector between 0 (inclusive) and the length of the bit vector
	 *            (exclusive).
	 * @return the number of ones preceding position {@code pos}; if {@code pos} is out of bounds,
	 *         behavior is undefined.
	 * @see #rank(long)
	 */
	public long rankStrict(final long pos) {
		assert pos >= 0;
		assert bitVector.length() != bits(bits.length) || pos < bitVector.length();

		final int word = word(pos);
		final int block = (word >>> 2) & ~1;
		final int offset = (word & 0x7) - 1;

		return count[block] + (count[block + 1] >>> (offset + (offset >>> 32 - 4 & 0x8)) * 9 & 0x1FF) + Long.bitCount(bits[word] & ((1L << pos) - 1));
	}

	@Override
	public long numBits() {
		return bits(count.length);
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
