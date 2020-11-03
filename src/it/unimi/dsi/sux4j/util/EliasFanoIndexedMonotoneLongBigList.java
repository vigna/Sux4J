/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2020 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.util;

import static it.unimi.dsi.bits.LongArrayBitVector.bit;
import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;

import java.io.Serializable;

import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.sux4j.bits.SimpleSelectZero;

/**
 * An extension of {@link EliasFanoMonotoneLongBigList} providing indexing (i.e., content-based
 * addressing).
 *
 * <p>
 * This implementation uses a {@link SimpleSelectZero} structure to support zero-selection inside
 * the upper-bits array, which makes it possible to implement fast content-addressed based access
 * methods such as {@link #predecessor(long)}, {@link #weakPredecessor(long)},
 * {@link #successor(long)}, {@link #strictSuccessor(long)}, {@link #contains(long)} and
 * {@link #indexOf(long)}.
 */

public class EliasFanoIndexedMonotoneLongBigList extends EliasFanoMonotoneLongBigList implements Serializable {
	private static final long serialVersionUID = 0L;

	/** The select structure used to extract the upper bits. */
	protected final SimpleSelectZero selectUpperZero = new SimpleSelectZero(selectUpper.bitVector());
	/** The upper bits as a long array. */
	protected final long[] upperBits = selectUpper.bitVector().bits();
	/**
	 * The index of the value returned by {@link #predecessor(long)}, {@link #weakPredecessor(long)},
	 * {@link #successor(long)}, {@link #strictSuccessor(long)}, and {@link #contains(long)}.
	 */
	private long currentIndex = -1;
	/** The last element of the sequence, or -1 if the sequence is empty. */
	private final long lastElement = isEmpty() ? -1 : getLong(size64() - 1);
	/** The first element of the sequence, or {@link Long#MAX_VALUE} if the sequence is empty. */
	private final long firstElement = isEmpty() ? Long.MAX_VALUE : getLong(0);

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final ByteIterable list) {
		super(list);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final IntIterable list) {
		super(list);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final long length, final int l, final long[] lowerBits, final SimpleSelect selectUpper) {
		super(length, l, lowerBits, selectUpper);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final ByteIterator iterator) {
		super(n, upperBound, iterator);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final IntIterator iterator) {
		super(n, upperBound, iterator);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final LongIterator iterator) {
		super(n, upperBound, iterator);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final ShortIterator iterator) {
		super(n, upperBound, iterator);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final long[] a, final LongIterator iterator) {
		super(a, iterator);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final LongIterable list) {
		super(list);
	}

	/** {@inheritDoc} */
	public EliasFanoIndexedMonotoneLongBigList(final ShortIterable list) {
		super(list);
	}

	/**
	 * Returns the first element of the sequence that is greater than or equal to the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrived using {@link #index()}.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the first value of the sequence that is greater than or equal to {@code lowerBound}, or
	 *         {@link Long#MAX_VALUE} if no such value exists.
	 * @see #strictSuccessor(long)
	 */

	public long successor(final long lowerBound) {
		if (lowerBound > lastElement) return Long.MAX_VALUE;
		final long zeroesToSkip = lowerBound >>> l;
		final long position = zeroesToSkip == 0 ? 0 : selectUpperZero.selectZero(zeroesToSkip - 1);
		int curr = word(position);
		long window = upperBits[curr];
		window &= -1L << position;
		currentIndex = zeroesToSkip == 0 ? 0 : position - zeroesToSkip + 1;

		if (l == 0) {
			for (;;) {
				while (window == 0) window = upperBits[++curr];
				final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - currentIndex;
				if (upperBits >= lowerBound) return upperBits;

				window &= window - 1;
				currentIndex++;
			}

		} else {
			long lowerBitsOffset = currentIndex * l;
			final int m = Long.SIZE - l;
			for (;;) {
				while (window == 0) window = upperBits[++curr];
				final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - currentIndex;

				final int startWord = word(lowerBitsOffset);
				final int startBit = bit(lowerBitsOffset);
				final long lower = lowerBits[startWord] >>> startBit;
				final long v = upperBits << l | (startBit <= m ? lower : lower | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
				if (v >= lowerBound) return v;

				window &= window - 1;
				currentIndex++;
				lowerBitsOffset += l;
			}
		}
	}

	/**
	 * Returns the first element of the sequence that is greater than the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the first value of the sequence that is greater than or equal to {@code lowerBound}, or
	 *         {@link Long#MAX_VALUE} if no such value exists.
	 * @see #successor(long)
	 */

	public long strictSuccessor(final long lowerBound) {
		if (lowerBound >= lastElement) return Long.MAX_VALUE;
		final long zeroesToSkip = lowerBound >>> l;
		final long position = zeroesToSkip == 0 ? 0 : selectUpperZero.selectZero(zeroesToSkip - 1);
		int curr = word(position);
		long window = upperBits[curr];
		window &= -1L << position;
		currentIndex = zeroesToSkip == 0 ? 0 : position - zeroesToSkip + 1;

		if (l == 0) {
			for (;;) {
				while (window == 0) window = upperBits[++curr];
				final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - currentIndex;
				if (upperBits > lowerBound) return upperBits;

				window &= window - 1;
				currentIndex++;
			}

		} else {
			long lowerBitsOffset = currentIndex * l;
			final int m = Long.SIZE - l;
			for (;;) {
				while (window == 0) window = upperBits[++curr];
				final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - currentIndex;

				final int startWord = word(lowerBitsOffset);
				final int startBit = bit(lowerBitsOffset);
				final long lower = lowerBits[startWord] >>> startBit;
				final long v = upperBits << l | (startBit <= m ? lower : lower | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
				if (v > lowerBound) return v;

				window &= window - 1;
				currentIndex++;
				lowerBitsOffset += l;
			}
		}
	}

	/**
	 * Returns the last value of the sequence that is less than the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param upperBound a strict upper bound on the returned value.
	 * @return the last value of the sequence that is less than {@code upperBound}, or -1 if no such
	 *         value exists.
	 * @see #weakPredecessor(long)
	 */

	public long predecessor(final long upperBound) {
		if (upperBound <= firstElement) return -1;
		if (upperBound > lastElement) {
			currentIndex = length - 1;
			return lastElement;
		}
		final long zeroesToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zeroesToSkip) - 1;
		long rank = position - zeroesToSkip;

		if (l == 0) {
			for (;;) {
				if ((upperBits[word(position)] & 1L << position) == 0) break;

				position--;
				rank--;
			}

			currentIndex = rank;
			return selectUpper.select(rank) - rank;
		} else {
			long lowerBitsOffset = rank * l;
			long lower;
			final long upperBoundLowerBits = upperBound & lowerBitsMask;
			final int m = Long.SIZE - l;
			for (;;) {
				final int startWord = word(lowerBitsOffset);
				final int startBit = bit(lowerBitsOffset);
				lower = lowerBits[startWord] >>> startBit;
				if (startBit > m) lower |= lowerBits[startWord + 1] << -startBit;
				lower &= lowerBitsMask;

				if ((upperBits[word(position)] & 1L << position) == 0 || lower < upperBoundLowerBits) break;

				lowerBitsOffset -= l;
				position--;
				rank--;
			}

			currentIndex = rank;
			return selectUpper.select(rank) - rank << l | lower;
		}
	}

	/**
	 * Returns the last value of the sequence that is less than or equal to the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param upperBound an upper bound on the returned value.
	 * @return the last value of the sequence that is less than or equal to {@code upperBound}, or -1 if
	 *         no such value exists.
	 * @see #predecessor(long)
	 */

	public long weakPredecessor(final long upperBound) {
		if (upperBound < firstElement) return -1;
		if (upperBound >= lastElement) {
			currentIndex = length - 1;
			return lastElement;
		}
		final long zeroesToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zeroesToSkip) - 1;
		long rank = position - zeroesToSkip;

		if (l == 0) {
			currentIndex = rank;
			return selectUpper.select(rank) - rank;
		} else {
			long lowerBitsOffset = rank * l;
			long lower;
			final long upperBoundLowerBits = upperBound & lowerBitsMask;
			final int m = Long.SIZE - l;
			for (;;) {
				final int startWord = word(lowerBitsOffset);
				final int startBit = bit(lowerBitsOffset);
				lower = lowerBits[startWord] >>> startBit;
				if (startBit > m) lower |= lowerBits[startWord + 1] << -startBit;
				lower &= lowerBitsMask;

				if ((upperBits[word(position)] & 1L << position) == 0 || lower <= upperBoundLowerBits) break;

				rank--;
				lowerBitsOffset -= l;
				position--;
			}

			currentIndex = rank;
			return selectUpper.select(rank) - rank << l | lower;
		}
	}

	/**
	 * Returns the index of the first occurrence of the specified element in the sequence, or -1 if the
	 * element does not belong to the sequence.
	 *
	 * <p>
	 * This implementation checks whether {@link #successor(long) successor(x) == x}, in which case it
	 * returns {@link #index()}.
	 *
	 * @param x a long.
	 */
	@Override
	public long indexOf(final long x) {
		if (successor(x) == x) return currentIndex;
		return -1;
	}

	/**
	 * Returns true if the sequence contains the specified element.
	 *
	 * <p>
	 * This implementation checks whether {@link #successor(long) successor(x) == x}.
	 *
	 * @param x a long.
	 * @return true if the sequence contains {@code x}; after the call, {@link #index()} will return the
	 *         index of {@code x} in the sequence.
	 */

	@Override
	public boolean contains(final long x) {
		return successor(x) == x;
	}

	/**
	 * Returns the index realizing the last value returned by {@link #predecessor(long)},
	 * {@link #weakPredecessor(long)}, {@link #successor(long)}, {@link #strictSuccessor(long)}, and
	 * {@link #contains(long)}.
	 *
	 * @return the index of the element realizing the last value returned by {@link #predecessor(long)},
	 *         {@link #weakPredecessor(long)}, {@link #successor(long)}, {@link #strictSuccessor(long)},
	 *         and {#contains(long)}, or -1 if no such method has ever been called.
	 */
	public long index() {
		return currentIndex;
	}
}
