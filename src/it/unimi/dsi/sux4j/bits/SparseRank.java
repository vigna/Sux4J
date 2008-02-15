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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.util.LongBigList;

/** An opportunistic rank implementation for sparse arrays. 
 * 
 * <p>Please see the {@link SparseSelect} class documentation for some reference on the inner workings of this class.
 * 
 * <p>Note that some data is shared with {@link SparseSelect}: correspondingly, a suitable {@linkplain #SparseRank(SparseSelect) constructor}
 * makes it possible to build an instance using an underlying {@link SparseSelect} instance.
 */

public class SparseRank extends AbstractRank {
	private static final long serialVersionUID = 2L;
	
	/** The length of the underlying bit array. */
	protected final long n;
	/** The number of ones in the underlying bit array. */
	protected final long m;
	/** The number of lower bits. */
	protected final int l;
	/** The mask for lower bits. */
	protected final long lowerLBitsMask;
	/** The list of lower bits of the position of each one, stored explicitly. */
	protected final LongBigList lowerBits;
	/** The upper bits. */
	protected final BitVector upperBits;
	/** The rank structure used to extract the upper bits. */ 
	protected final SimpleSelectZero selectZeroUpper;

	/** Creates a new <code>sdarray</code> rank structure using a long array.
	 * 
	 * <p>The resulting structure keeps no reference to the original array.
	 * 
	 * @param bits a long array containing the bits.
	 * @param length the number of valid bits in <code>bits</code>.
	 */
	public SparseRank( final long[] bits, final long length ) {
		this( LongArrayBitVector.wrap( bits, length ) );
	}
	
	/** Creates a new <code>sparse</code> rank structure using a bit vector.
	 * 
	 * <p>The resulting structure keeps no reference to the original bit vector.
	 * 
	 * @param bitVector the input bit vector.
	 */
	public SparseRank( final BitVector bitVector ) {
		this( bitVector.length(), bitVector.count(), bitVector.asLongSet().iterator() );
	}
	
	
	/** Creates a new <code>sparse</code> rank structure using an {@linkplain LongIterator iterator}.
	 * 
	 * <p>This constructor is particularly useful if the positions of the ones are provided by
	 * some sequential source.
	 *
	 * @param n the number of bits in the underlying bit vector.
	 * @param m the number of ones in the underlying bit vector.
	 * @param iterator an iterator returning the positions of the ones in the underlying bit vector in increasing order.
	 */
	public SparseRank( final long n, long m, final LongIterator iterator ) {
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
		lowerLBitsMask = ( 1L << l ) - 1;
		lowerBits = LongArrayBitVector.getInstance().asLongBigList( l ).length( this.m );
		upperBits = LongArrayBitVector.getInstance().length( this.m * 2 );
		long last = 0;
		for( long i = 0; i < this.m; i++ ) {
			pos = iterator.nextLong();
			if ( pos >= n ) throw new IllegalArgumentException( "Position too large for " + n + " bits: " + pos );
			if ( pos < last ) throw new IllegalArgumentException( "Positions are not nondecreasing: " + pos + " < " + last );
			if ( l != 0 ) lowerBits.set( i, pos & lowerLBitsMask );
			upperBits.set( ( pos >> l ) + i );
			last = pos;
		}
		
		if ( iterator.hasNext() ) throw new IllegalArgumentException( "There are more than " + this.m + " positions in the provided iterator" );
		
		selectZeroUpper = new SimpleSelectZero( upperBits );
	}

	
	/** Creates a new <code>sparse</code> rank structure using a {@link SparseSelect}.
	 *
	 * @param sparseSelect a sparse rank structure.
	 */
	public SparseRank( final SparseSelect sparseSelect ) {
		n = sparseSelect.n;
		m = sparseSelect.m;
		l = sparseSelect.l;
		lowerLBitsMask = ( 1L << l ) - 1;
		upperBits = sparseSelect.selectUpper.bitVector();
		lowerBits = sparseSelect.lowerBits;
		selectZeroUpper = new SimpleSelectZero( upperBits );
	}
	

	public long rank( final long pos ) {
		if ( m == 0 ) return 0;
		if ( pos >= n ) return m;
		final long posShiftrL = pos >>> l;

		long upperPos = selectZeroUpper.selectZero( posShiftrL );
	    long rank = upperPos - ( posShiftrL );
	    final long posLowerBits = pos & lowerLBitsMask;
	    
	    do {
	    	rank--; 
	    	upperPos--; 
	    } while( upperPos >= 0 && upperBits.getBoolean( upperPos ) && lowerBits.getLong( rank ) >= posLowerBits );

	    return ++rank;
	}

	public long numBits() {
		return selectZeroUpper.numBits() + upperBits.length() + lowerBits.size() * l;
	}

	/** Returns the bit vector indexed; since the bits are not stored in this data structure,
	 * a copy is built on purpose and returned.
	 * 
	 * <p><strong>Warning</strong>: this method is very slow, as the only way to recover the original bit
	 * array is to rank each position in the bit vector.
	 * 
	 * @return a copy of the underlying bit vector.
	 */
	public BitVector bitVector() {
		final LongArrayBitVector result = LongArrayBitVector.getInstance().length( n );
		long prev = 0, rank;
		for( long i = 1; i <= n; i++ ) {
			if ( ( rank = rank( i ) ) != prev ) result.set( i - 1 );
			prev = rank;
		}
		return result;
	}

}
