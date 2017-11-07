package it.unimi.dsi.sux4j.scratch;

import java.util.Random;

import org.junit.Test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.RankSelectTestCase;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class Rank11OriginalTest extends RankSelectTestCase {

	@Test
	public void testEmpty() {
		Rank11Original rank11;
		rank11 = new Rank11Original( new long[ 1 ], 64 );
		assertRank( rank11 );
		rank11 = new Rank11Original( new long[ 2 ], 128 );
		assertRank( rank11 );
		rank11 = new Rank11Original( new long[ 1 ], 63 );
		assertRank( rank11 );
		rank11 = new Rank11Original( new long[ 2 ], 65 );
		assertRank( rank11 );
		rank11 = new Rank11Original( new long[ 3 ], 129 );
		assertRank( rank11 );
	}

	@Test
	public void testSingleton() {
		Rank11Original rank11;

		rank11 = new Rank11Original( new long[] { 1L << 63, 0 }, 64 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 1 }, 64 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 1L << 63, 0 }, 128 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 1L << 63, 0 }, 65 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 1L << 63, 0, 0 }, 129 );
		assertRank( rank11 );
	}

	@Test
	public void testDoubleton() {
		Rank11Original rank11;

		rank11 = new Rank11Original( new long[] { 1 | 1L << 32 }, 64 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 1, 1 }, 128 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 1 | 1L << 32, 0 }, 63 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 1, 1, 0 }, 129 );
		assertRank( rank11 );
	}

	@Test
	public void testAlternating() {
		Rank11Original rank11;

		rank11 = new Rank11Original( new long[] { 0xAAAAAAAAAAAAAAAAL }, 64 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 0xAAAAAAAAL }, 33 );
		assertRank( rank11 );

		rank11 = new Rank11Original( new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128 );
		assertRank( rank11 );
	}

	@Test
	public void testSelect() {
		Rank11Original rank11;
		rank11 = new Rank11Original( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ).bits(), 7 );
		assertRank( rank11 );
	}

	@Test
	public void testRandom() {
		for ( int size = 10; size <= 100000000; size *= 10 ) {
			System.err.println( size );
			final Random r = new XoRoShiRo128PlusRandom( 1 );
			final LongArrayBitVector bitVector = LongArrayBitVector.getInstance( size );
			for ( int i = 0; i < size; i++ )
				bitVector.add( r.nextBoolean() );
			Rank11Original rank11;

			rank11 = new Rank11Original( bitVector );
			assertRank( rank11 );
		}
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		Rank11Original rank11;
		for ( int size = 0; size <= 4096; size++ ) {
			v = LongArrayBitVector.getInstance().length( size );
			for ( int i = ( size + 1 ) / 2; i-- != 0; )
				v.set( i * 2 );
			rank11 = new Rank11Original( v );
			assertRank( rank11 );
		}
	}
}
