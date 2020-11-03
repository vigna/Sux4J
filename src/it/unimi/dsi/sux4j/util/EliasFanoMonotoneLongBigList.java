/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
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
import static it.unimi.dsi.bits.LongArrayBitVector.word;

import java.io.Serializable;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.sux4j.bits.SimpleSelect;

/** An implementation of Elias&ndash;Fano's representation of monotone sequences; an element occupies a number of bits bounded by two plus the logarithm of the average gap.
 *
 * <p>Instances of this class represent in a highly compacted form a nondecreasing sequence of natural numbers. Instances
 * are built by providing either an iterator returning the (nondecreasing) sequence, or an {@linkplain Iterable iterable object} that
 * provides such an iterator. In the first case, you must also provide in advance the number of elements that will be returned and an upper bound to their
 * values (see below), and at the end of the construction the iterator will be exhausted.
 *
 * <p>An additional {@linkplain #get(long, long[], int, int) bulk method} makes it possible
 * to extract several consecutive entries at high speed, and {@link #getDelta(long)} computes
 * directly the difference between two consecutive elements.
 *
 * <h2>Implementation details</h2>
 *
 * <p>Given a (nondecreasing) monotone sequence
 * <var>x</var><sub>0</sub>, <var>x</var><sub>1</sub>,&hellip; , <var>x</var><sub><var>n</var> &minus; 1</sub>
 * of natural numbers smaller than <var>u</var>,
 * the Elias&ndash;Fano representation makes it possible to store it using
 * at most 2 + log(<var>u</var>/<var>n</var>) bits per element, which is very close
 * to the information-theoretical lower bound &#x2248; log <i>e</i> + log(<var>u</var>/<var>n</var>). A typical example
 * is a list of pointer into records of a large file: instead of using, for each pointer, a number of bit sufficient to express the length of
 * the file, the Elias&ndash;Fano representation makes it possible to use, for each pointer, a number of bits roughly equal to
 * the logarithm of the average length of a record. The representation was introduced in Peter Elias,
 * &ldquo;Efficient storage and retrieval by content and address of static files&rdquo;, <i>J. Assoc. Comput. Mach.</i>, 21(2):246&minus;260, 1974,
 * and also independently by Robert Fano, &ldquo;On the number of bits required to implement an associative memory&rdquo;,
 *  Memorandum 61, Computer Structures Group, Project MAC, MIT, Cambridge, Mass., n.d., 1971.
 *
 * <p>The elements of the sequence are recorded by storing separately
 * the lower <var>s</var> = &lfloor;log(<var>u</var>/<var>n</var>)&rfloor; bits and the remaining upper bits.
 * The lower bits are stored contiguously, whereas the upper bits are stored in an array
 * of <var>n</var> + <var>x</var><sub><var>n</var> &minus; 1</sub> / 2<sup><var>s</var></sup> bits by setting,
 * for each 0 &le; <var>i</var> &lt; <var>n</var>,
 * the bit of index <var>x</var><sub><var>i</var></sub> / 2<sup><var>s</var></sup> + <var>i</var>; the value can then be recovered
 * by selecting the <var>i</var>-th bit of the resulting bit array and subtracting <var>i</var> (note that this will
 * work because the upper bits are nondecreasing).
 *
 * <p>This implementation uses {@link SimpleSelect} to support selection inside the upper-bits array, and
 * exploits {@link SimpleSelect#select(long, long[], int, int)} to implement
 * {@link #get(long, long[], int, int)}.
 */

public class EliasFanoMonotoneLongBigList extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 4L;

	/** The length of the sequence. */
	protected final long length;
	/** The number of lower bits. */
	protected final int l;
	/** The list of lower bits of each element, stored explicitly. */
	protected final long[] lowerBits;
	/** The select structure used to extract the upper bits. */
	protected final SimpleSelect selectUpper;
	/** The mask for the lower bits. */
	protected final long lowerBitsMask;


	/** Returns true if this class can accommodate a list with the given number of elements and upper bound.
	 *
	 * @return true if this class can accommodate a list with the given number of elements and upper bound.
	 */
	public static boolean fits(final long length, final long upperBound) {
		final int l = length == 0 ? 0 : Math.max(0, Fast.mostSignificantBit(upperBound / length));
		return length * l < Long.SIZE * ((1L << 31) - 16);
	}

	protected EliasFanoMonotoneLongBigList(final long length, final int l, final long[] lowerBits, final SimpleSelect selectUpper) {
		this.length = length;
		this.l = l;
		this.lowerBits = lowerBits;
		this.selectUpper = selectUpper;
		this.lowerBitsMask = (1L << l) - 1;
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList(final IntIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList(final ShortIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList(final ByteIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */

	public EliasFanoMonotoneLongBigList(final LongIterable list) {
		this(computeParameters(list.iterator()), list.iterator());
	}

	/** Computes the number of elements and the last element returned by the given iterator.
	 *
	 *
	 * @param iterator an iterator.
	 * @return a two-element array of longs containing the number of elements returned by
	 * the iterator and the last returned element, respectively.
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


	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final ByteIterator iterator) {
		this(new long[] { n, upperBound }, LongIterators.wrap(iterator));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final ShortIterator iterator) {
		this(new long[] { n, upperBound }, LongIterators.wrap(iterator));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final IntIterator iterator) {
		this(new long[] { n, upperBound }, LongIterators.wrap(iterator));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>This constructor is particularly useful if the elements of the iterator are provided by
	 * some sequential source.
	 *
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound an upper bound to the values returned by <code>iterator</code> (note that it used to be
	 * a <em>strict</em> upper bound).
	 * @param iterator an iterator returning nondecreasing elements.
	 */
	public EliasFanoMonotoneLongBigList(final long n, final long upperBound, final LongIterator iterator) {
		this(new long[] { n, upperBound }, iterator);
	}

	/**  Creates an Elias&ndash;Fano representation of the values returned by an iterator, given that
	 * the overall number of elements and an upper bound are provided, too.
	 *
	 * <p>This constructor is used only internally, to work around the usual problems
	 * caused by the obligation to call <code>this()</code> before anything else.
	 *
	 * @param a an array containing the number of elements returned by <code>iterator</code> and
	 * a (strict) upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning nondecreasing elements.
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
		lowerBitsList.size(length);
		final BitVector upperBits = LongArrayBitVector.getInstance().length(length + (upperBound >>> l) + 1);
		long last = Long.MIN_VALUE;
		for(long i = 0; i < length; i++) {
			v = iterator.nextLong();
			if (v > upperBound) throw new IllegalArgumentException("Too large value: " + v + " > " + upperBound);
			if (v < last) throw new IllegalArgumentException("Values are not nondecreasing: " + v + " < " + last);
			if (l != 0) lowerBitsList.set(i, v & lowerBitsMask);
			upperBits.set((v >>> l) + i);
			last = v;
		}

		if (iterator.hasNext()) throw new IllegalArgumentException("There are more than " + length + " values in the provided iterator");
		this.lowerBits = lowerBitsVector.bits();
		selectUpper = new SimpleSelect(upperBits);
	}


	public long numBits() {
		return selectUpper.numBits() + selectUpper.bitVector().length() + lowerBits.length * (long)Long.SIZE;
	}

	@Override
	public long getLong(final long index) {
		final int l = this.l;
		if (index >= length) throw new IllegalArgumentException();
		final long upperBits = selectUpper.select(index) - index;
		if (l == 0) return upperBits;

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
	 * @return the difference between the element of position {@link index + 1} and that of position
	 *         {@link index}.
	 * @see #get(long, long[])
	 */
	public long getDelta(long index) {
		final long[] dest = new long[2];
		selectUpper.select(index, dest, 0, 2);

		if (l == 0) return dest[1] - dest[0] - 1;

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

	/** Extracts a number of consecutive entries into a given array fragment.
	 *
	 * @param index the index of the first entry returned.
	 * @param dest the destination array; it will be filled with {@code length} consecutive entries starting at position {@code offset}.
	 * @param offset the first position written in {@code dest}.
	 * @param length the number of elements written in {@code dest} starting at {@code offset}.
	 * @return {@code dest}
	 * @see #get(long, long[])
	 */
	public long[] get(long index, final long dest[], final int offset, final int length) {
		selectUpper.select(index, dest, offset, length);
		if (l == 0) for(int i = 0; i < length; i++) dest[offset + i] -= index++;
		else {
			long position = index * l;
			for(int i = 0; i < length; i++) {
				final int startWord = word(position);
				final int startBit = bit(position);
				final long result = lowerBits[startWord] >>> startBit;
				dest[offset + i] = dest[offset + i] - index++ << l | (startBit + l <= Long.SIZE ? result : result | lowerBits[startWord + 1] << -startBit) & lowerBitsMask;
				position += l;
			}
		}

		return dest;
	}

	/** Extracts a number of consecutive entries into a given array.
	 *
	 * @param index the index of the first entry returned.
	 * @param dest the destination array; it will be filled with consecutive entries.
	 * @return {@code dest}
	 * @see #get(long, long[], int, int)
	 */
	public long[] get(final long index, final long dest[]) {
		return get(index, dest, 0, dest.length);
	}

	@Override
	public long size64() {
		return length;
	}
}
