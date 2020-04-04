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

package it.unimi.dsi.sux4j.bits;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

/** A simple zero-select implementation based on a two-level inventory, a spill list and broadword bit search.
 *
 * <p>This implementation uses around 13.75% additional space on evenly distributed bit arrays, and,
 * under the same conditions, provide very fast selects. For very unevenly distributed arrays
 * the space occupancy will grow significantly, and access time might vary wildly.
 *
 * <p>An additional {@linkplain #selectZero(long, long[], int, int) bulk method} makes it possible
 * to select several consecutive bits at high speed, if the array is reasonably uniform. This is
 * the typical case when this structure is backing an {@link EliasFanoMonotoneLongBigList}.
 */

public class SimpleSelectZero implements SelectZero {
	private static final boolean ASSERTS = true;

	private static final long serialVersionUID = 1L;

	private static final int MAX_ONES_PER_INVENTORY = 8192;
	private static final int MAX_LOG2_LONGWORDS_PER_SUBINVENTORY = 3;

	/** The maximum size of span to qualify for a subinventory made of 16-bit offsets. */
	private static final int MAX_SPAN = (1 << 16);

	/** The underlying bit vector. */
	private final BitVector bitVector;
	/** The number of ones in {@link #bitVector}. */
	private final long numOnes;
	/** The number of words in {@link #bitVector}. */
	private final int numWords;
	/** The cached result of {@link BitVector#bits() bitVector.bits()}. */
	private transient long[] bits;
	/** The first-level inventory containing information about one bit each {@link #onesPerInventory}.
	 * If the entry is nonnegative, it is the rank of the bit and subsequent information is recorded in {@link #subinventory16}
	 * as offsets of one bit each {@link #onesPerSub16} (then, sequential search is necessary). Otherwise, a negative value
	 * means that offsets are too large and they have been recorded as 64-bit values. If {@link #onesPerSub64} is 1, then offsets are directly stored
	 * into {@link #subinventory}. Otherwise, the first {@link #subinventory} entry is actually a pointer to {@link #exactSpill},
	 * where the offsets can be found. */
	private final long[] inventory;
	/** The logarithm of the number of ones per {@link #inventory} entry. */
	private final int log2OnesPerInventory;
	/** The number of ones per {@link #inventory} entry. */
	private final int onesPerInventory;
	/** The mask associated to the number of ones per {@link #inventory} entry. */
	private final int onesPerInventoryMask;
	/** The second-level inventory (records the offset of each bit w.r.t. the first-level inventory). */
	private final long[] subinventory;
	/** Exposes {@link #subinventory} as a list of 16-bits positive integers. */
	private transient LongBigList subinventory16;
	/** The logarithm of the number of longwords used in the part of the subinventory associated to an inventory entry. */
	private final int log2LongwordsPerSubinventory;
	/** The logarithm of the number of ones for each {@link #subinventory} longword. */
	private final int log2OnesPerSub64;
	/** The number of ones for each {@link #subinventory} longword. */
	private final int onesPerSub64;
	/** The logarithm of the number of ones for each {@link #subinventory} short. */
	private final int log2OnesPerSub16;
	/** The number of ones for each {@link #subinventory} short. */
	private final int onesPerSub16;
	/** The mask associated to number of ones for each {@link #subinventory} short. */
	private final int onesPerSub16Mask;
	/** The list of exact spills. */
	private final long[] exactSpill;

	/** Creates a new selection structure using a bit vector specified by an array of longs and a number of bits.
	 *
	 * @param bits an array of longs representing a bit array.
	 * @param length the number of bits to use from <code>bits</code>.
	 */
	public SimpleSelectZero(final long[] bits, final long length) {
		this(LongArrayBitVector.wrap(bits, length));
	}

	/** Creates a new selection structure using the specified bit vector.
	 *
	 * @param bitVector a bit vector.
	 */
	public SimpleSelectZero(final BitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();

		numWords = (int)((length + 63) / 64);

		// We compute quickly the number of ones (possibly counting spurious bits in the last word).
		long d = 0;
		for(int i = numWords; i-- != 0;) d += Long.bitCount(~bits[i]);

		onesPerInventory = 1 << (log2OnesPerInventory = Fast.mostSignificantBit(length == 0 ? 1 : (int)((d * MAX_ONES_PER_INVENTORY + length - 1) / length)));
		onesPerInventoryMask = onesPerInventory - 1;
		final int inventorySize = (int)((d + onesPerInventory - 1) / onesPerInventory);

		inventory = new long[inventorySize + 1];

		// First phase: we build an inventory for each one out of onesPerInventory.
		d = 0;
		for(int i = 0; i < numWords; i++)
			for(int j = 0; j < 64; j++) {
				if (i * 64L + j >= length) break;
				if ((~bits[i] & 1L << j) != 0) {
					if ((d & onesPerInventoryMask) == 0) inventory[(int)(d >>> log2OnesPerInventory)] = i * 64L + j;
					d++;
				}
			}

		numOnes = d;

		inventory[inventorySize] = length;

		log2LongwordsPerSubinventory = Math.min(MAX_LOG2_LONGWORDS_PER_SUBINVENTORY, Math.max(0, log2OnesPerInventory - 2));
		log2OnesPerSub64 = Math.max(0, log2OnesPerInventory - log2LongwordsPerSubinventory);
		log2OnesPerSub16 = Math.max(0, log2OnesPerSub64 - 2);
		onesPerSub64 = (1 << log2OnesPerSub64);
		onesPerSub16 = (1 << log2OnesPerSub16);
		onesPerSub16Mask = onesPerSub16 - 1;

		if (onesPerInventory > 1) {
			d = 0;
			int ones;
			long diff16 = 0, start = 0, span = 0;
			int spilled = 0, inventoryIndex = 0;

			for(int i = 0; i < numWords; i++)
				// We estimate the subinventory and exact spill size
				for(int j = 0; j < 64; j++) {
					if (i * 64L + j >= length) break;
					if ((~bits[i] & 1L << j) != 0) {
						if ((d & onesPerInventoryMask) == 0) {
							inventoryIndex = (int)(d >>> log2OnesPerInventory);
							start = inventory[inventoryIndex];
							span = inventory[inventoryIndex + 1] - start;
							ones = (int)Math.min(numOnes - d, onesPerInventory);

							// We must always count (possibly unused) diff16's. And we cannot store less then 4 diff16.
							diff16 += Math.max(4, (ones + onesPerSub16 - 1) >>> log2OnesPerSub16);
							if (span >= MAX_SPAN && onesPerSub64 > 1) spilled += ones;
						}
						d++;
					}
				}

			final int subinventorySize = (int)((diff16 + 3) / 4);
			final int exactSpillSize = spilled;
			subinventory = new long[subinventorySize];
			exactSpill = new long[exactSpillSize];
			subinventory16 = LongArrayBitVector.wrap(subinventory).asLongBigList(Short.SIZE);

			int offset = 0;
			spilled = 0;
			d = 0;

			for(int i = 0; i < numWords; i++)
				for(int j = 0; j < 64; j++) {
					if (i * 64L + j >= length) break;
					if ((~bits[i] & 1L << j) != 0) {
						if ((d & onesPerInventoryMask) == 0) {
							inventoryIndex = (int)(d >>> log2OnesPerInventory);
							start = inventory[inventoryIndex];
							span = inventory[inventoryIndex + 1] - start;
							offset = 0;
						}

						if (span < MAX_SPAN) {
							if (ASSERTS) assert i * 64L + j - start <= MAX_SPAN;
							if ((d & onesPerSub16Mask) == 0) {
								subinventory16.set((inventoryIndex << log2LongwordsPerSubinventory + 2) +  offset++, i * 64L + j - start);
							}
						}
						else {
							if (onesPerSub64 == 1) {
								subinventory[(inventoryIndex << log2LongwordsPerSubinventory) + offset++] = i * 64L + j;
							}
							else {
								if ((d & onesPerInventoryMask) == 0) {
									inventory[inventoryIndex] |= 1L << 63;
									subinventory[inventoryIndex << log2LongwordsPerSubinventory] = spilled;
								}
								exactSpill[spilled++] = i * 64L + j;
							}
						}

						d++;
					}
				}
		}
		else {
			subinventory = exactSpill = LongArrays.EMPTY_ARRAY;
			subinventory16 = null;
		}

	}

	@Override
	public long selectZero(final long rank) {
		if (rank >= numOnes) return -1;

		final int inventoryIndex = (int)(rank >>> log2OnesPerInventory);

		final long inventoryRank = inventory[inventoryIndex];
		final int subrank = (int)(rank & onesPerInventoryMask);

		if (subrank == 0) return inventoryRank & ~(1L<<63);

		long start;
		int residual;

		if (inventoryRank >= 0) {
			start = inventoryRank + subinventory16.getLong((inventoryIndex << log2LongwordsPerSubinventory + 2) + (subrank >>> log2OnesPerSub16));
			residual = subrank & onesPerSub16Mask;
		}
		else {
			if (onesPerSub64 == 1) return subinventory[(inventoryIndex << log2LongwordsPerSubinventory) + subrank];
			return exactSpill[(int)(subinventory[inventoryIndex << log2LongwordsPerSubinventory] + subrank)];
		}

		if (residual == 0) return start;

		final long bits[] = this.bits;
		int wordIndex = (int)(start / 64);
		long word = ~bits[wordIndex] & -1L << start;

		for(;;) {
			final int bitCount = Long.bitCount(word);
			if (residual < bitCount) break;
			word = ~bits[++wordIndex];
			residual -= bitCount;
		}

		return wordIndex * (long)Long.SIZE + Fast.select(word, residual);
	}

	/** Performs a bulk select of consecutive ranks into a given array fragment.
	 *
	 * @param rank the first rank to select.
	 * @param dest the destination array; it will be filled with {@code length} positions of consecutive bits starting at position {@code offset}.
	 * @param offset the first bit position written in {@code dest}.
	 * @param length the number of bit positions in {@code dest} starting at {@code offset}.
	 * @return {@code dest}
	 * @see #selectZero(long, long[])
	 */
	public long[] selectZero(final long rank, final long[] dest, final int offset, final int length) {
		if (length == 0) return dest;
		final long s = selectZero(rank);
		dest[offset] = s;
		int curr = (int)(s / Long.SIZE);

		long window = ~bits[curr] & -1L << s;
		window &= window - 1;

		for(int i = 1; i < length; i++) {
			while(window == 0) window = ~bits[++curr];
			dest[offset + i] = curr * (long)Long.SIZE + Long.numberOfTrailingZeros(window);
			window &= window - 1;
		}

		return dest;
	}

	/** Performs a bulk select of consecutive ranks into a given array.
	 *
	 * @param rank the first rank to select.
	 * @param dest the destination array; it will be filled with position of consecutive bits.
	 * @return {@code dest}
	 * @see #selectZero(long, long[], int, int)
	 */
	public long[] selectZero(final long rank, final long[] dest) {
		return selectZero(rank, dest, 0, dest.length);
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		subinventory16 = LongArrayBitVector.wrap(subinventory).asLongBigList(Short.SIZE);
		bits = bitVector.bits();
	}

	@Override
	public long numBits() {
		return inventory.length * (long)Long.SIZE + subinventory.length * (long)Long.SIZE + exactSpill.length * (long)Long.SIZE;
	}

	@Override
	public BitVector bitVector() {
		return bitVector;
	}
}
