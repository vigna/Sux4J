package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2016 Sebastiano Vigna 
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

import java.io.IOException;
import java.io.ObjectInputStream;

/** A <code>rank12</code> implementation. 
 * 
 * <p><code>rank12</code> is a ranking structure using 3.125% additional space and providing fast ranking.
 * It is the natural loosening of {@link Rank11} in which the words for superblock are doubled.
 */

public class Rank12 extends AbstractRank implements Rank {
	private static final long serialVersionUID = 1L;
	private static final int WORDS_PER_SUPERBLOCK = 64;

	protected transient long[] bits;
	protected final BitVector bitVector;
	protected final long[] count;
	protected final int numWords;
	protected final long numOnes;
	protected final long lastOne;
	
	public Rank12( long[] bits, long length ) {
		this( LongArrayBitVector.wrap( bits, length ) );
	}

	public Rank12( final BitVector bitVector ) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();
		
		numWords = (int)( ( length + Long.SIZE - 1 ) / Long.SIZE );

		final int numCounts = (int)( ( length + WORDS_PER_SUPERBLOCK * Long.SIZE - 1 ) / ( WORDS_PER_SUPERBLOCK * Long.SIZE ) ) * 2;
		// Init rank/select structure
		count = new long[ numCounts + 1 ];

		long c = 0, l = -1;
		int pos = 0;
		for( int i = 0; i < numWords; i += WORDS_PER_SUPERBLOCK, pos += 2 ) {
			count[ pos ] = c;

			for( int j = 0; j < WORDS_PER_SUPERBLOCK; j++ ) {
				if ( j != 0 && j % 12 == 0 ) count[ pos + 1 ] |= ( i + j <= numWords ? c - count[ pos ] : 0xFFFL ) << 12 * ( ( j - 1 ) / 12 );
				if ( i + j < numWords ) {
					if ( bits[ i + j ] != 0 ) l = ( i + j ) * 64L + Fast.mostSignificantBit( bits[ i + j ] );
					c += Long.bitCount( bits[ i + j ] );
					assert c - count[pos] <= 4096 : c - count[pos];
				}
			}
		}
		
		numOnes = c;
		lastOne = l;
		count[ numCounts ] = c;
	}
	
	
	public long rank( long pos ) {
		assert pos >= 0;
		assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if ( pos > lastOne ) return numOnes;
		
		int word = (int)( pos / Long.SIZE );
		final int block = word / ( WORDS_PER_SUPERBLOCK / 2 ) & ~1;
		final int offset = word % WORDS_PER_SUPERBLOCK / 12 - 1;
		long result = count[ block ] + ( count[ block + 1 ] >> 12 * ( offset + ( offset >>> 32 - 4 & 6 ) ) & 0xFFF ) +
				Long.bitCount( bits[ word ] & ( 1L << pos % Long.SIZE ) - 1 ); 
		
		for ( int todo = ( word % WORDS_PER_SUPERBLOCK ) % 12; todo-- != 0; ) result += Long.bitCount( bits[ --word ] );
        return result;
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
