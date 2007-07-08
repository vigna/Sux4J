package test.it.unimi.dsi.sux4j.bits;

import java.util.Random;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.SimpleRankAndSelect;

public class SimpleRankAndSelectTest extends RankAndSelectTestCase {
	
	public void testEmpty() {
		SimpleRankAndSelect rankAndSelect;
		
		rankAndSelect = new SimpleRankAndSelect( new long[ 1 ], 64, 1 );
		for( int i = 64; i-- != -1; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );
		
		rankAndSelect = new SimpleRankAndSelect( new long[ 2 ], 128, 1 );
		for( int i = 128; i-- != -1; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[ 2 ], 128, 2 );
		for( int i = 128; i-- != -1; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[ 1 ], 63, 1 );
		for( int i = 63; i-- != -1; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );
		
		rankAndSelect = new SimpleRankAndSelect( new long[ 2 ], 65, 1 );
		for( int i = 65; i-- != -1; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[ 3 ], 129, 2 );
		for( int i = 130; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.lastOne() );

	}
	
	public void testSingleton() {
		SimpleRankAndSelect rankAndSelect;
		int i;
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1 }, 64, 1 );
		assertRankAndSelect( rankAndSelect );
		assertEquals( 1, rankAndSelect.rank( 63 ) );
		for( i = 63; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63 }, 64, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64; i-- != 2; ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 1, rankAndSelect.rank( 0 ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 0, rankAndSelect.lastOne() );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0 }, 128, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0 }, 128, 2 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0 }, 65, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0, 0 }, 129, 2 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( 63, rankAndSelect.lastOne() );
	}

	public void testDoubleton() {
		SimpleRankAndSelect rankAndSelect;
		int i;
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63 | 1L << 32 }, 64, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64; i-- != 32; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 1, rankAndSelect.rank( 0 ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 31, rankAndSelect.select( 2 ) );
		assertEquals( 31, rankAndSelect.lastOne() );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63 }, 128, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 1, rankAndSelect.rank( 0 ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( 64, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63 }, 128, 2 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 1, rankAndSelect.rank( 0 ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( 64, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63 | 1L << 32, 0 }, 63, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 63; i-- != 32; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 1, rankAndSelect.rank( 0 ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 31, rankAndSelect.select( 2 ) );
		assertEquals( 31, rankAndSelect.lastOne() );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63 }, 65, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 65; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 1, rankAndSelect.rank( 0 ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( 64, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63, 0 }, 129, 2 );
		assertRankAndSelect( rankAndSelect );
		for( i = 129; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 1, rankAndSelect.rank( 0 ) );
		assertEquals( -1, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( 64, rankAndSelect.lastOne() );
	}

	public void testAlternating() {
		SimpleRankAndSelect rankAndSelect;
		int i;
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64; i-- != 0; ) assertEquals( ( i + 2 ) / 2, rankAndSelect.rank( i ) );
		for( i = 32; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );
		assertEquals( 62, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 0; ) assertEquals( ( i + 2 ) / 2, rankAndSelect.rank( i ) );
		for( i = 64; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );
		assertEquals( 126, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128, 2 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 0; ) assertEquals( ( i + 2 ) / 2, rankAndSelect.rank( i ) );
		for( i = 64; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );
		assertEquals( 126, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5, 2 );
		assertRankAndSelect( rankAndSelect );
		for( i = 64 * 5; i-- != 0; ) assertEquals( ( i + 2 ) / 2, rankAndSelect.rank( i ) );
		for( i = 32 * 5; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );
		assertEquals( 64 * 5 - 2, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL }, 33, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 33; i-- != 0; ) assertEquals( ( i + 2 ) / 2, rankAndSelect.rank( i ) );
		for( i = 16; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );
		assertEquals( 32, rankAndSelect.lastOne() );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAA0000L }, 128, 1 );
		assertRankAndSelect( rankAndSelect );
		for( i = 128; i-- != 113; ) assertEquals( 56, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( ( i + 2 ) / 2, rankAndSelect.rank( i ) );
		for( i = 56; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );
		assertEquals( 110, rankAndSelect.lastOne() );
	}

	public void testSelect() {
		SimpleRankAndSelect rankAndSelect;
		rankAndSelect = new SimpleRankAndSelect( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7, 2 );
		assertRankAndSelect( rankAndSelect );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 3, rankAndSelect.lastOne() );
	}

	public void testRandom() {
		Random r = new Random( 1 );
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( 1000 );
		for( int i = 0; i < 1000; i++ ) bitVector.add( r.nextBoolean() );
		SimpleRankAndSelect rankAndSelect;

		rankAndSelect = new SimpleRankAndSelect( bitVector, 1 );
		assertRankAndSelect( rankAndSelect );
		rankAndSelect = new SimpleRankAndSelect( bitVector, 2 );
		assertRankAndSelect( rankAndSelect );
		rankAndSelect = new SimpleRankAndSelect( bitVector, 3 );
		assertRankAndSelect( rankAndSelect );
	}
}
