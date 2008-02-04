package it.unimi.dsi.sux4j.bits;

import java.io.IOException;
import java.io.ObjectInputStream;


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


/** A hinted binary-search select implementation.
 * 
 * <p>Instances of this class perform selection
 * using a hinted binary search over an underlying {@link Rank9} instance. We use
 * 12.5% additional space for a small inventory.
 */

public class HintedBsearchSelect implements Select {
	@SuppressWarnings("unused")
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 1L;

	private static final long ONES_STEP_9 = 1L << 0 | 1L << 9 | 1L << 18 | 1L << 27 | 1L << 36 | 1L << 45 | 1L << 54;
	private static final long MSBS_STEP_9 = 0x100L * ONES_STEP_9;

	final private int[] inventory;
	final private int onesPerInventory;
	final private int log2OnesPerInventory;
	private final long numOnes;
	private final int numWords;
	private transient long[] bits;
	private final long[] count;
	final private Rank9 rank9;

	public HintedBsearchSelect( final Rank9 rank9 ) {
		this.rank9 = rank9;
		numOnes = rank9.numOnes;
		numWords = rank9.numWords;
		bits = rank9.bits;
		count = rank9.count;
		
		log2OnesPerInventory = rank9.bitVector.length() == 0 ? 0 : Fast.mostSignificantBit( ( numOnes * 16 * 64 + rank9.bitVector.length() - 1 ) / rank9.bitVector.length() );
		onesPerInventory = 1 << log2OnesPerInventory;
		final int inventorySize = (int)( ( numOnes + onesPerInventory - 1 ) / onesPerInventory );

		inventory = new int[ inventorySize + 1 ];
		long d = 0;
		final long mask = onesPerInventory - 1;
		for( int i = 0; i < numWords; i++ )
			for( int j = 0; j < 64; j++ )
				if ( ( bits[ i ] & 1L << j ) != 0 ) {
					if ( ( d & mask ) == 0 ) inventory[ (int)( d >> log2OnesPerInventory ) ] = ( i / 8 ) * 2;
					d++;
				}

		inventory[ inventorySize ] = ( numWords / 8 ) * 2;
	}
	
	public long select( long rank ) {
		if ( rank >= numOnes ) return -1;
		
		final long[] count = this.count;
		final int[] inventory = this.inventory;
		final int inventoryIndexLeft = (int)( rank >>> log2OnesPerInventory );
		int blockLeft = inventory[ inventoryIndexLeft ];
		int blockRight = inventory[ inventoryIndexLeft + 1 ];

		if ( rank >= count[ blockRight ] ) {
			blockRight = ( blockLeft = blockRight ) + 2;
		}
		else {
			int blockMiddle;

			while( blockRight - blockLeft > 2 ) {
				blockMiddle = ( blockRight + blockLeft ) / 2 & ~1;
				if ( rank >= count[ blockMiddle ] ) blockLeft = blockMiddle;
				else blockRight = blockMiddle;
			}
		}

		final long rankInBlock = rank - count[ blockLeft ];     

		final long rankInBlockStep9 = rankInBlock * ONES_STEP_9;
		final long subcounts = count[ blockLeft + 1 ];
		final long offsetInBlock = ( ( ( ( ( ( ( rankInBlockStep9 | MSBS_STEP_9 ) - ( subcounts & ~MSBS_STEP_9 ) ) | ( subcounts ^ rankInBlockStep9 ) ) ^ ( subcounts & ~rankInBlockStep9 ) ) & MSBS_STEP_9 ) >>> 8 ) * ONES_STEP_9 >>> 54 & 0x7 );
		
		final long word = blockLeft * 4 + offsetInBlock;
		final long rankInWord = rankInBlock - ( subcounts >>> ( offsetInBlock - 1 & 7 ) * 9 & 0x1FF );

        return word * 64 + Fast.select( bits[ (int)word ], (int)rankInWord );
	}

	public long numBits() {
		return rank9.numBits() + inventory.length * Integer.SIZE;
	}

	
	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		bits = rank9.bitVector.bits();
	}

	public BitVector bitVector() {
		return rank9.bitVector();
	}

}
