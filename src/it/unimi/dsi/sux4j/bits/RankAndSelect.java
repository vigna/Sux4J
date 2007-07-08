package it.unimi.dsi.sux4j.bits;

import java.io.Serializable;

/** A basic building block for most succinct data structures. 
 * 
 * <p>A instance of this class provide quick (e.g., constant time) computation
 * of {@link #rank(long)} and {@link #select(long)} on a vector of {@link #length()} bits.
 * Other derived operations are defined in terms of the former: for simplicity,
 * they are implemented in {@link AbstractRankAndSelect}.
 *  
 * <p>The following properties always hold:
 * <ul>
 *  	<li><code>rank(length()-1)</code> is the number of ones in the vector;
 *  	<li>if <code>r&le;rank(length()-1)</code>, then <code>rank(select(r))==r</code>;
 *  	<li>if <code>p&lt;length()</code>, then <code>select(rank(p))&lt;=p</code>, and equality
 *  	holds iff there is a one at position <code>p</code>.
 *  </ul>
 *
 */
public interface RankAndSelect extends Serializable {

	/** Returns the number of bits in the bit vector indexed by this class.
	 * 
	 * @return number of bits in the bit vector indexed by this class.
	 */
	public long length();

	/** Returns the number of ones in the bit vector indexed by this class.
	 * 
	 * @return number of bits in the bit vector indexed by this class.
	 */
	public long count();

	/** Returns the number of ones up to the specified position, inclusive.
	 * 
	 * @param pos a position in the bit vector, or -1.
	 * @return the number of ones up to <code>pos</code>, inclusive (if <code>pos</code> is -1, 0 is returned).
	 */
	public long rank( long pos );

	/** Returns the number of ones in the specified interval.
	 * 
	 * @param from a position in the bit vector.
	 * @param to a position in the bit vector.
	 * @return the number of ones between <code>from</code> and <code>to</code>, inclusive (if
	 * <code>to</code> is smaller than <code>from</code>, 0 is returned).
	 */
	public long rank( long from, long to );

	/** Returns the least position that is preceeded by the specified number of ones.
	 * 
	 * @param rank a number of bits.
	 * @return the least position <code>pos</code> such that there are
	 * <code>rank</code> ones between 0 and <code>pos</code>, inclusive (if no such position exists, 
	 * or <code>rank</code> is 0, -1 is returned).
	 */
	public long select( long rank );

	/** Returns the least position that is preceeded by the specified number of ones starting from
	 * the specified point
	 * 
	 * @param from a starting position.
	 * @param rank a number of bits.
	 * @return the least position <code>pos</code> such that there are
	 * <code>rank</code> ones between <code>from</code> and <code>pos</code>, inclusive (if no such position exists, 
	 * or <code>rank</code> is 0, <code>from</code>&minus;1 is returned).
	 */
	public long select( long from, long rank );
	
	/** Returns the position of the last one in the bit vector.
	 * 
	 * @return  the position of the last one in the bit vector (if the vector contains only
	 * zeroes, -1 is returned).
	 */
	public long lastOne();

	/** Returns the bits indexed as an array of longs, not to be modified.
	 * 
	 * @return an array of longs whose first {@link #length()} bits contain the bits of
	 * this bit vector. The array cannot be modified.
	 */
	public long[] bits();

}
