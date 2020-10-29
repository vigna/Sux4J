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

import java.io.Serializable;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortBigArrays;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.sux4j.bits.SimpleSelect;

/** An implementation of Elias&ndash;Fano's representation of monotone sequences with number of lower bits set fixed to 16.
 *
 * <p>Instances of this class behave like those of {@link EliasFanoMonotoneLongBigList}, but they use a big array of shorts
 * to store the lower bits, thus bypassing the 128Gb limit of a {@link LongArrayBitVector} instance.
 */

public class EliasFanoMonotoneLongBigList16 extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 4L;

	/** The length of the sequence. */
	protected final long length;
	/** The list of lower bits of each element, stored explicitly. */
	protected final short[][] lowerBits;
	/** The select structure used to extract the upper bits. */
	protected final SimpleSelect selectUpper;

	protected EliasFanoMonotoneLongBigList16(final long length, final short[][] lowerBits, final SimpleSelect selectUpper) {
		this.length = length;
		this.lowerBits = lowerBits;
		this.selectUpper = selectUpper;
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList16(final IntIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList16(final ShortIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */
	public EliasFanoMonotoneLongBigList16(final ByteIterable list) {
		this((LongIterable) () -> LongIterators.wrap(list.iterator()));
	}

	/** Creates an Elias&ndash;Fano representation of the values returned by the given {@linkplain Iterable iterable object}.
	 *
	 * @param list an iterable object.
	 */

	public EliasFanoMonotoneLongBigList16(final LongIterable list) {
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
	public EliasFanoMonotoneLongBigList16(final long n, final long upperBound, final ByteIterator iterator) {
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
	public EliasFanoMonotoneLongBigList16(final long n, final long upperBound, final ShortIterator iterator) {
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
	public EliasFanoMonotoneLongBigList16(final long n, final long upperBound, final IntIterator iterator) {
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
	public EliasFanoMonotoneLongBigList16(final long n, final long upperBound, final LongIterator iterator) {
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
	protected EliasFanoMonotoneLongBigList16(final long[] a, final LongIterator iterator) {
		length = a[0];
		long v = -1;
		final long upperBound = a[1];
		final int l = length == 0 ? 0 : Math.max(0, Fast.mostSignificantBit(upperBound / length));
		if (l > Short.SIZE) throw new IllegalArgumentException("This list does not support l > 16");
		final short[][] lowerBits = ShortBigArrays.newBigArray(length);
		final BitVector upperBits = LongArrayBitVector.getInstance().length(length + (upperBound >>> Short.SIZE) + 1);
		long last = Long.MIN_VALUE;
		for(long i = 0; i < length; i++) {
			v = iterator.nextLong();
			if (v > upperBound) throw new IllegalArgumentException("Too large value: " + v + " > " + upperBound);
			if (v < last) throw new IllegalArgumentException("Values are not nondecreasing: " + v + " < " + last);
			BigArrays.set(lowerBits, i, (short)v);
			upperBits.set((v >>> Short.SIZE) + i);
			last = v;
		}

		if (iterator.hasNext()) throw new IllegalArgumentException("There are more than " + length + " values in the provided iterator");
		this.lowerBits = lowerBits;
		selectUpper = new SimpleSelect(upperBits);
	}


	public long numBits() {
		return selectUpper.numBits() + selectUpper.bitVector().length() + BigArrays.length(lowerBits) * Short.SIZE;
	}

	@Override
	public long getLong(final long index) {
		return (selectUpper.select(index) - index) << Short.SIZE | BigArrays.get(lowerBits, index) & 0xFFFF;
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
		for(int i = 0; i < length; i++)
			dest[offset + i] = dest[offset + i] - index << Short.SIZE | BigArrays.get(lowerBits, index++) & 0xFFFF;

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
