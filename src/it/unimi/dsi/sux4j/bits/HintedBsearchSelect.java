package it.unimi.dsi.sux4j.bits;

/** A <code>rank9</code> implementation paired with a hinted binary search select implementation.
 * 
 * <p>Instances of this class use {@link Rank9} for ranking. Selection is performed using a binary
 * search over <code>rank9</code>'s block counts, but the search is narrowed by using a small inventory.
 * 
 */

public class HintedBsearchSelect implements Select {
	@SuppressWarnings("unused")
	private static final boolean ASSERTS = false;
	private static final long serialVersionUID = 0L;

	private static final long ONES_STEP_9 = 1L << 0 | 1L << 9 | 1L << 18 | 1L << 27 | 1L << 36 | 1L << 45 | 1L << 54;
	private static final long MSBS_STEP_9 = 0x100L * ONES_STEP_9;

	final private int[] inventory;
	final private int onesPerInventory;
	final private int log2OnesPerInventory;
	private final long numOnes;
	private final int numWords;
	private final long[] bits;
	private final long[] count;
	final private Rank9 rank9;

	public HintedBsearchSelect( final Rank9 rank9 ) {
		this.rank9 = rank9;
		numOnes = rank9.numOnes;
		numWords = rank9.numWords;
		bits = rank9.bits;
		count = rank9.count;
		
		log2OnesPerInventory = rank9.length == 0 ? 0 : Fast.mostSignificantBit( ( numOnes * 16 * 64 + rank9.length - 1 ) / rank9.length );
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

	public long[] bits() {
		return rank9.bits;
	}

	public long length() {
		return rank9.length;
	}

}
