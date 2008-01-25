package it.unimi.dsi.sux4j.bits;

import java.io.Serializable;

/** A data structure providing selecting zeroes over a bit array.
 * 
 * <p>This interface has essentially the same specification as that of {@link Select}, but
 * the method {@link #selectZero(long)} selects zeroes instead of ones. Ranking zeroes
 * is trivial (and trivially implemented in {@link AbstractRank}), but selecting
 * zeroes requires specific data structures.
 * 
 * @see Select
 */
public interface SelectZero extends Serializable {

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
	public long selectZero( long rank );

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
