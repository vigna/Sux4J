package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.util.XorShiftStarRandom;

import java.util.Random;

import org.junit.Test;

public class HashesTest {


	@Test
	public void testJenkinsPreprocessing() {
		Random r = new XorShiftStarRandom( 1 );
		for ( int l = 0; l < 1000; l++ ) {
			LongArrayBitVector bv = LongArrayBitVector.getInstance();
			for ( int i = 0; i < l; i++ )
				bv.add( r.nextBoolean() );
			long[][] state = Hashes.preprocessJenkins( bv, 0 );
			for ( int i = 0; i < l; i++ ) {
				final long[] h = new long[ 3 ];
				Hashes.jenkins( bv, i, state[ 0 ], state[ 1 ], state[ 2 ], h );
				assertEquals( "Prefix length " + i, Hashes.jenkins( bv.subVector( 0, i ) ), h[ 2 ] );
				assertEquals( "Prefix length " + i, Hashes.jenkins( bv.subVector( 0, i ) ), Hashes.jenkins( bv, i, state[ 0 ], state[ 1 ], state[ 2 ] ) );
				final long[] k = new long[ 3 ];
				Hashes.jenkins( bv.subVector( 0, i ), 0, k );
				assertArrayEquals( "Prefix length " + i, k, h );
			}
		}
	}

	@Test
	public void testMurmurPreprocessing() {
		Random r = new XorShiftStarRandom( 1 );
		for ( int l = 0; l < 1000; l++ ) {
			LongArrayBitVector bv = LongArrayBitVector.getInstance();
			for ( int i = 0; i < l; i++ )
				bv.add( r.nextBoolean() );
			long[] state = Hashes.preprocessMurmur( bv, 0 );
			for ( int i = 0; i < l; i++ ) {
				assertEquals( "Prefix length " + i, Hashes.murmur( bv.subVector( 0, i ), 0 ), Hashes.murmur( bv, i, state ) );
				for ( int p = 0; p < l; p += 16 )
					assertEquals( "Prefix length " + i + ", lcp " + p, Hashes.murmur( bv.subVector( 0, i ), 0 ), Hashes.murmur( bv, i, state, p ) );
			}
		}
	}

	@Test
	public void test0() {
		final long[] h = new long[ 3 ];
		Hashes.jenkins( BitVectors.EMPTY_VECTOR, 0, h );
		assertEquals( Hashes.jenkins( BitVectors.EMPTY_VECTOR, 0 ), h[ 2 ] );
		assertEquals( Hashes.jenkins( BitVectors.EMPTY_VECTOR ), h[ 2 ] );
	}
}
