/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2023 Sebastiano Vigna
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

import static it.unimi.dsi.bits.LongArrayBitVector.word;
import static it.unimi.dsi.bits.LongArrayBitVector.words;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.AbstractRank;
import it.unimi.dsi.sux4j.bits.Rank;

/** A <code>rank9</code> implementation.
 *
 * <p><code>rank9</code> is a ranking structure using 25% additional space and providing exceptionally fast ranking.
 */

public class Rank9GogPetri extends AbstractRank implements Rank {
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 1L;

	protected transient long[] bits;
	protected final BitVector bitVector;
	protected final long[] count;
	protected final int numWords;
	protected final long numOnes;
	protected final long lastOne;

	public Rank9GogPetri(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	public Rank9GogPetri(final BitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();

		numWords = words(length);

		final int numCounts = (int)((length + 8 * Long.SIZE - 1) / (8 * Long.SIZE)) * 2;
		// Init rank/select structure
		count = new long[numCounts + 1];

		long c = 0, l = -1;
		int pos = 0;
		for(int i = 0; i < numWords; i += 8, pos += 2) {
			count[pos] = c;
			c += Long.bitCount(bits[i]);
			if (bits[i] != 0) l = i * 64L + Fast.mostSignificantBit(bits[i]);
			for(int j = 1;  j < 8; j++) {
				count[pos + 1] |= (i + j <= numWords ? c - count[pos] : 0x1FFL) << 63 - 9 * j;
				if (i + j < numWords) {
					c += Long.bitCount(bits[i + j]);
					if (bits[i + j] != 0) l = (i + j) * 64L + Fast.mostSignificantBit(bits[i + j]);
				}
			}
		}

		numOnes = c;
		lastOne = l;
		count[numCounts] = c;
	}


	@Override
	public long rank(final long pos) {
		if (ASSERTS) assert pos >= 0;
		if (ASSERTS) assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if (pos > lastOne) return numOnes;

		final int word = word(pos);
		final int block = (word >>> 2) & ~1;
		final int offset = word & 7;

		return count[block] + (count[block + 1] >>> (63 - offset * 9) & 0x1FF) + Long.bitCount(bits[word] & (1L << pos) - 1);
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
