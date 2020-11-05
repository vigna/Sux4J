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
	private static final long serialVersionUID = 0L;

	/** The select structure used to extract the upper bits. */
	protected final SimpleSelectZero selectUpperZero = new SimpleSelectZero(selectUpper.bitVector());
	/** The upper bits as a long array. */
	protected final long[] upperBits = selectUpper.bitVector().bits();
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
	 */

	public long successor(final long lowerBound) {
		if (lowerBound <= firstElement) {
			currentIndex = 0;
			return firstElement;
		}
		if (lowerBound > lastElement) return Long.MAX_VALUE;
		final long zeroesToSkip = lowerBound >>> l;
		final long position = zeroesToSkip == 0 ? 0 : selectUpperZero.selectZero(zeroesToSkip - 1);
		int curr = word(position);
		long window = upperBits[curr];
		window &= -1L << position;
		long rank = zeroesToSkip == 0 ? 0 : position - zeroesToSkip + 1;

		if (l == 0) {
			while (window == 0) window = upperBits[++curr];
			final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - rank;
			currentIndex = rank;
			return upperBits;
		} else {
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

	public long successorIndex(final long lowerBound) {
		if (lowerBound <= firstElement) return 0;
		if (lowerBound > lastElement) return Long.MAX_VALUE;
		final long zeroesToSkip = lowerBound >>> l;
		long position = zeroesToSkip == 0 ? 0 : selectUpperZero.selectZero(zeroesToSkip - 1) + 1;
		long rank = position - zeroesToSkip;

		if (l == 0) return rank;
		else {
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
	 */

	public long strictSuccessor(final long lowerBound) {
		if (lowerBound < firstElement) {
			currentIndex = 0;
			return firstElement;
		}
		if (lowerBound >= lastElement) return Long.MAX_VALUE;
		final long zeroesToSkip = lowerBound >>> l;
		if (l == 0) {
			final long position = selectUpperZero.selectZero(zeroesToSkip);
			int curr = word(position);
			long window = upperBits[curr];
			window &= -1L << position;
			final long rank = position - zeroesToSkip;

			while (window == 0) window = upperBits[++curr];
			final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - rank;
			currentIndex = rank;
			return upperBits;
		} else {
			final long position = zeroesToSkip == 0 ? 0 : selectUpperZero.selectZero(zeroesToSkip - 1);
			int curr = word(position);
			long window = upperBits[curr];
			window &= -1L << position;
			long rank = zeroesToSkip == 0 ? 0 : position - zeroesToSkip + 1;

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
	 */

	public long strictSuccessorIndex(final long lowerBound) {
		if (lowerBound < firstElement) return 0;
		if (lowerBound >= lastElement) return Long.MAX_VALUE;
		final long zeroesToSkip = lowerBound >>> l;

		if (l == 0) {
			final long position = selectUpperZero.selectZero(zeroesToSkip);
			return position - zeroesToSkip;
		} else {
			long position = zeroesToSkip == 0 ? 0 : selectUpperZero.selectZero(zeroesToSkip - 1) + 1;
			long rank = position - zeroesToSkip;

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
	 *         {@link Long#MIN_VALUE} if no such value exists.
	 * @see #predecessor(long)
	 */

	public long predecessorIndex(final long upperBound) {
		if (upperBound <= firstElement) return Long.MIN_VALUE;
		if (upperBound > lastElement) return length - 1;

		final long zeroesToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zeroesToSkip) - 1;
		long rank = position - zeroesToSkip;

		if (l == 0) {
			for (;;) {
				if ((upperBits[word(position)] & 1L << position) == 0) return rank;
				position--;
				rank--;
			}
		} else {
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

		final long zeroesToSkip = upperBound >>> l;
		long position = selectUpperZero.selectZero(zeroesToSkip) - 1;
		long rank = position - zeroesToSkip;

		if (l == 0) return rank;

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
	 */
	@Override
	public long indexOf(final long x) {
		if (x < firstElement || x > lastElement) return -1;
		final long zeroesToSkip = x >>> l;
		final long position = zeroesToSkip == 0 ? 0 : selectUpperZero.selectZero(zeroesToSkip - 1);
		int curr = word(position);
		long window = upperBits[curr];
		window &= -1L << position;
		long rank = zeroesToSkip == 0 ? 0 : position - zeroesToSkip + 1;

		if (l == 0) {
			while (window == 0) window = upperBits[++curr];
			final long upperBits = bits(curr) + Long.numberOfTrailingZeros(window) - rank;
			return upperBits == x ? rank : -1;
		} else {
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
}
