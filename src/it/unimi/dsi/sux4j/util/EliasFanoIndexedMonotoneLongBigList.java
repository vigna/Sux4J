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
 *
 * <p>
 * This class is thread safe as long as you do not use {@link #index()}, as its value depend on an
 * interval variable that caches the result of {@link #predecessor(long)},
 * {@link #weakPredecessor(long)}, {@link #successor(long)}, and {@link #strictSuccessor(long)}.
 * Usually it is possible to work around the issue using the alternative methods
 * {@link #predecessorIndex(long)}, {@link #weakPredecessorIndex(long)},
 * {@link #successorIndex(long)}, and {@link #strictSuccessorIndex(long)}.
 */

public class EliasFanoIndexedMonotoneLongBigList extends EliasFanoMonotoneLongBigList implements Serializable {
	private static final long serialVersionUID = 2L;

	/** The select structure used to extract the upper bits. */
	protected final SimpleSelectZero selectUpperZero = new SimpleSelectZero(selectUpper.bitVector());
	/**
	 * The index of the value returned by {@link #predecessor(long)}, {@link #weakPredecessor(long)},
	 * {@link #successor(long)}, and {@link #strictSuccessor(long)}.
	 */
	private long currentIndex = -1;
	/** The last element of the sequence, or -1 if the sequence is empty. */
	private final long lastElement = isEmpty() ? -1 : getLong(size64() - 1);
	/** The first element of the sequence, or {@link Long#MAX_VALUE} if the sequence is empty. */
	private final long firstElement = isEmpty() ? Long.MAX_VALUE : getLong(0);

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final IntIterable list) {
		super(list);
	}

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final ShortIterable list) {
		super(list);
	}

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final ByteIterable list) {
		super(list);
	}

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final LongIterable list) {
		super(list);
	}

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by an iterator, given
	 * that the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final ByteIterator iterator) {
		super(n, upperBound, iterator);
	}

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by an iterator, given
	 * that the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final IntIterator iterator) {
		super(n, upperBound, iterator);
	}

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by an iterator, given
	 * that the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final LongIterator iterator) {
		super(n, upperBound, iterator);
	}

	/**
	 * Creates an indexed Elias&ndash;Fano representation of the values returned by an iterator, given
	 * that the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoIndexedMonotoneLongBigList(final long n, final long upperBound, final ShortIterator iterator) {
		super(n, upperBound, iterator);
	}


	/**
	 * Returns the first element of the sequence that is greater than or equal to the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the first value of the sequence that is greater than or equal to {@code lowerBound}, or
	 *         {@link Long#MAX_VALUE} if no such value exists.
	 * @see #strictSuccessor(long)
	 * @see #successorUnsafe(long)
	 */

	public long successor(final long lowerBound) {
		if (lowerBound <= firstElement) {
			currentIndex = 0;
			return firstElement;
		}
		if (lowerBound > lastElement) return Long.MAX_VALUE;
		return successorUnsafe(lowerBound);
	}

	/**
	 * Returns the first element of the sequence that is greater than or equal to the provided bound.
	 *
	 * <p>
	 * This method is slightly faster than {@link #successor(long)} as it does not check its argument.
	 *
	 * <p>
	 * The position of the returned element in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param lowerBound a nonnegative lower bound on the returned value; must be smaller than or equal
	 *            to the last element of the sequence.
	 * @return the first value of the sequence that is greater than or equal to {@code lowerBound};
	 * @see #successor(long)
	 */

	public long successorUnsafe(final long lowerBound) {
		assert lowerBound >= 0;
		assert lowerBound <= lastElement;
		final long zerosToSkip = lowerBound >>> l;
		final long position = zerosToSkip == 0 ? 0 : selectUpperZero.selectZero(zerosToSkip - 1) + 1;
		int curr = word(position);
		long window = upperBits[curr];
		window &= -1L << position;
		long rank = position - zerosToSkip;

		long lowerBitsOffset = rank * l;
		final int m = Long.SIZE - l;
		for (;;) {
			while (window == 0) window = upperBits[++curr];
			final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - rank;

			final int startWord = word(lowerBitsOffset);
			final int startBit = bit(lowerBitsOffset);
			final long lower = lowerBits[startWord] >>> startBit;
			final long v = upperBits << l | (startBit <= m ? lower : lower | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
			if (v >= lowerBound) {
				currentIndex = rank;
				return v;
			}

			window &= window - 1;
			rank++;
			lowerBitsOffset += l;
		}
	}

	/**
	 * Returns the index of first element of the sequence that is greater than or equal to the provided
	 * bound.
	 *
	 * <p>
	 * This method is significantly faster than {@link #successor(long)}, as it does not have to compute
	 * the actual successor.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the index of the first value of the sequence that is greater than or equal to
	 *         {@code lowerBound}, or {@link Long#MAX_VALUE} if no such value exists.
	 * @see #successor(long)
	 */

	public long successorIndex(long lowerBound) {
		if (lowerBound > lastElement) return Long.MAX_VALUE;
		lowerBound &= ~(lowerBound >> Long.SIZE - 1); // Max with zero
		return successorIndexUnsafe(lowerBound);
	}

	/**
	 * Returns the index of first element of the sequence that is greater than or equal to the provided
	 * bound.
	 *
	 * <p>
	 * This method is slightly faster than {@link #successorIndex(long)} as it does not check its
	 * argument.
	 *
	 * <p>
	 * This method is significantly faster than {@link #successorUnsafe(long)}, as it does not have to
	 * compute the actual successor.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param lowerBound a nonnegative lower bound on the returned value; must be smaller than or equal
	 *            to the last element of the sequence.
	 * @return the index of the first value of the sequence that is greater than or equal to
	 *         {@code lowerBound}.
	 * @see #successorUnsafe(long)
	 */

	public long successorIndexUnsafe(final long lowerBound) {
		assert lowerBound >= 0;
		assert lowerBound <= lastElement;
		final long zerosToSkip = lowerBound >>> l;
		long position = zerosToSkip == 0 ? 0 : selectUpperZero.selectZero(zerosToSkip - 1) + 1;
		long rank = position - zerosToSkip;

		long lowerBitsOffset = rank * l;
		final long lowerBoundLowerBits = lowerBound & lowerBitsMask;
		final int m = Long.SIZE - l;
		for (;;) {
			if ((upperBits[word(position)] & 1L << position) == 0) return rank;

			final int startWord = word(lowerBitsOffset);
			final int startBit = bit(lowerBitsOffset);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit > m) lower |= lowerBits[startWord + 1] << -startBit;
			lower &= lowerBitsMask;
			if (lower >= lowerBoundLowerBits) return rank;

			position++;
			rank++;
			lowerBitsOffset += l;
		}
	}

	/**
	 * Returns the first element of the sequence that is greater than the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the first value of the sequence that is greater than {@code lowerBound}, or
	 *         {@link Long#MAX_VALUE} if no such value exists.
	 * @see #successor(long)
	 * @see #strictSuccessorUnsafe(long)
	 */

	public long strictSuccessor(final long lowerBound) {
		if (lowerBound < firstElement) {
			currentIndex = 0;
			return firstElement;
		}
		if (lowerBound >= lastElement) return Long.MAX_VALUE;
		return strictSuccessorUnsafe(lowerBound);
	}

	/**
	 * Returns the first element of the sequence that is greater than the provided bound.
	 *
	 * <p>
	 * This method is slightly faster than {@link #strictSuccessor(long)} as it does not check its
	 * argument.
	 *
	 * <p>
	 * The position of the returned element in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param lowerBound a nonnegative lower bound on the returned value; must be smaller than or equal
	 *            to the last element of the sequence.
	 * @return the first value of the sequence that is greater than {@code lowerBound}, or the last
	 *         element plus one if no such value exists; in that case, {@link #index()} is set to the
	 *         length of the sequence.
	 * @see #successor(long)
	 * @see #strictSuccessor(long)
	 */

	public long strictSuccessorUnsafe(final long lowerBound) {
		assert lowerBound >= 0;
		assert lowerBound <= lastElement;
		final long zerosToSkip = lowerBound >>> l;
		final long position = zerosToSkip == 0 ? 0 : selectUpperZero.selectZero(zerosToSkip - 1) + 1;
		int curr = word(position);
		long window = upperBits[curr];
		window &= -1L << position;
		long rank = position - zerosToSkip;

		long lowerBitsOffset = rank * l;
		final int m = Long.SIZE - l;
		for (;;) {
			while (window == 0) window = upperBits[++curr];
			final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - rank;

			final int startWord = word(lowerBitsOffset);
			final int startBit = bit(lowerBitsOffset);
			final long lower = lowerBits[startWord] >>> startBit;
			final long v = upperBits << l | (startBit <= m ? lower : lower | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
			if (v > lowerBound) {
				currentIndex = rank;
				return v;
			}

			window &= window - 1;
			rank++;
			lowerBitsOffset += l;
		}
	}

	/**
	 * Returns the index of first element of the sequence that is greater than the provided bound.
	 *
	 * <p>
	 * This method is significantly faster than {@link #strictSuccessor(long)}, as it does not have to
	 * compute the actual strict successor.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the index of the first value of the sequence that is greater than {@code lowerBound}, or
	 *         {@link Long#MAX_VALUE} if no such value exists.
	 * @see #strictSuccessor(long)
	 * @see #strictSuccessorIndexUnsafe(long)
	 */

	public long strictSuccessorIndex(final long lowerBound) {
		if (lowerBound < firstElement) return 0;
		if (lowerBound >= lastElement) return Long.MAX_VALUE;
		return strictSuccessorIndexUnsafe(lowerBound);
	}

	/**
	 * Returns the index of first element of the sequence that is greater than the provided bound.
	 *
	 * <p>
	 * This method is slightly faster than {@link #strictSuccessorIndex(long)} as it does not check its
	 * argument.
	 *
	 * <p>
	 * This method is significantly faster than {@link #successorUnsafe(long)}, as it does not have to
	 * compute the actual successor.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param lowerBound a nonnegative lower bound on the returned value; must be smaller than or equal
	 *            to the last element of the sequence.
	 * @return the index of first value of the sequence that is greater than {@code lowerBound}, or the
	 *         length of the sequence if the argument is equal to the last element of th sequence.
	 * @see #strictSuccessorUnsafe(long)
	 * @see #strictSuccessor(long)
	 */

	public long strictSuccessorIndexUnsafe(final long lowerBound) {
		assert lowerBound >= 0;
		assert lowerBound <= lastElement;
		final long zerosToSkip = lowerBound >>> l;

		long position = zerosToSkip == 0 ? 0 : selectUpperZero.selectZero(zerosToSkip - 1) + 1;
		long rank = position - zerosToSkip;

		long lowerBitsOffset = rank * l;
		final long lowerBoundLowerBits = lowerBound & lowerBitsMask;
		final int m = Long.SIZE - l;
		for (;;) {
			if ((upperBits[word(position)] & 1L << position) == 0) return rank;

			final int startWord = word(lowerBitsOffset);
			final int startBit = bit(lowerBitsOffset);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit > m) lower |= lowerBits[startWord + 1] << -startBit;
			lower &= lowerBitsMask;
			if (lower > lowerBoundLowerBits) return rank;

			position++;
			rank++;
			lowerBitsOffset += l;
		}
	}

	/**
	 * Returns the last value of the sequence that is less than the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param upperBound a strict upper bound on the returned value.
	 * @return the last value of the sequence that is less than {@code upperBound}, or
	 *         {@link Long#MIN_VALUE} if no such value exists.
	 * @see #weakPredecessor(long)
	 */

	public long predecessor(final long upperBound) {
		if (upperBound <= firstElement) return Long.MIN_VALUE;
		if (upperBound > lastElement) {
			currentIndex = length - 1;
			return lastElement;
		}
		return predecessorUnsafe(upperBound);
	}

	/**
	 * Returns the last value of the sequence that is less than the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * <p>
	 * This method is slightly faster than {@link #strictSuccessor(long)} as it does not check its
	 * argument.
	 *
	 * @param upperBound a nonnegative strict upper bound on the returned value, smaller than or equal
	 *            to the last element of the sequence plus one.
	 * @return the last value of the sequence that is less than {@code upperBound}.
	 * @see #weakPredecessor(long)
	 * @see #predecessor(long)
	 */

	public long predecessorUnsafe(final long upperBound) {
		assert upperBound >= 0;
		assert upperBound <= lastElement + 1;
		final long zerosToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zerosToSkip) - 1;
		long rank = position - zerosToSkip;

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
			if ((upperBits[word(position)] & 1L << position) == 0) {
				int zeros = (int)(position | -Long.SIZE); // == - (Long.SIZE - (position % Long.SIZE))
				int curr = word(position);
				long window = upperBits[curr] & ~(-1L << position);
				while (window == 0) {
					window = upperBits[--curr];
					zeros += Long.SIZE;
				}
				currentIndex = rank;
				return position - zeros - Long.numberOfLeadingZeros(window) - rank - 1 << l | lower;
			}

			if (lower < upperBoundLowerBits) {
				currentIndex = rank;
				return position - rank << l | lower;
			}

			lowerBitsOffset -= l;
			position--;
			rank--;
		}
	}

	/**
	 * Returns the index of the last value of the sequence that is less than the provided bound.
	 *
	 * <p>
	 * This method is significantly faster than {@link #predecessor(long)}, as it does not have to
	 * compute the actual predecessor.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param upperBound an upper bound on the returned value.
	 * @return the index of the last value of the sequence that is less than {@code upperBound}, or
	 *         &minus;1 if no such value exists.
	 * @see #predecessor(long)
	 */

	public long predecessorIndex(final long upperBound) {
		if (upperBound <= firstElement) return -1;
		if (upperBound > lastElement) return length - 1;
		return predecessorIndexUnsafe(upperBound);
	}


	/**
	 * Returns the index of the last value of the sequence that is less than the provided bound.
	 *
	 * <p>
	 * This method is significantly faster than {@link #predecessor(long)}, as it does not have to
	 * compute the actual predecessor.
	 *
	 * <p>
	 * This method is slightly faster than {@link #predecessorIndex(long)} as it does not check its
	 * argument.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param upperBound a nonnegative strict upper bound, smaller than or equal to the last element of
	 *            the sequence plus one.
	 * @return the index of the last value of the sequence that is less than {@code upperBound};
	 *         behavior is undefined if no such element exists.
	 * @see #predecessor(long)
	 */

	public long predecessorIndexUnsafe(final long upperBound) {
		final long zerosToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zerosToSkip) - 1;
		long rank = position - zerosToSkip;

		long lowerBitsOffset = rank * l;
		long lower;
		final long upperBoundLowerBits = upperBound & lowerBitsMask;
		final int m = Long.SIZE - l;
		for (;;) {
			if ((upperBits[word(position)] & 1L << position) == 0) return rank;

			final int startWord = word(lowerBitsOffset);
			final int startBit = bit(lowerBitsOffset);
			lower = lowerBits[startWord] >>> startBit;
			if (startBit > m) lower |= lowerBits[startWord + 1] << -startBit;
			lower &= lowerBitsMask;

			if (lower < upperBoundLowerBits) return rank;

			rank--;
			position--;
			lowerBitsOffset -= l;
		}
	}

	/**
	 * Returns the last value of the sequence that is less than or equal to the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * @param upperBound an upper bound on the returned value.
	 * @return the last value of the sequence that is less than or equal to {@code upperBound}, or
	 *         {@link Long#MIN_VALUE} if no such value exists.
	 * @see #predecessor(long)
	 */

	public long weakPredecessor(final long upperBound) {
		if (upperBound < firstElement) return Long.MIN_VALUE;
		if (upperBound >= lastElement) {
			currentIndex = length - 1;
			return lastElement;
		}
		return weakPredecessorUnsafe(upperBound);
	}

	/**
	 * Returns the last value of the sequence that is less than or equal to the provided bound.
	 *
	 * <p>
	 * If such an element exists, its position in the sequence can be retrieved using {@link #index()}.
	 *
	 * <p>
	 * This method is slightly faster than {@link #weakPredecessor(long)} as it does not check its
	 * argument.
	 *
	 * @param upperBound a nonnegative strict upper bound on the returned value, smaller than or equal
	 *            to the last element of the sequence.
	 * @return the last value of the sequence that is less than or equal to {@code upperBound}; behavior
	 *         is undefined if no such element exists or if {@code upperBound} is out of range.
	 * @see #predecessor(long)
	 */

	public long weakPredecessorUnsafe(final long upperBound) {
		final long zerosToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zerosToSkip) - 1;
		long rank = position - zerosToSkip;

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

			if ((upperBits[word(position)] & 1L << position) == 0) {
				int zeros = (int)(position | -Long.SIZE); // == - (Long.SIZE - (position % Long.SIZE))
				int curr = word(position);
				long window = upperBits[curr] & ~(-1L << position);
				while (window == 0) {
					window = upperBits[--curr];
					zeros += Long.SIZE;
				}
				currentIndex = rank;
				return position - zeros - Long.numberOfLeadingZeros(window) - rank - 1 << l | lower;
			}

			if (lower <= upperBoundLowerBits) {
				currentIndex = rank;
				return position - rank << l | lower;
			}

			rank--;
			lowerBitsOffset -= l;
			position--;
		}
	}

	/**
	 * Returns the index of the last value of the sequence that is less than or equal to the provided
	 * bound.
	 *
	 * <p>
	 * This method is significantly faster than {@link #weakPredecessor(long)}, as it does not have to
	 * compute the actual weak predecessor.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param upperBound an upper bound on the returned value.
	 * @return the index of the last value of the sequence that is less than or equal to
	 *         {@code upperBound}, or {@link Long#MIN_VALUE} if no such value exists.
	 * @see #weakPredecessor(long)
	 */

	public long weakPredecessorIndex(final long upperBound) {
		if (upperBound < firstElement) return Long.MIN_VALUE;
		if (upperBound >= lastElement) return length - 1;
		return weakPredecessorIndexUnsafe(upperBound);
	}

	/**
	 * Returns the index of the last value of the sequence that is less than or equal to the provided
	 * bound.
	 *
	 * <p>
	 * This method is significantly faster than {@link #weakPredecessor(long)}, as it does not have to
	 * compute the actual weak predecessor.
	 *
	 * <p>
	 * This method is slightly faster than {@link #weakPredecessorIndex(long)} as it does not check its
	 * argument.
	 *
	 * <p>
	 * Note that this method does not change the return value of {@link #index()}.
	 *
	 * @param upperBound a nonnegative upper bound, smaller than or equal to the last element of the
	 *            sequence.
	 * @return the index of the last value of the sequence that is less than or equal to
	 *         {@code upperBound}; behavior is undefined if no such element exists.
	 * @see #weakPredecessor(long)
	 */

	public long weakPredecessorIndexUnsafe(final long upperBound) {
		assert upperBound >= firstElement;
		assert upperBound <= lastElement;
		final long zerosToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zerosToSkip) - 1;
		long rank = position - zerosToSkip;

		long lowerBitsOffset = rank * l;
		long lower;
		final long upperBoundLowerBits = upperBound & lowerBitsMask;
		final int m = Long.SIZE - l;
		for (;;) {
			if ((upperBits[word(position)] & 1L << position) == 0) return rank;
			final int startWord = word(lowerBitsOffset);
			final int startBit = bit(lowerBitsOffset);
			lower = lowerBits[startWord] >>> startBit;
			if (startBit > m) lower |= lowerBits[startWord + 1] << -startBit;
			lower &= lowerBitsMask;

			if (lower <= upperBoundLowerBits) return rank;

			rank--;
			position--;
			lowerBitsOffset -= l;
		}
	}


	/**
	 * Returns the index of the first occurrence of the specified element in the sequence, or -1 if the
	 * element does not belong to the sequence.
	 *
	 * <p>
	 * This method does not change the value returned by {@link #index()}.
	 *
	 * @param x a long.
	 * @return the position of {@code x} in the sequence, or &minus;1 if {@code x} does not belong to
	 *         the sequence.
	 */
	@Override
	public long indexOf(final long x) {
		if (x < firstElement || x > lastElement) return -1;
		return indexOfUnsafe(x);
	}

	/**
	 * Returns the index of the first occurrence of the specified element in the sequence, or -1 if the
	 * element does not belong to the sequence.
	 *
	 * <p>
	 * This method is slightly faster than {@link #indexOf(long)} as it does not check its argument.
	 *
	 * <p>
	 * This method does not change the value returned by {@link #index()}.
	 *
	 * @param x a nonnegative long smaller than or equal to the last element of the sequence.
	 * @return the position of {@code x} in the sequence, or &minus;1 if {@code x} does not belong to
	 *         the sequence; behavior is undefined of {@code x} is out of range.
	 */
	public long indexOfUnsafe(final long x) {
		final long zerosToSkip = x >>> l;
		final long position = zerosToSkip == 0 ? 0 : selectUpperZero.selectZero(zerosToSkip - 1) + 1;
		int curr = word(position);
		long window = upperBits[curr];
		window &= -1L << position;
		long rank = position - zerosToSkip;

		long lowerBitsOffset = rank * l;
		final int m = Long.SIZE - l;
		for (;;) {
			while (window == 0) window = upperBits[++curr];
			final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - rank;

			final int startWord = word(lowerBitsOffset);
			final int startBit = bit(lowerBitsOffset);
			final long lower = lowerBits[startWord] >>> startBit;
			final long v = upperBits << l | (startBit <= m ? lower : lower | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
			if (v >= x) return v == x ? rank : -1;

			window &= window - 1;
			rank++;
			lowerBitsOffset += l;
		}
	}

	/**
	 * Returns true if the sequence contains the specified element.
	 *
	 * <p>
	 * This method does not change the value returned by {@link #index()}. Use {@link #indexOf(long)} if
	 * you need to retrieve the position of an element.
	 *
	 * @param x a long.
	 * @return true if the sequence contains {@code x}.
	 */

	@Override
	public boolean contains(final long x) {
		return indexOf(x) != -1;
	}

	/**
	 * Returns the index realizing the last value returned by {@link #predecessor(long)},
	 * {@link #weakPredecessor(long)}, {@link #successor(long)}, and {@link #strictSuccessor(long)}.
	 *
	 * <p>
	 * Usage of this method makes instances of this class not thread safe, as the return value is cached
	 * internally.
	 *
	 * @return the index of the element realizing the last value returned by {@link #predecessor(long)},
	 *         {@link #weakPredecessor(long)}, {@link #successor(long)}, and
	 *         {@link #strictSuccessor(long)}, or -1 if no such method has ever been called.
	 */
	public long index() {
		return currentIndex;
	}

	/**
	 * An list iterator over the values of this {@link EliasFanoIndexedMonotoneLongBigList}
	 *
	 * <p>
	 * Besides the features of an
	 * {@link it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList.EliasFanoMonotoneLongBigListIterator
	 * EliasFanoMonotoneLongBigListIterator}, instance of this class provide a {@link #skipTo(long)}
	 * method that implements the same fast logic of
	 * {@link EliasFanoIndexedMonotoneLongBigList#successor(long)}.
	 */
	public final class EliasFanoIndexedMonotoneLongBigListIterator extends EliasFanoMonotoneLongBigListIterator {
		/**
		 * Skipping more than this number of zeroes will unleash a full selection instead of a sequential
		 * scan.
		 */
		private final static int SKIPPING_THRESHOLD = 8;
		/** The last returned value. */
		protected long last = -1;

		private EliasFanoIndexedMonotoneLongBigListIterator(final long from) {
			super(from);
		}

		@Override
		public long nextLong() {
			return last = super.nextLong();
		}

		@Override
		public long nextLongUnsafe() {
			return last = super.nextLongUnsafe();
		}

		@Override
		public long previousLong() {
			return last = super.previousLong();
		}

		@Override
		public long previousLongUnsafe() {
			return last = super.previousLongUnsafe();
		}

		/**
		 * Moves this iterator to the first element greater than or equal to the provided bound.
		 *
		 * @param lowerBound a lower bound.
		 * @return the last element returned by {@link #nextLong()} or {@link #previousLong()} if it is
		 *         smaller than or equal to {@code lowerBound}, in which case this method is a no-op.
		 *         Otherwise, the first element among the ones that will be returned by {@link #nextLong()}
		 *         that is greater than or equal to {@code lowerBound}. The iterator will be positioned on
		 *         the element (i.e., the next call to {@link #nextLong()} will return the element). In
		 *         particular, {@link #nextIndex()} returns the index of the returned element in the list.
		 *         If no such element exists, this methods returns {@link Long#MAX_VALUE} and sets the
		 *         current index to the list length.
		 */
		public long skipTo(long lowerBound) {
			lowerBound &= ~(lowerBound >> Long.SIZE - 1);
			if (lowerBound <= last) return last;
			if (lowerBound > lastElement) {
				index = length;
				return Long.MAX_VALUE;
			}
			return skipToUnsafe(lowerBound);
		}

		/**
		 * Moves this iterator to the first element greater than or equal to the provided bound without
		 * checkint its argument.
		 *
		 * <p>
		 * This method is slightly faster than {@link #skipTo(long)} as it does not check its argument.
		 *
		 * @param lowerBound a nonnegative lower bound smaller than or equal to the last element of the
		 *            sequence plus one.
		 * @return the last element returned by {@link #nextLong()} or {@link #previousLong()} if it is
		 *         smaller than or equal to {@code lowerBound}, in which case this method is a no-op.
		 *         Otherwise, the first element among the ones that will be returned by {@link #nextLong()}
		 *         that is greater than or equal to {@code lowerBound}. The iterator will be positioned on
		 *         the element (i.e., the next call to {@link #nextLong()} will return the element). In
		 *         particular, {@link #nextIndex()} returns the index of the returned element in the list.
		 *         If no such element exists, this methods returns the last element of the list plus one and
		 *         sets the current index to the list length. Behavior is undefined if {@code lowerbound} is
		 *         out of range.
		 */
		public long skipToUnsafe(final long lowerBound) {
			if (lowerBound <= last) return last;

			final long zerosToSkip = lowerBound >>> l;
			// We want this shift to be arithmetic so that last = -1 works
			if (zerosToSkip - (last >> l) >= SKIPPING_THRESHOLD) {
				final long position = selectUpperZero.selectZero(zerosToSkip - 1);
				word = word(position);
				window = upperBits[word];
				window &= -1L << position;
				index = position - zerosToSkip + 1;
				lowerBitsPosition = index * l;
			}

			final int m = Long.SIZE - l;
			for (;;) {
				while (window == 0) window = upperBits[++word];
				final long upperBits = bits(word) + Long.numberOfTrailingZeros(window) - index;

				final int startWord = word(lowerBitsPosition);
				final int startBit = bit(lowerBitsPosition);
				final long lower = lowerBits[startWord] >>> startBit;
				final long v = upperBits << l | (startBit <= m ? lower : lower | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
				if (v >= lowerBound) return v;

				window &= window - 1;
				index++;
				lowerBitsPosition += l;
			}
		}
	}

	@Override
	public EliasFanoIndexedMonotoneLongBigListIterator listIterator(final long from) {
		return new EliasFanoIndexedMonotoneLongBigListIterator(from);
	}

	@Override
	public EliasFanoIndexedMonotoneLongBigListIterator listIterator() {
		return new EliasFanoIndexedMonotoneLongBigListIterator(0);
	}

	@Override
	public EliasFanoIndexedMonotoneLongBigListIterator iterator() {
		return listIterator();
	}
}
