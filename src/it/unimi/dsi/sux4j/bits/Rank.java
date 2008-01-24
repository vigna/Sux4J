package it.unimi.dsi.sux4j.bits;

import java.io.Serializable;

/** A data structure providing rank over a bit array.
 * 
 * <p>Ranking is a basic building blocks for most succinct data structures. Usually,
 * instances of this class class provide quick (e.g., constant time) ranking.
 *  
 * <p>There is some variance in the literature about the exact semantics of ranking&mdash;in most
 * cases, it is a matter of off-by-ones. This interface specifies a zero-based ranking.
 * 
 * <p>More precisely, rank is applied to a bit vector in which bits <em>positions</em> are numbered
 * starting from <em>zero</em>. The {@link #rank(long)} of a bit is the number of ones that <em>precede</em> it (the bit
 * itself is not included).
 * 
 * <p>The following properties always hold:
 * <ul>
 *  	<li><code>rank(0)=0</code>;
 *  	<li><code>rank(length())</code> is the number of ones in the bit vector.
 *  </ul>
 *  
 *  @see Select
 *
 */
public interface Rank extends Serializable {

	/** Returns the number of bits in the bit vector indexed by this class.
	 * 
	 * @return number of bits in the bit vector indexed by this class.
	 */
	public long length();

	/** Returns the number of ones in the bit vector indexed by this class.
	 * 
	 * @return number of ones in the bit vector indexed by this class.
	 */
	public long count();

	/** Returns the number of ones preceding the specified position.
	 * 
	 * @param pos a position in the bit vector.
	 * @return the number of ones preceding <code>pos</code>.
	 */
	public long rank( long pos );

	/** Returns the number of ones in the specified interval.
	 * 
	 * @param from a position in the bit vector.
	 * @param to a position in the bit vector.
	 * @return the number of ones between <code>from</code> (inclusive) and <code>to</code> (exclusive); if
	 * <code>to</code> is smaller than <code>from</code>, 0 is returned.
	 */
	public long rank( long from, long to );

	/** Returns the number of zeroes preceding the specified position.
	 * 
	 * @param pos a position in the bit vector.
	 * @return the number of zeroes preceding <code>pos</code>.
	 */
	public long rankZero( long pos );

	/** Returns the number of zeroes in the specified interval.
	 * 
	 * @param from a position in the bit vector.
	 * @param to a position in the bit vector.
	 * @return the number of zeroes between <code>from</code> (inclusive) and <code>to</code> (exclusive); if
	 * <code>to</code> is smaller than <code>from</code>, 0 is returned.
	 */
	public long rankZero( long from, long to );

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
