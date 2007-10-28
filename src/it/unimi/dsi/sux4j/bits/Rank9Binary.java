package it.unimi.dsi.sux4j.bits;

public class Rank9Binary extends AbstractRankAndSelect {
	private static final long serialVersionUID = 0L;
	final public static int BITS_IN_LONG = 64;
	final public static int BITS_IN_LONG_SHIFT_MASK = 6;
	final public static int BITS_IN_LONG_MODULO_MASK = BITS_IN_LONG - 1;
	private static final boolean ASSERTS = false;

	public final static long ONES_STEP_9 = 1L << 0 | 1L << 9 | 1L << 18 | 1L << 27 | 1L << 36 | 1L << 45 | 1L << 54;
	public final static long MSBS_STEP_9 = 0x100L * ONES_STEP_9;

	final private long length;
	final private long[] bits;
	final private long[] count;
	final private int[] inventory;
	final private int numWords;
	final private long numOnes;
	final private int onesPerInventory;
	final private int log2OnesPerInventory;
	private long lastOne;

	public Rank9Binary( final BitVector bitVector ) {
		this( bitVector.bits(), bitVector.length() );
	}
		
	public Rank9Binary( long[] bits, long length ) {
		this.bits = bits;
		this.length = length;
		numWords = bits.length;
		
		// Init rank/select structure
		count = new long[ ( numWords / 8 ) * 2 + 3 ];

		long c = 0;
		int pos = 0;
		for( int i = 0; i < numWords; i += 8, pos += 2 ) {
			count[ pos ] = c;
			c += Fast.count( bits[ i ] );
			for( int j = 1;  j < 8; j++ ) {
				count[ pos + 1 ] |= ( ( i + j <= numWords ) ? c - count[ pos ] : 0x1FF ) << 9 * ( j - 1 );
				if ( i + j < numWords ) c += Fast.count( bits[ i + j ] );
			}
		}
		
		numOnes = c;
		if ( numWords % 8 == 0 ) count[ ( numWords / 8 ) * 2 ] = c;
		count[ ( numWords / 8 ) * 2 + 2 ] = c;

		log2OnesPerInventory = Fast.mostSignificantBit( ( c * 16 * 64 + length - 1 ) / length );
		onesPerInventory = 1 << log2OnesPerInventory;
		final int inventorySize = (int)( ( c + onesPerInventory - 1 ) / onesPerInventory );

		inventory = new int[ inventorySize + 1 ];
		long d = 0, l = -1;
		final long mask = onesPerInventory - 1;
		for( int i = 0; i < numWords; i++ )
			for( int j = 0; j < 64; j++ )
				if ( ( bits[ i ] & 1L << j ) != 0 ) {
					l = i * 64 + j;
					if ( ( d & mask ) == 0 ) inventory[ (int)( d >> log2OnesPerInventory ) ] = ( i / 8 ) * 2;
					d++;
				}

		lastOne = l;
		inventory[ inventorySize ] = ( numWords / 8 ) * 2;
	}
	
	
	public long rank( long pos ) {
		// This test can be eliminated if there is always an additional word at the end of the bit array.
		if ( pos > lastOne ) return numOnes;
		
		final int word = (int)( pos / 64 );
		final int block = word / 4 & ~1;
		final int offset = word % 8 - 1;
        
		return count[ block ] + ( count[ block + 1 ] >>> ( offset + ( offset >>> 32 - 4 & 0x8 ) ) * 9 & 0x1FF ) + Fast.count( bits[ word ] & ( ( 1L << pos % 64 ) - 1 ) );
	}

	
	public long select( long rank ) {
		if ( rank >= numOnes ) return -1;
		
		final long[] count = this.count;
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

		final long rankInBlockStep_9 = rankInBlock * ONES_STEP_9;
		final long subcounts = count[ blockLeft + 1 ];
		final long offsetInBlock = ( ( ( ( ( ( ( rankInBlockStep_9 | MSBS_STEP_9 ) - ( subcounts & ~MSBS_STEP_9 ) ) | ( subcounts ^ rankInBlockStep_9 ) ) ^ ( subcounts & ~rankInBlockStep_9 ) ) & MSBS_STEP_9 ) >>> 8 ) * ONES_STEP_9 >>> 54 & 0x7 );
		
		final long word = blockLeft * 4 + offsetInBlock;
		final long rankInWord = rankInBlock - ( subcounts >>> ( offsetInBlock - 1 & 7 ) * 9 & 0x1FF );

        return word * 64 + Fast.select( bits[ (int)word ], (int)rankInWord );
	}

	public long[] bits() {
		return bits;
	}
	
	public long length() {
		return length;
	}
	
	@Override
	public long lastOne() {
		return lastOne;
	}
}
