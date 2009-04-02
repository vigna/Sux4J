import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.sux4j.bits.JacobsonBalancedParentheses;

import java.util.Random;

public class TestFindClose {

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
	
	public static void fillParen( long bits[], long num_bits ) {
		Random random = new Random();
		bits[ 0 ] = 1; // First open parenthesis
		for( int i = 1, r = 0; i < num_bits - 1; i++ ) {
			assert( r * ( num_bits - 1 - i + r + 2 ) / ( 2. * ( num_bits - 1 - i ) * ( r + 1 ) ) >= 0 );
			assert( r * ( num_bits - 1 - i + r + 2 ) / ( 2. * ( num_bits - 1 - i ) * ( r + 1 ) ) <= 1 );

			if ( random.nextDouble() >=  ( r * ( num_bits - 1 - i + r + 2 ) / ( 2. * ( num_bits - 1 - i ) * ( r + 1 ) ) ) ) {
				bits[ i / 64 ] |= 1L << i % 64;
				r++;
			}
			else r--;
		} 
	}

	
	public static void main( String a[] ) {
		Random random = new Random( 1 );
		final long n = Long.parseLong( a[ 0 ] );
		
		long test[] = new long[ (int)( ( n + 63 ) / 64 ) ];
		fillParen( test, n );
		
		for( int k = 10; k-- != 0; ) {
			long start = - System.currentTimeMillis();
			for( long i = n; i -- != 0; ) {
				if ( ( test[ (int)(i / 64) ] & 1 ) != 0 ) JacobsonBalancedParentheses.findNearClose( test[(int)(i / 64)] );
			}
			start += System.currentTimeMillis();
			System.err.println( "Broadword: " + start + "ms " + ( start * 1000000.0 ) / (n / 2) + " ns/find" );

			start = - System.currentTimeMillis();
			for( long i = n; i -- != 0; ) {
				if ( ( test[ (int)(i / 64) ] & 1 ) != 0 ) JacobsonBalancedParentheses.findNearCloseAlt( test[(int)(i / 64)] );
			}
			start += System.currentTimeMillis();
			System.err.println( "Broadword 2: " + start + "ms " + ( start * 1000000.0 ) /( n / 2 ) + " ns/find" );

			start = - System.currentTimeMillis();
			for( long i = n; i -- != 0; ) {
				if ( ( test[ (int)(i / 64) ] & 1 ) != 0 ) JacobsonBalancedParentheses.findNearClose2( test[(int)(i / 64)] );
			}
			start += System.currentTimeMillis();
			System.err.println( "Loop: " + start + "ms " + ( start * 1000000.0 ) / ( n / 2 ) + " ns/find" );
		
		}

		
		/*
		for(;;) {
			long test = random.nextLong();

			for( long iq = Long.bitCount( test ); i-- != 0; ) {
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
