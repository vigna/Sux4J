package it.unimi.dsi.sux4j.bits;


/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2009-2014 Sebastiano Vigna 
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


import it.unimi.dsi.bits.BitVector;

import java.io.Serializable;

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
	public long findOpen( long pos );

	/** Returns the position of the matching closed parenthesis (optional operation).
	 * 
	 * <p>Note that if you do not implement this method you <strong>must</strong>
	 * implement {@link #findOpen(long)}.
	 * 
	 * @param pos a position in the bit vector containing an open parenthesis (a one).
	 * @return the position of the matching open parenthesis.
	 */
	public long findClose( long pos );

	/** Returns the position of the open parenthesis of the pair the most
	 * tightly encloses the given position (optional operation).
	 * 
	 * @param pos a position in the bit vector.
	 * @return the position of the open parenthesis of the pair the most
	 * tightly encloses the given position.
	 */
	public long enclose( long pos );

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
