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

package it.unimi.dsi.sux4j.util;

import static it.unimi.dsi.bits.LongArrayBitVector.bit;
import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;

import java.util.NoSuchElementException;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;

/**
 * A compressed big list of longs providing prefix sums; an element occupies a number of bits
 * bounded by two plus the logarithm of the average value.
 *
 * <p>
 * Instances of this class store in compacted form a list of longs. Values are provided either
 * through an {@linkplain Iterable iterable object}. As an additional service, this list provides
 * access to the <em>{@linkplain #prefixSum(long) prefix sums}</em> of its values.
 *
 * <h2>Implementation details</h2>
 *
 * <p>
 * Instances of this class are essentially a view over an instance of
 * {@link EliasFanoMonotoneLongBigList} storing the prefix sums. The {@link #getLong(long)} method
 * delegates to {@link #getDelta(long)}. The iterator has the same properties of the iterator
 * returned by {@link EliasFanoMonotoneLongBigList#listIterator(long) EliasFanoMonotoneLongBiglist}.
 *
 * @see EliasFanoMonotoneLongBigList
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

	@Override
	public long getLong(final long index) {
		return getDelta(index);
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

	/**
	 * An list iterator over the values of this {@link EliasFanoPrefixSumLongBigList}.
	 *
	 * <p>
	 * {@linkplain #nextLong() Forward iteration} will be faster than iterated calls to
	 * {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will performs similarly to {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}.
	 *
	 * <p>
	 * Additional <em>unsafe</em> methods {@link #nextLongUnsafe()} and {@link #previousLongUnsafe()}
	 * iterate without checking for the existence of a next element.
	 */
	public class EliasFanoPrefixSumLongBigListIterator extends EliasFanoMonotoneLongBigListIterator {
		/** The last value returned. */
		protected long last;
		/** The upper bits as a long array. */
		protected long upperBits[] = EliasFanoPrefixSumLongBigList.this.upperBits.bits();

		protected EliasFanoPrefixSumLongBigListIterator(final long from) {
			super(from);
			final int startWord = word(lowerBitsPosition);
			final int startBit = bit(lowerBitsPosition);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits[startWord + 1] << -startBit;
			lowerBitsPosition += l;
			last = (bits(word) + Long.numberOfTrailingZeros(window) - index++) << l | lower & lowerBitsMask;
			window &= window - 1;
		}

		private long getNextUpperBits() {
			while (window == 0) window = upperBits[++word];
			final long upperBits = bits(word) + Long.numberOfTrailingZeros(window) - index++;
			window &= window - 1;
			return upperBits;
		}

		@Override
		public long previousIndex() {
			return index - 2;
		}

		@Override
		public long nextIndex() {
			return index - 1;
		}

		@Override
		public boolean hasPrevious() {
			return index > 1;
		}

		@Override
		public boolean hasNext() {
			return index < length;
		}

		/**
		 * Returns the same element as {@link #nextLong()}, if {@link #hasNext()} is true; otherwise,
		 * behavior is undefined.
		 *
		 * @return the same element as {@link #nextLong()}, if {@link #hasNext()} is true; otherwise,
		 *         behavior is undefined.
		 */
		@Override
		public long nextLongUnsafe() {
			final int startWord = word(lowerBitsPosition);
			final int startBit = bit(lowerBitsPosition);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits[startWord + 1] << -startBit;
			lowerBitsPosition += l;
			final long curr = getNextUpperBits() << l | lower & lowerBitsMask;
			final long result = curr - last;
			last = curr;
			return result;
		}

		/**
		 * Returns the same element as {@link #previousLong()}, if {@link #hasPrevious()} is true;
		 * otherwise, behavior is undefined.
		 *
		 * @return the same element as {@link #previousLong()}, if {@link #hasPrevious()} is true;
		 *         otherwise, behavior is undefined.
		 */
		@Override
		public long previousLongUnsafe() {
			index -= 2;
			final long position = selectUpper.select(index);
			window = upperBits[word = word(position)] & -1L << position;

			lowerBitsPosition = index * l;
			int startWord = word(lowerBitsPosition);
			int startBit = bit(lowerBitsPosition);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits[startWord + 1] << -startBit;

			lowerBitsPosition += l;
			last = (position - index++) << l | lower & lowerBitsMask;
			window &= window - 1;

			startWord = word(lowerBitsPosition);
			startBit = bit(lowerBitsPosition);
			lower = lowerBits[startWord] >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits[startWord + 1] << -startBit;
			while (window == 0) window = upperBits[++word];
			final long curr = (bits(word) + Long.numberOfTrailingZeros(window) - index) << l | lower & lowerBitsMask;
			final long result = curr - last;
			return result;
		}
	}

	/**
	 * Returns a list iterator over the values of this {@link EliasFanoPrefixSumLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will performs similarly to {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}.
	 *
	 * @param from the starting element in the list.
	 * @return a list iterator over the values of this {@link EliasFanoPrefixSumLongBigList}.
	 */
	@Override
	public EliasFanoPrefixSumLongBigListIterator listIterator(final long from) {
		return new EliasFanoPrefixSumLongBigListIterator(from);
	}

	/**
	 * Returns a list iterator over the values of this {@link EliasFanoPrefixSumLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will performs similarly to {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}.
	 *
	 * @return a list iterator over the values of this {@link EliasFanoPrefixSumLongBigList}.
	 */
	@Override
	public EliasFanoPrefixSumLongBigListIterator listIterator() {
		return listIterator(0);
	}

	/**
	 * Returns a list iterator over the values of this {@link EliasFanoPrefixSumLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will performs similarly to {@link EliasFanoPrefixSumLongBigList#getLong(long) getLong()}.
	 *
	 * @return a list iterator over the values of this {@link EliasFanoPrefixSumLongBigList}.
	 */
	@Override
	public EliasFanoPrefixSumLongBigListIterator iterator() {
		return listIterator();
	}
}
