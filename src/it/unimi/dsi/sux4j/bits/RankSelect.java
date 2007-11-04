package it.unimi.dsi.sux4j.bits;


/** A data structure providing rank and select over a bit array.
 * 
 * <p>This interface combines {@link Rank} and {@link Select}.
 * Besides the standard {@link #rank(long)} and {@link #select(long)} methods, 
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
public interface RankSelect extends Rank, Select {

	/** Returns the position of the last one in the bit vector.
	 * 
	 * @return the position of the last one in the bit vector (if the vector contains only
	 * zeroes, &minus;1 is returned).
	 */
	public long lastOne();

}
