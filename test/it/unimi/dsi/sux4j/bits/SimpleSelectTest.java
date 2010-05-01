package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.SimpleSelect;

import java.util.Random;

public class SimpleSelectTest extends RankSelectTestCase {
	
	public void testEmpty() {
		SimpleSelect select;
		
		select = new SimpleSelect( new long[ 1 ], 64 );
		assertEquals( -1, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );
		
		select = new SimpleSelect( new long[ 2 ], 128 );
		assertEquals( -1, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );

		select = new SimpleSelect( new long[ 1 ], 63 );
		assertEquals( -1, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );
		
		select = new SimpleSelect( new long[ 2 ], 65 );
		assertEquals( -1, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );

		select = new SimpleSelect( new long[ 3 ], 129 );
		assertEquals( -1, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );

	}
	
	public void testSingleton() {
		SimpleSelect select;
		
		select = new SimpleSelect( new long[] { 1L << 63, 0 }, 64 );
		assertSelect( select );
		assertEquals( 63, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );
		
		select = new SimpleSelect( new long[] { 1 }, 64 );
		assertSelect( select );
		assertEquals( 0, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );
		
		select = new SimpleSelect( new long[] { 1L << 63, 0 }, 128 );
		assertSelect( select );
		assertEquals( 63, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );

		select = new SimpleSelect( new long[] { 1L << 63, 0 }, 65 );
		assertSelect( select );
		assertEquals( 63, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );

		select = new SimpleSelect( new long[] { 1L << 63, 0, 0 }, 129 );
		assertSelect( select );
		assertEquals( 63, select.select( 0 ) );
		assertEquals( -1, select.select( 1 ) );
	}

	public void testDoubleton() {
		SimpleSelect select;
		
		select = new SimpleSelect( new long[] { 1 | 1L << 32 }, 64 );
		assertSelect( select );
		assertEquals( 0, select.select( 0 ) );
		assertEquals( 32, select.select( 1 ) );
		assertEquals( -1, select.select( 2 ) );
		
		select = new SimpleSelect( new long[] { 1, 1 }, 128 );
		assertSelect( select );
		assertEquals( 0, select.select( 0 ) );
		assertEquals( 64, select.select( 1 ) );
		assertEquals( -1, select.select( 2 ) );

		select = new SimpleSelect( new long[] { 1 | 1L << 32, 0 }, 63 );
		assertSelect( select );
		assertEquals( 0, select.select( 0 ) );
		assertEquals( 32, select.select( 1 ) );
		assertEquals( -1, select.select( 2 ) );
		
		select = new SimpleSelect( new long[] { 1, 1, 0 }, 129 );
		assertSelect( select );
		assertEquals( 0, select.select( 0 ) );
		assertEquals( 64, select.select( 1 ) );
		assertEquals( -1, select.select( 2 ) );
	}

	public void testAlternating() {
		SimpleSelect select;
		int i;
		
		select = new SimpleSelect( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64 );
		assertSelect( select );
		for( i = 32; i-- != 1; ) assertEquals( i * 2 + 1, select.select( i ) );

		select = new SimpleSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128 );
		assertSelect( select );
		for( i = 64; i-- != 1; ) assertEquals( i * 2 + 1, select.select( i ) );

		select = new SimpleSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5 );
		assertSelect( select );
		for( i = 32 * 5; i-- != 1; ) assertEquals( i * 2 + 1, select.select( i ) );

		select = new SimpleSelect( new long[] { 0xAAAAAAAAL }, 33 );
		assertSelect( select );
		for( i = 16; i-- != 1; ) assertEquals( i * 2 + 1, select.select( i ) );

		select = new SimpleSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128 );
		assertSelect( select );
		for( i = 56; i-- != 1; ) assertEquals( i * 2 + 1, select.select( i ) );
	}

	public void testSelect() {
		SimpleSelect select;
		select = new SimpleSelect( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 );
		assertSelect( select );
		assertEquals( 0, select.select( 0 ) );
	}

	public void testSparse() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length( 256 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 100000 );
		bitVector.set( 199999 );
		SimpleSelect select;

		select = new SimpleSelect( bitVector );
		assertSelect( select );

		bitVector = LongArrayBitVector.getInstance().length( 64 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 40000 );
		bitVector.set( 49999 );

		select = new SimpleSelect( bitVector );
		assertSelect( select );

		bitVector = LongArrayBitVector.getInstance().length( 32 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 20000 );
		bitVector.set( 29999 );

		select = new SimpleSelect( bitVector );
		assertSelect( select );
	}

	public void testDense() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 2 );
		SimpleSelect select;

		select = new SimpleSelect( bitVector );
		assertSelect( select );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 4 );

		select = new SimpleSelect( bitVector );
		assertSelect( select );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 8 );

		select = new SimpleSelect( bitVector );
		assertSelect( select );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 16 );

		select = new SimpleSelect( bitVector );
		assertSelect( select );

		bitVector = LongArrayBitVector.getInstance().length( 32 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 32 );

		select = new SimpleSelect( bitVector );
		assertSelect( select );
	}
	
	public void testRandom() {
		Random r = new Random( 1 );
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( 1000 );
		for( int i = 0; i < 1000; i++ ) bitVector.add( r.nextBoolean() );
		SimpleSelect select;

		select = new SimpleSelect( bitVector );
		assertSelect( select );
	}
	
	public void testAllSizes() {
		LongArrayBitVector v;
		SimpleSelect r;
		for( int size = 0; size <= 4096; size++ ) {
			v = LongArrayBitVector.getInstance().length( size );
			for( int i = ( size + 1 ) / 2; i-- != 0; ) v.set( i * 2 );
			r = new SimpleSelect( v );
			for( int i = size / 2; i-- != 0; ) assertEquals( i * 2, r.select( i ) );
			
			v = LongArrayBitVector.getInstance().length( size );
			v.fill( true );
			r = new SimpleSelect( v );
			for( int i = size; i-- != 0; ) assertEquals( i, r.select( i ) );
		}
	}
	
	public void testVeryLarge() {
		LongArrayBitVector v = LongArrayBitVector.getInstance( 2200000000L );
		for( int i = 0; i < 2200000000L / 64; i++ ) v.append( 0x5555555555555555L, 64 );
		SimpleSelect simpleSelect = new SimpleSelect( v );
		for( int i = 0; i < 1100000000; i++ ) assertEquals( i * 2L, simpleSelect.select( i ) );
	}
}
