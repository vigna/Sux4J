package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.LongBigList;

import java.util.Arrays;

import junit.framework.TestCase;

public abstract class BitVectorTest extends TestCase {

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
	
	
	public static void testRemove( final BitVector v ) {
		v.clear();
		v.size( 100 );
		v.set( 0, true );
		assertTrue( v.removeBoolean( 0 ) );
		assertFalse( v.getBoolean( 0 ) );

		v.clear();
		v.size( 100 );
		v.set( 63, true );
		v.set( 64, true );
		assertTrue( v.removeBoolean( 63 ) );
		assertTrue( v.getBoolean( 63 ) );
		assertFalse( v.getBoolean( 64 ) );
		assertFalse( v.getBoolean( 0 ) );
	}
	
	public static void testAdd( final BitVector v ) {
		v.clear();
		v.size( 100 );
		v.add( 0, true );
		assertTrue( v.getBoolean( 0 ) );
		v.add( 0, true );
		assertTrue( v.getBoolean( 0 ) );
		assertTrue( v.getBoolean( 1 ) );

		v.clear();
		v.append( 1, 2 );
		v.append( 1, 2 );
		v.append( 3, 2 );
		assertEquals( LongArrayBitVector.of( 0, 1, 0, 1, 1, 1 ), v );
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
	
	
	public static void testCopy( final BitVector v ) {
		v.clear();
		v.size( 100 );
		v.fill( 5, 80, true );
		BitVector w = v.copy( 0, 85 );
		for( int i = w.size(); i-- != 0; ) assertEquals( i >= 5 && i < 80, w.getBoolean( i ) );	
		w = v.copy( 5, 85 );
		for( int i = w.size(); i-- != 0; ) assertEquals( i < 75, w.getBoolean( i ) );	

		v.clear();
		int[] bits = { 0,0,0,0,1,1,1,0,0,0,0,1,1,0,0 };
		for( int i = 0; i < bits.length; i++ ) v.add( bits[ i ] );
		
		LongArrayBitVector c = LongArrayBitVector.getInstance();
		for( int i = 5; i < bits.length; i++ ) c.add( bits[ i ] );
		
		assertEquals( c, v.copy( 5, 15 ) );

		v.clear();
		bits = new int[] { 0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0,0,0,0,0,1,1,1,0,0,0,0,1,1,0,0 };
		for( int i = 0; i < bits.length; i++ ) v.add( bits[ i ] );
		c = LongArrayBitVector.getInstance();
		for( int i = 5; i < bits.length - 2; i++ ) c.add( bits[ i ] );
		
		assertEquals( c, v.copy( 5, bits.length - 2 ) );
		
		assertEquals( v, v.copy() );
	}
	
	public static void testBits( BitVector b ) {
		for( int i = 0; i < 100; i++ ) b.add( i % 2 );
		assertTrue( LongArrayBitVector.wrap( b.bits(), b.length() ).toString(), Arrays.equals( new long[] { 0x5555555555555555L, 0x5555555550000000L }, b.bits() ) );
	}

	public static void testLongBigListView( BitVector b ) {
		LongBigList list = b.asLongBigList( 10 );
		for( int i = 0; i < 100; i++ ) list.add( i );
		for( int i = 0; i < 100; i++ ) assertEquals( i, list.getLong( i ) );
		for( int i = 0; i < 100; i++ ) list.add( i );
		for( int i = 0; i < 100; i++ ) assertEquals( i, list.set( i, i + 1 ) );
		for( int i = 0; i < 100; i++ ) assertEquals( i + 1, list.getLong( i ) );
	}

	public static void testFirstLastPrefix( BitVector b ) {
		b.clear();
		b.length( 60 );
		b.set( 4, true );
		assertEquals( 4, b.firstOne() );
		assertEquals( 4, b.lastOne() );
		b.set( 50, true );
		assertEquals( 4, b.firstOne() );
		assertEquals( 50, b.lastOne() );
		b.set( 20, true );
		assertEquals( 4, b.firstOne() );
		assertEquals( 50, b.lastOne() );
		b.length( 100 );
		b.set( 90, true );
		assertEquals( 4, b.firstOne() );
		assertEquals( 90, b.lastOne() );
		b.clear();
		b.length( 100 );
		b.set( 4, true );
		assertEquals( 4, b.firstOne() );
		assertEquals( 4, b.lastOne() );
		b.set( 90, true );
		assertEquals( 4, b.firstOne() );
		assertEquals( 90, b.lastOne() );
		
		b.length( 60 );
		BitVector c = b.copy();
		c.length( 40 );
		assertEquals( c.length(), b.maximumCommonPrefixLength( c ) );
		c.flip( 20 );
		assertEquals( 20, b.maximumCommonPrefixLength( c ) );
		c.flip( 0 );
		assertEquals( 0, b.maximumCommonPrefixLength( c ) );

		b.length( 100 );
		c = b.copy();
		c.length( 80 );
		assertEquals( c.length(), b.maximumCommonPrefixLength( c ) );
		c.flip( 20 );
		assertEquals( 20, b.maximumCommonPrefixLength( c ) );
		c.flip( 0 );
		assertEquals( 0, b.maximumCommonPrefixLength( c ) );
	}
}
