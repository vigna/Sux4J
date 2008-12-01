package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.bits.SparseRank;
import it.unimi.dsi.sux4j.bits.SparseSelect;

import java.util.Random;

public class SparseRankTest extends RankSelectTestCase {
	
	public void testEmpty() {
		SparseRank rank;
		
		rank = new SparseRank( new long[ 1 ], 64 );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 0, rank.rank( 1 ) );
		
		rank = new SparseRank( new long[ 2 ], 128 );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 0, rank.rank( 1 ) );

		rank = new SparseRank( new long[ 1 ], 63 );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 0, rank.rank( 1 ) );
		
		rank = new SparseRank( new long[ 2 ], 65 );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 0, rank.rank( 1 ) );

		rank = new SparseRank( new long[ 3 ], 129 );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 0, rank.rank( 1 ) );

	}
	
	public void testSingleton() {
		SparseRank rank;
		
		rank = new SparseRank( new long[] { 1L << 63, 0 }, 64 );
		assertRank( rank );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 1, rank.rank( 64 ) );
		
		rank = new SparseRank( new long[] { 1 }, 64 );
		assertRank( rank );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 1, rank.rank( 1 ) );
	}

	public void testDoubleton() {
		SparseRank rank;
		
		rank = new SparseRank( new long[] { 1 | 1L << 32 }, 64 );
		assertRank( rank );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 1, rank.rank( 32 ) );
		assertEquals( 2, rank.rank( 64 ) );
		
		rank = new SparseRank( new long[] { 1, 1 }, 128 );
		assertRank( rank );
		assertEquals( 0, rank.rank( 0 ) );
		assertEquals( 1, rank.rank( 1) );
		assertEquals( 2, rank.rank( 65 ) );

	}

	public void testAlternating() {
		SparseRank rank;
		int i;
		
		rank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64 );
		assertRank( rank );
		for( i = 64; i-- != 1; ) assertEquals( i / 2, rank.rank( i ) );

		rank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128 );
		assertRank( rank );
		for( i = 128; i-- != 1; ) assertEquals( i / 2, rank.rank( i ) );

		rank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5 );
		assertRank( rank );
		for( i = 64 * 5; i-- != 1; ) assertEquals( i / 2, rank.rank( i ) );

		rank = new SparseRank( new long[] { 0xAAAAAAAAL }, 33 );
		assertRank( rank );
		for( i = 33; i-- != 1; ) assertEquals( i / 2, rank.rank( i ) );

		rank = new SparseRank( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128 );
		assertRank( rank );
		for( i = 112; i-- != 1; ) assertEquals( i / 2, rank.rank( i ) );
	}

	public void testrank() {
		SparseRank rank;
		rank = new SparseRank( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 );
		assertRank( rank );
		assertEquals( 0, rank.rank( 0 ) );
	}

	public void testPred() {
		SparseRank rank;
		rank = new SparseRank( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 );
		SparseSelect select = rank.getSelect();
		for( int i = 1; i < 7; i++ ) {
			//System.err.println( i );
			assertEquals( select.select( rank.rank( i ) - 1 ), rank.pred( i ) );
		}
	}
	
	public void testSparse() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length( 256 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 100000 );
		bitVector.set( 199999 );
		SparseRank rank;

		rank = new SparseRank( bitVector );
		assertRank( rank );

		bitVector = LongArrayBitVector.getInstance().length( 64 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 40000 );
		bitVector.set( 49999 );

		rank = new SparseRank( bitVector );
		assertRank( rank );

		bitVector = LongArrayBitVector.getInstance().length( 32 * 1024 );
		bitVector.set( 1 );
		bitVector.set( 20000 );
		bitVector.set( 29999 );

		rank = new SparseRank( bitVector );
		assertRank( rank );
	}

	public void testDense() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 2 );
		SparseRank rank;

		rank = new SparseRank( bitVector );
		assertRank( rank );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 4 );

		rank = new SparseRank( bitVector );
		assertRank( rank );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 8 );

		rank = new SparseRank( bitVector );
		assertRank( rank );

		bitVector = LongArrayBitVector.getInstance().length( 16 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 16 );

		rank = new SparseRank( bitVector );
		assertRank( rank );

		bitVector = LongArrayBitVector.getInstance().length( 32 * 1024 );
		for( int i = 0; i <= 512; i++ ) bitVector.set( i * 32 );

		rank = new SparseRank( bitVector );
		assertRank( rank );
	}
	
	public void testRandom() {
		Random r = new Random( 1 );
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( 1000 );
		for( int i = 0; i < 1000; i++ ) bitVector.add( r.nextBoolean() );
		SparseRank rank;

		rank = new SparseRank( bitVector );
		assertRank( rank );
	}
	
	public void testAllSizes() {
		LongArrayBitVector v;
		SparseRank r;
		for( int size = 0; size <= 4096; size++ ) {
			v = LongArrayBitVector.getInstance().length( size );
			for( int i = ( size + 1 ) / 2; i-- != 0; ) v.set( i * 2 );
			r = new SparseRank( v );
			for( int i = size; i-- != 0; ) assertEquals( ( i + 1 ) / 2, r.rank( i ) );
			
			v = LongArrayBitVector.getInstance().length( size );
			v.fill( true );
			r = new SparseRank( v );
			for( int i = size; i-- != 0; ) assertEquals( i, r.rank( i ) );
		}
	}
	
	public void testGetRank() {
		SparseSelect select = new SparseSelect( LongArrayList.wrap( new long[] { 0, 48, 128 } ) );
		SparseRank rank = select.getRank();
		assertRankAndSelect( rank, select );
	}
}
