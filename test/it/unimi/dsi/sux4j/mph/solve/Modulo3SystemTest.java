package it.unimi.dsi.sux4j.mph.solve;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.sux4j.mph.solve.Modulo3System;
import it.unimi.dsi.sux4j.mph.solve.Modulo3System.Modulo3Equation;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

import org.junit.Test;

public class Modulo3SystemTest {

	@Test
	public void testAddMod3() {
		assertEquals( 1L << 62, Modulo3System.Modulo3Equation.addMod3( 2L << 62, 2L << 62 ) );
	}

	@Test
	public void testSubMod3() {
		assertEquals( 1, Modulo3System.Modulo3Equation.subMod3( 0, 2 ) );
		assertEquals( 1 << 2, Modulo3System.Modulo3Equation.subMod3( 0, 2 << 2 ) );
	}


	@Test
	public void testBuilder() {
		Modulo3Equation equation = new Modulo3Equation( 2, 3 ).add( 2, 1 ).add( 0, 2 ).add( 1, 1 );
		assertEquals( 2, equation.c );
		assertEquals( 3, equation.variables().length );
		assertArrayEquals( new int[] { 0, 1, 2 }, equation.variables() );
		assertArrayEquals( new int[] { 2, 1, 1 }, equation.coefficients() );
	}

	@Test
	public void testSub0() {
		Modulo3Equation equation0 = new Modulo3Equation( 2, 11 ).add( 1 ).add( 4 ).add( 9 );
		Modulo3Equation equation1 = new Modulo3Equation( 1, 11 ).add( 1 ).add( 4 ).add( 10 );
		//equation1.updateOp();
		equation0.sub( equation1, 1 );
		assertArrayEquals( new int[] { 9, 10 }, equation0.variables() );
		assertArrayEquals( new int[] { 1, 2 }, equation0.coefficients() );
	}

	@Test
	public void testSub1() {
		Modulo3Equation equation0 = new Modulo3Equation( 2, 11 ).add( 0 ).add( 6 ).add( 9 );
		Modulo3Equation equation1 = new Modulo3Equation( 1, 11 ).add( 9 ).add( 10, 2 );
		//equation1.updateOp();
		equation0.sub( equation1, 1 );
		assertArrayEquals( new int[] { 0, 6, 10 }, equation0.variables() );
		assertArrayEquals( new int[] { 1, 1, 1 }, equation0.coefficients() );
	}

	@Test
	public void testEliminate() {
		Modulo3Equation equation0 = new Modulo3Equation( 2, 11 ).add( 0, 1 ).add( 2, 2 ).add( 1, 1 );
		Modulo3Equation equation1 = new Modulo3Equation( 2, 11 ).add( 0, 1 ).add( 3, 1 ).add( 1, 2 );
		//equation1.updateOp();
		Modulo3Equation reduced = equation0.eliminate( 0, equation1 );
		assertArrayEquals( new int[] { 1, 2, 3 }, reduced.variables() );
		assertArrayEquals( new int[] { 2, 2, 2 }, reduced.coefficients() );
	}

	@Test(expected=IllegalStateException.class)
	public void testAdd() {
		new Modulo3Equation( 2, 4 ).add( 0, 1 ).add( 0, 2 );
	}
	
	@Test
	public void testOne() {
		Modulo3System system = new Modulo3System( 2 );
		system.add( new Modulo3Equation( 2, 2 ).add( 0, 1 ) );
		final long[] solution = new long[ 2 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().lazyGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testImpossible() {
		Modulo3System system = new Modulo3System( 1 );
		system.add( new Modulo3Equation( 2, 1 ).add( 0 ) );
		system.add( new Modulo3Equation( 1, 1 ).add( 0 ) );
		final long[] solution = new long[ 1 ];
		assertFalse( system.copy().gaussianElimination( solution ) );
		assertFalse( system.copy().lazyGaussianElimination( solution ) );
	}

	@Test
	public void testRedundant() {
		Modulo3System system = new Modulo3System( 1 );
		system.add( new Modulo3Equation( 2, 1 ).add( 0 ) );
		system.add( new Modulo3Equation( 2, 1 ).add( 0 ) );
		final long[] solution = new long[ 1 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().lazyGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testSmall() {
		Modulo3System system = new Modulo3System( 11 );
		system.add( new Modulo3Equation( 0, 11 ).add( 1 ).add( 4 ).add( 10 ) );
		system.add( new Modulo3Equation( 2, 11 ).add( 1 ).add( 4 ).add( 9 ) );
		system.add( new Modulo3Equation( 0, 11 ).add( 0 ).add( 6 ).add( 8 ) );
		system.add( new Modulo3Equation( 1, 11 ).add( 0 ).add( 6 ).add( 9 ) );
		system.add( new Modulo3Equation( 2, 11 ).add( 2 ).add( 4 ).add( 8 ) );
		system.add( new Modulo3Equation( 0, 11 ).add( 2 ).add( 6 ).add( 10 ) );
		final long[] solution = new long[ 11 ];
		assertTrue( system.copy().gaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
		Arrays.fill( solution, 0 );
		assertTrue( system.copy().lazyGaussianElimination( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testRandom() {
		XoRoShiRo128PlusRandomGenerator random = new XoRoShiRo128PlusRandomGenerator( 1 );
		for( int size: new int[] { 10, 100, 1000 } ) {
			Modulo3System system = new Modulo3System( size );
			// Few equations
			for( int i = 0; i < 2 * size / 3; i++ ) system.add( new Modulo3Equation( random.nextInt( 3 ), size ).add( random.nextInt( size / 3 ), 1 ).add( size / 3 + random.nextInt( size / 3 ), 2 ).add( 2 * size / 3 + random.nextInt( size / 3 ), 1 ) ); 
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
		XoRoShiRo128PlusRandomGenerator random = new XoRoShiRo128PlusRandomGenerator( 1 );
		for( int size: new int[] { 10, 100, 1000, 10000 } ) {
			Modulo3System system = new Modulo3System( size );

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
				Modulo3Equation equation = new Modulo3Equation( random.nextInt( 3 ), size );
				for( IntIterator iterator = edge[ i ].iterator(); iterator.hasNext(); ) equation.add( iterator.nextInt() );
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
