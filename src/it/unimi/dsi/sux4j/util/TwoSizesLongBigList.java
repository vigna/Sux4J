package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2010 Sebastiano Vigna 
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

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.sux4j.bits.Rank9;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;

import java.io.Serializable;
import java.util.Arrays;

/** A compressed big list of longs; small elements and large elements are stored separately, using two different, optimally chosen bit sizes.
 * 
 * <p>Instances of this class store in a compacted form a list of natural numbers. Values are provided either through an {@linkplain Iterable iterable object}.
 * You will obtain a reduction in size only if the distribution of the values of the list is skewed towards small values.
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>Instances of this class store elements in two different {@link LongArrayBitVector}-based lists&mdash;one
 * for large values and one for small values. The threshold between large and small is established by
 * measuring at construction time the most proficuous choice. A ranking structure built on a marker array (recording
 * which elements are stored in the large list) provides access of the correct element in each array.
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
	
	/** Builds a new two-sizes long big list using a given iterable object.
	 * 
	 * @param elements an iterable object.
	 */
	public TwoSizesLongBigList( final IntIterable elements ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( elements.iterator() );
			}
		});
	}
	
	/** Builds a new two-sizes long big list using a given iterable object.
	 * 
	 * @param elements an iterable object.
	 */
	public TwoSizesLongBigList( final ShortIterable elements ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( elements.iterator() );
			}
		});
	}
	
	/** Builds a new two-sizes long big list using a given iterable object.
	 * 
	 * @param elements an iterable object.
	 */
	public TwoSizesLongBigList( final ByteIterable elements ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( elements.iterator() );
			}
		});
	}
	
	/** Builds a new two-sizes long big list using a given iterable object.
	 * 
	 * @param elements an iterable object.
	 */
	public TwoSizesLongBigList( final LongIterable elements ) {
		long l = 0;
		final Long2LongOpenHashMap counts = new Long2LongOpenHashMap();
		int width = 0;
		for( LongIterator i = elements.iterator(); i.hasNext(); ) {
			final long value = i.nextLong();
			width = Math.max( width, Fast.mostSignificantBit( value ) + 1 );
			counts.put( value, counts.get( value ) + 1 );
			l++;
		}
		
		length = l;
		
		final long[] keys = counts.keySet().toLongArray();
		Arrays.sort( keys );
		
		long costSmall = 0;
		long costLarge = length * width;
		int minIndex = width;
		long minCostLarge = costLarge;
		long minCostSmall = costSmall;
		long k;
		int j = 0;
		
		// Find the best cutpoint
		for( int i = 1; i < width; i++ ) {
			if ( ASSERTS ) assert costSmall % i == 0;
			if ( i != 1 ) costSmall = ( costSmall / i ) * ( i + 1 );
			while( j < keys.length && ( k = keys[ j ] ) < ( 1 << i ) ) {
				final long c = counts.get( k ); 
				costLarge -= c * width;
				costSmall += c * ( i + 1 );
				j++;
			}
			//System.err.println( "At " + i + ":" + costSmall + " -> " + costLarge );
			if ( costLarge + costSmall < minCostLarge + minCostSmall ) {
				minIndex = i;
				minCostLarge = costLarge;
				minCostSmall = costSmall; 
			}
		}
		
		if ( ASSERTS ) assert minCostSmall / ( minIndex + 1 ) + minCostLarge / width == length;
		//System.err.println( minCostLarge + " " + minCostSmall + " " + minIndex);
		//System.err.println( numSmall );
		if ( width != 0 && minIndex != width ) {
			final long numSmall = minCostSmall / ( minIndex + 1 );
			final long numLarge = minCostLarge / width;
			( small = LongArrayBitVector.getInstance().asLongBigList( minIndex ) ).size( numSmall );
			( marker = LongArrayBitVector.getInstance() ).length( length );
			( large = LongArrayBitVector.getInstance().asLongBigList( width ) ).size( numLarge );
		}
		else {
			( small = LongArrayBitVector.getInstance().asLongBigList( minIndex ) ).size( length );
			marker = null;
			large = null;
		}
		
		final int maxSmall = ( 1 << minIndex );
		
		final LongIterator iterator = elements.iterator();
		for( long i = 0, p = 0, q = 0; i < length; i++ ) {
			final long value = iterator.nextLong();
			if ( value < maxSmall ) small.set( p++, value );
			else {
				large.set( q++, value );
				marker.set( i );
			}
		}

		rank = marker != null ? new Rank9( marker ) : null;

		numBits = small.size64() * minIndex + ( marker != null ? rank.numBits() + marker.length() + large.size64() * width : 0 );
		if ( ASSERTS ) {
			final LongIterator t = elements.iterator();
			for( int i = 0; i < length; i++ ) {
				final long value = t.nextLong();
				assert value == getLong( i ) : "At " + i + ": " + value + " != " + getLong( i ); 
			}
		}
	}

	public long getLong( long index ) {
		if ( marker == null ) return small.getLong( index );
		if ( marker.getBoolean( index ) ) return large.getLong( rank.rank( index ) );
		return small.getLong( index - rank.rank( index ) );
	}

	@Deprecated
	public long length() {
		return length;
	}
	
	public long size64() {
		return length;
	}
	
	public long numBits() {
		return numBits;
	}
}
