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

package it.unimi.dsi.sux4j.bits;

import static it.unimi.dsi.bits.LongBigArrayBitVector.bits;
import static it.unimi.dsi.bits.LongBigArrayBitVector.word;
import static it.unimi.dsi.fastutil.BigArrays.get;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.LongBigArrayBitVector;
import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongBigArrays;
import it.unimi.dsi.fastutil.longs.LongBigList;

/**
 * A big version of {@link SimpleSelectZero} that can only be used with a {@link LongBigArrayBitVector}
 * (or {@linkplain LongBigArrays long big arrays}).
 */

public class SimpleBigSelectZero implements SelectZero {
	private static final long serialVersionUID = 1L;

	private static final int MAX_ONES_PER_INVENTORY = 8192;
	private static final int MAX_LOG2_LONGWORDS_PER_SUBINVENTORY = 3;

	/** The maximum size of span to qualify for a subinventory made of 16-bit offsets. */
	private static final int MAX_SPAN = (1 << 16);

	/** The underlying big bit vector. */
	private final LongBigArrayBitVector bitVector;
	/** The number of ones in {@link #bitVector}. */
	private final long numOnes;
	/** The number of words in {@link #bitVector}. */
	private final long numWords;
	/** The cached result of {@link LongBigArrayBitVector#bigBits() bitVector.bigBits()}. */
	private transient long[][] bits;
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
	private final long onesPerInventory;
	/** The mask associated to the number of ones per {@link #inventory} entry. */
	private final long onesPerInventoryMask;
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

	/** Creates a new selection structure using a bit vector specified by a big array of longs and a number of bits.
	 *
	 * @param bits a big array of longs representing a bit array.
	 * @param length the number of bits to use from <code>bits</code>.
	 */
	public SimpleBigSelectZero(final long[][] bits, final long length) {
		this(LongBigArrayBitVector.wrap(bits, length));
	}

	/**
	 * Creates a new selection structure using the specified instance of {@link LongArrayBitVector}.
	 *
	 * @param bitVector an instance of {@link LongArrayBitVector}.
	 */
	public SimpleBigSelectZero(final LongBigArrayBitVector bitVector) {
		this.bitVector = bitVector;
		this.bits = bitVector.bigBits();
		final long length = bitVector.length();

		numWords = word(length + Long.SIZE - 1);

		// We compute quickly the number of ones (possibly counting spurious bits in the last word).
		long d = 0;
		for (final long[] s : bits) for (final long t : s) d += Long.bitCount(t);

		onesPerInventory = 1L << (log2OnesPerInventory = Fast.mostSignificantBit(length == 0 ? 1 : ((d * MAX_ONES_PER_INVENTORY + length - 1) / length)));
		onesPerInventoryMask = onesPerInventory - 1;
		assert ((d + onesPerInventory - 1) / onesPerInventory) >= Integer.MAX_VALUE - Arrays.MAX_ARRAY_SIZE : "Inventory too large: " + ((d + onesPerInventory - 1) / onesPerInventory);
		final int inventorySize = (int)((d + onesPerInventory - 1) / onesPerInventory);

		inventory = new long[inventorySize + 1];

		final long numWords = this.numWords;
		final long[] inventory = this.inventory;
		final int log2OnesPerInventory = this.log2OnesPerInventory;
		final long onesPerInventoryMask = this.onesPerInventoryMask;

		// First phase: we build an inventory for each one out of onesPerInventory.
		d = 0;
		for (long i = 0; i < numWords; i++)
			for (int j = 0; j < Long.SIZE; j++) {
				if (bits(i) + j >= length) break;
				if ((get(bits, i) & 1L << j) != 0) {
					if ((d & onesPerInventoryMask) == 0) inventory[(int)(d >>> log2OnesPerInventory)] = bits(i) + j;
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

		final long numOnes = this.numOnes;
		final long onesPerInventory = this.onesPerInventory;
		final int onesPerSub16 = this.onesPerSub16;
		final int log2OnesPerSub16 = this.log2OnesPerSub16;
		final int onesPerSub64 = this.onesPerSub64;

		if (onesPerInventory > 1) {
			d = 0;
			long ones;
			long diff16 = 0, start = 0, span = 0;
			int spilled = 0, inventoryIndex = 0;

			for (long i = 0; i < numWords; i++)
				// We estimate the subinventory and exact spill size
				for (int j = 0; j < Long.SIZE; j++) {
					if (bits(i) + j >= length) break;
					if ((get(bits, i) & 1L << j) != 0) {
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

			final int subinventorySize = (int)((diff16 + 3) >> 2);
			final int exactSpillSize = spilled;
			subinventory = new long[subinventorySize];
			exactSpill = new long[exactSpillSize];
			subinventory16 = LongArrayBitVector.wrap(subinventory).asLongBigList(Short.SIZE);

			long offset = 0;
			spilled = 0;
			d = 0;

			final int onesPerSub16Mask = this.onesPerSub16Mask;
			final int log2LongwordsPerSubinventory = this.log2LongwordsPerSubinventory;
			final long[] subinventory = this.subinventory;
			final LongBigList subinventory16 = this.subinventory16;

			for (long i = 0; i < numWords; i++)
				for (int j = 0; j < Long.SIZE; j++) {
					if (bits(i) + j >= length) break;
					if ((get(bits, i) & 1L << j) != 0) {
						if ((d & onesPerInventoryMask) == 0) {
							inventoryIndex = (int)(d >>> log2OnesPerInventory);
							start = inventory[inventoryIndex];
							span = inventory[inventoryIndex + 1] - start;
							offset = 0;
						}

						if (span < MAX_SPAN) {
							assert bits(i) + j - start <= MAX_SPAN;
							if ((d & onesPerSub16Mask) == 0) {
								subinventory16.set(((long)inventoryIndex << log2LongwordsPerSubinventory + 2) + offset++, bits(i) + j - start);
							}
						}
						else {
							assert onesPerSub64 > 1;
							if ((d & onesPerInventoryMask) == 0) {
								inventory[inventoryIndex] |= 1L << 63;
								subinventory[inventoryIndex << log2LongwordsPerSubinventory] = spilled;
							}
							exactSpill[spilled++] = bits(i) + j;
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
		assert rank >= 0;
		assert rank < numOnes;

		final int inventoryIndex = (int)(rank >>> log2OnesPerInventory);

		final long inventoryRank = inventory[inventoryIndex];
		final int subrank = (int)(rank & onesPerInventoryMask);

		if (subrank == 0) return inventoryRank & ~(1L << 63);

		long start;
		int residual;

		if (inventoryRank >= 0) {
			final long index16 = ((long)inventoryIndex << log2LongwordsPerSubinventory + 2) + (subrank >>> log2OnesPerSub16);
			start = inventoryRank + ((subinventory[(int)(index16 >>> 2)] >>> ((index16 & 3) << 4) & 0xFFFF));
			residual = subrank & onesPerSub16Mask;
		}
		else {
			assert onesPerSub64 > 1;
			return exactSpill[(int)(subinventory[inventoryIndex << log2LongwordsPerSubinventory] + subrank)];
		}

		if (residual == 0) return start;

		final long[][] bits = this.bits;
		long wordIndex = word(start);
		long word = get(bits, wordIndex) & -1L << start;

		for(;;) {
			final int bitCount = Long.bitCount(word);
			if (residual < bitCount) break;
			word = get(bits, ++wordIndex);
			residual -= bitCount;
		}

		return bits(wordIndex) + Fast.select(word, residual);
	}

	@Override
	public long[] selectZero(final long rank, final long[] dest, int offset, final int length) {
		assert rank >= 0;
		assert rank < numOnes;
		assert offset >= 0;
		assert dest != null;
		assert offset < dest.length;
		assert length >= 0;
		assert offset + length <= dest.length;

		final long s = selectZero(rank);
		dest[offset] = s;
		long curr = word(s);

		final long[][] bits = this.bits;

		long window = get(bits, curr) & -1L << s;
		window &= window - 1;

		for(int i = 1; i < length; i++) {
			while (window == 0) window = get(bits, ++curr);
			dest[++offset] = bits(curr) + Long.numberOfTrailingZeros(window);
			window &= window - 1;
		}

		return dest;
	}

	@Override
	public long[] selectZero(final long rank, final long[] dest) {
		assert rank >= 0;
		assert rank < numOnes;
		assert dest != null;
		assert dest.length > 0;
		return selectZero(rank, dest, 0, dest.length);
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		subinventory16 = LongArrayBitVector.wrap(subinventory).asLongBigList(Short.SIZE);
		bits = bitVector.bigBits();
	}

	@Override
	public long numBits() {
		return bits(inventory.length) + bits(subinventory.length) + bits(exactSpill.length);
	}

	@Override
	public LongBigArrayBitVector bitVector() {
		return bitVector;
	}
}
