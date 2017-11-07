package it.unimi.dsi.sux4j.util;

import java.util.NoSuchElementException;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2017 Sebastiano Vigna
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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;

/** A compressed big list of longs providing prefix sums; an element occupies a number of bits bounded by two plus the logarithm of the average value.
 *
 * <p>Instances of this class store in compacted form a list of longs.
 * Values are provided either through an {@linkplain Iterable iterable object}.
 * As an additional service, this list provides access to the <em>{@linkplain #prefixSum(long) prefix sums}</em> of its values.
 *
 * <h2>Implementation details</h2>
 *
 * <p>Instances of this class are essentially a view over an instance of {@link EliasFanoMonotoneLongBigList}
 * storing the prefix sums. The {@link #getLong(long)} method has been optimised so to avoid two calls
 * to the {@link #getLong(long)} method of the {@link EliasFanoMonotoneLongBigList}.
 *
 */
public class EliasFanoPrefixSumLongBigList extends EliasFanoMonotoneLongBigList {
	private static final long serialVersionUID = 2L;

	/** Wraps an iterator and returns prefix sums. */

	private final static class CumulativeLongIterable implements LongIterable {
		private final LongIterable iterable;

		public CumulativeLongIterable(final LongIterable iterable) {
			this.iterable = iterable;
		}

		@Override
		public LongIterator iterator() {
			return new LongIterator() {
				private final LongIterator iterator = iterable.iterator();
				private long prefixSum = 0;
				private boolean lastToDo = true;

				@Override
				public boolean hasNext() {
					return iterator.hasNext() || lastToDo;
				}

				@Override
				public long nextLong() {
					if (! hasNext()) throw new NoSuchElementException();
					if (! iterator.hasNext() && lastToDo) {
						lastToDo = false;
						return prefixSum;
					}
					final long result = prefixSum;
					prefixSum += iterator.nextLong();
					return result;
				}
			};
		}

	}

	private final BitVector upperBits;

	/** Creates a new Elias&ndash;Fano prefix-sum long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoPrefixSumLongBigList(final LongIterable elements) {
		super(new CumulativeLongIterable(elements));
		this.upperBits = selectUpper.bitVector();
	}

	/** Creates a new Elias&ndash;Fano prefix-sum long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoPrefixSumLongBigList(final IntIterable elements) {
		this((LongIterable) () -> LongIterators.wrap(elements.iterator()));
	}

	/** Creates a new Elias&ndash;Fano prefix-sum long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoPrefixSumLongBigList(final ShortIterable elements) {
		this((LongIterable) () -> LongIterators.wrap(elements.iterator()));
	}

	/** Creates a new Elias&ndash;Fano prefix-sum long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoPrefixSumLongBigList(final ByteIterable elements) {
		this((LongIterable) () -> LongIterators.wrap(elements.iterator()));
	}

	private final static long getDiff(long[] bits, long index, int l) {
		if (l == 0) return 0;
		final int m = Long.SIZE - l;
		final long start = index * l;
		int startWord = (int)(start >>> LongArrayBitVector.LOG2_BITS_PER_WORD);
		int startBit = (int)(start & LongArrayBitVector.WORD_MASK);
		final long a = startBit <= m ? bits[startWord] << m - startBit >>> m : bits[startWord] >>> startBit | bits[startWord + 1] << Long.SIZE + m - startBit >>> m;
		startWord = (int)((start + l) >>> LongArrayBitVector.LOG2_BITS_PER_WORD);
		startBit = (int)((start + l) & LongArrayBitVector.WORD_MASK);
		return (startBit <= m ? bits[startWord] << m - startBit >>> m : bits[startWord] >>> startBit | bits[startWord + 1] << Long.SIZE + m - startBit >>> m) - a;
	}

	@Override
	public long getLong(final long index) {
		if (index < 0 || index >= length - 1) throw new IndexOutOfBoundsException(Long.toString(index));
		final long pos = selectUpper.select(index + 1);
		if (upperBits.getBoolean(pos - 1)) return getDiff(lowerBits, index, l);
		else return (pos - upperBits.previousOne(pos) - 1) * (1L << l) + getDiff(lowerBits, index, l);
	}

	/** Returns the prefix sum of this list up to the given index.
	 *
	 * @param index an index from 0 to the length of this list.
	 * @return the sum of the values with index between 0 (inclusive) and <code>index</code> (exclusive).
	 */
	public long prefixSum(final long index) {
		return super.getLong(index);
	}

	@Override
	public long size64() {
		return length - 1;
	}
}
