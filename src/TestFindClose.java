import it.unimi.dsi.sux4j.bits.JacobsonBalancedParentheses;
import it.unimi.dsi.util.XorShiftStarRandom;

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

	public static void fillParen( long bits[], long num_bits ) {
		Random random = new XorShiftStarRandom( 1 );
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
		Random random = new XorShiftStarRandom( 1 );
		final long n = Long.parseLong( a[ 0 ] );
		final double p = a.length > 1 ? Double.parseDouble( a[ 1 ] ) : .5;
		
		long test[] = new long[ (int)( ( n + 63 ) / 64 ) ];
		boolean[] take = new boolean[ test.length ];
		fillParen( test, n );
		long m = 0, far = 0;
		int res;
		for( long i = n; i -- != 0; ) {
			if ( ( test[ (int)(i / 64) ] & 1 ) != 0 ) {
				res =  JacobsonBalancedParentheses.findNearClose( test[(int)( i / 64 ) ] );
				if ( res >= 64 ) {
					take[ (int)(i / 64) ] = true;
					m++;
					far++;
				}
				else if ( take[ (int)(i / 64) ] = random.nextDouble() < p ) m++;
			}
		}
		
		System.err.println( "m: " + m + " far: " + far );
		
		for( int k = 10; k-- != 0; ) {
			long start = - System.currentTimeMillis();
			for( long i = n; i -- != 0; ) {
				if ( take[ (int)(i / 64) ] ) JacobsonBalancedParentheses.findNearClose( test[(int)(i / 64)] );
			}
			start += System.currentTimeMillis();
			System.err.println( "Broadword: " + start + "ms " + ( start * 1000000.0 ) / m + " ns/find" );

			start = - System.currentTimeMillis();
			for( long i = n; i -- != 0; ) {
				if ( take[ (int)(i / 64) ] ) JacobsonBalancedParentheses.findNearCloseAlt( test[(int)(i / 64)] );
			}
			start += System.currentTimeMillis();
			System.err.println( "Broadword 2: " + start + "ms " + ( start * 1000000.0 ) / m+ " ns/find" );

			start = - System.currentTimeMillis();
			for( long i = n; i -- != 0; ) {
				if ( take[ (int)(i / 64) ] ) JacobsonBalancedParentheses.findNearClose2( test[(int)(i / 64)] );
			}
			start += System.currentTimeMillis();
			System.err.println( "Loop: " + start + "ms " + ( start * 1000000.0 ) / m + " ns/find" );
		
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
