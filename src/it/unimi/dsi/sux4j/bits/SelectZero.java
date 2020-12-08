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
 * A data structure providing zero selection over a bit array.
 *
 * <p>
 * This interface has essentially the same specification as that of {@link Select}, but the method
 * {@link #selectZero(long)} selects zeroes instead of ones. Ranking zeroes is trivial (and
 * trivially implemented in {@link AbstractRank}), but selecting zeroes requires specific data
 * structures.
 *
 * <p>
 * <strong>Warning</strong>: from Sux4J 5.2.0, the {@link #selectZero(long)} method is no longer
 * required to return &minus;1 when no bit with the given rank exists. If you relied on such
 * behavior, please test the argument before calling {@link #selectZero(long)}. Implementations
 * might provide assertions to check the argument for correctness.
 *
 * @see Select
 */
public interface SelectZero extends Serializable {

	/**
	 * Returns the position of the bit of given zero rank. Equivalently, returns the greatest position
	 * that is preceded by the specified number of zeroes.
	 *
	 * <p>
	 * <strong>Warning</strong>: from Sux4J 5.2.0, this method is no longer required to return &minus;1
	 * when no bit with the given rank exists. If you relied on such behavior, please test the argument
	 * before calling this method. Implementations might provide assertions to check the argument for
	 * correctness.
	 *
	 * @param zeroRank a zero rank.
	 * @return the position of the bit of given zero rank; if no such bit exists, behavior is undefined
	 *        .
	 */
	public long selectZero(long zeroRank);

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
