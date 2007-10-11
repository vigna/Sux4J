package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHash;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;
import static it.unimi.dsi.sux4j.mph.MinimalPerfectHash.countNonzeroPairs;

public class MinimalPerfectHashTest extends TestCase {
	
	public void testNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 1000 ];
		for( int i = s.length; i-- != 0; ) s[ i ] = Integer.toString( i );
		
		MinimalPerfectHash mph = new MinimalPerfectHash( Arrays.asList( s ) );
		
		int[] check = new int[ s.length ];
		IntArrays.fill( check, -1 );
		for( int i = s.length; i-- != 0; ) {
			assertEquals( Integer.toString( i ), -1, check[ mph.getNumber( s[ i ] ) ] );
			check[ mph.getNumber( s[ i ] ) ] = i;
		}
		
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (MinimalPerfectHash)BinIO.loadObject( temp );

		IntArrays.fill( check, -1 );
		for( int i = s.length; i-- != 0; ) {
			assertEquals( Integer.toString( i ), -1, check[ mph.getNumber( s[ i ] ) ] );
			check[ mph.getNumber( s[ i ] ) ] = i;
		}
	}
	
	public void testCountNonZeroPairs() {
		assertEquals( 0, countNonzeroPairs( 0 ) );
		assertEquals( 1, countNonzeroPairs( 1 ) );
		assertEquals( 1, countNonzeroPairs( 2 ) );
		assertEquals( 1, countNonzeroPairs( 3 ) );
		assertEquals( 2, countNonzeroPairs( 0xA ) );
		assertEquals( 2, countNonzeroPairs( 0x5 ) );
		assertEquals( 2, countNonzeroPairs( 0xF ) );
		assertEquals( 4, countNonzeroPairs( 0x1111 ) );
		assertEquals( 4, countNonzeroPairs( 0x3333 ) );
		assertEquals( 8, countNonzeroPairs( 0xFFFF ) );
	}
}
