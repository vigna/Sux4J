package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertTrue;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.util.XorShift128PlusRandomGenerator;

import java.util.Arrays;

import org.junit.Test;


public class HypergraphSolverTest {

	public static int[][] vertex2Edge( final int numVars, final int[] vertex0, final int[] vertex1, final int[] vertex2 ) {
		final int[][] vertex2Edge = new int[ numVars ][];
		final int[] d = new int[ numVars ];
		for( int i = vertex0.length; i-- != 0; ) {
			d[ vertex0[ i ] ]++;
			d[ vertex1[ i ] ]++;
			d[ vertex2[ i ] ]++;
		}
		
		for( int v = numVars; v-- != 0; ) vertex2Edge[ v ] = new int[ d[ v ] ];
		Arrays.fill( d, 0 );
		for( int i = vertex0.length; i-- != 0; ) {
			vertex2Edge[ vertex0[ i ] ][ d[ vertex0[ i ] ]++ ] = i;
			vertex2Edge[ vertex1[ i ] ][ d[ vertex1[ i ] ]++ ] = i;
			vertex2Edge[ vertex2[ i ] ][ d[ vertex2[ i ] ]++ ] = i;
		}
		
		return vertex2Edge;
	}

	@Test
	public void smallTest() {
		int[] vertex0 = { 0, 1, 2, 3 };
		int[] vertex1 = { 1, 2, 0, 1 };
		int[] vertex2 = { 2, 3, 4, 0 };
		int[] d = { 3, 3, 3, 2, 1 };
		int[] hinges = new int[ vertex1.length ];
		assertTrue( HypergraphSolver.directHyperedges( vertex2Edge( 5, vertex0, vertex1, vertex2 ), d, vertex0, vertex1, vertex2, hinges, 0 ) );
	}

	@Test
	public void randomTest() {
		XorShift128PlusRandomGenerator random = new XorShift128PlusRandomGenerator( 1 );
		for( int n : new int[] { 5, 10, 100, 1000 } ) {
			for( int count = 0; count < 10; count++ ) {
				final int size = (int)( .9 * n );
				int[] d = new int[ n ];
				int[] vertex0 = new int[ size ];
				int[] vertex1 = new int[ size ];
				int[] vertex2 = new int[ size ];
				int[] hinges = new int[ size ];
				IntOpenHashSet edge[] = new IntOpenHashSet[ size ];
				
				int v, w;
				for ( int i = 0; i < size; i++ ) {
					boolean alreadySeen;
					do {
						vertex0[ i ] = i;

						do v = random.nextInt( n ); while( v == i );
						vertex1[ i ] = v;

						do w = random.nextInt( n ); while( w == i || w == v );
						vertex2[ i ] = w;

						edge[ i ] = new IntOpenHashSet();
						edge[ i ].add( i );
						edge[ i ].add( v );
						edge[ i ].add( w );
						
						alreadySeen = false;
						for( int j = 0; j < i; j++ ) 
							if ( edge[ j ].equals( edge[ i ] ) ) {
							alreadySeen = true;
							break;
						}
					} while( alreadySeen );

					d[ i ]++;
					d[ v ]++;
					d[ w ]++;
				}

				assertTrue( "size: " + n + ", count: " + count, HypergraphSolver.directHyperedges( vertex2Edge( d.length, vertex0, vertex1, vertex2 ), d, vertex0, vertex1, vertex2, hinges, 0 ) );
			}
		}
	}
}
