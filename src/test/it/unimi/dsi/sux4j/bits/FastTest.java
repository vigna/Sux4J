package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.Fast;
import junit.framework.TestCase;

public class FastTest extends TestCase {
	
	public void testCeilLog2() {
		for( int i = 1; i < 1000; i++ ) assertEquals( (int)Math.ceil( Math.log( i ) / Math.log( 2 ) ), Fast.ceilLog2( i ) );
	}

	public void testLength() {
		assertEquals( 1, Fast.length( 0 ) );
		assertEquals( 1, Fast.length( 0L ) );
		for( int i = 1; i < 100; i++ ) assertEquals( it.unimi.dsi.mg4j.util.Fast.mostSignificantBit( i ) + 1, Fast.length( i ) ); 
		for( long i = 1; i < 100; i++ ) assertEquals( it.unimi.dsi.mg4j.util.Fast.mostSignificantBit( i ) + 1, Fast.length( i ) ); 
	}
	
	public void testMostSignificantBit() {
		for( int i = 0; i < 64; i++ ) {
			assertEquals( i, Fast.mostSignificantBit( 1L << i ) );
			assertEquals( i, Fast.mostSignificantBit( 1L << i | 1 ) );
			assertEquals( i, Fast.mostSignificantBit( 1L << i ) );
			assertEquals( i, Fast.mostSignificantBit( 1L << i | 1 ) );
		}
		
		for( long i = 1; i < ( 1L << 62 ); i += 1000000000000L )
			assertEquals( Long.toString( i ), 63 - Long.numberOfLeadingZeros( i ), Fast.mostSignificantBit( i ) );
	}

}
