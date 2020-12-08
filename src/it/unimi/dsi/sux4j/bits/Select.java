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
 * select(r)} is the position of the leftmost bit set to one and preceded by {@code r} ones.
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
 * <p>
 * <strong>Warning</strong>: from Sux4J 5.2.0, the {@link #select(long)} method is no longer
 * required to return &minus;1 when no bit with the given rank exists. If you relied on such
 * behavior, please test the argument before calling {@link #select(long)}. Implementations might
 * provide assertions to check the argument for correctness.
 */
public interface Select extends Serializable {

	/**
	 * Returns the position of the bit of given rank. Equivalently, returns the greatest position that
	 * is preceded by the specified number of ones.
	 *
	 * <p>
	 * <strong>Warning</strong>: from Sux4J 5.2.0, this method is no longer required to return &minus;1
	 * when no bit with the given rank exists. If you relied on such behavior, please test the argument
	 * before calling this method. Implementations might provide assertions to check the argument for
	 * correctness.
	 *
	 * @param rank a rank.
	 * @return the position of the bit of given rank; if no such bit exists, behavior is undefined
	 *        .
	 */
	public long select(long rank);

	/** Returns the bit vector indexed by this structure.
	 *
	 * <p>Note that you are not supposed to modify the returned vector.
	 *
	 * @return the bit vector indexed by this structure.
	 */
	public BitVector bitVector();

	/** Returns the overall number of bits allocated by this structure.
	 *
	 * @return the overall number of bits allocated by this structure (not including the bits
	 * of the {@linkplain #bitVector() indexed vector}).
	 */

	public long numBits();
}
