package it.unimi.dsi.sux4j.bits;

import java.io.Serializable;

/** A data structure providing selecting over a bit array.
 * 
 * <p>Selection is a basic building blocks for most succinct data structures. Usually,
 * instances of this class class provide quick (e.g., constant time) selection.
 * 
 * <p>There is some variance in the literature about the exact semantics of selection&mdash;in most
 * cases, it is a matter of off-by-ones. This interface specifies a zero-based selection.
 * 
 * <p>More precisely, select is applied to a bit vector in which bits <em>positions</em> are numbered
 * starting from <em>zero</em>. Then, <code>select(r)</code> is the position of the 
 * leftmost bit set to one and preceded by <code>r</code> bits.
 *
 * @see RankSelect
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

	/** Returns the position of the bit of given rank beyond a specified position.
	 * 
	 * @param from a starting position.
	 * @param rank a rank.
	 * @return the position of the bit of given rank starting from <code>from</code>; 
	 * if no such position exists, <code>from</code> &minus;1 is returned.
	 */
	public long select( long from, long rank );
	
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
