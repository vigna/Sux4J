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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigListIterator;
import it.unimi.dsi.fastutil.longs.MappedLongBigList;
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
 * {@link MappedEliasFanoMonotoneLongBigListIterator#nextLong() nextLong()} method of an
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

public class MappedEliasFanoMonotoneLongBigList extends AbstractLongBigList implements Serializable, Closeable {
	private static final long serialVersionUID = 4L;

	public static final String OBJECT_EXTENSION = ".object";
	public static final String LOWER_BITS_EXTENSION = ".lowerbits";

	/** The length of the sequence. */
	protected final long length;
	/** The number of lower bits. */
	protected final int l;
	/** The upper bits, stored as unary gaps. */
	protected long[] upperBits;
	/** The list of lower bits of each element, stored explicitly. */
	protected MappedLongBigList lowerBits;
	/** The select structure used to extract the upper bits. */
	protected final SimpleSelect selectUpper;
	/** The mask for the lower bits. */
	protected final long lowerBitsMask;
	/** The file channel used for memory mapping. */
	private FileChannel fileChannel;
	/** Whether the byte order is little endian. */
	private final boolean littleEndian;

	public static MappedEliasFanoMonotoneLongBigList load(final String basename) throws IOException, ClassNotFoundException {
		return ((MappedEliasFanoMonotoneLongBigList)BinIO.loadObject(basename + OBJECT_EXTENSION)).lowerBits(basename + LOWER_BITS_EXTENSION);
	}

	public MappedEliasFanoMonotoneLongBigList lowerBits(final String lowerBits) throws IOException {
		fileChannel = FileChannel.open(new File(lowerBits).toPath());
		this.lowerBits = MappedLongBigList.map(fileChannel, littleEndian ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
		return this;
	}

	@Override
	public void close() throws IOException {
		fileChannel.close();
	}

	protected MappedEliasFanoMonotoneLongBigList(final long length, final int l, final long[] upperBits, final SimpleSelect selectUpper, final boolean littleEndian) {
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

		final int startWord = word(position);
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
		final MappedLongBigList lowerBits = this.lowerBits;

		long position = index * l;
		int startWord = word(position);
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
		final MappedLongBigList lowerBits = this.lowerBits;

		long position = index * l;
		for (int i = 0; i < length; i++) {
			final int startWord = word(position);
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
		protected int word;
		/** The current window. */
		protected long window;
		/** The current position in the array of lower bits. */
		protected long lowerBitsPosition;

		protected MappedEliasFanoMonotoneLongBigListIterator(final long from) {
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
			final int l = MappedEliasFanoMonotoneLongBigList.this.l;
			final int startWord = word(lowerBitsPosition);
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
			window = upperBits[word = word(position)] & -1L << position;

			lowerBitsPosition = index * l;
			final int startWord = word(lowerBitsPosition);
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
		upperBits = selectUpper.bitVector().bits();
	}
}
