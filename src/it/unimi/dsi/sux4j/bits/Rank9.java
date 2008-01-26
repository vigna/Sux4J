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


/** A <code>rank9</code> implementation. */

public class Rank9 extends AbstractRank implements Rank {
	private static final boolean ASSERTS = true;
	private static final long serialVersionUID = 0L;

	final protected long length;
	final protected long[] bits;
	final protected long[] count;
	final protected int numWords;
	final protected long numOnes;
	final protected long lastOne;
	
	public Rank9( final BitVector bitVector ) {
		this( bitVector.bits(), bitVector.length() );
	}
		
	public Rank9( long[] bits, long length ) {
		this.bits = bits;
		this.length = length;
		numWords = (int)( ( length + Long.SIZE - 1 ) / Long.SIZE );

		final int numCounts = (int)( ( length + 8 * Long.SIZE - 1 ) / ( 8 * Long.SIZE ) ) * 2;
		// Init rank/select structure
		count = new long[ numCounts + 1 ];

		long c = 0, l = -1;
		int pos = 0;
		for( int i = 0; i < numWords; i += 8, pos += 2 ) {
			count[ pos ] = c;
			c += Fast.count( bits[ i ] );
			if ( bits[ i ] != 0 ) l = i * 64 + Fast.mostSignificantBit( bits[ i ] );
			for( int j = 1;  j < 8; j++ ) {
				count[ pos + 1 ] |= ( ( true && i + j <= numWords ) ? c - count[ pos ] : 0x1FFL ) << 9 * ( j - 1 );
				if ( i + j < numWords ) {
					c += Fast.count( bits[ i + j ] );
					if ( bits[ i + j ] != 0 ) l = ( i + j ) * 64 + Fast.mostSignificantBit( bits[ i + j ] );
				}
			}
		}
		
		numOnes = c;
		lastOne = l;
		count[ numCounts ] = c;
	}
	
	
	public long rank( long pos ) {
		if ( ASSERTS ) assert pos >= 0;
		if ( ASSERTS ) assert pos <= length;
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if ( pos > lastOne ) return numOnes;
		
		final int word = (int)( pos / 64 );
		final int block = word / 4 & ~1;
		final int offset = word % 8 - 1;
        
		return count[ block ] + ( count[ block + 1 ] >>> ( offset + ( offset >>> 32 - 4 & 0x8 ) ) * 9 & 0x1FF ) + Fast.count( bits[ word ] & ( ( 1L << pos % 64 ) - 1 ) );
	}

	public long[] bits() {
		return bits;
	}
	
	public long length() {
		return length;
	}
	
	public long numBits() {
		return count.length * Long.SIZE;
	}

	public long count() {
		return numOnes;
	}

	public long rank( long from, long to ) {
		return rank( to ) - rank( from );
	}

	public long lastOne() {
		return lastOne;
	}
}
