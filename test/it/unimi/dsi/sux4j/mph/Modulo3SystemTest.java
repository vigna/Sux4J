package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.sux4j.mph.Modulo3System.Modulo3Equation;
import it.unimi.dsi.sux4j.mph.Modulo3System.Modulo3Equation.Builder;
import it.unimi.dsi.util.XorShift128PlusRandomGenerator;

import org.junit.Test;

public class Modulo3SystemTest {

	@Test
	public void testBuilder() {
		Modulo3Equation equation = new Modulo3Equation.Builder( 2 ).add( 2, 1 ).add( 0, 2 ).add( 1, 1 ).build();
		assertEquals( 2, equation.c );
		assertEquals( 3, equation.variable.length );
		assertArrayEquals( new int[] { 0, 1, 2 }, equation.variable );
		assertArrayEquals( new int[] { 2, 1, 1 }, equation.coefficient );
	}

	@Test
	public void testSub0() {
		Modulo3Equation equation0 = new Modulo3Equation.Builder( 2 ).add( 1 ).add( 4 ).add( 9 ).build();
		Modulo3Equation equation1 = new Modulo3Equation.Builder( 1 ).add( 1 ).add( 4 ).add( 10 ).build();
		Modulo3Equation result = equation0.sub( equation1, 1 );
		assertArrayEquals( new int[] { 9, 10 }, result.variable );
		assertArrayEquals( new int[] { 1, 2 }, result.coefficient );
	}

	@Test
	public void testSub1() {
		Modulo3Equation equation0 = new Modulo3Equation.Builder( 2 ).add( 0 ).add( 6 ).add( 9 ).build();
		Modulo3Equation equation1 = new Modulo3Equation.Builder( 1 ).add( 9 ).add( 10, 2 ).build();
		Modulo3Equation result = equation0.sub( equation1, 1 );
		assertArrayEquals( new int[] { 0, 6, 10 }, result.variable );
		assertArrayEquals( new int[] { 1, 1, 1 }, result.coefficient );
	}

	@Test
	public void testEliminate() {
		Modulo3Equation equation0 = new Modulo3Equation.Builder( 2 ).add( 0, 1 ).add( 2, 2 ).add( 1, 1 ).build();
		Modulo3Equation equation1 = new Modulo3Equation.Builder( 2 ).add( 0, 1 ).add( 3, 1 ).add( 1, 2 ).build();
		Modulo3Equation reduced = equation0.eliminate( equation1, equation0.variable[ 0 ] );
		assertArrayEquals( new int[] { 1, 2, 3 }, reduced.variable );
		assertArrayEquals( new int[] { 2, 2, 2 }, reduced.coefficient );
	}


	@Test
	public void testOne() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation.Builder( 2 ).add( 0, 1 ).build() );
		final int[] solution = new int[ 1 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().structuredGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testImpossible() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation.Builder( 2 ).add( 0 ).build() );
		system.add( new Modulo3Equation.Builder( 1 ).add( 0 ).build() );
		final int[] solution = new int[ 1 ];
		assertFalse( system.copy().gaussianElimination( solution ) );
		assertFalse( system.copy().structuredGaussianElimination( solution ) );
	}

	@Test
	public void testRedundant() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation.Builder( 2 ).add( 0 ).build() );
		system.add( new Modulo3Equation.Builder( 2 ).add( 0 ).build() );
		final int[] solution = new int[ 1 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().structuredGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testSmall() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation.Builder( 0 ).add( 1 ).add( 4 ).add( 10 ).build() );
		system.add( new Modulo3Equation.Builder( 2 ).add( 1 ).add( 4 ).add( 9 ).build() );
		system.add( new Modulo3Equation.Builder( 0 ).add( 0 ).add( 6 ).add( 8 ).build() );
		system.add( new Modulo3Equation.Builder( 1 ).add( 0 ).add( 6 ).add( 9 ).build() );
		system.add( new Modulo3Equation.Builder( 2 ).add( 2 ).add( 4 ).add( 8 ).build() );
		system.add( new Modulo3Equation.Builder( 0 ).add( 2 ).add( 6 ).add( 10 ).build() );
		final int[] solution = new int[ 11 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().structuredGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testRandom() {
		XorShift128PlusRandomGenerator random = new XorShift128PlusRandomGenerator( 1 );
		for( int size: new int[] { 1000 } ) {
			Modulo3System system = new Modulo3System();
			// Few equations
			for( int i = 0; i < 2 * size / 3; i++ ) system.add( new Modulo3Equation.Builder( random.nextInt( 3 ) ).add( random.nextInt( size / 3 ), 1 ).add( size / 3 + random.nextInt( size / 3 ), 2 ).add( 2 * size / 3 + random.nextInt( size / 3 ), 1 ).build() ); 
			final int[] solution = new int[ size ];
			assertTrue( system.copy().gaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
			assertTrue( system.copy().structuredGaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
		}
	}
	
	@Test
	public void testRandom2() {
		XorShift128PlusRandomGenerator random = new XorShift128PlusRandomGenerator( 1 );
		for( int size: new int[] { 10, 100, 1000 } ) {
			Modulo3System system = new Modulo3System();

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
				Builder builder = new Modulo3Equation.Builder( random.nextInt( 3 ) );
				for( IntIterator iterator = edge[ i ].iterator(); iterator.hasNext(); ) builder.add( iterator.nextInt() );
				system.add( builder.build() ); 
			}
			final int[] solution = new int[ size ];
			assertTrue( system.copy().gaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
			Arrays.fill( solution, 0 );
			assertTrue( system.copy().structuredGaussianElimination( solution ) );
			assertTrue( system.check( solution ) );
		}
	}

}
