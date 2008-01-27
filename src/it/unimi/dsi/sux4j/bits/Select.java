package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import java.io.Serializable;

/** A data structure providing selection over a bit array.
 * 
 * <p>Selection is a basic building blocks for most succinct data structures. Usually,
 * instances of this class class provide quick (e.g., constant time) selection.
 * 
 * <p>There is some variance in the literature about the exact semantics of selection&mdash;in most
 * cases, it is a matter of off-by-ones. This interface specifies a zero-based selection.
 * 
 * <p>More precisely, select is applied to a bit vector in which bits <em>positions</em> are numbered
 * starting from <em>zero</em>. Then, <code>select(r)</code> is the position of the 
 * leftmost bit set to one and preceded by <code>r</code> ones.
 *
 * <p>A number of equations link {@link Rank#rank(long) rank()} and {@link #select(long) select()}:
 * <ul>
 *  	<li><code>rank(0)=0</code>;
 *  	<li><code>rank(length())</code> is the number of ones in the bit vector;
 *  	<li>if <code>r &lt; rank(length())</code>, then <code>rank(select(r))==r</code>;
 *  	<li>if <code>r &ge; rank(length())</code>, then <code>select(r)=-1</code>;
 *  	<li>if <code>p &le; length()</code>, then <code>select(rank(p))&lt;=p</code>, and equality
 *  	holds iff there is a one at position <code>p</code>.
 *  </ul>
 */
public interface Select extends Serializable {

	/** Returns the number of bits in the bit vector indexed by this class.
	 * 
	 * @return number of bits in the bit vector indexed by this class.
	 */
	public long length();

	/** Returns the position of the bit of given rank. 
	 *  Equivalently, returns the greatest position that is preceded by the specified number of ones.
	 * 
	 * @param rank a rank.
	 * @return the position of the bit of given rank; if no such position exists, &minus;1 is returned.
	 */
	public long select( long rank );

	/** Returns the bits indexed as an array of longs (not to be modified).
	 * 
	 * <p>The returned array must follow the {@link LongArrayBitVector} conventions.
	 * 
	 * @return an array of longs whose first {@link #length()} bits contain the bits of
	 * this bit vector. The array cannot be modified.
	 */
	public long[] bits();
	
	/** Returns the overall number of bits allocated by this structure. 
	 * 
	 * @return the overall number of bits allocated by this structure (not including {@link #bits()}).
	 */

	public long numBits();
}
