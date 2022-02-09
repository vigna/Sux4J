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

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongBigListIterator;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.sux4j.bits.SimpleSelect;

/**
 * An implementation of Elias&ndash;Fano's representation of monotone sequences; an element occupies
 * a number of bits bounded by two plus the logarithm of the average gap.
 *
 * <p>
 * Instances of this class represent in a highly compacted form a nondecreasing sequence of natural
 * numbers. Instances are built by providing either an iterator returning the (nondecreasing)
 * sequence, or an {@linkplain Iterable iterable object} that provides such an iterator. In the
 * first case, you must also provide in advance the number of elements that will be returned and an
 * upper bound to their values (see below), and at the end of the construction the iterator will be
 * exhausted.
 *
 * <p>
 * An additional {@linkplain #get(long, long[], int, int) bulk method} makes it possible to extract
 * several consecutive entries at high speed, and {@link #getDelta(long)} computes directly the
 * difference between two consecutive elements. Moreover, the
 * {@link EliasFanoMonotoneLongBigListIterator#nextLong() nextLong()} method of an
 * {@linkplain #listIterator(long) iterator} will read read consecutive data much faster than
 * repeated calls to {@link #getLong(long)}.
 *
 * <p>
 * Methods to not usually perform bound checks on the arguments. Bounds checks can be enabled,
 * however, by enabling assertions.
 *
 * <p>
 * This class is thread safe.
 *
 * <h2>Implementation details</h2>
 *
 * <p>
 * Given a monotone sequence 0 &le; <var>x</var><sub>0</sub> &le; <var>x</var><sub>1</sub> &le;
 * &hellip; &le; <var>x</var><sub><var>n</var>&nbsp;&minus;&nbsp;1</sub> &lt; <var>u</var>, where
 * <var>u</var> is a given upper bound (the size of the universe), the Elias&ndash;Fano
 * representation makes it possible to store it using at most 2 + log(<var>u</var>/<var>n</var>)
 * bits per element, which is very close to the information-theoretical lower bound &#x2248; log
 * <i>e</i> + log(<var>u</var>/<var>n</var>). A typical example is a list of pointer into records of
 * a large file: instead of using, for each pointer, a number of bit sufficient to express the
 * length of the file, the Elias&ndash;Fano representation makes it possible to use, for each
 * pointer, a number of bits roughly equal to the logarithm of the average length of a record. The
 * representation was introduced in Peter Elias, &ldquo;Efficient storage and retrieval by content
 * and address of static files&rdquo;, <i>J. Assoc. Comput. Mach.</i>, 21(2):246&minus;260, 1974,
 * and also independently by Robert Fano, &ldquo;On the number of bits required to implement an
 * associative memory&rdquo;, Memorandum 61, Computer Structures Group, Project MAC, MIT, Cambridge,
 * Mass., n.d., 1971.
 *
 * <p>
 * The elements of the sequence are recorded by storing separately the lower <var>s</var> =
 * &lfloor;log(<var>u</var>/<var>n</var>)&rfloor; bits and the remaining upper bits. The lower bits
 * are stored contiguously, whereas the upper bits are stored in an array of <var>n</var> +
 * <var>u</var> / 2<sup><var>s</var></sup> bits by setting, for each 0 &le; <var>i</var> &lt;
 * <var>n</var>, the bit of index <var>x</var><sub><var>i</var></sub> / 2<sup><var>s</var></sup> +
 * <var>i</var>; the value can then be recovered by selecting the <var>i</var>-th bit of the
 * resulting bit array and subtracting <var>i</var> (note that this will work because the upper bits
 * are nondecreasing).
 *
 * <p>
 * This implementation uses {@link SimpleSelect} to support selection inside the upper-bits array,
 * and exploits {@link SimpleSelect#select(long, long[], int, int)} to implement
 * {@link #get(long, long[], int, int)}.
 *
 * @see EliasFanoIndexedMonotoneLongBigList
 */

public class EliasFanoMonotoneLongBigList extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 4L;

	/** The length of the sequence. */
	protected final long length;
	/** The number of lower bits. */
	protected final int l;
	/** The upper bits, stored as unary gaps. */
	protected transient long[] upperBits;
	/** The list of lower bits of each element, stored explicitly. */
	protected long[] lowerBits;
	/** The select structure used to extract the upper bits. */
	protected final SimpleSelect selectUpper;
	/** The mask for the lower bits. */
	protected final long lowerBitsMask;


	/** Returns true if this class can accommodate a list with the given number of elements and upper bound.
	 *
	 * @param length the length of the list.
	 * @param upperBound a strict upper bound to the values of the list.
	 * @return true if this class can accommodate a list with the given number of elements and upper bound.
	 */
	public static boolean fits(final long length, final long upperBound) {
		final int l = length == 0 ? 0 : Math.max(0, Fast.mostSignificantBit(upperBound / length));
		return length * l < bits(Arrays.MAX_ARRAY_SIZE);
	}

	protected EliasFanoMonotoneLongBigList(final long length, final int l, final long[] upperBits, final long[] lowerBits, final SimpleSelect selectUpper) {
		this.length = length;
		this.l = l;
		this.upperBits = upperBits;
		this.lowerBits = lowerBits;
		this.selectUpper = selectUpper;
		this.lowerBitsMask = (1L << l) - 1;
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */
	public EliasFanoMonotoneLongBigList(final IntIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */
	public EliasFanoMonotoneLongBigList(final ShortIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */
	public EliasFanoMonotoneLongBigList(final ByteIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by the given
	 * {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object returning nondecreasing natural numbers.
	 */

	public EliasFanoMonotoneLongBigList(final LongIterable list) {
		this(computeParameters(list.iterator()), list.iterator());
	}

	/**
	 * Computes the number of elements and the last element returned by the given iterator.
	 *
	 *
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 * @return a two-element array of longs containing the number of elements returned by the iterator
	 *         and the last returned element, respectively.
	 */
	private static long[] computeParameters(final LongIterator iterator) {
		long v = -1, prev = -1, c = 0;
		while(iterator.hasNext()) {
			v = iterator.nextLong();
			if (prev > v) throw new IllegalArgumentException("The list of values is not monotone: " + prev + " > " + v);
			prev = v;
			c++;
		}

		return new long[] { c, v };
	}


	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that the
	 * overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a strict upper bound to the values returned by <code>iterator</code> (note that
	 *            it used to be non-strict).
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final ByteIterator iterator) {
		this(new long[] { n, upperBound }, LongIterators.wrap(iterator));
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that the
	 * overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a strict upper bound to the values returned by <code>iterator</code> (note that
	 *            it used to be non-strict).
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final ShortIterator iterator) {
		this(new long[] { n, upperBound }, LongIterators.wrap(iterator));
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that the
	 * overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a strict upper bound to the values returned by <code>iterator</code> (note that
	 *            it used to be non-strict).
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final IntIterator iterator) {
		this(new long[] { n, upperBound }, LongIterators.wrap(iterator));
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that the
	 * overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is particularly useful if the elements of the iterator are provided by some
	 * sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a strict upper bound to the values returned by <code>iterator</code> (note that
	 *            it used to be non-strict).
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final LongIterator iterator) {
		this(new long[] { n, upperBound }, iterator);
	}

	/**
	 * Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that the
	 * overall number of elements and an upper bound are provided, too.
	 *
	 * <p>
	 * This constructor is used only internally, to work around the usual problems caused by the
	 * obligation to call <code>this()</code> before anything else.
	 *
	 * @param a an array containing the number of elements returned by <code>iterator</code> and a
	 *            strict upper bound to the values returned by <code>iterator</code> (note that it used
	 *            to be non-strict).
	 * @param iterator an iterator returning nondecreasing natural numbers.
	 */
	protected EliasFanoMonotoneLongBigList(final long[] a, final LongIterator iterator) {
		length = a[0];
		long v = -1;
		final long upperBound = a[1];
		l = length == 0 ? 0 : Math.max(0, Fast.mostSignificantBit(upperBound / length));
		lowerBitsMask = (1L << l) - 1;
		final long lowerBitsMask = (1L << l) - 1;
		final LongArrayBitVector lowerBitsVector = LongArrayBitVector.getInstance();
		final LongBigList lowerBitsList = lowerBitsVector.asLongBigList(l);
		lowerBitsList.size(length + 1);
		final BitVector upperBits = LongArrayBitVector.getInstance().length(length + (upperBound >>> l) + 2);
		long last = -1;
		long finalUpperBound = upperBound;

		for(long i = 0; i < length; i++) {
			v = iterator.nextLong();
			if (v > upperBound) throw new IllegalArgumentException("Too large value: " + v + " > " + upperBound);
			// Correct for users of previous versions, where upperBound was non-strict
			if (v == finalUpperBound) upperBits.length(length + (++finalUpperBound >>> l) + 2);
			if (v < last) throw new IllegalArgumentException("Values are not nondecreasing: " + v + " < " + last);
			if (l != 0) lowerBitsList.set(i, v & lowerBitsMask);
			upperBits.set((v >>> l) + i);
			last = v;
		}

		// Sentinel value; if the user provided a non-strict upper bound, finalUpperBound will be fixed in
		// the loop above.
		if (l != 0) lowerBitsList.set(length, finalUpperBound & lowerBitsMask);
		upperBits.set((finalUpperBound >>> l) + length);

		if (iterator.hasNext()) throw new IllegalArgumentException("There are more than " + length + " values in the provided iterator");
		// The initialization for l == 0 avoids tests for l == 0 throughout the code.
		this.lowerBits = l == 0 ? new long[1] : lowerBitsVector.bits();
		this.upperBits = upperBits.bits();
		selectUpper = new SimpleSelect(upperBits);
	}


	public long numBits() {
		return selectUpper.numBits() + selectUpper.bitVector().length() + bits(lowerBits.length);
	}

	/**
	 * Returns the element at the specified position.
	 *
	 * @param index a position in the list.
	 * @return the element at the specified position; if {@code index} is out of bounds, behavior is
	 *         undefined.
	 */
	@Override
	public long getLong(final long index) {
		assert index >= 0;
		assert index < length;

		final int l = this.l;
		final long upperBits = selectUpper.select(index) - index;

		final long position = index * l;

		final int startWord = word(position);
		final int startBit = bit(position);
		final long result = lowerBits[startWord] >>> startBit;
		return upperBits << l | (startBit + l <= Long.SIZE ? result : result | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
	}

	/**
	 * Returns the difference between two consecutive elements of the sequence.
	 *
	 * @param index the index of an element (smaller then {@link #size64()} - 1).
	 * @return the difference between the element of position {@code index + 1} and that of position
	 *         {@code index}; if {@code index} is out of bounds, behavior is undefined.
	 * @see #get(long, long[])
	 */
	public long getDelta(long index) {
		assert index >= 0;
		assert index < length - 1;

		final long[] dest = new long[2];
		selectUpper.select(index, dest, 0, 2);

		final int l = this.l;
		final long[] lowerBits = this.lowerBits;

		long position = index * l;
		int startWord = word(position);
		int startBit = bit(position);
		long first = lowerBits[startWord] >>> startBit;
		first = dest[0] - index++ << l | (startBit + l <= Long.SIZE ? first : first | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
		position += l;
		startWord = word(position);
		startBit = bit(position);
		long second = lowerBits[startWord] >>> startBit;
		second = dest[1] - index << l | (startBit + l <= Long.SIZE ? second : second | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
		return second - first;
	}

	/**
	 * Extracts a number of consecutive entries into a given array fragment.
	 *
	 * @param index the index of the first entry returned.
	 * @param dest the destination array; it will be filled with {@code length} consecutive entries
	 *            starting at position {@code offset}; must be of length greater than {@code offset}.
	 * @param offset the first position written in {@code dest}.
	 * @param length the number of elements written in {@code dest} starting at {@code offset}.
	 * @return {@code dest}; if the arguments are out of bounds, behavior is undefined.
	 * @see #get(long, long[])
	 */
	public long[] get(long index, final long dest[], final int offset, final int length) {
		assert index >= 0;
		assert index < this.length;
		assert offset >= 0;
		assert offset < dest.length;
		assert length >= 0;
		assert offset + length <= dest.length;

		selectUpper.select(index, dest, offset, length);

		final int l = this.l;
		final long lowerBitsMask = this.lowerBitsMask;
		final long[] lowerBits = this.lowerBits;

		long position = index * l;
		for (int i = 0; i < length; i++) {
			final int startWord = word(position);
			final int startBit = bit(position);
			final long result = lowerBits[startWord] >>> startBit;
			dest[offset + i] = dest[offset + i] - index++ << l | (startBit + l <= Long.SIZE ? result : result | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
			position += l;
		}

		return dest;
	}

	/**
	 * Extracts a number of consecutive entries into a given array.
	 *
	 * @param index the index of the first entry returned.
	 * @param dest the destination array, of nonzero length; it will be filled with consecutive entries.
	 * @return {@code dest}; if {@code index} is out of bounds or {@code dest} has length zero, behavior
	 *         is undefined.
	 * @see #get(long, long[], int, int)
	 */
	public long[] get(final long index, final long dest[]) {
		return get(index, dest, 0, dest.length);
	}

	/**
	 * A list iterator over the values of this {@link EliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * {@linkplain #nextLong() Forward iteration} will be faster than iterated calls to
	 * {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * <p>
	 * Additional <em>unsafe</em> methods {@link #nextLongUnsafe()} and {@link #previousLongUnsafe()}
	 * iterate without checking for the existence of a next element.
	 */
	public class EliasFanoMonotoneLongBigListIterator implements LongBigListIterator {
		/** The index of the next element to return. */
		protected long index;
		/** The current word in the array of upper bits. */
		protected int word;
		/** The current window. */
		protected long window;
		/** The current position in the array of lower bits. */
		protected long lowerBitsPosition;

		protected EliasFanoMonotoneLongBigListIterator(final long from) {
			index = from;
			final long position = selectUpper.select(from);
			window = upperBits[word = word(position)] & -1L << position;
			lowerBitsPosition = index * l;
		}

		private long getNextUpperBits() {
			while (window == 0) window = upperBits[++word];
			final long upperBits = bits(word) + Long.numberOfTrailingZeros(window) - index++;
			window &= window - 1;
			return upperBits;
		}

		@Override
		public long previousIndex() {
			return index - 1;
		}

		@Override
		public long nextIndex() {
			return index;
		}

		@Override
		public boolean hasPrevious() {
			return index > 0;
		}

		@Override
		public boolean hasNext() {
			return index < length;
		}

		@Override
		public long nextLong() {
			if (!hasNext()) throw new NoSuchElementException();
			return nextLongUnsafe();
		}

		/**
		 * Returns the same element as {@link #nextLong()}, if {@link #hasNext()} is true; otherwise,
		 * behavior is undefined.
		 *
		 * @return the same element as {@link #nextLong()}, if {@link #hasNext()} is true; otherwise,
		 *         behavior is undefined.
		 */
		public long nextLongUnsafe() {
			final int l = EliasFanoMonotoneLongBigList.this.l;
			final int startWord = word(lowerBitsPosition);
			final int startBit = bit(lowerBitsPosition);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits[startWord + 1] << -startBit;
			lowerBitsPosition += l;
			return getNextUpperBits() << l | lower & lowerBitsMask;
		}

		@Override
		public long previousLong() {
			if (!hasPrevious()) throw new NoSuchElementException();
			return previousLongUnsafe();
		}

		/**
		 * Returns the same element as {@link #previousLong()}, if {@link #hasPrevious()} is true;
		 * otherwise, behavior is undefined.
		 *
		 * @return the same element as {@link #previousLong()}, if {@link #hasPrevious()} is true;
		 *         otherwise, behavior is undefined.
		 */
		public long previousLongUnsafe() {
			final int l = EliasFanoMonotoneLongBigList.this.l;
			--index;
			final long position = selectUpper.select(index);
			window = upperBits[word = word(position)] & -1L << position;

			lowerBitsPosition = index * l;
			final int startWord = word(lowerBitsPosition);
			final int startBit = bit(lowerBitsPosition);
			long lower = lowerBits[startWord] >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits[startWord + 1] << -startBit;
			return (position - index) << l | lower & lowerBitsMask;
		}
	}

	/**
	 * Returns a list iterator over the values of this {@link EliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * @param from the starting position in the sequence.
	 * @return a list iterator over the values of this {@link EliasFanoMonotoneLongBigList}.
	 * @see EliasFanoMonotoneLongBigListIterator
	 */
	@Override
	public EliasFanoMonotoneLongBigListIterator listIterator(final long from) {
		return new EliasFanoMonotoneLongBigListIterator(from);
	}

	/**
	 * Returns a list iterator over the values of this {@link EliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * @return a list iterator over the values of this {@link EliasFanoMonotoneLongBigList}.
	 * @see EliasFanoMonotoneLongBigListIterator
	 */
	@Override
	public EliasFanoMonotoneLongBigListIterator listIterator() {
		return new EliasFanoMonotoneLongBigListIterator(0);
	}

	/**
	 * Returns a list iterator over the values of this {@link EliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link EliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * @return a list iterator over the values of this {@link EliasFanoMonotoneLongBigList}.
	 * @see EliasFanoMonotoneLongBigListIterator
	 */
	@Override
	public EliasFanoMonotoneLongBigListIterator iterator() {
		return listIterator();
	}

	@Override
	public long size64() {
		return length;
	}

	public void dump(final String basename) throws IOException {
		dump(basename, ByteOrder.nativeOrder());
	}

	public void dump(final String basename, final ByteOrder byteOrder) throws IOException {
		BinIO.storeObject(new MappedEliasFanoMonotoneLongBigList(length, l, upperBits, selectUpper, byteOrder == ByteOrder.LITTLE_ENDIAN), basename + MappedEliasFanoMonotoneLongBigList.OBJECT_EXTENSION);
		final FileChannel fileChannel = FileChannel.open(new File(basename + MappedEliasFanoMonotoneLongBigList.LOWER_BITS_EXTENSION).toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE);
		final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);
		byteBuffer.order(byteOrder);
		for (final long l : lowerBits) {
			byteBuffer.putLong(l);
			if (!byteBuffer.hasRemaining()) {
				byteBuffer.flip();
				fileChannel.write(byteBuffer);
				byteBuffer.clear();
			}
		}

		byteBuffer.flip();
		fileChannel.write(byteBuffer);

		fileChannel.close();
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		upperBits = selectUpper.bitVector().bits();
		// A fix that avoids bumping the serial id from 4L
		if (lowerBits.length == 0) lowerBits = new long[1];
	}
}
