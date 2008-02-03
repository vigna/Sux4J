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

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.sux4j.bits.Fast;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank9;

import java.io.Serializable;
import java.util.Arrays;

/** A two-sizes immutable list implementation.
 * 
 * <p>Instances of this class store elements in two different {@link LongArrayBitVector}-based lists&mdash;one
 * for large values and one for small values. The threshold between large and small is established by
 * measuring at construction time the most proficuous choice.
 * 
 */
public class TwoSizesLongBigList extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final boolean ASSERTS = false;
	/** The number of elements in this list. */
	private final long length;
	/** The storage for small elements. */
	private final LongBigList small;
	/** The storage for large elements. */
	private final LongBigList large;
	/** A bit array marking whether an element is stored in the small or large storage. */
	private final LongArrayBitVector marker;
	/** A ranking structure to index {@link #small} and {@link #large}. */
	private final Rank9 rank;
	/** The number of bits used by this structure. */
	private final long numBits;
	
	/** Builds a new two-sizes long bit list using a given list of integers.
	 * 
	 * @param list a list of integers.
	 */
	public TwoSizesLongBigList( final IntList list ) {
		this( new AbstractLongBigList() {

			public long getLong( final long index ) {
				return list.getInt( (int)index );
			}

			public long length() {
				return list.size();
			}
		} );
	}
	
	/** Builds a new two-sizes long bit list using a given big list of long.
	 * 
	 * @param list a list of long.
	 */
	public TwoSizesLongBigList( final LongBigList list ) {
		length = list.length();
		final Long2LongOpenHashMap counts = new Long2LongOpenHashMap();
		int width = 0;
		for( long x: list ) {
			width = Math.max( width, Fast.mostSignificantBit( x ) + 1 );
			counts.put( x, counts.get( x ) + 1 );
		}
		final long[] keys = counts.keySet().toLongArray();
		Arrays.sort( keys );
		
		long costSmall = 0;
		long costLarge = length * width;
		int minIndex = width;
		long minCostLarge = costLarge;
		long minCostSmall = costSmall;
		long k;
		int j = 0;
		
		for( int i = 1; i < width; i++ ) {
			if ( ASSERTS ) assert costSmall % i == 0;
			if ( i != 1 ) costSmall = ( costSmall / i ) * ( i + 1 );
			while( j < keys.length && ( k = keys[ j ] ) < ( 1 << i ) ) {
				final long c = counts.get( k ); 
				costLarge -= c * width;
				costSmall += c * ( i + 1 );
				j++;
			}
			System.err.println( "At " + i + ":" + costSmall + " -> " + costLarge );
			if ( costLarge + costSmall < minCostLarge + minCostSmall ) {
				minIndex = i;
				minCostLarge = costLarge;
				minCostSmall = costSmall; 
			}
		}
		
		if ( ASSERTS ) assert minCostSmall / ( minIndex + 1 ) + minCostLarge / width == length;
		System.err.println( minCostLarge + " " + minCostSmall + " " + minIndex);
		final long numSmall = minCostSmall / ( minIndex + 1 );
		final long numLarge = minCostLarge / width;
		System.err.println( numSmall );
		if ( minIndex != width ) {
			small = LongArrayBitVector.getInstance().asLongBigList( minIndex ).length( numSmall );
			marker = LongArrayBitVector.getInstance().length( length );
			large = LongArrayBitVector.getInstance().asLongBigList( width ).length( numLarge );
		}
		else {
			small = LongArrayBitVector.getInstance().asLongBigList( minIndex ).length( length );
			marker = null;
			large = null;
		}
		
		final int maxSmall = ( 1 << minIndex );
		
		for( long i = 0, p = 0, q = 0; i < length; i++ ) {
			final long value = list.getLong( i );
			if ( value < maxSmall ) small.set( p++, value );
			else {
				large.set( q++, value );
				marker.set( i );
			}
		}

		rank = marker != null ? new Rank9( marker ) : null;

		numBits = small.length() * minIndex + ( marker != null ? rank.numBits() + marker.length() + + large.length() * width : 0 );
		if ( ASSERTS ) {
			for( int i = 0; i < length; i++ ) assert list.getLong( i ) == getLong( i ) : "At " + i + ": " + list.getLong( i ) + " != " + getLong( i ); 
		}
	}

	public long getLong( long index ) {
		if ( marker == null ) return small.getLong( index );
		if ( marker.getBoolean( index ) ) return large.getLong( rank.rank( index ) );
		return small.getLong( index - rank.rank( index ) );
	}

	public long length() {
		return length;
	}
	
	public long numBits() {
		return numBits;
	}
}
