package it.unimi.dsi.sux4j.bits;

import java.io.Serializable;

/** A data structure providing rank and select over a bit array.
 * 
 * <p>Rank and select are a basic building blocks for most succinct data structures. Usually,
 * instances of this class class provide quick (e.g., constant time) computation of both primitives.
 * 
 * <p>Besides the standard {@link #rank(long)} and {@link #select(long)} methods, 
 * other natural derived operations are defined in terms of the former: for simplicity,
 * they are implemented in {@link AbstractRankAndSelect}.
 *  
 * <p>There is some variance in the literature about the exact semantics of rank and select&mdash;in most
 * cases, it is a matter of off-by-ones. This interface specifies a zero-based set of rank/select operations.
 * 
 * <p>More precisely, rank and select are applied to a bit vector in which bits <em>positions</em> are numbered
 * starting from <em>zero</em>. The {@link #rank(long)} of a bit is the number of ones that <em>precede</em> it (the bit
 * itself is not included). Given a rank <code>r</code>, {@link #select(long)} returns the position of the
 * bit set to one (assuming there is one)
 * satisfying <code>rank(select(r))=r</code>. In other words, <code>select(r)</code> is the position of the 
 * leftmost bit set to one and preceded by <code>r</code> bits.
 * 
 * <p>The following properties always hold:
 * <ul>
 *  	<li><code>rank(0)=0</code>;
 *  	<li><code>rank(length())</code> is the number of ones in the bit vector;
 *  	<li>if <code>r &lt; rank(length())</code>, then <code>rank(select(r))==r</code>;
 *  	<li>if <code>r &ge; rank(length())</code>, then <code>select(r)=-1</code> is undefined;
 *  	<li>if <code>p &le; length()</code>, then <code>select(rank(p))&lt;=p</code>, and equality
 *  	holds iff there is a one at position <code>p</code>.
 *  </ul>
 *
 */
public interface RankSelect extends Serializable {

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
	
	/** Returns the position of the last one in the bit vector.
	 * 
	 * @return the position of the last one in the bit vector (if the vector contains only
	 * zeroes, &minus;1 is returned).
	 */
	public long lastOne();

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
