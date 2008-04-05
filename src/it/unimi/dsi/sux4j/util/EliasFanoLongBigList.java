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

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteIterable;
import it.unimi.dsi.fastutil.bytes.ByteIterator;
import it.unimi.dsi.fastutil.ints.IntIterable;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.shorts.ShortIterable;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.util.AbstractLongBigList;

import java.io.Serializable;
import java.util.Iterator;

/** A compressed big list of longs; each element occupies a number of bits roughly equal to one plus its bit length.
 * 
 * <p>Instances of this class store in a highly compacted form a list of longs. Values are provided either through an {@linkplain Iterable iterable object},
 * or through an {@linkplain Iterator iterator}, but in the latter case the user must also provide a (not necessarily strict) lower bound (0 by default)
 * on the returned values. The compression is particularly high if the distribution of the values of the list is skewed towards the smallest values.
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>Instances of this class store values by offsetting them so that they are strictly positive. Then,
 * the bits of each element, excluding the most significant one, are concatenated in a bit array, and the positions
 * of the initial bit of each element are stored using the {@linkplain EliasFanoMonotoneLongBigList Elias&ndash;Fano representation}.
 * If the distribution of the elements is skewed towards small values, this method achieves very a good compression (and, in any case,
 * w.r.t. exact binary length it will not lose more than one bit per element, plus lower-order terms).
 * 
 */
public class EliasFanoLongBigList extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 1L;
	/** The number of elements in this list. */
	private final long length;
	/** The storage for small elements. */
	private final LongArrayBitVector bits;
	/** The offset (derived from the lower bound computed or provided at construction time) that must be added before returning a value. */
	private final long offset;
	/** The position of the initial bit of each element, plus a final marker for the end of the bit array. */
	private EliasFanoMonotoneLongBigList borders;
	
	private static long findMin( final LongIterator iterator ) {
		long lowerBound = Long.MAX_VALUE;
		while( iterator.hasNext() ) lowerBound = Math.min( lowerBound, iterator.nextLong() );
		return lowerBound;
	}
	
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList( final LongIterable elements ) {
		this( elements.iterator(), findMin( elements.iterator() ) ); 
	}
	
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList( final IntIterable elements ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( elements.iterator() );
			}
		});
	}
	
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList( final ShortIterable elements ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( elements.iterator() );
			}
		});
	}
	
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param elements an iterable object.
	 */
	public EliasFanoLongBigList( final ByteIterable elements ) {
		this( new LongIterable() {
			public LongIterator iterator() {
				return LongIterators.wrap( elements.iterator() );
			}
		});
	}
	
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList( final LongIterator iterator ) {
		this( iterator, 0 );
	}
	
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList( final IntIterator iterator ) {
		this( LongIterators.wrap( iterator ) );
	}
		
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList( final ShortIterator iterator ) {
		this( LongIterators.wrap( iterator ) );
	}
		
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 */
	public EliasFanoLongBigList( final ByteIterator iterator ) {
		this( LongIterators.wrap( iterator ) );
	}
		
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the value returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList( final IntIterator iterator, final int lowerBound ) {
		this( LongIterators.wrap( iterator ), lowerBound );
	}
		
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the value returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList( final ShortIterator iterator, final short lowerBound ) {
		this( LongIterators.wrap( iterator ), lowerBound );
	}
		
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the value returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList( final ByteIterator iterator, final byte lowerBound ) {
		this( LongIterators.wrap( iterator ), lowerBound );
	}
		
	/** Creates a new Elias&ndash;Fano long big list.
	 * 
	 * @param iterator an iterator returning natural numbers.
	 * @param lowerBound a (not necessarily strict) lower bound on the value returned by <code>iterator</code>.
	 */
	public EliasFanoLongBigList( final LongIterator iterator, long lowerBound ) {
		this.offset = -lowerBound + 1;
		bits = LongArrayBitVector.getInstance();
		LongArrayList borders = new LongArrayList();
		borders.add( 0 );
		long lastBorder = 0;
		long v;
		long c = 0;
		int msb;
		while( iterator.hasNext() ) {
			v = iterator.nextLong();
			if ( v < lowerBound ) throw new IllegalArgumentException( v + " < " + lowerBound );
			v -= lowerBound;
			v++;
			msb = Fast.mostSignificantBit( v );
			borders.add( lastBorder += msb );
			bits.append( v & ( 1L << msb ) - 1, msb );
			c++;
		}
		this.length = c;
		this.borders = new EliasFanoMonotoneLongBigList( borders );
		this.bits.trim();
	}

	public long getLong( long index ) {
		final long from = borders.getLong( index ), to = borders.getLong( index + 1 );
		return ( ( 1L << ( to - from ) ) | bits.getLong( from, to ) ) - offset;
	}

	public long length() {
		return length;
	}
	
	public long numBits() {
		return borders.numBits() + bits.length();
	}
}
