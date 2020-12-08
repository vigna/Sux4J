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

package it.unimi.dsi.sux4j.util;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;

/** A compressed big list of longs; each element occupies a number of bits bounded by one plus its bit length plus the logarithm of the average bit length of an element.
 *
 * <p>Instances of this class store in a highly compacted form a list of longs. Values are provided either through an {@linkplain Iterable iterable object},
 * or through an {@linkplain Iterator iterator}, but in the latter case the user must also provide a (not necessarily strict) lower bound (0 by default)
 * on the returned values. The compression is particularly high if the distribution of the values of the list is skewed towards the smallest values.
 *
 * <p>An additional {@linkplain #get(long, long[], int, int) bulk method} makes it possible
 * to extract several consecutive entries at high speed.
 *
 * <h2>Implementation details</h2>
 *
 * <p>Instances of this class store values by offsetting them so that they are strictly positive. Then,
 * the bits of each element, excluding the most significant one, are concatenated in a bit array, and the positions
 * of the initial bit of each element are stored using the {@linkplain EliasFanoMonotoneLongBigList Elias&ndash;Fano representation}.
 * If the distribution of the elements is skewed towards small values, this method achieves very a good compression (and, in any case,
 * w.r.t. exact binary length it will not lose more than one bit per element, plus lower-order terms).
 *
 * <p>During the construction, the list of borders (i.e., bit positions where each stored element starts) must be temporarily stored. For very large lists,
 * it might be useful to use a {@linkplain EliasFanoLongBigList#EliasFanoLongBigList(LongIterator, long, boolean)
 * constructor that provides offline storage for borders}.
 */
public class EliasFanoLongBigList extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 2L;
	/** The number of elements in this list. */
	private final long length;
	/** The storage for small elements. */
	private final LongArrayBitVector bits;
	/** The offset (derived from the lower bound computed or provided at construction time) that must be added before returning a value. */
	private final long offset;
	/** The position of the initial bit of each element, plus a final marker for the end of the bit array. */
	private EliasFanoMonotoneLongBigList borders;

	private static long findMin(final LongIterator iterator) {
		long lowerBound = Long.MAX_VALUE;
		while(iterator.hasNext()) lowerBound = Math.min(lowerBound, iterator.nextLong());
		return lowerBound;
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList(final LongIterable elements) {
		this(elements.iterator(), findMin(elements.iterator()));
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList(final IntIterable elements) {
		this((LongIterable) () -> LongIterators.wrap(elements.iterator()));
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList(final ShortIterable elements) {
		this((LongIterable) () -> LongIterators.wrap(elements.iterator()));
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList(final ByteIterable elements) {
		this((LongIterable) () -> LongIterators.wrap(elements.iterator()));
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList(final LongIterator iterator) {
		this(iterator, 0);
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList(final IntIterator iterator) {
		this(LongIterators.wrap(iterator));
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList(final ShortIterator iterator) {
		this(LongIterators.wrap(iterator));
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList(final ByteIterator iterator) {
		this(LongIterators.wrap(iterator));
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the values returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList(final IntIterator iterator, final int lowerBound) {
		this(LongIterators.wrap(iterator), lowerBound);
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the values returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList(final ShortIterator iterator, final short lowerBound) {
		this(LongIterators.wrap(iterator), lowerBound);
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the values returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList(final ByteIterator iterator, final byte lowerBound) {
		this(LongIterators.wrap(iterator), lowerBound);
	}

	/** Creates a new Elias&ndash;Fano long big list.
	 *
	 * <p>This constructor does not use offline space.
	 *
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the values returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList(final LongIterator iterator, final long lowerBound) {
		this(iterator, lowerBound, false);
	}

	/** Creates a new Elias&ndash;Fano long big list with low memory requirements.
	 *
	 * <p>This constructor will use a temporary file to store the border array if <code>offline</code> is true.
	 *
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the values returned by <code>iterator</code>.
	 * @param offline if true, the construction uses offline memory.
	 */
	public EliasFanoLongBigList(final LongIterator iterator, final long lowerBound, final boolean offline) {
		this.offset = -lowerBound + 1;
		bits = LongArrayBitVector.getInstance();
		LongArrayList borders = null;
		File tempFile = null;
		DataOutputStream dos = null;
		try {
			if (offline) {
				tempFile = File.createTempFile(EliasFanoLongBigList.class.getSimpleName(), "borders");
				tempFile.deleteOnExit();
				dos = new DataOutputStream(new FastBufferedOutputStream(new FileOutputStream(tempFile)));
			}
			else borders = new LongArrayList();

			if (offline) dos.writeLong(0);
			else borders.add(0);

			long lastBorder = 0, maxBorder = 0;
			long v;
			long c = 0;
			int msb;
			while(iterator.hasNext()) {
				v = iterator.nextLong();
				if (v < lowerBound) throw new IllegalArgumentException(v + " < " + lowerBound);
				v -= lowerBound;
				v++;
				msb = Fast.mostSignificantBit(v);
				lastBorder += msb;
				if (offline) dos.writeLong(lastBorder);
				else borders.add(lastBorder);
				if (maxBorder < lastBorder) maxBorder = lastBorder;
				bits.append(v & (1L << msb) - 1, msb);
				c++;
			}
			this.length = c;
			if (offline) dos.close();
			this.borders = new EliasFanoMonotoneLongBigList(c + 1, maxBorder + 1, offline ? BinIO.asLongIterator(tempFile) : borders.iterator());
			if (offline) tempFile.delete();
			this.bits.trim();
		}
		catch(final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public long getLong(final long index) {
		final long from = borders.getLong(index), to = borders.getLong(index + 1);
		return ((1L << (to - from)) | bits.getLong(from, to)) - offset;
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
		long from = borders.getLong(index++), to;
		// We use the destination array to cache borders.
		borders.get(index, dest, offset, length);

		for(int i = 0; i < length; i++) {
			to = dest[offset + i];
			dest[offset + i] = ((1L << (to - from)) | bits.getLong(from, to)) - this.offset;
			from = to;
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

	public long numBits() {
		return borders.numBits() + bits.length();
	}
}
