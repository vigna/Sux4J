package it.unimi.dsi.sux4j.bits;

import java.io.Serializable;

/** A container for associated rank/select(zero) structures.
 *  
 */
public class RankSelect implements Rank, Select, SelectZero, Serializable {

	private static final long serialVersionUID = 1L;
	public final Rank rank;
	public final Select select;
	public final SelectZero selectZero;

	public RankSelect( final Rank rank, final Select select, final SelectZero selectZero ) {
		this.rank = rank;
		this.select = select;
		this.selectZero = selectZero;
	}
	
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
