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

import java.io.IOException;
import java.io.ObjectInputStream;

/** A <code>select9</code> implementation.
 * 
 *  <p><code>select9</code> is based on an underlying <code>{@linkplain Rank9 rank9}</code> instance
 *  and uses 25%-37.5% additional space (beside the 25% due to <code>rank9</code>), depending on density. It guarantees practical
 *  constant time evaluation.
 */

public class Select9 implements Select {
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 1L;

	private final static long ONES_STEP_16 = 1L << 0 | 1L << 16 | 1L << 32 | 1L << 48;
	private final static long MSBS_STEP_16 = 0x8000L * ONES_STEP_16;

	private final static long ONES_STEP_9 = 1L << 0 | 1L << 9 | 1L << 18 | 1L << 27 | 1L << 36 | 1L << 45 | 1L << 54;
	private final static long MSBS_STEP_9 = 0x100L * ONES_STEP_9;

	private final static int LOG2_ONES_PER_INVENTORY = 9;
	private final static int ONES_PER_INVENTORY = 1 << LOG2_ONES_PER_INVENTORY;
	private final static int INVENTORY_MASK = ONES_PER_INVENTORY - 1;
	
	private final long[] inventory;
	private final long[] subinventory;
	private transient LongBigList subinventoryAsShorts;
	private transient LongBigList subinventoryasInts;
	private final long numOnes;
	private final int numWords;
	private transient long[] bits;
	private final long[] count;
	private final Rank9 rank9;
	
	public Select9( final Rank9 rank9 ) {
		this.rank9 = rank9;
		numOnes = rank9.numOnes;
		numWords = rank9.numWords;
		bits = rank9.bits;
		count = rank9.count;
		
		final int inventorySize = (int)( ( numOnes + ONES_PER_INVENTORY - 1 ) / ONES_PER_INVENTORY );

		inventory = new long[ inventorySize + 1 ];
		subinventory = new long[ ( numWords + 3 ) / 4 ];

		long d = 0;
		for ( int i = 0; i < numWords; i++ )
			for ( int j = 0; j < 64; j++ )
				if ( ( bits[ i ] & 1L << j ) != 0 ) {
					if ( ( d & INVENTORY_MASK ) == 0 ) inventory[ (int)( d >> LOG2_ONES_PER_INVENTORY ) ] = i * 64L + j;
					d++;
				}

		
		inventory[ inventorySize ] = ( ( numWords + 3 ) & ~3L ) * Long.SIZE;

		d = 0;
		int state = 0; 
		long firstBit = 0; 
		int index, span, subinventoryPosition = 0;
		int blockSpan, blockLeft;
		long countsAtStart;
		final BitVector v = LongArrayBitVector.wrap( subinventory );
		subinventoryAsShorts = v.asLongBigList( Short.SIZE );
		subinventoryasInts = v.asLongBigList( Integer.SIZE );
		LongBigList s;

		for( int i = 0; i < numWords; i++ )
			for( int j = 0; j < 64; j++ )
				if ( ( bits[ i ] & 1L << j ) != 0 ) {
					if ( ( d & INVENTORY_MASK ) == 0 ) {
						firstBit = i * 64L + j;
						index = (int)( d >> LOG2_ONES_PER_INVENTORY );
						if ( ASSERTS ) assert inventory[ index ] == firstBit;

						subinventoryPosition = (int)( ( inventory[ index ] / 64 ) / 4 );

						span = (int)( ( inventory[ index + 1 ] / 64 ) / 4 - ( inventory[ index ] / 64 ) / 4 );
						state = -1;
						countsAtStart = count[ (int)( ( ( inventory[ index ] / 64 ) / 8 ) * 2 ) ];
						blockSpan = (int)( ( inventory[ index + 1 ] / 64 ) / 8 - ( inventory[ index ] / 64 ) / 8 );
						blockLeft = (int)( ( inventory[ index ] / 64 ) / 8 );

						if ( span >= 512 ) state = 0;
						else if ( span >= 256 ) state = 1;
						else if ( span >= 128 ) state = 2;
						else if ( span >= 16 ) {
							if ( ASSERTS ) assert ( blockSpan + 8 & -8L ) + 8 <= span * 4;
							s = subinventoryAsShorts.subList( subinventoryPosition * 4, subinventoryAsShorts.size64() );

							int k;
							for( k = 0; k < blockSpan; k++ ) {
								if ( ASSERTS ) assert s.getLong( k + 8 ) == 0;
								s.set( k + 8, count[ ( blockLeft + k + 1 ) * 2 ] - countsAtStart );
							}

							for( ; k < ( blockSpan + 8 & -8L ); k++ ) {
								if ( ASSERTS ) assert s.getLong( k + 8 ) == 0;
								s.set( k + 8, 0xFFFF );
							}

							if ( ASSERTS ) assert blockSpan / 8 <= 8;

							for( k = 0; k < blockSpan / 8; k++ ) {
								if ( ASSERTS ) assert s.getLong( k ) == 0;
								s.set( k , count[ ( blockLeft + ( k + 1 ) * 8 ) * 2 ] - countsAtStart );
							}

							for( ; k < 8; k++ ) {
								if ( ASSERTS ) assert s.getLong( k ) == 0;
								s.set( k, 0xFFFF );
							}
						}
						else if ( span >= 2 ) {
							if ( ASSERTS ) assert ( blockSpan + 8 & -8L ) <= span * 4;
							s = subinventoryAsShorts.subList( subinventoryPosition * 4, subinventoryAsShorts.size64() );

							int k;
							for( k = 0; k < blockSpan; k++ ) {
								if ( ASSERTS ) assert s.getLong( k ) == 0;
								s.set( k, count[ ( blockLeft + k + 1 ) * 2 ] - countsAtStart );
							}

							for( ; k < ( blockSpan + 8 & -8L ); k++ ) {
								if ( ASSERTS ) assert s.getLong( k ) == 0;
								s.set( k, 0xFFFF );
							}
						}
					}

					switch( state ) {
					case 0: 
						if ( ASSERTS ) assert subinventory[ subinventoryPosition + (int)( d & INVENTORY_MASK ) ] == 0;
						subinventory[ subinventoryPosition + (int)( d & INVENTORY_MASK ) ] = i * 64L + j;
						break;
					case 1: 
						if ( ASSERTS ) assert subinventoryasInts.getLong( subinventoryPosition * 2 + ( d & INVENTORY_MASK ) ) == 0;
						if ( ASSERTS ) assert i * 64L + j - firstBit < (1L << 32);
						subinventoryasInts.set(  subinventoryPosition * 2 + ( d & INVENTORY_MASK ), i * 64L + j - firstBit );
						break;
					case 2: 
						if ( ASSERTS ) assert subinventoryAsShorts.getLong( subinventoryPosition * 4 + ( d & INVENTORY_MASK ) ) == 0;
						if ( ASSERTS ) assert i * 64L + j - firstBit < (1 << 16);
						subinventoryAsShorts.set( subinventoryPosition * 4 + ( d & INVENTORY_MASK ), i * 64L + j - firstBit );
						break;
					}

					d++;
				}
	}

	public long select( long rank ) {
		if ( rank >= numOnes ) return -1;

		final int inventoryIndexLeft = (int)( rank >> LOG2_ONES_PER_INVENTORY );

		final long inventoryLeft = inventory[ inventoryIndexLeft ];
		final int blockRight = (int)( inventory[ inventoryIndexLeft + 1 ] / 64 );
		int blockLeft = (int)( inventoryLeft / 64 );
		final int subinventoryIndex = blockLeft / 4;
		final long span = blockRight / 4 - blockLeft / 4;
		int countLeft, rankInBlock;
		final long count[] = this.count;
		
		if ( span < 2 ) {
			blockLeft &= ~7;
			countLeft = blockLeft / 4 & ~1;
			if ( ASSERTS ) assert rank >= count[ countLeft ] : rank + " < " + count[ countLeft ];
			if ( ASSERTS ) assert rank < count[ countLeft + 2 ] : rank + " >= " + count[ countLeft + 2 ];
			rankInBlock = (int)( rank - count[ countLeft ] );
		}
		else if ( span < 16 ) {
			blockLeft &= ~7;
			countLeft = blockLeft / 4 & ~1;
			final long rankInSuperblock = rank - count[ countLeft ];
			final long rankInSuperblockStep16 = rankInSuperblock * ONES_STEP_16;

			final long first = subinventory[ subinventoryIndex ], second = subinventory[ subinventoryIndex + 1 ];
			
			final int where = (int)( ( 
					( ( ( ( ( ( rankInSuperblockStep16 | MSBS_STEP_16 ) - ( first & ~MSBS_STEP_16 ) ) | ( first ^ rankInSuperblockStep16 ) ) ^ ( first & ~rankInSuperblockStep16 ) ) & MSBS_STEP_16 ) >>> 15 ) +
					( ( ( ( ( ( rankInSuperblockStep16 | MSBS_STEP_16 ) - ( second & ~MSBS_STEP_16 ) ) | ( second ^ rankInSuperblockStep16 ) ) ^ ( second & ~rankInSuperblockStep16 ) ) & MSBS_STEP_16 ) >>> 15 )
			) * ONES_STEP_16 >>> 47 );

			
			if ( ASSERTS ) assert where >= 0;
			if ( ASSERTS ) assert where <= 16;
			
			blockLeft += where * 4;
			countLeft += where;
			rankInBlock = (int)( rank - count[ countLeft ] );
			if ( ASSERTS ) assert rankInBlock >= 0;
			if ( ASSERTS ) assert rankInBlock < 512;
		}
		else if ( span < 128 ) {
			final long[] subinventory = this.subinventory;
			blockLeft &= ~7;
			countLeft = blockLeft / 4 & ~1;
			final long rankInSuperblock = rank - count[ countLeft ];
			final long rankInSuperblockStep16 = rankInSuperblock * ONES_STEP_16;

			final long first = subinventory[ subinventoryIndex ], second = subinventory[ subinventoryIndex + 1 ];
			final int where0 = (int)( ( 
					( ( ( ( ( ( rankInSuperblockStep16 | MSBS_STEP_16 ) - ( first & ~MSBS_STEP_16 ) ) | ( first ^ rankInSuperblockStep16 ) ) ^ ( first & ~rankInSuperblockStep16 ) ) & MSBS_STEP_16 ) >>> 15 ) +
					( ( ( ( ( ( rankInSuperblockStep16 | MSBS_STEP_16 ) - ( second & ~MSBS_STEP_16 ) ) | ( second ^ rankInSuperblockStep16 ) ) ^ ( second & ~rankInSuperblockStep16 ) ) & MSBS_STEP_16 ) >>> 15 )
			) * ONES_STEP_16 >>> 47 );
			if ( ASSERTS ) assert where0 <= 16;
			final long first_bis = subinventory[ subinventoryIndex + where0 + 2 ], second_bis = subinventory[ subinventoryIndex + where0 + 2 + 1 ];
			final int where1 = where0 * 8 + (int)( ( 
					( ( ( ( ( ( rankInSuperblockStep16 | MSBS_STEP_16 ) - ( first_bis & ~MSBS_STEP_16 ) ) | ( first_bis ^ rankInSuperblockStep16 ) ) ^ ( first_bis & ~rankInSuperblockStep16 ) ) & MSBS_STEP_16 ) >>> 15 ) +
					( ( ( ( ( ( rankInSuperblockStep16 | MSBS_STEP_16 ) - ( second_bis & ~MSBS_STEP_16 ) ) | ( second_bis ^ rankInSuperblockStep16 ) ) ^ ( second_bis & ~rankInSuperblockStep16 ) ) & MSBS_STEP_16 ) >>> 15 )
			) * ONES_STEP_16 >>> 47 );

			blockLeft += where1 * 4;
			countLeft += where1;
			rankInBlock = (int)( rank - count[ countLeft ] );
			if ( ASSERTS ) assert rankInBlock >= 0;
			if ( ASSERTS ) assert rankInBlock < 512;
		}
		else if ( span < 256 ) {
			return subinventoryAsShorts.getLong( subinventoryIndex * 4 + (int)( rank % ONES_PER_INVENTORY ) ) + inventoryLeft;
		}
		else if ( span < 512 ) {
			return subinventoryasInts.getLong( subinventoryIndex * 2 + (int)( rank % ONES_PER_INVENTORY ) ) + inventoryLeft;
		}
		else {
			return subinventory[ subinventoryIndex + (int)( rank % ONES_PER_INVENTORY ) ];
		}

		final long rankInBlockStep9 = rankInBlock * ONES_STEP_9;
		final long subcounts = count[ countLeft + 1 ];
		final int offsetInBlock = (int)( ( ( ( ( ( ( rankInBlockStep9 | MSBS_STEP_9 ) - ( subcounts & ~MSBS_STEP_9 ) ) | ( subcounts ^ rankInBlockStep9 ) ) ^ ( subcounts & ~rankInBlockStep9 ) ) & MSBS_STEP_9 ) >>> 8 ) * ONES_STEP_9 >>> 54 & 0x7 );

		final int word = blockLeft + offsetInBlock;
		final int rankInWord = (int)( rankInBlock - ( subcounts >>> ( offsetInBlock - 1 & 7 ) * 9 & 0x1FF ) );
		if ( ASSERTS ) assert offsetInBlock >= 0;
		if ( ASSERTS ) assert offsetInBlock <= 7;

		if ( ASSERTS ) assert rankInWord < 64;
		if ( ASSERTS ) assert rankInWord >= 0;

		return word * 64L + Fast.select( bits[ word ], rankInWord );
	}

	public long numBits() {
		return rank9.numBits() + inventory.length * (long)Long.SIZE + subinventory.length * (long)Long.SIZE;
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		bits = rank9.bitVector.bits();
		final BitVector v = LongArrayBitVector.wrap( subinventory );
		subinventoryAsShorts = v.asLongBigList( Short.SIZE );
		subinventoryasInts = v.asLongBigList( Integer.SIZE );
	}

	public BitVector bitVector() {
		return rank9.bitVector();
	}
}
