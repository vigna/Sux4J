package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2007 Sebastiano Vigna 
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

import static it.unimi.dsi.mg4j.util.Fast.mostSignificantBit;
import it.unimi.dsi.mg4j.io.InputBitStream;

/** All-purpose optimised static-method container class.
 *
 * <P>This class contains static optimised utility methods that are used by all
 * Sux4J classes.
 *
 * @author Sebastiano Vigna
 * @since 0.1
 */

public final class Fast {
	private Fast() {}

	final static public long ONES_STEP_4 = 0x1111111111111111L;
	final static public long ONES_STEP_8 = 0x0101010101010101L;
	
	public static int ceilLog2( final int x ) {
		return mostSignificantBit( x - 1 ) + 1;
	}

	public static int ceilLog2( final long x ) {
		return mostSignificantBit( x - 1 ) + 1;
	}

	public static int length( final int x ) {
		return x == 0 ? 1 : mostSignificantBit( x ) + 1;
	}

	public static int length( final long x ) {
		return x == 0 ? 1 : mostSignificantBit( x ) + 1;
	}

	public static int count( final long x ) {
		long byteSums = x - ( ( x & 0xa * ONES_STEP_4 ) >>> 1 );
        byteSums = ( byteSums & 3 * ONES_STEP_4 ) + ( ( byteSums >>> 2 ) & 3 * ONES_STEP_4 );
        byteSums = ( byteSums + ( byteSums >>> 4 ) ) & 0x0f * ONES_STEP_8;
        return (int)( byteSums * ONES_STEP_8 >>> 56 );
	}
	
	public static int mostSignificantBit( long x ) {
		//System.err.println( "x: "+ x );
		
		int msb = 0;
		
		if ( ( x & 0xFFFFFFFF00000000L ) != 0 ) {
			x >>>= ( 1 << 5 );
			msb += ( 1 << 5 );
		}
		
		if ( ( x & 0xFFFF0000 ) != 0 ) {
			x >>>= ( 1 << 4 );
			msb += ( 1 << 4 );
		}
		
		x |= x << 16;
		x |= x << 32;
		
		//System.err.println( "x2: " + Long.toBinaryString( x ) );
		
		final long y = x & 0xFF00F0F0CCCCAAAAL;
		//System.err.println( "y: " + Long.toBinaryString( y  ) );
		
		long t = 0x8000800080008000L & ( y | (( y | 0x8000800080008000L ) - ( x ^ y )));
		
		//System.err.println( "t: " + Long.toBinaryString( t  ) );
		
		t |= t << 15;
		t |= t << 30;
		t |= t << 60;
		
		//System.err.println( "msb:" + msb + " t2: " + Long.toBinaryString( t  ) );

		return (int)( msb + ( t >>> 60 ) );
	}
	
	public static void main( final String a[] ) {
		final long n = Long.parseLong( a[ 0 ] );
		final long incr = Long.MAX_VALUE / ( n / 2 );
		
		long start, elapsed;
		
		for( int k = 10; k-- !=0;  ) {
			System.out.print( "Broadword: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) mostSignificantBit( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

			System.out.print( "Dichotomous: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) it.unimi.dsi.mg4j.util.Fast.mostSignificantBit( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

			System.out.print( "java.lang: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) Long.numberOfLeadingZeros( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );
		}
	}
	
}
