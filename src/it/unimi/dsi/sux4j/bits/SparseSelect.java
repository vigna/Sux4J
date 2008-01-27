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

import it.unimi.dsi.fastutil.longs.LongIterator;

/** An opportunistic select implementation for sparse arrays. 
 * 
 * <p>The code is based on a 64-bit reimplementation of the <code>sdarray</code> structure
 * described by Daisuke Okanohara and Kunihiko Sadakane in &ldquo;Practical Entropy-CompressedRank/SelectDictionary&rdquo;, In <i>Proc. of the 
 * Workshop on Algorithm Engineering and Experiments, ALENEX 2007</i>. SIAM, 2007.
 * 
 * <p>The positions of the <var>{@linkplain #m}</var> ones in a bit array of <var>{@linkplain #n}</var> bits are stored explicitly 
 * by storing separately 
 * the lower <var>{@linkplain #l}</var> = &lceil; log <var>{@linkplain #n}</var> /  <var>{@linkplain #m} )</var> &rceil; bits
 * and the remaining upper bits.
 * The lower bits are stored in a bit array, whereas the upper bits are stored in an array
 * of 2<var>{@linkplain #m}</var> bits by setting, if the <var>i</var>-th one is at position
 * <var>p</var>, the bit of index <var>p</var> / 2<sup><var>l</var></sup> + <var>i</var>; the value can then be recovered
 * by selecting the <var>i</var>-th bit of the resulting dense (but small) bit array and subtracting <var>i</var> (note that this will
 * work because the upper bits are nondecreasing).
 * 
 * <p>This implementation uses {@link SimpleSelect} to support selection inside the dense array. The resulting data structure uses 
 * <var>m</var> &lceil; log(<var>n</var>/<var>m</var>) &rceil; + 2.25 <var>m</var> bits, and <em>does not store the original bit vector</em>.
 * 
 * <p>Note that some data is shared with {@link SparseRank}: correspondingly, a suitable {@linkplain #SparseSelect(SparseRank) constructor}
 * makes it possible to build an instance using an underlying {@link SparseRank} instance.
 */

public class SparseSelect implements Select {
	private static final long serialVersionUID = 1L;
	
	/** The length of the underlying bit array. */
	protected final long n;
	/** The number of ones in the underlying bit array. */
	protected final long m;
	/** The number of lower bits. */
	protected final int l;
	/** The list of lower bits of the position of each one, stored explicitly. */
	protected final LongBigList lowerBits;
	/** The select structure used to extract the upper bits. */ 
	protected final SimpleSelect selectUpper;

	/** Creates a new <code>sdarray</code> select structure using a long array.
	 * 
	 * <p>The resulting structure keeps no reference to the original array.
	 * 
	 * @param bits a long array containing the bits.
	 * @param length the number of valid bits in <code>bits</code>.
	 */
	public SparseSelect( final long[] bits, final long length ) {
		this( LongArrayBitVector.wrap( bits, length ) );
	}
	
	/** Creates a new <code>sdarray</code> select structure using a bit vector.
	 * 
	 * <p>The resulting structure keeps no reference to the original bit vector.
	 * 
	 * @param bitVector the input bit vector.
	 */
	public SparseSelect( final BitVector bitVector ) {
		this( bitVector.length(), bitVector.count(), bitVector.asLongSet().iterator() );
	}
	
	
	/** Creates a new <code>sdarray</code> select structure using an {@linkplain LongIterator iterator}.
	 * 
	 * <p>This constructor is particularly useful if the positions of the ones are provided by
	 * some sequential source.
	 *
	 * @param n the number of bits in the underlying bit vector.
	 * @param m the number of ones in the underlying bit vector.
	 * @param iterator an iterator returning the positions of the ones in the underlying bit vector in increasing order.
	 */
	public SparseSelect( final long n, long m, final LongIterator iterator ) {
		long pos = -1;
		this.m = m;
		this.n = n;
		int l = 0;
		if ( m > 0 ) {
			while( m < n ) {
				m *= 2;
				l++;
			}
		}
		this.l = l;
		final long lowerBitsMask = ( 1L << l ) - 1;
		lowerBits = LongArrayBitVector.getInstance().asLongBigList( l ).length( this.m );
		final BitVector upperBits = LongArrayBitVector.getInstance( this.m * 2 ).length( this.m * 2 );
		long last = 0;
		for( long i = 0; i < this.m; i++ ) {
			pos = iterator.nextLong();
			if ( pos >= n ) throw new IllegalArgumentException( "Position too large for " + n + " bits: " + pos );
			if ( pos < last ) throw new IllegalArgumentException( "Positions are not nondecreasing: " + pos + " < " + last );
			if ( l != 0 ) lowerBits.set( i, pos & lowerBitsMask );
			upperBits.set( ( pos >> l ) + i );
			last = pos;
		}
		
		if ( iterator.hasNext() ) throw new IllegalArgumentException( "There are more than " + this.m + " positions in the provided iterator" );
		
		selectUpper = new SimpleSelect( upperBits );
	}
	
	/** Creates a new <code>sdarray</code> select structure using a {@link SparseRank}.
	 *
	 * @param sparseRank a sparse rank structure.
	 */
	public SparseSelect( final SparseRank sparseRank ) {
		n = sparseRank.n;
		m = sparseRank.m;
		l = sparseRank.l;
		lowerBits = sparseRank.lowerBits;
		selectUpper = new SimpleSelect( sparseRank.upperBits, sparseRank.selectZeroUpper.length() );
	}
	

	public long numBits() {
		return selectUpper.numBits() + selectUpper.length() + lowerBits.size() * l;
	}

	/** Returns the bit indexed as an array of longs; since the bits are not stored in this data structure,
	 * a copy is built on purpose and returned.
	 * 
	 * @return a copy of the underlying bit vector.
	 */
	
	public long[] bits() {
		final LongArrayBitVector result = LongArrayBitVector.getInstance( n ).length( n );
		for( long i = m; i-- != 0; ) result.set( select( i ) );
		return result.bits();
	}

	public long length() {
		return n;
	}

	public long select( final long rank ) {
		if ( rank >= m ) return -1;
		if ( l == 0 ) return ( selectUpper.select( rank ) - rank );
		return ( selectUpper.select( rank ) - rank ) << l | lowerBits.getLong( rank );
	}
}
