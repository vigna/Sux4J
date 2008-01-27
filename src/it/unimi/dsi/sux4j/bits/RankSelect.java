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

/** A serialisation-oriented container for associated rank/select(zero) structures.
 *  
 *  <p>Since structures in Sux4J serialise all contained data, including, if necessary, the underlying bit vector,
 *  serialising separately a rank and a select structure might result in storing the underlying bit
 *  vector twice. This class provide a simple solution by allowing one-shot serialisation of
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
