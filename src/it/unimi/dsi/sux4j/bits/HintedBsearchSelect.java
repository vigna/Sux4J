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

package it.unimi.dsi.sux4j.bits;

import java.io.IOException;
import java.io.ObjectInputStream;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongBigArrayBitVector;

/** A hinted binary-search select implementation.
 *
 * <p>Instances of this class perform selection
 * using a hinted binary search over an underlying {@link Rank9} instance. We use
 * 12.5% additional space for a small inventory.
 */

public class HintedBsearchSelect implements Select {
	@SuppressWarnings("unused")
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 1L;

	private static final long ONES_STEP_9 = 1L << 0 | 1L << 9 | 1L << 18 | 1L << 27 | 1L << 36 | 1L << 45 | 1L << 54;
	private static final long MSBS_STEP_9 = 0x100L * ONES_STEP_9;

	private final int[] inventory;
	private final int onesPerInventory;
	private final int log2OnesPerInventory;
	private final long numOnes;
	private final int numWords;
	private transient long[] bits;
	private final long[] count;
	private final Rank9 rank9;

	public HintedBsearchSelect(final Rank9 rank9) {
		this.rank9 = rank9;
		numOnes = rank9.numOnes;
		numWords = rank9.numWords;
		bits = rank9.bits;
		count = rank9.count;

		log2OnesPerInventory = rank9.bitVector.length() == 0 ? 0 : Fast.mostSignificantBit((numOnes * 16 * 64 + rank9.bitVector.length() - 1) / rank9.bitVector.length());
		onesPerInventory = 1 << log2OnesPerInventory;
		final int inventorySize = (int)((numOnes + onesPerInventory - 1) / onesPerInventory);

		inventory = new int[inventorySize + 1];
		long d = 0;
		final long mask = onesPerInventory - 1;
		for(int i = 0; i < numWords; i++)
			for (int j = 0; j < Long.SIZE; j++)
				if ((bits[i] & 1L << j) != 0) {
					if ((d & mask) == 0) inventory[(int)(d >> log2OnesPerInventory)] = (i >>> 3) << 1;
					d++;
				}

		inventory[inventorySize] = (numWords >>> 3) * 2;
	}

	@Override
	public long select(final long rank) {
		if (rank >= numOnes) return -1;

		final long[] count = this.count;
		final int[] inventory = this.inventory;
		final int inventoryIndexLeft = (int)(rank >>> log2OnesPerInventory);
		int blockLeft = inventory[inventoryIndexLeft];
		int blockRight = inventory[inventoryIndexLeft + 1];

		if (rank >= count[blockRight]) {
			blockRight = (blockLeft = blockRight) + 2;
		}
		else {
			int blockMiddle;

			while(blockRight - blockLeft > 2) {
				blockMiddle = ((blockRight + blockLeft) >>> 1) & ~1;
				if (rank >= count[blockMiddle]) blockLeft = blockMiddle;
				else blockRight = blockMiddle;
			}
		}

		final long rankInBlock = rank - count[blockLeft];

		final long rankInBlockStep9 = rankInBlock * ONES_STEP_9;
		final long subcounts = count[blockLeft + 1];
		final long offsetInBlock = (((((((rankInBlockStep9 | MSBS_STEP_9) - (subcounts & ~MSBS_STEP_9)) | (subcounts ^ rankInBlockStep9)) ^ (subcounts & ~rankInBlockStep9)) & MSBS_STEP_9) >>> 8) * ONES_STEP_9 >>> 54 & 0x7);

		final long word = (blockLeft << 2) + offsetInBlock;
		final long rankInWord = rankInBlock - (subcounts >>> (offsetInBlock - 1 & 7) * 9 & 0x1FF);

		return LongBigArrayBitVector.bits(word) + Fast.select(bits[(int)word], (int)rankInWord);
	}

	@Override
	public long numBits() {
		return rank9.numBits() + inventory.length * (long)Integer.SIZE;
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
