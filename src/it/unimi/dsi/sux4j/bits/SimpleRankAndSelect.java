package it.unimi.dsi.sux4j.bits;

public class SimpleRankAndSelect extends AbstractRankAndSelect {
	final public static int BITS_IN_LONG = 64;
	final public static int BITS_IN_LONG_SHIFT_MASK = 6;
	final public static int BITS_IN_LONG_MODULO_MASK = BITS_IN_LONG - 1;

	
	final private long length;
	final private long[] bits;
	final private long[] count;
	final private int numCounts;
	final private long numOnes;
	final private int k;
	final private int blockBits;
	
	public static int popCount( long x ) {
		int c = 0;
		for( int i = 4; i-- != 0; ) {
			c += BitVectors.COUNT[ (int)( x & 0xFFFF ) ];
			x >>>= 16;
		}
		return c;
	}
	
	public SimpleRankAndSelect( final BitVector bitVector, int k ) {
		this( bitVector.bits(), bitVector.length(), k );
	}
		
	public SimpleRankAndSelect( long[] bits, long length, int k ) {
		this.bits = bits;
		this.length = length;
		this.k = k;
		
		blockBits = k * BITS_IN_LONG;
		numCounts = (int)( length / blockBits );
		count = new long[ numCounts + 1 ];

		if ( numCounts != 0 ) {

			// Initialise counts
			int c;
			c = 0;
			for( int j = k; j-- != 0; ) c += popCount( bits[ j ] );
			count[ 0 ] = c;

			for( int i = 1; i < numCounts; i++ ) {
				c = 0;
				for( int j = k; j-- != 0; ) c += popCount( bits[ i * k + j ] );
				count[ i ] = count[ i - 1 ] + c;
			}
		}
		
		count[ numCounts ] = Integer.MAX_VALUE; // To simplify binary search
		numOnes = rank( length - 1 );
	}
	
	
	public long rank( long pos ) {
		if ( pos == -1 ) return 0;
		pos++;
		final int end = (int)( pos / blockBits );
		long c = end > 0 ? count[ end - 1 ] : 0;
		int r;
		int index = (int)( pos >> BITS_IN_LONG_SHIFT_MASK );
		index -= index % k;
		int i = 0;
		for( r = (int)( pos - end * blockBits ); r >= BITS_IN_LONG; r -= BITS_IN_LONG ) c += popCount( bits[ index + i++ ] );
		if ( r != 0 ) c += popCount( bits[ index + i ] & -1L << BITS_IN_LONG - r );
		return c;
	}

	public static int bitSearch( int x, int k ) {
		for( int i = 16; i-- != 0; ) if ( ( x & 1 << i ) != 0 && --k == 0 ) return 15 - i;
		return -1;
	}
	
	public static int popSearch( long x, int k ) {
		int c, t, tot = 0;
		
		t = (int)( x >>> 48 & 0xFFFF );
		c = BitVectors.COUNT[ t ];
		tot += c;
		if ( k - c <= 0 ) return bitSearch( t, k );
		k -= c;
		
		t = (int)( x >>> 32 & 0xFFFF );
		c = BitVectors.COUNT[ t ];
		tot += c;
		if ( k - c <= 0 ) return bitSearch( t, k ) + 16;
		k -= c;
		
		t = (int)( x >>> 16 & 0xFFFF );
		c = BitVectors.COUNT[ t ];
		tot += c;
		if ( k - c <= 0 ) return bitSearch( t, k ) + 32;
		k -= c;
		
		t = (int)( x & 0xFFFF );
		c = BitVectors.COUNT[ t ];
		tot += c;
		if ( k - BitVectors.COUNT[ t ] <= 0 ) return bitSearch( t, k ) + 48;
		return -tot - 1;
	}
	
	public long select( long rank ) {
		if ( rank == 0 || rank > numOnes ) return -1;
		int l = 0, r = numCounts + 1, p = 0;
		while( r != l ) {
			p = ( l + r ) / 2;
			if ( count[ p ] >= rank && ( p == 0 || count[ p - 1 ] < rank ) ) break;
			if ( count[ p ] < rank ) l = p + 1;
			else r = p;
		}
		
		if ( p > numCounts ) return -1;
		int residual = (int)( p == 0 ? rank : rank - count[ p - 1 ] );
		int pos = p * k, t;
		for( int i = 0; i < k; i++ ) {
			t = popSearch( bits[ pos + i ], residual );
			if ( t >= 0 ) return p * blockBits + ( i << BITS_IN_LONG_SHIFT_MASK ) + t;
			residual -= -t - 1;
		}
		
		return -1;
	}

	public long[] bits() {
		return bits;
	}
	
	public long length() {
		return length;
	}
}
