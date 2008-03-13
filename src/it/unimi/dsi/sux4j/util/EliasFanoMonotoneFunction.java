package it.unimi.dsi.sux4j.util;

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
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.util.AbstractLongBigList;
import it.unimi.dsi.util.LongBigList;

/** An implementation of Elias&ndash;Fano's representation for monotone functions.
 * 
 * <p>Given a (nondecreasing) monotone function <var>n</var>&nbsp;&larr;&nbsp;2<sup><var>s</var></sup>,
 * the Elias&ndash;Fano representation makes it possible to store it using
 * just 2 + <var>s</var> &minus; log&nbsp;<var>n</var> bits per element (which is very close
 * to the information-theoretical lower bound log <it>e</it> + <var>s</var> &minus; log&nbsp;<var>n</var>). 
 * 
 * <p>The values of the function are recorded by storing separately 
 * the lower <var>s</var> &minus; log&nbsp;<var>n</var> bits
 * and the remaining upper bits.
 * The lower bits are stored in a bit array, whereas the upper bits are stored in an array
 * of 2<var>n</var> bits by setting, if the <var>i</var>-th one is at position
 * <var>p</var>, the bit of index <var>p</var> / 2<sup><var>s</var></sup> + <var>i</var>; the value can then be recovered
 * by selecting the <var>i</var>-th bit of the resulting bit array and subtracting <var>i</var> (note that this will
 * work because the upper bits are nondecreasing).
 * 
 * <p>This implementation uses {@link SimpleSelect} to support selection inside the dense array. The resulting data structure uses 
 * <var></var> &lceil; log(<var>n</var>/<var>m</var>) &rceil; + 2.25 <var>m</var> bits.
 * 
 */

public class EliasFanoMonotoneFunction extends AbstractLongBigList {
	private static final long serialVersionUID = 2L;
	
	/** The length of the underlying bit array. */
	protected final long n;
	/** The number of ones in the underlying bit array. */
	protected final long size;
	/** The number of lower bits. */
	protected final int l;
	/** The list of lower bits of the position of each one, stored explicitly. */
	protected final LongBigList lowerBits;
	/** The select structure used to extract the upper bits. */ 
	protected final SimpleSelect selectUpper;
	
	public EliasFanoMonotoneFunction( final LongArrayList list ) {
		this( list.size(), list.size() == 0 ? 0 : list.getLong( list.size() - 1 ) + 1, list.iterator() );
	}

	public EliasFanoMonotoneFunction( final IntArrayList list ) {
		this( list.size(), list.size() == 0 ? 0 : list.getInt( list.size() - 1 ) + 1, 
		new AbstractLongIterator() {
			final IntIterator iterator = list.iterator();
			
			public boolean hasNext() {
				return iterator.hasNext();
			}
			
			public long nextLong() {
				return iterator.nextInt();
			}
		});
	}

	public EliasFanoMonotoneFunction( final LongBigList list ) {
		this( list.length(), list.length() == 0 ? 0 : list.getLong( list.length() - 1 ) + 1, list.iterator() );
	}

	/** Creates a new <code>sdarray</code> select structure using an {@linkplain LongIterator iterator}.
	 * 
	 * <p>This constructor is particularly useful if the positions of the ones are provided by
	 * some sequential source.
	 * @param n the number of elements returned by <code>iterator</code>.
	 * @param upperBound a (strict) upper bound to the values returned by <code>iterator</code>.
	 * @param iterator an iterator returning the positions of the ones in the underlying bit vector in increasing order.
	 */
	public EliasFanoMonotoneFunction( long n, final long upperBound, final LongIterator iterator ) {
		long pos = -1;
		this.size = n;
		this.n = upperBound;
		int l = 0;
		if ( n > 0 ) {
			while( n < upperBound ) {
				n *= 2;
				l++;
			}
		}
		this.l = l;
		final long lowerBitsMask = ( 1L << l ) - 1;
		lowerBits = LongArrayBitVector.getInstance().asLongBigList( l ).length( this.size );
		final BitVector upperBits = LongArrayBitVector.getInstance().length( this.size * 2 );
		long last = 0;
		for( long i = 0; i < this.size; i++ ) {
			pos = iterator.nextLong();
			if ( pos >= upperBound ) throw new IllegalArgumentException( "Too large value: " + pos + " >= " + upperBound );
			if ( pos < last ) throw new IllegalArgumentException( "Values are not nondecreasing: " + pos + " < " + last );
			if ( l != 0 ) lowerBits.set( i, pos & lowerBitsMask );
			upperBits.set( ( pos >> l ) + i );
			last = pos;
		}
		
		if ( iterator.hasNext() ) throw new IllegalArgumentException( "There are more than " + this.size + " positions in the provided iterator" );
		
		selectUpper = new SimpleSelect( upperBits );
	}
	
	public long numBits() {
		return selectUpper.numBits() + selectUpper.bitVector().length() + lowerBits.length() * l;
	}

	
	public long select( final long rank ) {
		if ( rank >= size ) return -1;
		return ( selectUpper.select( rank ) - rank ) << l | lowerBits.getLong( rank );
	}

	public long getLong( final long index ) {
		if ( index >= size ) return -1;
		return ( selectUpper.select( index ) - index ) << l | lowerBits.getLong( index );
	}

	public long length() {
		return size;
	}
}
