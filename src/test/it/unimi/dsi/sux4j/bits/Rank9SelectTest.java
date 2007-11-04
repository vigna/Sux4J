package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank9Select;

import java.util.Random;

public class Rank9SelectTest extends RankSelectTestCase {
	
	public void testEmpty() {
		Rank9Select rankAndSelect;
		
		rankAndSelect = new Rank9Select( new long[ 1 ], 64 );
		for( int i = 64; i-- != 0; ) assertEquals( Integer.toString( i ), 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );
		
		rankAndSelect = new Rank9Select( new long[ 2 ], 128 );
		for( int i = 128; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[ 1 ], 63 );
		for( int i = 63; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );
		
		rankAndSelect = new Rank9Select( new long[ 2 ], 65 );
		for( int i = 65; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[ 3 ], 129 );
		for( int i = 130; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );

	}
	
	public void testSingleton() {
		Rank9Select rankAndSelect;
		int i;
		
		rankAndSelect = new Rank9Select( new long[] { 1L << 63, 0 }, 64 );
		assertRankAndSelect( rankAndSelect );
		assertEquals( 1, rankAndSelect.rank( 64 ) );
		assertEquals( 0, rankAndSelect.rank( 63 ) );
		for( i = 63; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 63, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );
		
		rankAndSelect = new Rank9Select( new long[] { 1 }, 64 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64; i-- != 2; ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( 0, rankAndSelect.lastOne() );
		
		rankAndSelect = new Rank9Select( new long[] { 1L << 63, 0 }, 128 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 63, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[] { 1L << 63, 0 }, 65 );
		assertRankAndSelect( rankAndSelect );
		for( i = 65; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 63, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[] { 1L << 63, 0, 0 }, 129 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 63, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );
	}

	public void testDoubleton() {
		Rank9Select rankAndSelect;
		int i;
		
		rankAndSelect = new Rank9Select( new long[] { 1 | 1L << 32 }, 64 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64; i-- != 33; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 32, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );
		assertEquals( 32, rankAndSelect.lastOne() );
		
		rankAndSelect = new Rank9Select( new long[] { 1, 1 }, 128 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 64, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );
		assertEquals( 64, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[] { 1 | 1L << 32, 0 }, 63 );
		assertRankAndSelect( rankAndSelect );
		for( i = 63; i-- != 33; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 32, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );
		assertEquals( 32, rankAndSelect.lastOne() );
		
		rankAndSelect = new Rank9Select( new long[] { 1, 1, 0 }, 129 );
		assertRankAndSelect( rankAndSelect );
		for( i = 129; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 64, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );
		assertEquals( 64, rankAndSelect.lastOne() );
	}

	public void testAlternating() {
		Rank9Select rankAndSelect;
		int i;
		
		rankAndSelect = new Rank9Select( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64; i-- != 0; ) assertEquals( i / 2, rankAndSelect.rank( i ) );
		for( i = 32; i-- != 1; ) assertEquals( i * 2 + 1, rankAndSelect.select( i ) );
		assertEquals( 63, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 0; ) assertEquals( i / 2, rankAndSelect.rank( i ) );
		for( i = 64; i-- != 1; ) assertEquals( i * 2 + 1, rankAndSelect.select( i ) );
		assertEquals( 127, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64 * 5; i-- != 0; ) assertEquals( i / 2, rankAndSelect.rank( i ) );
		for( i = 32 * 5; i-- != 1; ) assertEquals( i * 2 + 1, rankAndSelect.select( i ) );
		assertEquals( 64 * 5 - 1, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[] { 0xAAAAAAAAL }, 33 );
		assertRankAndSelect( rankAndSelect );
		for( i = 33; i-- != 0; ) assertEquals( i / 2, rankAndSelect.rank( i ) );
		for( i = 16; i-- != 1; ) assertEquals( i * 2 + 1, rankAndSelect.select( i ) );
		assertEquals( 31, rankAndSelect.lastOne() );

		rankAndSelect = new Rank9Select( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 113; ) assertEquals( 56, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( i / 2, rankAndSelect.rank( i ) );
		for( i = 56; i-- != 1; ) assertEquals( i * 2 + 1, rankAndSelect.select( i ) );
		assertEquals( 111, rankAndSelect.lastOne() );
	}

	public void testSelect() {
		Rank9Select rankAndSelect;
		rankAndSelect = new Rank9Select( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 );
		assertRankAndSelect( rankAndSelect );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 3, rankAndSelect.lastOne() );
	}

	public void testSparse() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length( 256 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 100000 );
		bitVector.set( 199999 );
		Rank9Select rankAndSelect;

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );

		bitVector = LongArrayBitVector.getInstance().length( 64 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 40000 );
		bitVector.set( 49999 );

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );

		bitVector = LongArrayBitVector.getInstance().length( 32 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 20000 );
		bitVector.set( 29999 );

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );
	}

	public void testDense() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 2 );
		Rank9Select rankAndSelect;

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 4 );

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 8 );

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 16 );

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );

		bitVector = LongArrayBitVector.getInstance().length( 32 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 32 );

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );
	}
	
	public void testRandom() {
		Random r = new Random( 1 );
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( 1000 );
		for( int i = 0; i < 1000; i++ ) bitVector.add( r.nextBoolean() );
		Rank9Select rankAndSelect;

		rankAndSelect = new Rank9Select( bitVector );
		assertRankAndSelect( rankAndSelect );
	}
	
	public void testAllSizes() {
		LongArrayBitVector v;
		Rank9Select r;
		for( int size = 0; size <= 4096; size++ ) {
			v = LongArrayBitVector.getInstance( size ).length( size );
			for( int i = ( size + 1 ) / 2; i-- != 0; ) v.set( i * 2 );
			r = new Rank9Select( v );
			for( int i = size + 1; i-- != 0; ) assertEquals( ( i + 1 ) / 2, r.rank( i ) );
			for( int i = size / 2; i-- != 0; ) assertEquals( i * 2, r.select( i ) );
			
		}
	}
}
