package it.unimi.dsi.sux4j.bits;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2014-2017 Sebastiano Vigna
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

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;

/** A <code>rank11</code> implementation.
 *
 * <p><code>rank11</code> is a ranking structure using 6.25% additional space and providing very fast ranking.
 * It was proposed by Simon Gog and Matthias Petri in &ldquo;Optimized succinct data structures for massive data&rdquo;,
 * <i>Softw. Pract. Exper.</i>, 2014. The only difference between this implementation and their
 * <code>rank_support_v5</code> is that counts for blocks are stored as in {@link Rank9} (that is, in opposite order).
 *
 * <p>Note that to achieve such a low overhead, {@link #rank(long)} contains a loop. As
 * a consequence, the implementation of this method is significantly
 * slower than that of {@link Rank9} or {@link Rank16}, which don't do any looping.
 */

public class Rank11 extends AbstractRank implements Rank {
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 1L;
	private static final int WORDS_PER_SUPERBLOCK = 32;

	protected transient long[] bits;
	protected final BitVector bitVector;
	protected final long[] count;
	protected final int numWords;
	protected final long numOnes;
	protected final long lastOne;

	public Rank11(long[] bits, long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	public Rank11(final BitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();

		numWords = (int)((length + Long.SIZE - 1) / Long.SIZE);

		final int numCounts = (int)((length + 32 * Long.SIZE - 1) / (32 * Long.SIZE)) * 2;
		// Init rank/select structure
		count = new long[numCounts + 1];

		long c = 0, l = -1;
		int pos = 0;
		for(int i = 0; i < numWords; i += WORDS_PER_SUPERBLOCK, pos += 2) {
			count[pos] = c;

			for(int j = 0; j < WORDS_PER_SUPERBLOCK; j++) {
				if (j != 0 && j % 6 == 0) count[pos + 1] |= (i + j <= numWords ? c - count[pos] : 0x7FFL) << 12 * (j / 6 - 1);
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
	public long rank(long pos) {
		if (ASSERTS) assert pos >= 0;
		if (ASSERTS) assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if (pos > lastOne) return numOnes;

		int word = (int)(pos / Long.SIZE);
		final int block = word / (WORDS_PER_SUPERBLOCK / 2) & ~1;
        final int offset = ((word % WORDS_PER_SUPERBLOCK) / 6) - 1;

        long result = count[block] + (count[block + 1] >> 12 * (offset + (offset >>> 32 - 4 & 6)) & 0x7FF) +
				Long.bitCount(bits[word] & (1L << pos % Long.SIZE) - 1);

		for (int todo = (word & 0x1F) % 6; todo-- != 0;) result += Long.bitCount(bits[--word]);
        return result;
	}

	@Override
	public long numBits() {
		return count.length * (long)Long.SIZE;
	}

	@Override
	public long count() {
		return numOnes;
	}

	@Override
	public long rank(long from, long to) {
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
