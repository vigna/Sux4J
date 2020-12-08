/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
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
import static it.unimi.dsi.bits.LongArrayBitVector.word;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

/** A select implementation for sparse bit arrays based on the {@linkplain EliasFanoMonotoneLongBigList Elias&ndash;Fano representation of monotone functions}.
 *
 * <p>Instances of this classes do not add support to a bit vector: rather, they replace the bit vector
 * with a succinct representation of the positions of the ones in the bit vector.
 *
 * <p>Note that some data may be shared with {@link SparseRank}: just use the factory method {@link SparseRank#getSelect()} to obtain an instance. In that
 * case, {@link #numBits()} counts just the new data used to build the class, and not the shared part.
 */

public class SparseSelect extends EliasFanoMonotoneLongBigList implements Select {
	private static final long serialVersionUID = 2L;

	/** The number of bits in the underlying bit array. */
	private final long n;
	/** Whether this structure was built from a {@link SparseRank} structure, and thus shares part of its internal state. */
	protected final boolean fromRank;

	/** Creates a new select structure using a long array.
	 *
	 * <p>The resulting structure keeps no reference to the original array.
	 *
	 * @param bits a long array containing the bits.
	 * @param length the number of valid bits in <code>bits</code>.
	 */
	public SparseSelect(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	/** Creates a new select structure using a bit vector.
	 *
	 * <p>The resulting structure keeps no reference to the original bit vector.
	 *
	 * @param bitVector the input bit vector.
	 */
	public SparseSelect(final BitVector bitVector) {
		this(bitVector.length(), bitVector.count(), bitVector.asLongSet().iterator());
	}


	/** Creates a new select structure using an {@linkplain LongIterator iterator}.
	 *
	 * <p>This constructor is particularly useful if the positions of the ones are provided by
	 * some sequential source.
	 *
	 * @param n the number of bits in the underlying bit vector.
	 * @param m the number of ones in the underlying bit vector.
	 * @param iterator an iterator returning the positions of the ones in the underlying bit vector in increasing order.
	 */
	public SparseSelect(final long n, final long m, final LongIterator iterator) {
		super(m, n, iterator);
		this.n = n;
		fromRank = false;
	}

	/** Creates a new select structure using a {@linkplain LongList list} of longs.
	 *
	 * @param list the list of the positions of ones.
	 */
	public SparseSelect(final LongList list) {
		super(list);
		this.n = list.isEmpty() ? 0 : list.getLong(list.size() - 1) + 1;
		fromRank = false;
	}

	/** Creates a new select structure using a {@linkplain LongBigList big list} of longs.
	 *
	 * <p>This constructor is particularly useful if the positions of the ones are provided by
	 * some sequential source.
	 *
	 * @param list the list of the positions of ones.
	 */
	public SparseSelect(final LongBigList list) {
		super(list);
		this.n = list.isEmpty() ? 0 : list.getLong(list.size64() - 1) + 1;
		fromRank = false;
	}

	protected SparseSelect(final long n, final long m, final int l, final long[] lowerBits, final SimpleSelect selectUpper) {
		super(m, l, selectUpper.bitVector().bits(), lowerBits, selectUpper);
		this.n = n;
		this.fromRank = true;
	}

	/** Creates a new {@link SparseRank} structure sharing data with this instance.
	 *
	 * @return a new {@link SparseRank} structure sharing data with this instance.
	 */
	public SparseRank getRank() {
		return new SparseRank(n, length, l, lowerBits, selectUpper.bitVector());
	}

	@Override
	public long size64() {
		return n;
	}

	@Override
	@Deprecated
	public int size() {
		return (int)Math.min(n, Integer.MAX_VALUE);
	}

	@Override
	public long getLong(final long pos) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long numBits() {
		return selectUpper.numBits() + (fromRank ? 0 : selectUpper.bitVector().length() + lowerBits.length * (long)Long.SIZE);
	}

	@Override
	public long select(final long rank) {
		assert rank >= 0;
		assert rank < length;

		final int l = this.l;
		final long upperBits = selectUpper.select(rank) - rank;

		final long position = rank * l;
		final int startWord = word(position);
		final int startBit = bit(position);
		final long result = lowerBits[startWord] >>> startBit;
		return upperBits << l | (startBit + l <= Long.SIZE ? result : result | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
	}

	/** Returns the bit vector indexed; since the bits are not stored in this data structure,
	 * a copy is built on purpose and returned.
	 *
	 * @return a copy of the underlying bit vector.
	 */
	@Override
	public BitVector bitVector() {
		final LongArrayBitVector result = LongArrayBitVector.ofLength(n);
		for(long i = length; i-- != 0;) result.set(select(i));
		return result;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(this);
	}

	@Override
	public boolean equals(final Object o) {
		return o == this;
	}

	@Override
	public String toString() {
		return getClass().getName() + '@' + Integer.toHexString(hashCode());
	}
}
