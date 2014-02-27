package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2014 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

/** A rank implementation for sparse bit arrays based on the {@linkplain EliasFanoMonotoneLongBigList Elias&ndash;Fano representation of monotone functions}. 
 * 
 * <p>Note that some data may be shared with {@link SparseSelect}: just use the factory method {@link SparseSelect#getRank()} to obtain an instance. In that
 * case, {@link #numBits()} counts just the new data used to build the class, and not the shared part.
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
	protected final long[] lowerBits;
	/** The upper bits. */
	protected final BitVector upperBits;
	/** The rank structure used to extract the upper bits. */ 
	protected final SimpleSelectZero selectZeroUpper;
	/** Whether this structure was built from a {@link SparseSelect} structure, and thus shares part of its internal state. */
	protected final boolean fromSelect;
	
	/** Creates a new rank structure using a long array.
	 * 
	 * <p>The resulting structure keeps no reference to the original array.
	 * 
	 * @param bits a long array containing the bits.
	 * @param length the number of valid bits in <code>bits</code>.
	 */
	public SparseRank( final long[] bits, final long length ) {
		this( LongArrayBitVector.wrap( bits, length ) );
	}
	
	/** Creates a new rank structure using a bit vector.
	 * 
	 * <p>The resulting structure keeps no reference to the original bit vector.
	 * 
	 * @param bitVector the input bit vector.
	 */
	public SparseRank( final BitVector bitVector ) {
		this( bitVector.length(), bitVector.count(), bitVector.asLongSet().iterator() );
	}
	
	/** Creates a new rank structure using an {@linkplain LongIterator iterator}.
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
		this.n = n;
		this.m = m;
		l = m == 0 ? 0 : Math.max( 0, Fast.mostSignificantBit( n / m ) );
		lowerLBitsMask = ( 1L << l ) - 1;
		final LongArrayBitVector lowerBitsVector = LongArrayBitVector.getInstance(); 
		final LongBigList lowerBitsList = lowerBitsVector.asLongBigList( l );
		lowerBitsList.size( m );
		upperBits = LongArrayBitVector.getInstance().length( m + ( n >>> l ) + 1 );
		long last = 0;
		for( long i = 0; i < m; i++ ) {
			pos = iterator.nextLong();
			if ( pos >= n ) throw new IllegalArgumentException( "Too large bit poisition: " + pos + " >= " + n );
			if ( pos < last ) throw new IllegalArgumentException( "Positions are not nondecreasing: " + pos + " < " + last );
			if ( l != 0 ) lowerBitsList.set( i, pos & lowerLBitsMask );
			upperBits.set( ( pos >> l ) + i );
			last = pos;
		}
		
		if ( iterator.hasNext() ) throw new IllegalArgumentException( "There are more than " + m + " positions in the provided iterator" );
		lowerBits = lowerBitsVector.bits();
		selectZeroUpper = new SimpleSelectZero( upperBits );
		fromSelect = false;
	}

	protected SparseRank( final long n, final long m, final int l, final long[] lowerBits, final BitVector upperBits ) {
		this.n = n;
		this.m = m;
		this.l = l;
		this.lowerLBitsMask = ( 1L << l ) - 1;
		this.lowerBits = lowerBits;
		this.upperBits = upperBits;
		this.selectZeroUpper = new SimpleSelectZero( upperBits );
		this.fromSelect = true;
	}

	private final long extractLowerBits( final long index ) {
		final int l = this.l;
		if ( l == 0 ) return 0;
		
		final long position = index * l; 
		final int startWord = (int)( position / Long.SIZE );
		final int startBit = (int)( position % Long.SIZE );
		final int totalOffset = startBit + l;
		final long result = lowerBits[ startWord ] >>> startBit;
		return ( totalOffset <= Long.SIZE ? result : result | lowerBits[ startWord + 1 ] << -startBit ) & lowerLBitsMask;
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
	    } while( upperPos >= 0 && upperBits.getBoolean( upperPos ) && extractLowerBits( rank ) >= posLowerBits );

	    return ++rank;
	}

	public long numBits() {
		return selectZeroUpper.numBits() + ( fromSelect ? 0 : upperBits.length() + lowerBits.length * (long)Long.SIZE );
	}
		
	/** Creates a new {@link SparseSelect} structure sharing data with this instance.
	 *
	 * @return a new {@link SparseSelect} structure sharing data with this instance.
	 */
	public SparseSelect getSelect() {
		return new SparseSelect( n, m, l, lowerBits, new SimpleSelect( upperBits ) );
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
