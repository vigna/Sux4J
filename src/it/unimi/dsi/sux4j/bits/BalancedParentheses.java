/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2009-2021 Sebastiano Vigna
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

/** A data structure providing primitives for balanced parentheses
 * represented in a bit array.
 *
 * <p>A bit array of viewed by implementations of this class as a string of open (=one) and closed
 * (=zero) parentheses, which must be nested correctly. All operations are optional, but by contract
 * at least one of {@link #findOpen(long)} and {@link #findClose(long)} <strong>must</strong> be
 * provided.
 */
public interface BalancedParentheses extends Serializable {

	/** Returns the position of the matching open parenthesis (optional operation).
	 *
	 * <p>Note that if you do not implement this method you <strong>must</strong>
	 * implement {@link #findClose(long)}.
	 *
	 * @param pos a position in the bit vector containing a closed parenthesis (a zero).
	 * @return the position of the matching open parenthesis.
	 */
	public long findOpen(long pos);

	/** Returns the position of the matching closed parenthesis (optional operation).
	 *
	 * <p>Note that if you do not implement this method you <strong>must</strong>
	 * implement {@link #findOpen(long)}.
	 *
	 * @param pos a position in the bit vector containing an open parenthesis (a one).
	 * @return the position of the matching open parenthesis.
	 */
	public long findClose(long pos);

	/** Returns the position of the open parenthesis of the pair the most
	 * tightly encloses the given position (optional operation).
	 *
	 * @param pos a position in the bit vector.
	 * @return the position of the open parenthesis of the pair the most
	 * tightly encloses the given position.
	 */
	public long enclose(long pos);

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
