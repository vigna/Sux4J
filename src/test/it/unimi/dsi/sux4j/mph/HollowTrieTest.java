package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.mph.HollowTrie;

import java.util.Arrays;
import java.util.Random;

import junit.framework.TestCase;

public class HollowTrieTest extends TestCase {

	public static ObjectArrayList<BitVector> listOf( final int[]... bit ) {
		ObjectArrayList<BitVector> vectors = new ObjectArrayList<BitVector>();
		for( int[] v : bit ) vectors.add( LongArrayBitVector.of(  v  ) );
		return vectors;
	}
	
	public void testSimple() {
		HollowTrie hollowTrie = new HollowTrie( listOf( new int[][] { { 0, 0, 0 }, { 0, 0, 1 } } ).iterator() );
		hollowTrie.succinct();

		assertEquals( LongArrayBitVector.of( 1, 0, 0 ), hollowTrie.trie );
		assertEquals( LongArrayList.wrap( new long[] { 2 } ), hollowTrie.skips );

		hollowTrie = new HollowTrie( listOf( new int[][] { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 0 } } ).iterator() );
		hollowTrie.succinct();

		assertEquals( LongArrayBitVector.of( 1, 1, 0, 0, 0 ), hollowTrie.trie );
		assertEquals( LongArrayList.wrap( new long[] { 1, 0 } ), hollowTrie.skips );

		hollowTrie = new HollowTrie( listOf( new int[][] { { 0, 0, 0 }, { 0, 0, 1 }, { 0, 1, 0 }, { 0, 1, 1 } } ).iterator() );
		hollowTrie.succinct();

		assertEquals( LongArrayBitVector.of( 1, 1, 1, 0, 0, 0, 0 ), hollowTrie.trie );
		assertEquals( LongArrayList.wrap( new long[] { 1, 0, 0 } ), hollowTrie.skips );

		hollowTrie = new HollowTrie( listOf( new int[][] { { 0, 0, 0, 0 }, { 0, 1, 0, 0 }, { 0, 1, 1, 1 } } ).iterator() );
		hollowTrie.succinct();

		assertEquals( LongArrayBitVector.of( 1, 0, 1, 0, 0 ), hollowTrie.trie );
		assertEquals( LongArrayList.wrap( new long[] { 1, 0 } ), hollowTrie.skips );

		hollowTrie = new HollowTrie( listOf( new int[][] { { 0, 0, 0, 0, 0 }, { 0, 0, 0, 0, 1 }, { 0, 1, 1, 1, 1 } } ).iterator() );
		hollowTrie.succinct();

		assertEquals( LongArrayBitVector.of( 1, 1, 0, 0, 0 ), hollowTrie.trie );
		assertEquals( LongArrayList.wrap( new long[] { 1, 2 } ), hollowTrie.skips );

		hollowTrie = new HollowTrie( 
				listOf( new int[][] { { 0 }, { 1, 0, 0, 0, 0 }, { 1, 0, 0, 0, 1 }, { 1, 0, 0, 1 } } ).iterator() );
		hollowTrie.succinct();

		assertEquals( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 0 ), hollowTrie.trie );
		assertEquals( LongArrayList.wrap( new long[] { 0, 2, 0 } ), hollowTrie.skips );
		
		hollowTrie = new HollowTrie( 
				listOf( new int[][] { { 0, 0, 0, 0, 0 }, { 0, 1, 0, 0, 0 }, { 0, 1, 0, 1, 0, 0 }, { 0, 1, 0, 1, 0, 1 }, { 0, 1, 1, 1, 0 } } ).iterator() );
		hollowTrie.succinct();

		assertEquals( LongArrayBitVector.of( 1, 0, 1, 1, 0, 0, 1, 0, 0 ), hollowTrie.trie );
		assertEquals( LongArrayList.wrap( new long[] { 1, 0, 0, 1 } ), hollowTrie.skips );
		
		assertEquals( 0, hollowTrie.getLeafIndex( LongArrayBitVector.of( 0, 0, 0, 0, 0 ) ) );
		assertEquals( 1, hollowTrie.getLeafIndex( LongArrayBitVector.of( 0, 1, 0, 0, 0 ) ) );
		assertEquals( 2, hollowTrie.getLeafIndex( LongArrayBitVector.of( 0, 1, 0, 1, 0, 0 ) ) );
		assertEquals( 3, hollowTrie.getLeafIndex( LongArrayBitVector.of( 0, 1, 0, 1, 0, 1 ) ) );
		assertEquals( 4, hollowTrie.getLeafIndex( LongArrayBitVector.of( 0, 1, 1, 1, 0 ) ) );
	}
	
	public void testRandom() {
		Random r = new Random( 3 );
		final int n = 10;
		final LongArrayBitVector[] bitVector = new LongArrayBitVector[ n ];
		for( int i = 0; i < n; i++ ) {
			bitVector[ i ] = LongArrayBitVector.getInstance();
			int l = 12;
			while( l-- != 0 ) bitVector[ i ].add( r.nextBoolean() );
		}
		
		// Sort lexicographically
		Arrays.sort( bitVector );
		
		HollowTrie hollowTrie = new HollowTrie( Arrays.asList( bitVector ).iterator() );
		hollowTrie.succinct();
		
		for( int i = 0; i < n; i++ ) assertEquals( i, hollowTrie.getLeafIndex( bitVector[ i ] ) );

		// Test that random inquiries do not break the trie
		for( int i = 0; i < 10; i++ ) {
			bitVector[ i ] = LongArrayBitVector.getInstance();
			int l = 8;
			while( l-- != 0 ) bitVector[ i ].add( r.nextBoolean() );
		}
	}
}
