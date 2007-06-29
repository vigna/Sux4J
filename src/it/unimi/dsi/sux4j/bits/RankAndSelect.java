package it.unimi.dsi.sux4j;

/** A <em>rank-and-select</em> can be thought of as a vector of <var>n</var> bits, indexed
 *  from 0 to <var>n</var>&minus;1, that can be accessed via two basic methods:
 *  <ul>
 * 		<li>{@linkplain #rank(p)}: given an index <var>p</var>, find the number of 1s up to index <var>p</var> (inclusive);
 *  	<li>{@linkplain #select(r)}: given an integer <var>r</var>, find the index of the 1 whose rank is <var>r</var>.
 *  </ul>
 *  
 *  <p>The following properties always hold:
 *  <ul>
 *  	<li><pre>rank(n-1)</pre> is the number of 1s in the vector;
 *  	<li>if <pre>r&lt;=rank(n-1)</pre>, then <pre>rank(select(r))==r</pre>;
 *  	<li>if <pre>p&lt;n</pre>, then <pre>select(rank(p))&lt;=p</pre>, and equality
 *  	holds iff there is a 1 at position <pre>p</pre>.
 *  </ul>
 *
 */
public interface RankAndSelect {
	
	public long size();
	public long rank( long pos );
	public long select( long rank );
}
