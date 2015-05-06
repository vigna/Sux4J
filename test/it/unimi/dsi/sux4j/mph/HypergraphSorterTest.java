package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertTrue;
import it.unimi.dsi.util.XorShift128PlusRandomGenerator;

import org.junit.Test;


public class HypergraphSorterTest {

	@Test
	public void smallTest() {
		int[] vertex1 = { 19, 1, 30, 15, 39, 30, 24, 1, 27, 10, 1, 16, 11, 30, 35, 3, 5, 15, 31, 35 };
		int[] vertex2 = { 5, 1, 22, 36, 20, 39, 13, 20, 6, 9, 9, 11, 13, 14, 6, 31, 3, 25, 24, 11 };
		int[] stack = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19 };
		int[] d = { 1, 5, 1, 3, 1, 3, 3, 1, 1, 3, 2, 4, 1, 3, 2, 3, 2, 1, 1, 2, 2, 0, 1, 0, 2, 1, 0, 1, 0, 0, 3, 2, 0, 0, 0, 2, 1, 0, 0, 2 };
		int[] hinges = new int[ vertex1.length ];
		assertTrue( HypergraphSorter.directHyperedges( d, stack, vertex1, vertex2, hinges, 0 ) );
	}

	@Test
	public void randomTest() {
		XorShift128PlusRandomGenerator random = new XorShift128PlusRandomGenerator( 1 );
		for( int size : new int[] { 10, 100, 1000 } ) {
			for( int count = 0; count < 100; count++ ) {
				int[] stack = new int[ size / 2 ];
				int[] d = new int[ size ];
				int[] vertex1 = new int[ size / 2 ];
				int[] vertex2 = new int[ size / 2 ];
				int[] hinges = new int[ size / 2 ];

				for ( int i = 0; i < stack.length; i++ ) {
					stack[ i ] = i;
					d[ i ]++;
					int v = random.nextInt( size );
					vertex1[ i ] = v;
					d[ v ]++;
					v = random.nextInt( size );
					vertex2[ i ] = v;
					d[ v ]++;
				}

				assertTrue( "size: " + size + ", count: " + count, HypergraphSorter.directHyperedges( d, stack, vertex1, vertex2, hinges, 0 ) );
			}
		}
	}
}
