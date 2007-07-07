package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.BitVector;
import junit.framework.TestCase;

public class BitVectorTest extends TestCase {

	
	public static void testSetClearFlip( final BitVector v ) {
		final int size = v.size();
		for( int i = size; i-- != 0; ) {
			v.set( i );
			assertTrue( v.getBoolean( i ) );
		}

		for( int i = size; i-- != 0; ) {
			v.clear( i );
			assertFalse( v.getBoolean( i ) );
		}

		for( int i = size; i-- != 0; ) {
			v.set( i );
			v.flip( i );
			assertFalse( v.getBoolean( i ) );
			v.flip( i );
			assertTrue( v.getBoolean( i ) );
		}
	}
	
	public static void testFillFlip( final BitVector v ) {

		v.clear();
		v.size( 100 );
		v.fill( true );
		for( int i = v.size(); i-- != 0; ) assertTrue( v.getBoolean( i ) );
		v.fill( false );
		for( int i = v.size(); i-- != 0; ) assertFalse( v.getBoolean( i ) );
		
		v.clear();
		v.size( 100 );
		v.fill( 1 );
		for( int i = v.size(); i-- != 0; ) assertTrue( v.getBoolean( i ) );
		v.fill( 0 );
		for( int i = v.size(); i-- != 0; ) assertFalse( v.getBoolean( i ) );
		
		v.clear();
		v.size( 100 );
		v.fill( 5, 70, true );
		for( int i = v.size(); i-- != 0; ) assertEquals( i >= 5 && i < 70, v.getBoolean( i ) );
		v.fill( true );
		v.fill( 5, 70, false );
		for( int i = v.size(); i-- != 0; ) assertEquals( i < 5 || i >= 70, v.getBoolean( i ) );
		
		v.clear();
		v.size( 100 );
		v.fill( 5, 70, 1 );
		for( int i = v.size(); i-- != 0; ) assertEquals( Integer.toString(  i  ), i >= 5 && i < 70, v.getBoolean( i ) );
		v.fill( true );
		v.fill( 5, 70, 0 );
		for( int i = v.size(); i-- != 0; ) assertEquals( i < 5 || i >= 70, v.getBoolean( i ) );
		
		v.clear();
		v.size( 100 );
		v.flip( 5, 70 );
		for( int i = v.size(); i-- != 0; ) assertEquals( Integer.toString(  i  ), i >= 5 && i < 70, v.getBoolean( i ) );
		v.fill( true );
		v.flip( 5, 70 );
		for( int i = v.size(); i-- != 0; ) assertEquals( i < 5 || i >= 70, v.getBoolean( i ) );
		
	}
}
