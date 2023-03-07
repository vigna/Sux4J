/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2023 Sebastiano Vigna
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

import static it.unimi.dsi.bits.LongArrayBitVector.bits;
import static it.unimi.dsi.bits.LongArrayBitVector.word;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.LongBigArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;

/** A <code>select9</code> implementation.
 *
 *  <p><code>select9</code> is based on an underlying <code>{@linkplain Rank9 rank9}</code> instance
 *  and uses 25%-37.5% additional space (beside the 25% due to <code>rank9</code>), depending on density. It guarantees practical
 *  constant time evaluation.
 */

public class Select9 implements Select {
	private static final long serialVersionUID = 2L;

	private final static long ONES_STEP_16 = 1L << 0 | 1L << 16 | 1L << 32 | 1L << 48;
	private final static long MSBS_STEP_16 = 0x8000L * ONES_STEP_16;

	private final static long ONES_STEP_9 = 1L << 0 | 1L << 9 | 1L << 18 | 1L << 27 | 1L << 36 | 1L << 45 | 1L << 54;
	private final static long MSBS_STEP_9 = 0x100L * ONES_STEP_9;

	private final static int LOG2_ONES_PER_INVENTORY = 9;
	private final static int ONES_PER_INVENTORY = 1 << LOG2_ONES_PER_INVENTORY;
	private final static int INVENTORY_MASK = ONES_PER_INVENTORY - 1;

	private final long[] inventory;
	private final long[] subinventory;
	private final long numOnes;
	private final int numWords;
	private transient long[] bits;
	private final long[] count;
	private final Rank9 rank9;

	public Select9(final Rank9 rank9) {
		this.rank9 = rank9;
		numOnes = rank9.numOnes;
		numWords = rank9.numWords;
		bits = rank9.bits;
		count = rank9.count;

		final int inventorySize = (int)((numOnes + ONES_PER_INVENTORY - 1) / ONES_PER_INVENTORY);

		inventory = new long[inventorySize + 1];
		subinventory = new long[(numWords + 3) >> 2];

		final int numWords = this.numWords;
		final long[] bits = this.bits;
		final long[] count = this.count;
		final long[] inventory = this.inventory;
		final long[] subinventory = this.subinventory;

		long d = 0;
		for (int i = 0; i < numWords; i++)
			for (int j = 0; j < Long.SIZE; j++)
				if ((bits[i] & 1L << j) != 0) {
					if ((d & INVENTORY_MASK) == 0) inventory[(int)(d >> LOG2_ONES_PER_INVENTORY)] = bits(i) + j;
					d++;
				}


		inventory[inventorySize] = LongBigArrayBitVector.bits((numWords + 3) & ~3L);

		d = 0;
		int state = 0;
		long firstBit = 0;
		int index, span, subinventoryPosition = 0;
		int blockSpan, blockLeft;
		long countsAtStart;
		final BitVector v = LongArrayBitVector.wrap(subinventory);
		final LongBigList subinventoryAsShorts = v.asLongBigList(Short.SIZE);
		final LongBigList subinventoryasInts = v.asLongBigList(Integer.SIZE);
		LongBigList s;

		for(int i = 0; i < numWords; i++)
			for (int j = 0; j < Long.SIZE; j++)
				if ((bits[i] & 1L << j) != 0) {
					if ((d & INVENTORY_MASK) == 0) {
						firstBit = bits(i) + j;
						index = (int)(d >> LOG2_ONES_PER_INVENTORY);
						assert inventory[index] == firstBit;

						subinventoryPosition = word(inventory[index]) >>> 2;

						span = (word(inventory[index + 1]) >>> 2) - (word(inventory[index]) >>> 2);
						state = -1;
						countsAtStart = count[(word(inventory[index]) >>> 3) << 1];
						blockSpan = (word(inventory[index + 1]) >>> 3) - (word(inventory[index]) >>> 3);
						blockLeft = word(inventory[index]) >>> 3;

						if (span >= 512) state = 0;
						else if (span >= 256) state = 1;
						else if (span >= 128) state = 2;
						else if (span >= 16) {
							assert (blockSpan + 8 & -8L) + 8 <= span * 4;
							s = subinventoryAsShorts.subList(subinventoryPosition * 4, subinventoryAsShorts.size64());

							int k;
							for(k = 0; k < blockSpan; k++) {
								assert s.getLong(k + 8) == 0;
								s.set(k + 8, count[(blockLeft + k + 1) * 2] - countsAtStart);
							}

							for(; k < (blockSpan + 8 & -8L); k++) {
								assert s.getLong(k + 8) == 0;
								s.set(k + 8, 0xFFFF);
							}

							assert blockSpan / 8 <= 8;

							for (k = 0; k < blockSpan >>> 3; k++) {
								assert s.getLong(k) == 0;
								s.set(k , count[(blockLeft + (k + 1) * 8) * 2] - countsAtStart);
							}

							for(; k < 8; k++) {
								assert s.getLong(k) == 0;
								s.set(k, 0xFFFF);
							}
						}
						else if (span >= 2) {
					assert (blockSpan + 8 & -8L) <= span * 4;
							s = subinventoryAsShorts.subList(subinventoryPosition * 4, subinventoryAsShorts.size64());

							int k;
							for(k = 0; k < blockSpan; k++) {
						assert s.getLong(k) == 0;
								s.set(k, count[(blockLeft + k + 1) * 2] - countsAtStart);
							}

							for(; k < (blockSpan + 8 & -8L); k++) {
						assert s.getLong(k) == 0;
								s.set(k, 0xFFFF);
							}
						}
					}

					switch(state) {
					case 0:
						assert subinventory[subinventoryPosition + (int)(d & INVENTORY_MASK)] == 0;
						subinventory[subinventoryPosition + (int)(d & INVENTORY_MASK)] = bits(i) + j;
						break;
					case 1:
						assert subinventoryasInts.getLong((subinventoryPosition << 1) + (d & INVENTORY_MASK)) == 0;
						assert bits(i) + j - firstBit < (1L << 32);
						subinventoryasInts.set((subinventoryPosition << 1) + (d & INVENTORY_MASK), bits(i) + j - firstBit);
						break;
					case 2:
						assert subinventoryAsShorts.getLong((subinventoryPosition << 2) + (d & INVENTORY_MASK)) == 0;
						assert bits(i) + j - firstBit < (1 << 16);
						subinventoryAsShorts.set((subinventoryPosition << 2) + (d & INVENTORY_MASK), bits(i) + j - firstBit);
						break;
					}

					d++;
				}
	}

	@Override
	public long select(final long rank) {
		final int inventoryIndexLeft = (int)(rank >> LOG2_ONES_PER_INVENTORY);

		final long inventoryLeft = inventory[inventoryIndexLeft];
		final int blockRight = word(inventory[inventoryIndexLeft + 1]);
		int blockLeft = word(inventoryLeft);
		final int subinventoryIndex = blockLeft >>> 2;
		final long span = (blockRight >>> 2) - (blockLeft >>> 2);
		int countLeft, rankInBlock;
		final long count[] = this.count;

		if (span < 2) {
			blockLeft &= ~7;
			countLeft = (blockLeft >> 2) & ~1;
			assert rank >= count[countLeft] : rank + " < " + count[countLeft];
			assert rank < count[countLeft + 2] : rank + " >= " + count[countLeft + 2];
			rankInBlock = (int)(rank - count[countLeft]);
		}
		else if (span < 16) {
			blockLeft &= ~7;
			countLeft = (blockLeft >> 2) & ~1;
			final long rankInSuperblock = rank - count[countLeft];
			final long rankInSuperblockStep16 = rankInSuperblock * ONES_STEP_16;

			final long first = subinventory[subinventoryIndex], second = subinventory[subinventoryIndex + 1];

			final int where = (int)((
					((((((rankInSuperblockStep16 | MSBS_STEP_16) - (first & ~MSBS_STEP_16)) | (first ^ rankInSuperblockStep16)) ^ (first & ~rankInSuperblockStep16)) & MSBS_STEP_16) >>> 15) +
					((((((rankInSuperblockStep16 | MSBS_STEP_16) - (second & ~MSBS_STEP_16)) | (second ^ rankInSuperblockStep16)) ^ (second & ~rankInSuperblockStep16)) & MSBS_STEP_16) >>> 15)
			) * ONES_STEP_16 >>> 47);


			assert where >= 0;
			assert where <= 16;

			blockLeft += where * 4;
			countLeft += where;
			rankInBlock = (int)(rank - count[countLeft]);
			assert rankInBlock >= 0;
			assert rankInBlock < 512;
		}
		else if (span < 128) {
			final long[] subinventory = this.subinventory;
			blockLeft &= ~7;
			countLeft = (blockLeft >> 2) & ~1;
			final long rankInSuperblock = rank - count[countLeft];
			final long rankInSuperblockStep16 = rankInSuperblock * ONES_STEP_16;

			final long first = subinventory[subinventoryIndex], second = subinventory[subinventoryIndex + 1];
			final int where0 = (int)((
					((((((rankInSuperblockStep16 | MSBS_STEP_16) - (first & ~MSBS_STEP_16)) | (first ^ rankInSuperblockStep16)) ^ (first & ~rankInSuperblockStep16)) & MSBS_STEP_16) >>> 15) +
					((((((rankInSuperblockStep16 | MSBS_STEP_16) - (second & ~MSBS_STEP_16)) | (second ^ rankInSuperblockStep16)) ^ (second & ~rankInSuperblockStep16)) & MSBS_STEP_16) >>> 15)
			) * ONES_STEP_16 >>> 47);
			assert where0 <= 16;
			final long first_bis = subinventory[subinventoryIndex + where0 + 2], second_bis = subinventory[subinventoryIndex + where0 + 2 + 1];
			final int where1 = (where0 << 3) + (int)((
					((((((rankInSuperblockStep16 | MSBS_STEP_16) - (first_bis & ~MSBS_STEP_16)) | (first_bis ^ rankInSuperblockStep16)) ^ (first_bis & ~rankInSuperblockStep16)) & MSBS_STEP_16) >>> 15) +
					((((((rankInSuperblockStep16 | MSBS_STEP_16) - (second_bis & ~MSBS_STEP_16)) | (second_bis ^ rankInSuperblockStep16)) ^ (second_bis & ~rankInSuperblockStep16)) & MSBS_STEP_16) >>> 15)
			) * ONES_STEP_16 >>> 47);

			blockLeft += where1 << 2;
			countLeft += where1;
			rankInBlock = (int)(rank - count[countLeft]);
			assert rankInBlock >= 0;
			assert rankInBlock < 512;
		}
		else if (span < 256) {
			final int index16 = (subinventoryIndex << 2) + (int)(rank & ~-ONES_PER_INVENTORY);
			return (subinventory[index16 >>> 2] >>> ((index16 & 3) << 4) & 0xFFFF) + inventoryLeft;
		}
		else if (span < 512) {
			final int index32 = (subinventoryIndex << 1) + (int)(rank & ~-ONES_PER_INVENTORY);
			return (subinventory[index32 >>> 1] >>> ((index32 & 1) << 5) & 0xFFFFFFFFL) + inventoryLeft;
		}
		else {
			return subinventory[subinventoryIndex + (int)(rank & ~-ONES_PER_INVENTORY)];
		}

		final long rankInBlockStep9 = rankInBlock * ONES_STEP_9;
		final long subcounts = count[countLeft + 1];
		final int offsetInBlock = (int)(((((((rankInBlockStep9 | MSBS_STEP_9) - (subcounts & ~MSBS_STEP_9)) | (subcounts ^ rankInBlockStep9)) ^ (subcounts & ~rankInBlockStep9)) & MSBS_STEP_9) >>> 8) * ONES_STEP_9 >>> 54 & 0x7);

		final int word = blockLeft + offsetInBlock;
		final int rankInWord = (int)(rankInBlock - (subcounts >>> (offsetInBlock - 1 & 7) * 9 & 0x1FF));
		assert offsetInBlock >= 0;
		assert offsetInBlock <= 7;

		assert rankInWord < Long.SIZE;
		assert rankInWord >= 0;

		return bits(word) + Fast.select(bits[word], rankInWord);
	}

	@Override
	public long numBits() {
		return rank9.numBits() + bits(inventory.length) + bits(subinventory.length);
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		bits = rank9.bitVector.bits();
	}

	@Override
	public BitVector bitVector() {
		return rank9.bitVector();
	}
}
