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

import static it.unimi.dsi.bits.LongArrayBitVector.bit;
import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

/** A rank implementation for sparse bit arrays based on the {@linkplain EliasFanoMonotoneLongBigList Elias&ndash;Fano representation of monotone functions}.
 *
 * <p>Note that some data may be shared with {@link SparseSelect}: just use the factory method {@link SparseSelect#getRank()} to obtain an instance. In that
 * case, {@link #numBits()} counts just the new data used to build the class, and not the shared part.
 */

public class SparseRank extends AbstractRank {
	private static final long serialVersionUID = 2L;

	/** The length of the underlying bit array. */
	protected final long n;
	/** The number of ones in the underlying bit array. */
	protected final long m;
	/** The number of lower bits. */
	protected final int l;
	/** The mask for lower bits. */
	protected final long lowerLBitsMask;
	/** The list of lower bits of the position of each one, stored explicitly. */
	protected long[] lowerBits;
	/** The upper bits. */
	protected final BitVector upperBits;
	/** The rank structure used to extract the upper bits. */
	protected final SimpleSelectZero selectZeroUpper;
	/** Whether this structure was built from a {@link SparseSelect} structure, and thus shares part of its internal state. */
	protected final boolean fromSelect;

	/** Creates a new rank structure using a long array.
	 *
	 * <p>The resulting structure keeps no reference to the original array.
	 *
	 * @param bits a long array containing the bits.
	 * @param length the number of valid bits in <code>bits</code>.
	 */
	public SparseRank(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	/** Creates a new rank structure using a bit vector.
	 *
	 * <p>The resulting structure keeps no reference to the original bit vector.
	 *
	 * @param bitVector the input bit vector.
	 */
	public SparseRank(final BitVector bitVector) {
		this(bitVector.length(), bitVector.count(), bitVector.asLongSet().iterator());
	}

	/** Creates a new rank structure using an {@linkplain LongIterator iterator}.
	 *
	 * <p>This constructor is particularly useful if the positions of the ones are provided by
	 * some sequential source.
	 *
	 * @param n the number of bits in the underlying bit vector.
	 * @param m the number of ones in the underlying bit vector.
	 * @param iterator an iterator returning the positions of the ones in the underlying bit vector in increasing order.
	 */
	public SparseRank(final long n, final long m, final LongIterator iterator) {
		long pos = -1;
		this.n = n;
		this.m = m;
		l = m == 0 ? 0 : Math.max(0, Fast.mostSignificantBit(n / m));
		lowerLBitsMask = (1L << l) - 1;
		final LongArrayBitVector lowerBitsVector = LongArrayBitVector.getInstance();
		final LongBigList lowerBitsList = lowerBitsVector.asLongBigList(l);
		lowerBitsList.size(m + 1);
		upperBits = LongArrayBitVector.getInstance().length(m + 1 + (n >>> l) + 2);

		final int l = this.l;
		final BitVector upperBits = this.upperBits;
		final long lowerLBitsMask = this.lowerLBitsMask;

		long last = 0;
		for(long i = 0; i < m; i++) {
			pos = iterator.nextLong();
			if (pos >= n) throw new IllegalArgumentException("Too large bit position: " + pos + " >= " + n);
			if (pos < last) throw new IllegalArgumentException("Positions are not nondecreasing: " + pos + " < " + last);
			if (l != 0) lowerBitsList.set(i, pos & lowerLBitsMask);
			upperBits.set((pos >>> l) + i);
			last = pos;
		}

		upperBits.set((n >>> l) + m);
		lowerBitsList.set(m, n & lowerLBitsMask);
		if (iterator.hasNext()) throw new IllegalArgumentException("There are more than " + m + " positions in the provided iterator");
		lowerBits = l == 0 ? new long[1] : lowerBitsVector.bits();
		selectZeroUpper = new SimpleSelectZero(upperBits);
		fromSelect = false;
	}

	protected SparseRank(final long n, final long m, final int l, final long[] lowerBits, final BitVector upperBits) {
		this.n = n;
		this.m = m;
		this.l = l;
		this.lowerLBitsMask = (1L << l) - 1;
		this.lowerBits = lowerBits.length == 0 ? new long[1] : lowerBits;
		this.upperBits = upperBits;
		this.selectZeroUpper = new SimpleSelectZero(upperBits);
		this.fromSelect = true;
	}

	@Override
	public long rank(final long pos) {
		assert pos >= 0;
		assert pos <= n;

		final BitVector upperBits = this.upperBits;
		final long[] lowerBits = this.lowerBits;
		final int l = this.l;
		final long lowerLBitsMask = this.lowerLBitsMask;
		final long posLowerBits = pos & lowerLBitsMask;

		final long zerosToSkip = pos >>> l;
		long upperPos = selectZeroUpper.selectZero(zerosToSkip) - 1;
		long rank = upperPos - zerosToSkip;
		long lowerBitsPosition = rank++ * l;

		for (;;) {
			if (upperPos < 0 || !upperBits.getBoolean(upperPos)) return rank;

			final int startWord = word(lowerBitsPosition);
			final int startBit = bit(lowerBitsPosition);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits[startWord + 1] << -startBit;

			if ((lower & lowerLBitsMask) < posLowerBits) return rank;

			rank--;
			upperPos--;
			lowerBitsPosition -= l;
		}
	}

	@Override
	public long numBits() {
		return selectZeroUpper.numBits() + (fromSelect ? 0 : upperBits.length() + bits(lowerBits.length));
	}

	/** Creates a new {@link SparseSelect} structure sharing data with this instance.
	 *
	 * @return a new {@link SparseSelect} structure sharing data with this instance.
	 */
	public SparseSelect getSelect() {
		return new SparseSelect(n, m, l, lowerBits, new SimpleSelect(upperBits));
	}


	/**
	 * Returns the bit vector indexed; since the bits are not stored in this data structure, a copy is
	 * built on purpose and returned.
	 *
	 * <p>
	 * <strong>Warning</strong>: this method is quite slow, as it has to rebuild all bit positions.
	 *
	 * @return a copy of the underlying bit vector.
	 */
	@Override
	public BitVector bitVector() {
		final LongArrayBitVector result = LongArrayBitVector.getInstance().length(n);
		final long[] upperBits = this.upperBits.bits();
		final long[] lowerBits = this.lowerBits;
		final int l = this.l;
		final long lowerLBitsMask = this.lowerLBitsMask;
		final long m = this.m;

		int curr = 0;
		long window = upperBits[curr];
		long lowerBitsPosition = 0;

		for (long i = 0; i < m; i++) {
			while (window == 0) window = upperBits[++curr];
			final long upper = bits(curr) + Long.numberOfTrailingZeros(window) - i;

			final int startWord = LongArrayBitVector.word(lowerBitsPosition);
			final int startBit = LongArrayBitVector.bit(lowerBitsPosition);
			result.set(upper << l | (startBit <= Long.SIZE - l ? lowerBits[startWord] >>> startBit : lowerBits[startWord] >>> startBit | lowerBits[startWord + 1] << -startBit) & lowerLBitsMask);

			window &= window - 1;
			lowerBitsPosition += l;
		}
		return result;
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		// A fix that avoids bumping the serial id from 2L
		if (lowerBits.length == 0) lowerBits = new long[1];
	}
}
