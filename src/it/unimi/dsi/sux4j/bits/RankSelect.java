package it.unimi.dsi.sux4j.bits;

import java.io.Serializable;

/** A serialisation-oriented container for associated rank/select(zero) structures.
 *  
 *  <p>Since structures in Sux4J serialise all contained data, including, if necessary, the underlying bit vector,
 *  serialising separately a rank and a select structure might result in storing the underlying bit
 *  vector twice. This class provide a simple solution by providing one-shot serialisation of
 *  all structures related to a bit vector. As a commodity, it provides also delegate methods, albeit
 *  the suggested usage is deserialisation and extraction of non-<code>null</code> structures.  
 *  
 */
public class RankSelect implements Rank, Select, SelectZero, Serializable {

	private static final long serialVersionUID = 1L;
	/** A rank structure, or <code>null</code>. */
	public final Rank rank;
	/** A select structure, or <code>null</code>. */
	public final Select select;
	/** A zero-select structure, or <code>null</code>. */
	public final SelectZero selectZero;

	/** Creates a new rank/select container using the given structures.
	 * 
	 * @param rank a rank structure, or <code>null</code>. 
	 * @param select a select structure, or <code>null</code>. 
	 * @param selectZero a zero-select structure, or <code>null</code>.
	 */
	public RankSelect( final Rank rank, final Select select, final SelectZero selectZero ) {
		this.rank = rank;
		this.select = select;
		this.selectZero = selectZero;
	}
	
	/** Creates a new rank/select container without zero selection using the given structures.
	 * 
	 * @param rank a rank structure, or <code>null</code>. 
	 * @param select a select structure, or <code>null</code>. 
	 */
	public RankSelect( final Rank rank, final Select select ) {
		this( rank, select, null );
	}

	public long[] bits() {
		return rank.bits();
	}

	public long count() {
		return rank.count();
	}

	public long length() {
		return rank.length();
	}

	public long numBits() {
		return rank.numBits();
	}

	public long rank( final long from, final long to ) {
		return rank.rank( from, to );
	}

	public long rank( final long pos ) {
		return rank.rank( pos );
	}

	public long rankZero( final long from, final long to ) {
		return rank.rankZero( from, to );
	}

	public long rankZero( final long pos ) {
		return rank.rankZero( pos );
	}

	public long select( final long rank ) {
		return select.select( rank );
	}

	public long selectZero( final long rank ) {
		return selectZero.selectZero( rank );
	}

}
