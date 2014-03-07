import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import org.apache.commons.math3.random.RandomGenerator;

public class TestRank {

	public static String binary(long l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Long.toBinaryString( l );
		s = s.substring( s.length() - 64 );
		StringBuilder t = new StringBuilder();
		for( int i = 0; i < 8; i++ ) {
			if ( i > 0 ) t.append( '.' );
			t.append( s.substring( i * 8, i * 8 + 8 ) );
		}
		return t.toString();
	}
	public static String binary(int l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		s = s.substring( s.length() - 32 );
		StringBuilder t = new StringBuilder();
		for( int i = 0; i < 4; i++ ) {
			if ( i > 0 ) t.append( '.' );
			t.append( s.substring( i * 8, i * 8 + 8 ) );
		}
		return t.toString();
	}

	public final static long SPREAD = 0x0101010101010101L;
	public final static long BYTE_MSBS = 0x80 * SPREAD;
	public final static long INCR = 0xffL << 56 | 0x7fL << 48 | 0x3fL << 40 | 0x1FL << 32 | 0xF << 24 | 0x7 << 16 | 0x3 << 8 | 0x1; 
	
	public static int selectVerbose( long x, int k ) {
		System.out.println( "select(" + binary( x ) + ", " + k + ")");
		// Phase 1: sums by byte
        long bytesums = x - ( ( x & 0xaaaaaaaaaaaaaaaaL ) >>> 1 );
        bytesums = ( bytesums & 0x3333333333333333L ) + ( (bytesums >>> 2 ) & 0x3333333333333333L );
        bytesums = ( bytesums + ( bytesums >>> 4 ) ) & 0x0f0f0f0f0f0f0f0fL;
        
        System.out.println( "After sums by byte:\t" + binary( bytesums ) );
        // Phase 2: cumulative sums        
        bytesums *= SPREAD;
        System.out.println( "After cumulation:\t" + binary( bytesums ) );
        
        // Phase 2: rule out  wrong bytes
        long spreadK = ( k + 1 ) * SPREAD;
        System.out.println( "k, spreaded in bytes:\t" + binary( spreadK ) );
        long z = (( bytesums | BYTE_MSBS ) - ( spreadK & ~BYTE_MSBS ) ) ^ ( ( bytesums ^ ~spreadK ) & BYTE_MSBS );
		
        System.out.println( "After subtraction:\t" + binary( z ) );
        System.out.println( "After msb selection:\t" + binary( ( z & BYTE_MSBS ) >>> 7 ) );
        System.out.println( "After cumulation:\t" + binary( ( ( z & BYTE_MSBS ) >>> 7 ) * SPREAD ) );
        long place = ( ( z & BYTE_MSBS ) >>> 7 ) * SPREAD >>> 56;
        int offset = (int)( ( ( bytesums << 8 ) >>> ( place << 3 ) ) & 0xFF );
        System.out.println( "The  rank " + k + " bit is in byte " + place + ", so its byte rank is " + ( k - offset ) );
        int byteRank = (int)( k - ( ( ( bytesums << 8 ) >>> ( place << 3 ) ) & 0xFF ) );
        
        long test = ( x >>> ( place << 3 ) & 0xFF ) * SPREAD & INCR;
        System.out.println( "Byte " + place+  ", spreaded:\t" + binary( test ) );
        
        bytesums = test - ( ( test & 0xaaaaaaaaaaaaaaaaL ) >>> 1 );
        bytesums = ( bytesums & 0x3333333333333333L ) + ( (bytesums >>> 2 ) & 0x3333333333333333L );
        bytesums = ( bytesums + ( bytesums >>> 4 ) ) & 0x0f0f0f0f0f0f0f0fL;
        System.out.println( "Bit-by-bit increments:\t" + binary( bytesums ) );
        
        long spreadByteRank = ( byteRank + 1 ) * SPREAD;
        z = (( bytesums | BYTE_MSBS ) - ( spreadByteRank & ~BYTE_MSBS ) ) ^ ( ( bytesums ^ ~spreadByteRank ) & BYTE_MSBS );
        System.out.println( "After subtraction:\t" + binary( z ) );
        return (int)( ( place << 3 ) + ( ( ( z & BYTE_MSBS ) >>> 7 ) * SPREAD >>> 56 ) );
	}
	
	public static int select( long x, int k ) {
		// Phase 1: sums by byte
        long bytesums = x - ( ( x & 0xaaaaaaaaaaaaaaaaL ) >>> 1 );
        bytesums = ( bytesums & 0x3333333333333333L ) + ( (bytesums >>> 2 ) & 0x3333333333333333L );
        bytesums = ( bytesums + ( bytesums >>> 4 ) ) & 0x0f0f0f0f0f0f0f0fL;
        bytesums *= SPREAD;
        long spreadK = ( k + 1 ) * SPREAD;
		
        long place = ( (( ( (( bytesums | BYTE_MSBS ) - ( spreadK & ~BYTE_MSBS ) ) ^ ( ( bytesums ^ ~spreadK ) & BYTE_MSBS ) ) & BYTE_MSBS ) >>> 7 ) * SPREAD >>> 56 ) << 3;
        int byteRank = (int)( k - ( ( ( bytesums << 8 ) >>> place ) & 0xFF ) );
        
        long test = ( x >>> place & 0xFF ) * SPREAD & INCR;
        bytesums = test - ( ( test & 0xaaaaaaaaaaaaaaaaL ) >>> 1 );
        bytesums = ( bytesums & 0x3333333333333333L ) + ( (bytesums >>> 2 ) & 0x3333333333333333L );
        bytesums = ( bytesums + ( bytesums >>> 4 ) ) & 0x0f0f0f0f0f0f0f0fL;
        
        long spreadByteRank = ( byteRank + 1 ) * SPREAD;
        return (int)( place + ( ( ( ((( bytesums | BYTE_MSBS ) - ( spreadByteRank & ~BYTE_MSBS ) ) ^ ( ( bytesums ^ ~spreadByteRank ) & BYTE_MSBS ) ) & BYTE_MSBS ) >>> 7 ) * SPREAD >>> 56 ) );
	}
	
	public static void main( String a[] ) {
		RandomGenerator random = new XorShift1024StarRandomGenerator( 1 );
		
		long test[] = new long[10000000];
		int pos[] = new int[ 10000000 ], count;
		for( int i = test.length; i -- != 0; ) {
			test[ i ] = random.nextLong() % 100 << 50;
			count = Long.bitCount( test[ i ] );
			pos[ i ] = random.nextInt( count > 0 ? count : 1 );
		}
		
		for( int k = 10; k-- != 0; ) {
			long start = - System.currentTimeMillis();
			for( int i = test.length; i -- != 0; ) Fast.select( test[ i ], pos[ i ] );
			start += System.currentTimeMillis();
			System.err.println( "Popsearch: " + start + "ms " + test.length *1000 / start + " ranks/s" );

			start = - System.currentTimeMillis();
			for( int i = test.length; i -- != 0; ) select( test[ i ], pos[ i ] );
			start += System.currentTimeMillis();
			System.err.println( "SWAR: " + start + "ms " + test.length *1000 / start + " ranks/s" );
		
		}

		
		/*
		for(;;) {
			long test = random.nextLong();

			for( int i = Long.bitCount( test ); i-- != 0; ) {
				int k = i;
				int j;
				for( j = 0; j < 64; j++ ) if ( ( test & 1L << j ) != 0 && k-- == 0 ) break;

				selectVerbose( test, i + 1 );
				if ( select( test, i + 1 ) != j ) {
					System.out.println("Error on " + ( i + 1 ) + ": should be " + j + ", but it is " + select( test, i + 1 ) );
					break;
				}
			}
		}*/
	}
}
