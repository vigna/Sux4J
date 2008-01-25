package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.fastutil.longs.LongArrays;

import java.io.IOException;
import java.io.ObjectInputStream;

/** A simple select implementation based on a two-level inventory and broadword bit search. */

public class SimpleSelect implements Select {
	@SuppressWarnings("unused")
	private static final boolean ASSERTS = true;
	private static final long serialVersionUID = 0L;

	private static final long ONES_STEP_8 = 0x0101010101010101L;
	private static final long ONES_STEP_4 = 0x1111111111111111L;

	private static final int MAX_ONES_PER_INVENTORY = 8192;
	private static final int MAX_LOG2_LONGWORDS_PER_SUBINVENTORY = 3;
	private static final long MSBS_STEP_8 = 0x80L * ONES_STEP_8;
	private static final long INCR_STEP_8 = 0x80L << 56 | 0x40L << 48 | 0x20L << 40 | 0x10L << 32 | 0x8L << 24 | 0x4L << 16 | 0x2L << 8 | 0x1;

	private final long[] inventory;
	private final long[] subinventory;
	private transient LongBigList subinventory16;
	private final long numOnes;
	private final long length;
	private final int numWords;
	private final long[] bits;
	private final int onesPerInventory;
	private final int log2OnesPerInventory;
	private final int onesPerInventoryMask;
	private final int log2LongwordsPerSubinventory;
	private final int log2OnesPerSub64;
	private final int log2OnesPerSub16;
	private final int onesPerSub64;
	private final int onesPerSub16;
	private final int onesPerSub16Mask;
	private final long[] exactSpill;
	
	public SimpleSelect( final BitVector bitVector ) {
		this( bitVector.bits(), bitVector.length() );
	}
		
	public SimpleSelect( long[] bits, long length ) {
		this.length = length;
		this.bits = bits;

		numWords = (int)( ( length + 63 ) / 64 );
		
		// Init rank/select structure
		long c = 0;
		for( int i = 0; i < numWords; i++ ) c += Fast.count( bits[ i ] );
		numOnes = c;

		onesPerInventory = 1 << ( log2OnesPerInventory = Fast.mostSignificantBit( length == 0 ? 1 : (int)( ( c * MAX_ONES_PER_INVENTORY + length - 1 ) / length ) ) );
		onesPerInventoryMask = onesPerInventory - 1;
		final int inventorySize = (int)( ( c + onesPerInventory - 1 ) / onesPerInventory );

		inventory = new long[ inventorySize + 1 ];

		long d = 0;

		// First phase: we build an inventory for each one out of onesPerInventory.
		for( int i = 0; i < numWords; i++ )
			for( int j = 0; j < 64; j++ )
				if ( ( bits[ i ] & 1L << j ) != 0 ) {
					if ( ( d & onesPerInventoryMask ) == 0 ) inventory[ (int)( d >>> log2OnesPerInventory ) ] = i * 64 + j;
					d++;
				}

		assert( c == d );
		inventory[ inventorySize ] = length;

		log2LongwordsPerSubinventory = Math.min( MAX_LOG2_LONGWORDS_PER_SUBINVENTORY, Math.max( 0, log2OnesPerInventory - 2 ) );
		log2OnesPerSub64 = Math.max( 0, log2OnesPerInventory - log2LongwordsPerSubinventory );
		log2OnesPerSub16 = Math.max( 0, log2OnesPerSub64 - 2 );
		onesPerSub64 = ( 1 << log2OnesPerSub64 );
		onesPerSub16 = ( 1 << log2OnesPerSub16 );
		onesPerSub16Mask = onesPerSub16 - 1;

		if ( onesPerInventory > 1 ) {
			d = 0;
			int ones;
			long diff16 = 0, start = 0, span = 0;
			int spilled = 0, exact = 0,inventoryIndex = 0;

			for( int i = 0; i < numWords; i++ )
				// We estimate the subinventory and exact spill size
				for( int j = 0; j < 64; j++ )
					if ( ( bits[ i ] & 1L << j ) != 0 ) {
						if ( ( d & onesPerInventoryMask ) == 0 ) {
							inventoryIndex = (int)( d >>> log2OnesPerInventory );
							start = inventory[ inventoryIndex ];
							span = inventory[ inventoryIndex + 1 ] - start;
							ones = (int)Math.min( c - d, onesPerInventory );

							// We must always count (possibly unused) diff16's. And we cannot store less then 4 diff16.
							diff16 += Math.max( 4, ( ones + onesPerSub16 - 1 ) >>> log2OnesPerSub16 );

							// We accumulate space for exact pointers ONLY if necessary.
							if ( span >= (1<<16) ) {
								exact += ones;
								if ( onesPerSub64 > 1 ) spilled += ones;
							}

						}
						d++;
					}

			final int subinventorySize = (int)( ( diff16 + 3 ) / 4 );
			final int exactSpillSize = spilled;
			subinventory = new long[ subinventorySize ];
			exactSpill = new long[ exactSpillSize ];
			subinventory16 = LongArrayBitVector.wrap( subinventory ).asLongBigList( 16 );

			int offset = 0;
			spilled = 0;
			d = 0;

			for( int i = 0; i < numWords; i++ )
				for( int j = 0; j < 64; j++ )
					if ( ( bits[ i ] & 1L << j ) != 0 ) {
						if ( ( d & onesPerInventoryMask ) == 0 ) {
							inventoryIndex = (int)( d >>> log2OnesPerInventory );
							start = inventory[ inventoryIndex ];
							span = inventory[ inventoryIndex + 1 ] - start;
							offset = 0;
						}

						if ( span < (1<<16) ) {
							assert( i * 64 + j - start <= (1<<16) );
							if ( ( d & onesPerSub16Mask ) == 0 ) {
								subinventory16.set( ( inventoryIndex << log2LongwordsPerSubinventory + 2 ) +  offset++, i * 64 + j - start );
							}
						}
						else {
							if ( onesPerSub64 == 1 ) {
								subinventory[ ( inventoryIndex << log2LongwordsPerSubinventory ) + offset++ ] = i * 64 + j;
							}
							else {
								if ( ( d & onesPerInventoryMask ) == 0 ) subinventory[ inventoryIndex << log2LongwordsPerSubinventory ] = - spilled - 1;
								exactSpill[ spilled++ ] = i * 64 + j;
							}
						}

						d++;
					}

		}
		else {
			subinventory = exactSpill = LongArrays.EMPTY_ARRAY;
			subinventory16 = null;
		}

}

	public long select( long rank ) {
		if ( rank >= numOnes ) return -1;

		final int inventoryIndex = (int)( rank >>> log2OnesPerInventory );

		final long inventoryRank = inventory[ inventoryIndex ];
		final int subrank = (int)( rank & onesPerInventoryMask );

		if ( subrank == 0 ) return inventoryRank;

		long start;
		int residual;

		if ( inventoryIndex >= 0 ) {
			start = inventoryRank + subinventory16.getLong( ( inventoryIndex << log2LongwordsPerSubinventory + 2 ) + ( subrank >>> log2OnesPerSub16 ) );
			residual = subrank & onesPerSub16Mask;
		}
		else {
			if ( onesPerSub64 == 1 ) return subinventory[ ( ( - inventoryIndex - 1 ) << log2LongwordsPerSubinventory ) + subrank ];
			return exactSpill[ (int)( subinventory[ ( - inventoryIndex - 1 ) << log2LongwordsPerSubinventory ] + subrank ) ];
		}

		if ( residual == 0 ) return start;

		final long bits[] = this.bits;
		int wordIndex = (int)( start / 64 );
		long word = bits[ wordIndex ] & -1L << start;
		long byteSums;

		for(;;) {
			// Phase 1: sums by byte
			byteSums = word - ( ( word & 0xa * ONES_STEP_4 ) >>> 1 );
			byteSums = ( byteSums & 3 * ONES_STEP_4 ) + ( ( byteSums >>> 2 ) & 3 * ONES_STEP_4 );
			byteSums = ( byteSums + ( byteSums >>> 4 ) ) & 0x0f * ONES_STEP_8;
			byteSums *= ONES_STEP_8;

			final int bitCount = (int)( byteSums >>> 56 );
			if ( residual < bitCount ) break;

			word = bits[ ++wordIndex ];
			residual -= bitCount;
		} 

        // Phase 2: compare each byte sum with k
        final long residualStep8 = residual * ONES_STEP_8;
        final long byteOffset = ( ( ( ( ( ( residualStep8 | MSBS_STEP_8 ) - ( byteSums & ~MSBS_STEP_8 ) ) ^ byteSums ^ residualStep8 ) & MSBS_STEP_8 ) >>> 7 ) * ONES_STEP_8 >>> 53 ) & ~0x7;

        // Phase 3: Locate the relevant byte and make 8 copies with incremental masks
        final int byteRank = (int)( residual - ( ( ( byteSums << 8 ) >>> byteOffset ) & 0xFF ) );

        final long spreadBits = ( word >>> byteOffset & 0xFF ) * ONES_STEP_8 & INCR_STEP_8;
        final long bitSums = ( ( ( spreadBits | ( ( spreadBits | MSBS_STEP_8 ) - ONES_STEP_8 ) ) & MSBS_STEP_8 ) >>> 7 ) * ONES_STEP_8;

        // Compute the inside-byte location and return the sum
        final long byteRankStep8 = byteRank * ONES_STEP_8;

        return wordIndex * 64 + byteOffset + ( ( ( ( ( ( byteRankStep8 | MSBS_STEP_8 ) - ( bitSums & ~MSBS_STEP_8 ) ) ^ bitSums ^ byteRankStep8 ) & MSBS_STEP_8 ) >>> 7 ) * ONES_STEP_8 >>> 56 );
	}

	public long numBits() {
		return (long)inventory.length * Long.SIZE + (long)subinventory.length * Long.SIZE + (long)exactSpill.length * Long.SIZE;
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		subinventory16 = LongArrayBitVector.wrap( subinventory ).asLongBigList( Short.SIZE );
	}

	public long[] bits() {
		return bits;
	}

	public long length() {
		return length; 
	}
}
