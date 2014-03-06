package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;

import java.io.IOException;
import java.io.ObjectInputStream;


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


/** A <code>rank9</code> implementation. 
 * 
 * <p><code>rank9</code> is a ranking structure using 25% additional space and providing exceptionally fast ranking. 
 */

public class Rank9 extends AbstractRank implements Rank {
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 1L;

	protected transient long[] bits;
	final protected BitVector bitVector;
	final protected long[] count;
	final protected int numWords;
	final protected long numOnes;
	final protected long lastOne;
	
	public Rank9( long[] bits, long length ) {
		this( LongArrayBitVector.wrap( bits, length ) );
	}

	public Rank9( final BitVector bitVector ) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();
		
		numWords = (int)( ( length + Long.SIZE - 1 ) / Long.SIZE );

		final int numCounts = (int)( ( length + 8 * Long.SIZE - 1 ) / ( 8 * Long.SIZE ) ) * 2;
		// Init rank/select structure
		count = new long[ numCounts + 1 ];

		long c = 0, l = -1;
		int pos = 0;
		for( int i = 0; i < numWords; i += 8, pos += 2 ) {
			count[ pos ] = c;
			c += Long.bitCount( bits[ i ] );
			if ( bits[ i ] != 0 ) l = i * 64L + Fast.mostSignificantBit( bits[ i ] );
			for( int j = 1;  j < 8; j++ ) {
				count[ pos + 1 ] |= ( i + j <= numWords ? c - count[ pos ] : 0x1FFL ) << 9 * ( j - 1 );
				if ( i + j < numWords ) {
					c += Long.bitCount( bits[ i + j ] );
					if ( bits[ i + j ] != 0 ) l = ( i + j ) * 64L + Fast.mostSignificantBit( bits[ i + j ] );
				}
			}
		}
		
		numOnes = c;
		lastOne = l;
		count[ numCounts ] = c;
	}
	
	
	public long rank( long pos ) {
		if ( ASSERTS ) assert pos >= 0;
		if ( ASSERTS ) assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if ( pos > lastOne ) return numOnes;
		
		final int word = (int)( pos / 64 );
		final int block = word / 4 & ~1;
		final int offset = word % 8 - 1;
        
		return count[ block ] + ( count[ block + 1 ] >>> ( offset + ( offset >>> 32 - 4 & 0x8 ) ) * 9 & 0x1FF ) + Long.bitCount( bits[ word ] & ( ( 1L << pos % 64 ) - 1 ) );
	}

	public long numBits() {
		return count.length * (long)Long.SIZE;
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

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		bits = bitVector.bits();
	}

	public BitVector bitVector() {
		return bitVector;
	}
}
