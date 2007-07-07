package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.SimpleRankAndSelect;
import junit.framework.TestCase;

public class SimpleRankAndSelectTest extends TestCase {

	public void testEmpty() {
		SimpleRankAndSelect rankAndSelect;
		
		rankAndSelect = new SimpleRankAndSelect( new long[ 1 ], 64, 1 );
		for( int i = 65; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		
		rankAndSelect = new SimpleRankAndSelect( new long[ 2 ], 128, 1 );
		for( int i = 129; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[ 2 ], 128, 2 );
		for( int i = 129; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[ 1 ], 63, 1 );
		for( int i = 64; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );
		
		rankAndSelect = new SimpleRankAndSelect( new long[ 2 ], 65, 1 );
		for( int i = 66; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[ 3 ], 129, 2 );
		for( int i = 130; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( -1, rankAndSelect.select( 1 ) );

	}
	
	public void testSingleton() {
		SimpleRankAndSelect rankAndSelect;
		int i;
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1 }, 64, 1 );
		assertEquals( 1, rankAndSelect.rank( 64 ) );
		for( i = 64; i-- != 0; ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63 }, 64, 1 );
		for( i = 65; i-- != 2; ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0 }, 128, 1 );
		for( i = 129; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0 }, 128, 2 );
		for( i = 129; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0 }, 65, 1 );
		for( i = 66; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1, 0, 0 }, 129, 2 );
		for( i = 130; i-- != 64; ) assertEquals( 1, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 63, rankAndSelect.select( 1 ) );
		assertEquals( -1, rankAndSelect.select( 2 ) );
	}

	public void testDoubleton() {
		SimpleRankAndSelect rankAndSelect;
		int i;
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63 | 1L << 32 }, 64, 1 );
		for( i = 65; i-- != 32; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 31, rankAndSelect.select( 2 ) );
		assertEquals( -1, rankAndSelect.select( 3 ) );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63 }, 128, 1 );
		for( i = 129; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( -1, rankAndSelect.select( 3 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63 }, 128, 2 );
		for( i = 129; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( -1, rankAndSelect.select( 3 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63 | 1L << 32, 0 }, 63, 1 );
		for( i = 64; i-- != 32; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 31, rankAndSelect.select( 2 ) );
		assertEquals( -1, rankAndSelect.select( 3 ) );
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63 }, 65, 1 );
		for( i = 66; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( -1, rankAndSelect.select( 3 ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 1L << 63, 1L << 63, 0 }, 129, 2 );
		for( i = 130; i-- != 65; ) assertEquals( 2, rankAndSelect.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rankAndSelect.rank( i ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.rank( 0 ) );
		assertEquals( 0, rankAndSelect.select( 0 ) );
		assertEquals( 0, rankAndSelect.select( 1 ) );
		assertEquals( 64, rankAndSelect.select( 2 ) );
		assertEquals( -1, rankAndSelect.select( 3 ) );
	}

	public void testAlternating() {
		SimpleRankAndSelect rankAndSelect;
		int i;
		
		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64, 1 );
		for( i = 65; i-- != 0; ) assertEquals( ( i + 1 ) / 2, rankAndSelect.rank( i ) );
		for( i = 33; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128, 1 );
		for( i = 129; i-- != 0; ) assertEquals( ( i + 1 ) / 2, rankAndSelect.rank( i ) );
		for( i = 65; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128, 2 );
		for( i = 129; i-- != 0; ) assertEquals( ( i + 1 ) / 2, rankAndSelect.rank( i ) );
		for( i = 65; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5, 2 );
		for( i = 64 * 5 + 1; i-- != 0; ) assertEquals( ( i + 1 ) / 2, rankAndSelect.rank( i ) );
		for( i = 32 * 5 + 1; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL }, 33, 1 );
		for( i = 34; i-- != 0; ) assertEquals( ( i + 1 ) / 2, rankAndSelect.rank( i ) );
		for( i = 17; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );

		rankAndSelect = new SimpleRankAndSelect( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAA0000L }, 128, 1 );
		for( i = 129; i-- != 113; ) assertEquals( 56, rankAndSelect.rank( i ) );
		while( i-- != 0 ) assertEquals( ( i + 1 ) / 2, rankAndSelect.rank( i ) );
		for( i = 57; i-- != 1; ) assertEquals( i * 2 - 2, rankAndSelect.select( i ) );
	}
}
