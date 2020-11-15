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

import java.io.Serializable;

import it.unimi.dsi.bits.BitVector;

/**
 * A data structure providing ranking over a bit array.
 *
 * <p>
 * Ranking is a basic building blocks for most succinct data structures. Usually, instances of this
 * class class provide quick (e.g., constant-time) ranking.
 *
 * <p>
 * This interface specifies a zero-based ranking. More precisely, rank is applied to a bit vector in
 * which bits positions are numbered starting from zero. Then, {@link #rank(long) rank(p)} is the
 * number of of ones that precede position {@code p} (the bit at position {@code p} is not included
 * in the count).
 *
 * <p>
 * The following properties always hold:
 * <ul>
 * <li><code>rank(0)=0</code>;
 * <li><code>rank(length())</code> is the number of ones in the bit vector.
 * </ul>
 *
 * @see Select
 *
 */
public interface Rank extends Serializable {

	/** Returns the number of ones in the bit vector indexed by this class.
	 *
	 * @return number of ones in the bit vector indexed by this class.
	 */
	public long count();

	/**
	 * Returns the number of ones preceding the specified position.
	 *
	 * @param pos a position in the bit vector between 0 (inclusive) and the length of the bit vector
	 *            (inclusive).
	 * @return the number of ones preceding position {@code pos}; if {@code pos} is out of bounds,
	 *         behavior is undefined.
	 */
	public long rank(long pos);

	/**
	 * Returns the number of ones in the specified interval.
	 *
	 * @param from a position in the bit vector between 0 (inclusive) and the length of the bit vector
	 *            (inclusive).
	 * @param to a position in the bit vector between 0 (inclusive) and the length of the bit vector
	 *            (inclusive); must be greater than or equal to {@code from}.
	 * @return the number of ones between <code>from</code> (inclusive) and <code>to</code> (exclusive);
	 *         if the parameters are out of bounds, behavior is undefined.
	 */
	public long rank(long from, long to);

	/**
	 * Returns the number of zeroes preceding the specified position.
	 *
	 * @param pos a position in the bit vector between 0 (inclusive) and the length of the bit vector
	 *            (inclusive).
	 * @return the number of zeroes preceding {@code pos}; if {@code pos} is out of bounds, behavior is
	 *         undefined.
	 */
	public long rankZero(long pos);

	/**
	 * Returns the number of zeroes in the specified interval.
	 *
	 * @param from a position in the bit vector between 0 (inclusive) and the length of the bit vector
	 *            (inclusive).
	 * @param to a position in the bit vector between 0 (inclusive) and the length of the bit vector
	 *            (inclusive); must be greater than or equal to {@code from}.
	 * @return the number of zeros between <code>from</code> (inclusive) and <code>to</code>
	 *         (exclusive); if the parameters are out of bounds, behavior is undefined (might throw an
	 *         exception).
	 */
	public long rankZero(long from, long to);

	/**
	 * Returns the bit vector indexed by this structure.
	 *
	 * <p>
	 * Note that you must not modify the returned vector.
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
