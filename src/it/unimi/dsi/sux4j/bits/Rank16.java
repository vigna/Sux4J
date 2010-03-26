package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2009 Sebastiano Vigna 
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
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;

import java.io.IOException;
import java.io.ObjectInputStream;


/** A <code>rank16</code> implementation. 
 * 
 * <p><code>rank16</code> is a ranking structure using just 12.6% additional space
 * and providing fast ranking. It is the natural ranking structure for 128-bit processors. */

public class Rank16 extends AbstractRank implements Rank {
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 1L;
	private static final int BLOCK_LENGTH = 1024;
	
	protected transient long[] bits;
	final protected long[] superCount;
	final protected short[] count;
	final protected int numWords;
	final protected long numOnes;
	final protected long lastOne;
	final protected BitVector bitVector;
	
	public Rank16( long[] bits, long length ) {
		this( LongArrayBitVector.wrap( bits, length ) );
	}
		
	public Rank16( final BitVector bitVector ) {
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		numWords = (int)( ( bitVector.length() + Long.SIZE - 1 ) / Long.SIZE );

		final int numSuperCounts = (int)( ( bitVector.length() + BLOCK_LENGTH - 1 ) / BLOCK_LENGTH );
		final int numCounts = ( numWords + 1 ) / 2;
		// Init rank/select structure
		count = new short[ numCounts ];
		superCount = new long[ numSuperCounts ];

		long c = 0, l = -1;
		for( int i = 0; i < numWords; i++ ) {
			if ( i % BLOCK_LENGTH == 0 ) superCount[ i / BLOCK_LENGTH ] = c;
			if ( i % 2 == 0 ) count[ i / 2 ] = (short)( c - superCount[ i / BLOCK_LENGTH ] );
			c += Fast.count( bits[ i ] );
			if ( bits[ i ] != 0 ) l = i * 64L + Fast.mostSignificantBit( bits[ i ] );
		}
		
		numOnes = c;
		lastOne = l;
	}
	
	
	public long rank( long pos ) {
		if ( ASSERTS ) assert pos >= 0;
		if ( ASSERTS ) assert pos <= bitVector.length();
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if ( pos > lastOne ) return numOnes;
		
		final int word = (int)( pos / Long.SIZE );
		final int block = word / BLOCK_LENGTH;
		final int offset = word / 2;
        
		return word % 2 == 0 ?
				superCount[ block ] + ( count[ offset ] & 0xFFFF ) + Fast.count( bits[ word ] & ( ( 1L << pos % 64 ) - 1 ) ) :
				superCount[ block ] + ( count[ offset ] & 0xFFFF ) + Fast.count( bits[ word - 1 ] ) + Fast.count( bits[ word ] & ( 1L << pos % 64 ) - 1 );
	}

	public long numBits() {
		return count.length * Short.SIZE + superCount.length * Long.SIZE;
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
