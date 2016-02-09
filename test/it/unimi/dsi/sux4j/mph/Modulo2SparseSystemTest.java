package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.sux4j.mph.solve.Modulo2SparseSystem;
import it.unimi.dsi.sux4j.mph.solve.Modulo2SparseSystem.Modulo2Equation;
import it.unimi.dsi.util.XorShift128PlusRandomGenerator;

import java.util.Arrays;

import org.junit.Test;

public class Modulo2SparseSystemTest {

	@Test
	public void testBuilder() {
		Modulo2Equation equation = new Modulo2Equation( 1 ).add( 0 ).add( 1 ).add( 2 );
		assertEquals( 1, equation.c );
		assertEquals( 3, equation.variables.size() );
		assertArrayEquals( new int[] { 0, 1, 2 }, equation.variables.toIntArray() );
	}

	@Test
	public void testSub0() {
		Modulo2Equation equation0 = new Modulo2Equation( 2 ).add( 1 ).add( 4 ).add( 9 );
		Modulo2Equation equation1 = new Modulo2Equation( 1 ).add( 1 ).add( 4 ).add( 10 );
		equation0.add( equation1 );
		assertArrayEquals( new int[] { 9, 10 }, equation0.variables.toIntArray() );
	}

	@Test
	public void testOne() {
		Modulo2SparseSystem system = new Modulo2SparseSystem( 2 );
		system.add( new Modulo2Equation( 2 ).add( 0 ) );
		final long[] solution = new long[ 2 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().lazyGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testImpossible() {
		Modulo2SparseSystem system = new Modulo2SparseSystem( 1 );
		system.add( new Modulo2Equation( 2 ).add( 0 ) );
		system.add( new Modulo2Equation( 1 ).add( 0 ) );
		final long[] solution = new long[ 1 ];
		assertFalse( system.copy().gaussianElimination( solution ) );
		assertFalse( system.copy().lazyGaussianElimination( solution ) );
	}

	@Test
	public void testRedundant() {
		Modulo2SparseSystem system = new Modulo2SparseSystem( 1 );
		system.add( new Modulo2Equation( 2 ).add( 0 ) );
		system.add( new Modulo2Equation( 2 ).add( 0 ) );
		final long[] solution = new long[ 1 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().lazyGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testSmall() {
		Modulo2SparseSystem system = new Modulo2SparseSystem( 11 );
		system.add( new Modulo2Equation( 0 ).add( 1 ).add( 4 ).add( 10 ) );
		system.add( new Modulo2Equation( 2 ).add( 1 ).add( 4 ).add( 9 ) );
		system.add( new Modulo2Equation( 0 ).add( 0 ).add( 6 ).add( 8 ) );
		system.add( new Modulo2Equation( 1 ).add( 0 ).add( 6 ).add( 9 ) );
		system.add( new Modulo2Equation( 2 ).add( 2 ).add( 4 ).add( 8 ) );
		system.add( new Modulo2Equation( 0 ).add( 2 ).add( 6 ).add( 10 ) );
		final long[] solution = new long[ 11 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().lazyGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testRandom() {
		XorShift128PlusRandomGenerator random = new XorShift128PlusRandomGenerator( 1 );
		for( int size: new int[] { 1000 } ) {
			Modulo2SparseSystem system = new Modulo2SparseSystem( size );
			// Few equations
			for( int i = 0; i < 2 * size / 3; i++ ) system.add( new Modulo2Equation( random.nextInt( 100 ) ).add( random.nextInt( size / 3 ) ).add( size / 3 + random.nextInt( size / 3 ) ).add( 2 * size / 3 + random.nextInt( size / 3 ) ) ); 
			final long[] solution = new long[ size ];
			assertTrue( system.copy().gaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
			Arrays.fill( solution, 0 );
			assertTrue( system.copy().lazyGaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
		}
	}
	
	@Test
	public void testRandom2() {
		XorShift128PlusRandomGenerator random = new XorShift128PlusRandomGenerator( 1 );
		for( int size: new int[] { 10, 100, 1000, 10000 } ) {
			Modulo2SparseSystem system = new Modulo2SparseSystem( size );

			IntOpenHashSet edge[] = new IntOpenHashSet[ size ];
			
			int x, v, w;
			for ( int i = 0; i < 2 * size / 3; i++ ) {
				boolean alreadySeen;
				do {
					x = random.nextInt( size );
					do v = random.nextInt( size ); while( v == i );
					do w = random.nextInt( size ); while( w == i || w == v );

					edge[ i ] = new IntOpenHashSet();
					edge[ i ].add( x );
					edge[ i ].add( v );
					edge[ i ].add( w );
					
					alreadySeen = false;
					for( int j = 0; j < i; j++ ) 
						if ( edge[ j ].equals( edge[ i ] ) ) {
						alreadySeen = true;
						break;
					}
				} while( alreadySeen );
			}

			for( int i = 0; i < 2 * size / 3; i++ ) {
				Modulo2Equation equation = new Modulo2Equation( random.nextInt( 100 ) );
				IntOpenHashSet variables = new IntOpenHashSet();
				for( IntIterator iterator = edge[ i ].iterator(); iterator.hasNext(); ) variables.add( iterator.nextInt() );
				final int[] array = variables.toIntArray();
				Arrays.sort( array );
				for( int var: array ) equation.add( var );
				system.add( equation ); 
			}
			final long[] solution = new long[ size ];
			assertTrue( system.copy().gaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
			Arrays.fill( solution, 0 );
			assertTrue( system.copy().lazyGaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
		}
	}

}
