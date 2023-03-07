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

import java.io.Serializable;

import it.unimi.dsi.bits.BitVector;

/**
 * A data structure providing selection over a bit array.
 *
 * <p>
 * Selection is a basic building blocks for most succinct data structures. Usually, instances of
 * this class class provide quick (e.g., constant-time) selection.
 *
 * <p>
 * This interface specifies a zero-based selection. More precisely, select is applied to a bit
 * vector in which bits positions are numbered starting from zero. Then, {@link #select(long)
 * select(r)} is the position of the leftmost bit set to one and preceded by {@code r} ones. There
 * are also default bulk methods {@link #select(long, long[], int, int)} and {@link #select(long)}
 * whose implementation delegates to {@link #select(long)} but might be implemented more
 * efficiently.
 *
 * <p>
 * A number of equations link {@link Rank#rank(long) rank()} and {@link #select(long) select()}:
 * <ul>
 * <li><code>rank(0)=0</code>;
 * <li><code>rank(length())</code> is the number of ones in the bit vector;
 * <li>if <code>r &lt; rank(length())</code>, then <code>rank(select(r))==r</code>;
 * <li>if <code>p &le; length()</code>, then <code>p&le;select(rank(p))</code>, and equality holds
 * iff there is a one at position <code>p</code>.
 * </ul>
 *
 * @apiNote From Sux4J 5.2.0, the {@link #select(long)} method is no longer required to return
 *          &minus;1 when no bit with the given rank exists. If you relied on such behavior, please
 *          test the argument before calling {@link #select(long)}. Implementations might provide
 *          assertions to check the argument for correctness.
 * 
 * @see SelectZero
 */
public interface Select extends Serializable {

	/**
	 * Returns the position of the bit of given rank. Equivalently, returns the greatest position that
	 * is preceded by the specified number of ones.
	 *
	 * @apiNote From Sux4J 5.2.0, this method is no longer required to return &minus;1 when no bit with
	 *          the given rank exists. If you relied on such behavior, please test the argument before
	 *          calling this method. Implementations might provide assertions to check the argument for
	 *          correctness.
	 *
	 * @param rank a rank.
	 * @return the position of the bit of given rank; if no such bit exists, behavior is undefined .
	 */
	public long select(long rank);

	/**
	 * Performs a bulk select of consecutive ranks into a given array fragment.
	 *
	 * @apiNote Implementations are allowed to require that {@code dest} be of length greater than
	 *          {@code offset} even if {@code length} is zero.
	 *
	 * @implSpec This implementation just makes multiple calls to {@link #select(long)}.
	 *
	 * @param rank the first rank to select.
	 * @param dest the destination array; it will be filled with {@code length} positions of consecutive
	 *            bits starting at position {@code offset}; must be of length greater than
	 *            {@code offset}.
	 * @param offset the first bit position written in {@code dest}.
	 * @param length the number of bit positions in {@code dest} starting at {@code offset}.
	 * @return {@code dest}
	 * @see #select(long, long[])
	 */
	public default long[] select(final long rank, final long[] dest, final int offset, final int length) {
		for (int i = 0; i < length; i++) dest[offset + i] = select(rank + i);
		return dest;
	}

	/**
	 * Performs a bulk select of consecutive ranks into a given array.
	 *
	 * @apiNote Implementations are allowed to require that {@code dest} be of length greater than zero.
	 *
	 * @implSpec This implementation just delegates to {@link #select(long, long[], int, int)}.
	 *
	 * @param rank the first rank to select.
	 * @param dest the destination array; it will be filled with position of consecutive bits.
	 * @return {@code dest}
	 * @see #select(long, long[], int, int)
	 */
	public default long[] select(final long rank, final long[] dest) {
		final int length = dest.length;
		if (length == 0) return dest;
		return select(rank, dest, 0, dest.length);
	}

	/**
	 * Returns the bit vector indexed by this structure.
	 *
	 * <p>
	 * Note that you are not supposed to modify the returned vector.
	 *
	 * @return the bit vector indexed by this structure.
	 */
	public BitVector bitVector();

	/**
	 * Returns the overall number of bits allocated by this structure.
	 *
	 * @return the overall number of bits allocated by this structure (not including the bits of the
	 *         {@linkplain #bitVector() indexed vector}).
	 */

	public long numBits();
}
