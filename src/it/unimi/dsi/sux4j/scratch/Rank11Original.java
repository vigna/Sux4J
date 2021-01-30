/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2014-2021 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.scratch;

import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;
import static it.unimi.dsi.bits.LongArrayBitVector.words;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.AbstractRank;
import it.unimi.dsi.sux4j.bits.Rank;
import it.unimi.dsi.sux4j.bits.Rank9;

/** A <code>rank11</code> implementation.
 *
 * <p><code>rank11</code> is a ranking structure using 6.25% additional space and providing very fast ranking.
 * It was proposed by Simon Gog and Matthias Petri in &ldquo;Optimized succinct data structures for massive data&rdquo;,
 * <i>Softw. Pract. Exper.</i>, 2014. The only difference between this implementation and their
 * <code>rank_support_v5</code> is that counts for blocks are stored as in {@link Rank9} (that is, in opposite order).
 */

public class Rank11Original extends AbstractRank implements Rank {
	private static final long serialVersionUID = 1L;
	private static final int LOG2_WORDS_PER_SUPERBLOCK = 5;
	private static final int WORDS_PER_SUPERBLOCK = 1 << LOG2_WORDS_PER_SUPERBLOCK;

	protected transient long[] bits;
	protected final BitVector bitVector;
	protected final long[] count;
	protected final int numWords;
	protected final long numOnes;
	protected final long lastOne;

	public Rank11Original(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	public Rank11Original(final BitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();

		numWords = words(length);

		final int numCounts = (int)((length + 32 * Long.SIZE - 1) / (32 * Long.SIZE)) * 2;
		// Init rank/select structure
		count = new long[numCounts + 1];

		long c = 0, l = -1;
		int pos = 0;
		for(int i = 0; i < numWords; i += WORDS_PER_SUPERBLOCK, pos += 2) {
			count[pos] = c;

			for(int j = 0; j < WORDS_PER_SUPERBLOCK; j++) {
				if (j % 6 == 0) count[pos + 1] |= (i + j <= numWords ? c - count[pos] : 0x7FFL) << 60 - 12 * (j / 6);
				if (i + j < numWords) {
					if (bits[i + j] != 0) l = (i + j) * 64L + Fast.mostSignificantBit(bits[i + j]);
					c += Long.bitCount(bits[i + j]);
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
		final int block = (word >>> (LOG2_WORDS_PER_SUPERBLOCK - 1)) & ~1;
		final int offset = (word & ~-WORDS_PER_SUPERBLOCK) / 6;
		long result = count[block] + (count[block + 1] >> (60 - 12 * offset) & 0x7FF) +
				Long.bitCount(bits[word] & (1L << pos) - 1);

		for (int todo = (word & 0x1F) % 6; todo-- != 0;) result += Long.bitCount(bits[--word]);
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
