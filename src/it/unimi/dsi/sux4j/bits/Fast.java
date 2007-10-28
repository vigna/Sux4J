package it.unimi.dsi.sux4j.bits;

import java.util.Arrays;

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

	/** Returns the number of bits that are necessary to encode the argument.
	 * 
	 * @param x an integer.
	 * @return the number of bits that are necessary to encode <code>x</code>.
	 */
	public static int length( final int x ) {
		return x == 0 ? 1 : mostSignificantBit( x ) + 1;
	}

	/** Returns the number of bits that are necessary to encode the argument.
	 * 
	 * @param x a long.
	 * @return the number of bits that are necessary to encode <code>x</code>.
	 */
	public static int length( final long x ) {
		return x == 0 ? 1 : mostSignificantBit( x ) + 1;
	}

	/** Returns the number of bits set to one in a long.
	 * 
	 * <p>This method implements a classical broadword algorithm. 
	 * 
	 * @param x a long.
	 * @return the number of bits set to one in <code>x</code>.
	 */

	public static int count( final long x ) {
		long byteSums = x - ( ( x & 0xa * ONES_STEP_4 ) >>> 1 );
        byteSums = ( byteSums & 3 * ONES_STEP_4 ) + ( ( byteSums >>> 2 ) & 3 * ONES_STEP_4 );
        byteSums = ( byteSums + ( byteSums >>> 4 ) ) & 0x0f * ONES_STEP_8;
        return (int)( byteSums * ONES_STEP_8 >>> 56 );
	}
	
	/** Returns the most significant bit of a long.
	 * 
	 * <p>This method implements Gerth Brodal's broadword algorithm. On 64-bit architectures
	 * it is an order of magnitude faster than standard bit-fiddling techniques.
	 * 
	 * @param x a long.
	 * @return the most significant bit of <code>x</code>, of <code>x</code> is nonzero; &minus;1, otherwise.
	 */
	public static int mostSignificantBit( long x ) {
		if ( x == 0 ) return -1;
		
		int msb = 0;
		
		if ( ( x & 0xFFFFFFFF00000000L ) != 0 ) {
			x >>>= ( 1 << 5 );
			msb += ( 1 << 5 );
		}
		
		if ( ( x & 0xFFFF0000 ) != 0 ) {
			x >>>= ( 1 << 4 );
			msb += ( 1 << 4 );
		}
		
		// We have now reduced the problem to finding the msb in a 16-bit word.
		
		x |= x << 16;
		x |= x << 32;
		
		final long y = x & 0xFF00F0F0CCCCAAAAL;
		
		long t = 0x8000800080008000L & ( y | (( y | 0x8000800080008000L ) - ( x ^ y )));
		
		t |= t << 15;
		t |= t << 30;
		t |= t << 60;
		
		return (int)( msb + ( t >>> 60 ) );
	}
	
	final private static byte[] LSB_TABLE = { 
		0, 1, 56, 2, 57, 49, 28, 3, 61, 58, 42, 50, 38, 29, 17, 4, 62, 47, 59, 36, 45, 43, 51, 22, 53, 39, 33, 30, 24, 18, 12, 5, 63, 55, 48, 27, 60, 41, 37, 16, 46, 35, 44, 21, 52, 32, 23, 11, 54, 26, 40, 15, 34, 20, 31, 10, 25, 14, 19, 9, 13, 8, 7, 6
	};
	
	/** Returns the least significant bit of a long.
	 * 
	 * 
	 * @param x a long.
	 * @return the least significant bit of <code>x</code>, of <code>x</code> is nonzero; &minus;1, otherwise.
	 */
	public static int leastSignificantBit( long x ) {
		return LSB_TABLE[ (int)( ( ( x & -x ) * 0x03f79d71b4ca8b09L ) >>> 58 ) ]; 
	}
	
	public static void main( final String a[] ) {
		final long n = Long.parseLong( a[ 0 ] );
		final long incr = Long.MAX_VALUE / ( n / 2 );
				
		long start, elapsed;
		
		for( int k = 10; k-- !=0;  ) {
			System.out.print( "Broadword msb: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) mostSignificantBit( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

			System.out.print( "Dichotomous msb: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) it.unimi.dsi.mg4j.util.Fast.mostSignificantBit( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

			System.out.print( "java.lang msb: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) Long.numberOfLeadingZeros( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

			System.out.print( "Multiplication/lookup lsb: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) leastSignificantBit( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

			System.out.print( "Byte-by-byte lsb: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) it.unimi.dsi.mg4j.util.Fast.leastSignificantBit( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

			System.out.print( "java.lang lsb: " );
			
			start = System.currentTimeMillis();
			for( long i = n, v = 0; i-- != 0; ) Long.numberOfTrailingZeros( v += incr );
			elapsed = System.currentTimeMillis() - start;

			System.out.println( "elapsed " + elapsed + ", " + ( 1000000.0 * elapsed / n ) + " ns/call" );

		}

	}
	
}
