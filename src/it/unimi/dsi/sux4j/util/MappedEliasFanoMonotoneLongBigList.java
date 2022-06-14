/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2022 Sebastiano Vigna
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
import static it.unimi.dsi.bits.LongBigArrayBitVector.word;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.NoSuchElementException;

import it.unimi.dsi.bits.LongBigArrayBitVector;
import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigListIterator;
import it.unimi.dsi.fastutil.longs.LongMappedBigList;
import it.unimi.dsi.lang.FlyweightPrototype;
import it.unimi.dsi.sux4j.bits.SimpleBigSelect;

/**
 * A memory-mapped implementation of
 * {@link EliasFanoMonotoneLongBigList}/{@link EliasFanoMonotoneBigLongBigList}.
 *
 * <p>
 * This class makes it possible to use an {@link EliasFanoMonotoneLongBigList} (or
 * {@link EliasFanoMonotoneBigLongBigList}) without actually loading the lower bits, but rather
 * {@linkplain LongMappedBigList mapping them into memory}. You build an
 * {@link EliasFanoMonotoneLongBigList} (or {@link EliasFanoMonotoneBigLongBigList}) instance first,
 * and then you use the {@linkplain EliasFanoMonotoneLongBigList#dump(String, ByteOrder) dump} it to
 * disk providing a basename. The same basename must be used when {@linkplain #load(String) loading
 * an instance of this class}.
 *
 * <p>
 * After usage, you should {@link #close()} instances of this class to release the associated
 * {@link FileChannel}.
 *
 * <p>
 * Instances of this class are not thread safe, but the {@link #copy()} method provides a
 * lightweight duplicate that can be read independently by another thread. The method uses
 * {@link LongMappedBigList#copy()} to provide an independent mapping of the lower bits. Note that
 * the {@link #close()} method, when invoked on any copy, will stop mapping of all copies. *
 *
 * @see EliasFanoMonotoneLongBigList
 * @see EliasFanoMonotoneBigLongBigList
 */

public class MappedEliasFanoMonotoneLongBigList extends AbstractLongBigList implements Serializable, Closeable, FlyweightPrototype<MappedEliasFanoMonotoneLongBigList> {
	private static final long serialVersionUID = 5L;

	public static final String OBJECT_EXTENSION = ".object";
	public static final String LOWER_BITS_EXTENSION = ".lowerbits";

	/** The length of the sequence. */
	protected final long length;
	/** The number of lower bits. */
	protected final int l;
	/** The upper bits, stored as unary gaps. */
	protected long[][] upperBits;
	/** The list of lower bits of each element, stored explicitly. */
	protected LongMappedBigList lowerBits;
	/** The select structure used to extract the upper bits. */
	protected final SimpleBigSelect selectUpper;
	/** The mask for the lower bits. */
	protected final long lowerBitsMask;
	/** The file channel used for memory mapping. */
	private FileChannel fileChannel;
	/** Whether the byte order is little endian. */
	private final boolean littleEndian;

	/**
	 * Maps an Elias&ndash;Fano monotone list into memory.
	 *
	 * @param basename the basename of the
	 *            {@linkplain EliasFanoMonotoneLongBigList#dump(String, ByteOrder) dumped list}. From
	 *            the basename, two files will be derived: a serialized object with extension
	 *            {@link MappedEliasFanoMonotoneLongBigList#OBJECT_EXTENSION} and a list of longs with
	 *            extension {@link MappedEliasFanoMonotoneLongBigList#LOWER_BITS_EXTENSION}.
	 *
	 * @return an instance of this class.
	 */
	public static MappedEliasFanoMonotoneLongBigList load(final String basename) throws IOException, ClassNotFoundException {
		return ((MappedEliasFanoMonotoneLongBigList)BinIO.loadObject(basename + OBJECT_EXTENSION)).lowerBits(basename + LOWER_BITS_EXTENSION);
	}

	/**
	 * Maps the lower bits of this list from a file.
	 *
	 * <p>
	 * This method is low level and should not be used in geneneral. There is no check that the file you
	 * are using is actually the right file for this instance.
	 *
	 * @param lowerBits the name of the file containing the lower bits.
	 * @return this instance.
	 */
	public MappedEliasFanoMonotoneLongBigList lowerBits(final String lowerBits) throws IOException {
		fileChannel = FileChannel.open(new File(lowerBits).toPath());
		this.lowerBits = LongMappedBigList.map(fileChannel, littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
		return this;
	}

	@Override
	public void close() throws IOException {
		fileChannel.close();
	}

	protected MappedEliasFanoMonotoneLongBigList(final long length, final int l, final long[][] upperBits, final SimpleBigSelect selectUpper, final boolean littleEndian) {
		this.length = length;
		this.l = l;
		this.upperBits = upperBits;
		this.selectUpper = selectUpper;
		this.littleEndian = littleEndian;
		this.lowerBitsMask = (1L << l) - 1;
	}

	public long numBits() {
		return selectUpper.numBits() + selectUpper.bitVector().length() + lowerBits.size64() * Long.SIZE;
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

		final long startWord = word(position);
		final int startBit = bit(position);
		final long result = lowerBits.getLong(startWord) >>> startBit;
		return upperBits << l | (startBit + l <= Long.SIZE ? result : result | lowerBits.getLong(startWord + 1) << -startBit) & lowerBitsMask;
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
		final LongMappedBigList lowerBits = this.lowerBits;

		long position = index * l;
		long startWord = word(position);
		int startBit = bit(position);
		long first = lowerBits.getLong(startWord) >>> startBit;
		first = dest[0] - index++ << l | (startBit + l <= Long.SIZE ? first : first | lowerBits.getLong(startWord + 1) << -startBit) & lowerBitsMask;
		position += l;
		startWord = word(position);
		startBit = bit(position);
		long second = lowerBits.getLong(startWord) >>> startBit;
		second = dest[1] - index << l | (startBit + l <= Long.SIZE ? second : second | lowerBits.getLong(startWord + 1) << -startBit) & lowerBitsMask;
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
		final LongMappedBigList lowerBits = this.lowerBits;

		long position = index * l;
		for (int i = 0; i < length; i++) {
			final long startWord = word(position);
			final int startBit = bit(position);
			final long result = lowerBits.getLong(startWord) >>> startBit;
			dest[offset + i] = dest[offset + i] - index++ << l | (startBit + l <= Long.SIZE ? result : result | lowerBits.getLong(startWord + 1) << -startBit) & lowerBitsMask;
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
	 * A list iterator over the values of this {@link MappedEliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * {@linkplain #nextLong() Forward iteration} will be faster than iterated calls to
	 * {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * <p>
	 * Additional <em>unsafe</em> methods {@link #nextLongUnsafe()} and {@link #previousLongUnsafe()}
	 * iterate without checking for the existence of a next element.
	 */
	public class MappedEliasFanoMonotoneLongBigListIterator implements LongBigListIterator {
		/** The index of the next element to return. */
		protected long index;
		/** The current word in the array of upper bits. */
		protected long word;
		/** The current window. */
		protected long window;
		/** The current position in the array of lower bits. */
		protected long lowerBitsPosition;

		protected MappedEliasFanoMonotoneLongBigListIterator(final long from) {
			index = from;
			final long position = selectUpper.select(from);
			window = BigArrays.get(upperBits, word = word(position)) & -1L << position;
			lowerBitsPosition = index * l;
		}

		private long getNextUpperBits() {
			while (window == 0) window = BigArrays.get(upperBits, ++word);
			final long upperBits = LongBigArrayBitVector.bits(word) + Long.numberOfTrailingZeros(window) - index++;
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
			final int l = MappedEliasFanoMonotoneLongBigList.this.l;
			final long startWord = word(lowerBitsPosition);
			final int startBit = bit(lowerBitsPosition);
			long lower = lowerBits.getLong(startWord) >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits.getLong(startWord + 1) << -startBit;
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
			final int l = MappedEliasFanoMonotoneLongBigList.this.l;
			--index;
			final long position = selectUpper.select(index);
			window = BigArrays.get(upperBits, word = word(position)) & -1L << position;

			lowerBitsPosition = index * l;
			final long startWord = word(lowerBitsPosition);
			final int startBit = bit(lowerBitsPosition);
			long lower = lowerBits.getLong(startWord) >>> startBit;
			if (startBit + l > Long.SIZE) lower |= lowerBits.getLong(startWord + 1) << -startBit;
			return (position - index) << l | lower & lowerBitsMask;
		}
	}

	/**
	 * Returns a list iterator over the values of this {@link MappedEliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * @param from the starting position in the sequence.
	 * @return a list iterator over the values of this {@link MappedEliasFanoMonotoneLongBigList}.
	 * @see MappedEliasFanoMonotoneLongBigListIterator
	 */
	@Override
	public MappedEliasFanoMonotoneLongBigListIterator listIterator(final long from) {
		return new MappedEliasFanoMonotoneLongBigListIterator(from);
	}

	/**
	 * Returns a list iterator over the values of this {@link MappedEliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * @return a list iterator over the values of this {@link MappedEliasFanoMonotoneLongBigList}.
	 * @see MappedEliasFanoMonotoneLongBigListIterator
	 */
	@Override
	public MappedEliasFanoMonotoneLongBigListIterator listIterator() {
		return new MappedEliasFanoMonotoneLongBigListIterator(0);
	}

	/**
	 * Returns a list iterator over the values of this {@link MappedEliasFanoMonotoneLongBigList}.
	 *
	 * <p>
	 * Forward iteration will be faster than iterated calls to
	 * {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}. Backward iteration is available,
	 * but it will perform similarly to {@link MappedEliasFanoMonotoneLongBigList#getLong(long) getLong()}.
	 *
	 * @return a list iterator over the values of this {@link MappedEliasFanoMonotoneLongBigList}.
	 * @see MappedEliasFanoMonotoneLongBigListIterator
	 */
	@Override
	public MappedEliasFanoMonotoneLongBigListIterator iterator() {
		return listIterator();
	}

	@Override
	public long size64() {
		return length;
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		upperBits = selectUpper.bitVector().bigBits();
	}

	@Override
	public MappedEliasFanoMonotoneLongBigList copy() {
		final MappedEliasFanoMonotoneLongBigList copy = new MappedEliasFanoMonotoneLongBigList(length, l, upperBits, selectUpper, littleEndian);
		copy.lowerBits = this.lowerBits.copy();
		return copy;
	}
}
