package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank16;

import java.util.Random;

public class Rank16Test extends RankSelectTestCase {
	
	public void testEmpty() {
		Rank16 rank16;
		
		rank16 = new Rank16( new long[ 1 ], 64 ) ;
		assertRank( rank16 );
		for( int i = 64; i-- != 0; ) assertEquals( Integer.toString( i ), 0, rank16.rank( i ) );
		rank16 = new Rank16( new long[ 2 ], 128 ) ;
		for( int i = 128; i-- != 0; ) assertEquals( 0, rank16.rank( i ) );
		rank16 = new Rank16( new long[ 1 ], 63 ) ;
		for( int i = 63; i-- != 0; ) assertEquals( 0, rank16.rank( i ) );
		rank16 = new Rank16( new long[ 2 ], 65 ) ;
		for( int i = 65; i-- != 0; ) assertEquals( 0, rank16.rank( i ) );
		rank16 = new Rank16( new long[ 3 ], 129 ) ;
		for( int i = 130; i-- != 0; ) assertEquals( 0, rank16.rank( i ) );
		
	}
	
	public void testSingleton() {
		Rank16 rank16;
		
		int i;
		
		rank16 = new Rank16( new long[] { 1L << 63, 0 }, 64 ) ;
		assertRank( rank16 );
		assertEquals( 1, rank16.rank( 64 ) );
		assertEquals( 0, rank16.rank( 63 ) );
		for( i = 63; i-- != 0; ) assertEquals( 0, rank16.rank( i ) );
		
		rank16 = new Rank16( new long[] { 1 }, 64 ) ;
		assertRank( rank16 );
		for( i = 64; i-- != 2; ) assertEquals( 1, rank16.rank( i ) );
		assertEquals( 0, rank16.rank( 0 ) );
		
		rank16 = new Rank16( new long[] { 1L << 63, 0 }, 128 ) ;
		assertRank( rank16 );
		for( i = 128; i-- != 64; ) assertEquals( 1, rank16.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rank16.rank( i ) );

		rank16 = new Rank16( new long[] { 1L << 63, 0 }, 65 ) ;
		assertRank( rank16 );
		for( i = 65; i-- != 64; ) assertEquals( 1, rank16.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rank16.rank( i ) );

		rank16 = new Rank16( new long[] { 1L << 63, 0, 0 }, 129 ) ;
		assertRank( rank16 );
		for( i = 128; i-- != 64; ) assertEquals( 1, rank16.rank( i ) );
		while( i-- != 0 ) assertEquals( 0, rank16.rank( i ) );
	}

	public void testDoubleton() {
		Rank16 rank16;
		
		int i;
		
		rank16 = new Rank16( new long[] { 1 | 1L << 32 }, 64 ) ;
		assertRank( rank16 );
		for( i = 64; i-- != 33; ) assertEquals( 2, rank16.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rank16.rank( i ) );
		assertEquals( 0, rank16.rank( 0 ) );
		
		rank16 = new Rank16( new long[] { 1, 1 }, 128 ) ;
		assertRank( rank16 );
		for( i = 128; i-- != 65; ) assertEquals( 2, rank16.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rank16.rank( i ) );
		assertEquals( 0, rank16.rank( 0 ) );

		rank16 = new Rank16( new long[] { 1 | 1L << 32, 0 }, 63 ) ;
		assertRank( rank16 );
		for( i = 63; i-- != 33; ) assertEquals( 2, rank16.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rank16.rank( i ) );
		assertEquals( 0, rank16.rank( 0 ) );
		
		rank16 = new Rank16( new long[] { 1, 1, 0 }, 129 ) ;
		assertRank( rank16 );
		for( i = 129; i-- != 65; ) assertEquals( 2, rank16.rank( i ) );
		while( i-- != 1 ) assertEquals( 1, rank16.rank( i ) );
		assertEquals( 0, rank16.rank( 0 ) );
	}

	public void testAlternating() {
		Rank16 rank16;
		
		int i;
		
		 rank16 = new Rank16( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64 ) ;
		assertRank( rank16 );
		for( i = 64; i-- != 0; ) assertEquals( i / 2, rank16.rank( i ) );

		 rank16 = new Rank16( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128 ) ;
		assertRank( rank16 );
		for( i = 128; i-- != 0; ) assertEquals( i / 2, rank16.rank( i ) );

		 rank16 = new Rank16( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5 ) ;
		assertRank( rank16 );
		for( i = 64 * 5; i-- != 0; ) assertEquals( i / 2, rank16.rank( i ) );

		 rank16 = new Rank16( new long[] { 0xAAAAAAAAL }, 33 ) ;
		assertRank( rank16 );
		for( i = 33; i-- != 0; ) assertEquals( i / 2, rank16.rank( i ) );

		 rank16 = new Rank16( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128 ) ;
		assertRank( rank16 );
		for( i = 128; i-- != 113; ) assertEquals( 56, rank16.rank( i ) );
		while( i-- != 0 ) assertEquals( i / 2, rank16.rank( i ) );
	}

	public void testSelect() {
		Rank16 rank16;
		
		 rank16 = new Rank16( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 ) ;
		assertRank( rank16 );
	}

	public void testRandom() {
		Random r = new Random( 1 );
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance( 1000 );
		for( int i = 0; i < 1000; i++ ) bitVector.add( r.nextBoolean() );
		Rank16 rank16;
		

		 rank16 = new Rank16( bitVector ) ;
		assertRank( rank16 );
	}
	
	public void testAllSizes() {
		LongArrayBitVector v;
		Rank16 rank16;
		
		for( int size = 0; size <= 4096; size++ ) {
			v = LongArrayBitVector.getInstance().length( size );
			for( int i = ( size + 1 ) / 2; i-- != 0; ) v.set( i * 2 );
			 rank16 = new Rank16( v ) ;
			for( int i = size + 1; i-- != 0; ) assertEquals( ( i + 1 ) / 2, rank16.rank( i ) );
			
		}
	}
	
}
