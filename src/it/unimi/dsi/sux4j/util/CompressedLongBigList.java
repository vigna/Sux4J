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
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.AbstractLongIterator;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.util.AbstractLongBigList;

import java.io.Serializable;

/** A compressed big list of longs.
 * 
 * <p>Instances of this class store elements by ensuring (adding a suitable offset) that they are strictly positive. Then,
 * the bits of each element, excluding the most significant one, are concatenated in a bit array, and the positions
 * of the initial bit of each element are stored as an {@linkplain EliasFanoMonotoneBigList Elias&ndash;Fano monotone function}.
 * 
 */
public class CompressedLongBigList extends AbstractLongBigList implements Serializable {
	private static final long serialVersionUID = 1L;
	private static final boolean ASSERTS = false;
	/** The number of elements in this list. */
	private final long length;
	/** The storage for small elements. */
	private final LongArrayBitVector bits;
	
	private final int offset;
	private EliasFanoMonotoneBigList borders;
	
	/** Builds a new two-sizes long bit list using a given big list of long.
	 * 
	 * @param list a list of long.
	 */
	public CompressedLongBigList( final LongIterator iterator ) {
		this( iterator, 0 );
	}
	
	public CompressedLongBigList( final IntIterator iterator ) {
		this( new AbstractLongIterator() {
			public boolean hasNext() {
				return iterator.hasNext();
			}
			
			public long nextLong() {
				return iterator.nextInt();
			}
		});
	}
		
	public CompressedLongBigList( final LongIterator iterator, int offset ) {
		this.offset = offset + 1;
		bits = LongArrayBitVector.getInstance();
		LongArrayList borders = new LongArrayList();
		borders.add( 0 );
		long lastBorder = 0;
		long v;
		long c = 0;
		int msb;
		while( iterator.hasNext() ) {
			v = iterator.nextLong();
			if ( v < -offset ) throw new IllegalArgumentException( Long.toString( v ) );
			v += offset + 1;
			msb = Fast.mostSignificantBit( v );
			borders.add( lastBorder += msb );
			bits.append( v & ( 1L << msb ) - 1, msb );
			c++;
		}
		this.length = c;
		this.borders = new EliasFanoMonotoneBigList( borders );
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
