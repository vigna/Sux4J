/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2022 Sebastiano Vigna
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

/** A <code>rank12</code> implementation.
 *
 * <p><code>rank12</code> is a ranking structure using 3.125% additional space and providing fast ranking.
 * It is the natural loosening of {@link Rank11} in which the words for superblock are doubled.
 */

public class Rank12 extends AbstractRank implements Rank {
	private static final long serialVersionUID = 1L;
	private static final int LOG2_WORDS_PER_SUPERBLOCK = 6;
	private static final int WORDS_PER_SUPERBLOCK = 1 << LOG2_WORDS_PER_SUPERBLOCK;

	protected transient long[] bits;
	protected final BitVector bitVector;
	protected final long[] count;
	protected final int numWords;
	protected final long numOnes;
	protected final long lastOne;

	public Rank12(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	public Rank12(final BitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();

		numWords = words(length);

		final int numCounts = (int)((length + WORDS_PER_SUPERBLOCK * Long.SIZE - 1) / (WORDS_PER_SUPERBLOCK * Long.SIZE)) * 2;
		// Init rank/select structure
		count = new long[numCounts + 1];

		long c = 0, l = -1;
		int pos = 0;

		final int numWords = this.numWords;
		final long[] bits = this.bits;
		final long[] count = this.count;

		for(int i = 0; i < numWords; i += WORDS_PER_SUPERBLOCK, pos += 2) {
			count[pos] = c;

			for(int j = 0; j < WORDS_PER_SUPERBLOCK; j++) {
				if (j != 0 && j % 12 == 0) count[pos + 1] |= (i + j <= numWords ? c - count[pos] : 0xFFFL) << 12 * ((j - 1) / 12);
				if (i + j < numWords) {
					if (bits[i + j] != 0) l = bits(i + j) + Fast.mostSignificantBit(bits[i + j]);
					c += Long.bitCount(bits[i + j]);
					assert c - count[pos] <= 4096 : c - count[pos];
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

		int word = word(pos);
		final int block = (word >>> LOG2_WORDS_PER_SUPERBLOCK - 1) & ~1;
		final int offset = (word & ~-WORDS_PER_SUPERBLOCK) / 12 - 1;
		long result = count[block] + (count[block + 1] >> 12 * (offset + (offset >>> 32 - 4 & 6)) & 0xFFF) +
				Long.bitCount(bits[word] & (1L << pos) - 1);

		for (int todo = (word & ~-WORDS_PER_SUPERBLOCK) % 12; todo-- != 0;) result += Long.bitCount(bits[--word]);
        return result;
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
