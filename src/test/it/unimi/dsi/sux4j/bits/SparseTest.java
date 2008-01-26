package test.it.unimi.dsi.sux4j.bits;

import java.util.Random;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.SparseRank;
import it.unimi.dsi.sux4j.bits.SparseSelect;

public class SparseTest extends RankSelectTestCase {
	
	public void testEmpty() {
		SparseRank sparseRank;
		SparseSelect bsearch;
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[ 1 ], 64 ) );
		for( int i = 64; i-- != 0; ) assertEquals( Integer.toString( i ), 0, sparseRank.rank( i ) );
		assertEquals( -1, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[ 2 ], 128 ) );
		for( int i = 128; i-- != 0; ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( -1, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[ 1 ], 63 ) );
		for( int i = 63; i-- != 0; ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( -1, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[ 2 ], 65 ) );
		for( int i = 65; i-- != 0; ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( -1, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[ 3 ], 129 ) );
		for( int i = 130; i-- != 0; ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( -1, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );

	}
	
	public void testSingleton() {
		SparseRank sparseRank;
		SparseSelect bsearch;
		int i;
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1L << 63, 0 }, 64 ) );
		assertRankAndSelect( sparseRank, bsearch );
		assertEquals( 1, sparseRank.rank( 64 ) );
		assertEquals( 0, sparseRank.rank( 63 ) );
		for( i = 63; i-- != 0; ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( 63, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );
		assertEquals( 63, bsearch.select( sparseRank.count() - 1 ) );
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1 }, 64 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 64; i-- != 2; ) assertEquals( 1, sparseRank.rank( i ) );
		assertEquals( 0, sparseRank.rank( 0 ) );
		assertEquals( 0, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );
		assertEquals( 0, bsearch.select( sparseRank.count() - 1 ) );
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1L << 63, 0 }, 128 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 128; i-- != 64; ) assertEquals( 1, sparseRank.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( 63, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );
		assertEquals( 63, bsearch.select( sparseRank.count() - 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1L << 63, 0 }, 65 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 65; i-- != 64; ) assertEquals( 1, sparseRank.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( 63, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );
		assertEquals( 63, bsearch.select( sparseRank.count() - 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1L << 63, 0, 0 }, 129 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 128; i-- != 64; ) assertEquals( 1, sparseRank.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, sparseRank.rank( i ) );
		assertEquals( 63, bsearch.select( 0 ) );
		assertEquals( -1, bsearch.select( 1 ) );
		assertEquals( 63, bsearch.select( sparseRank.count() - 1 ) );
	}

	public void testDoubleton() {
		SparseRank sparseRank;
		SparseSelect bsearch;
		int i;
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1 | 1L << 32 }, 64 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 64; i-- != 33; ) assertEquals( 2, sparseRank.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, sparseRank.rank( i ) );
		assertEquals( 0, sparseRank.rank( 0 ) );
		assertEquals( 0, bsearch.select( 0 ) );
		assertEquals( 32, bsearch.select( 1 ) );
		assertEquals( -1, bsearch.select( 2 ) );
		assertEquals( 32, bsearch.select( sparseRank.count() - 1 ) );
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1, 1 }, 128 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 128; i-- != 65; ) assertEquals( 2, sparseRank.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, sparseRank.rank( i ) );
		assertEquals( 0, sparseRank.rank( 0 ) );
		assertEquals( 0, bsearch.select( 0 ) );
		assertEquals( 64, bsearch.select( 1 ) );
		assertEquals( -1, bsearch.select( 2 ) );
		assertEquals( 64, bsearch.select( sparseRank.count() - 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1 | 1L << 32, 0 }, 63 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 63; i-- != 33; ) assertEquals( 2, sparseRank.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, sparseRank.rank( i ) );
		assertEquals( 0, sparseRank.rank( 0 ) );
		assertEquals( 0, bsearch.select( 0 ) );
		assertEquals( 32, bsearch.select( 1 ) );
		assertEquals( -1, bsearch.select( 2 ) );
		assertEquals( 32, bsearch.select( sparseRank.count() - 1 ) );
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 1, 1, 0 }, 129 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 129; i-- != 65; ) assertEquals( 2, sparseRank.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, sparseRank.rank( i ) );
		assertEquals( 0, sparseRank.rank( 0 ) );
		assertEquals( 0, bsearch.select( 0 ) );
		assertEquals( 64, bsearch.select( 1 ) );
		assertEquals( -1, bsearch.select( 2 ) );
		assertEquals( 64, bsearch.select( sparseRank.count() - 1 ) );
	}

	public void testAlternating() {
		SparseRank sparseRank;
		SparseSelect bsearch;
		int i;
		
		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 64; i-- != 0; ) assertEquals( i / 2, sparseRank.rank( i ) );
		for( i = 32; i-- != 1; ) assertEquals( i * 2 + 1, bsearch.select( i ) );
		assertEquals( 63, bsearch.select( sparseRank.count() - 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 128; i-- != 0; ) assertEquals( i / 2, sparseRank.rank( i ) );
		for( i = 64; i-- != 1; ) assertEquals( i * 2 + 1, bsearch.select( i ) );
		assertEquals( 127, bsearch.select( sparseRank.count() - 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 64 * 5; i-- != 0; ) assertEquals( i / 2, sparseRank.rank( i ) );
		for( i = 32 * 5; i-- != 1; ) assertEquals( i * 2 + 1, bsearch.select( i ) );
		assertEquals( 64 * 5 - 1, bsearch.select( sparseRank.count() - 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 0xAAAAAAAAL }, 33 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 33; i-- != 0; ) assertEquals( i / 2, sparseRank.rank( i ) );
		for( i = 16; i-- != 1; ) assertEquals( i * 2 + 1, bsearch.select( i ) );
		assertEquals( 31, bsearch.select( sparseRank.count() - 1 ) );

		bsearch = new SparseSelect( sparseRank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128 ) );
		assertRankAndSelect( sparseRank, bsearch );
		for( i = 128; i-- != 113; ) assertEquals( 56, sparseRank.rank( i ) );
		while( i-- != 0 ) assertEquals( i / 2, sparseRank.rank( i ) );
		for( i = 56; i-- != 1; ) assertEquals( i * 2 + 1, bsearch.select( i ) );
		assertEquals( 111, bsearch.select( sparseRank.count() - 1 ) );
	}

	public void testSelect() {
		SparseRank sparseRank;
		SparseSelect bsearch;
		bsearch = new SparseSelect( sparseRank = new SparseRank( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 ) );
		assertRankAndSelect( sparseRank, bsearch );
		assertEquals( 0, bsearch.select( 0 ) );
		assertEquals( 3, bsearch.select( sparseRank.count() - 1 ) );
	}

	public void testRandom() {
		Random r = new Random( 1 );
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( 1000 );
		for( int i = 0; i < 1000; i++ ) bitVector.add( r.nextBoolean() );
		SparseRank sparseRank;
		SparseSelect bsearch;

		bsearch = new SparseSelect( sparseRank = new SparseRank( bitVector ) );
		assertRankAndSelect( sparseRank, bsearch );
	}
	
	public void testAllSizes() {
		LongArrayBitVector v;
		SparseRank sparseRank;
		SparseSelect bsearch;
		for( int size = 0; size <= 4096; size++ ) {
			v = LongArrayBitVector.getInstance( size ).length( size );
			for( int i = ( size + 1 ) / 2; i-- != 0; ) v.set( i * 2 );
			bsearch = new SparseSelect( sparseRank = new SparseRank( v ) );
			for( int i = size + 1; i-- != 0; ) assertEquals( ( i + 1 ) / 2, sparseRank.rank( i ) );
			for( int i = size / 2; i-- != 0; ) assertEquals( i * 2, bsearch.select( i ) );
			
		}
	}
	
}
