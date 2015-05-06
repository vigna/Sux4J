package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.*;

import org.junit.Test;

import it.unimi.dsi.sux4j.mph.Modulo3System.Modulo3Equation;
import it.unimi.dsi.util.XorShift128PlusRandomGenerator;

public class Modulo3SystemTest {

	@Test
	public void testOne() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation( 2, 0 ) );
		final int[] solution = new int[ 1 ];
		assertTrue( system.solve( solution ) );
		assertEquals( 2, solution[ 0 ] );
	}

	@Test
	public void testImpossible() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation( 2, 0 ) );
		system.add( new Modulo3Equation( 1, 0 ) );
		final int[] solution = new int[ 1 ];
		assertFalse( system.solve( solution ) );
	}

	@Test
	public void testRedundant() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation( 2, 0 ) );
		system.add( new Modulo3Equation( 2, 0 ) );
		final int[] solution = new int[ 1 ];
		assertTrue( system.solve( solution ) );
		assertEquals( 2, solution[ 0 ] );
	}

	@Test
	public void testSmall() {
		Modulo3System system = new Modulo3System();
		system.add( new Modulo3Equation( 0, 1, 4, 10 ) );
		system.add( new Modulo3Equation( 2, 1, 4, 9 ) );
		system.add( new Modulo3Equation( 0, 0, 6, 8 ) );
		system.add( new Modulo3Equation( 1, 0, 6, 9 ) );
		system.add( new Modulo3Equation( 2, 2, 4, 8 ) );
		system.add( new Modulo3Equation( 0, 2, 6, 10 ) );
		final int[] solution = new int[ 11 ];
		assertTrue( system.solve( solution ) );
		assertTrue( system.check( solution ) );
	}

	@Test
	public void testRandom() {
		XorShift128PlusRandomGenerator random = new XorShift128PlusRandomGenerator( 1 );
		for( int size: new int[] { 10, 100, 1000 } ) {
			Modulo3System system = new Modulo3System();
			for( int i = 0; i < 3 * size / 4; i++ ) system.add( new Modulo3Equation( random.nextInt( 3 ), random.nextInt( size ), random.nextInt( size ), random.nextInt( size ) ) ); 
			final int[] solution = new int[ size ];
			assertTrue( system.solve( solution ) );
			assertTrue( system.check( solution ) );
		}
	}
}
