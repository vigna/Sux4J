package test.it.unimi.dsi.sux4j.mph;

import static it.unimi.dsi.sux4j.mph.MinimalPerfectHash.countNonzeroPairs;
import it.unimi.dsi.bits.Utf16TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHash;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class MinimalPerfectHashTest extends TestCase {
	
	@SuppressWarnings("unchecked")
	public void testNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 1000 ];
		for( int i = s.length; i-- != 0; ) s[ i ] = Integer.toString( i );
		
		MinimalPerfectHash<CharSequence> mph = new MinimalPerfectHash<CharSequence>( Arrays.asList( s ), new Utf16TransformationStrategy() );
		
		int[] check = new int[ s.length ];
		IntArrays.fill( check, -1 );
		for( int i = s.length; i-- != 0; ) {
			assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
			check[ (int)mph.getLong( s[ i ] ) ] = i;
		}
		
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (MinimalPerfectHash<CharSequence>)BinIO.loadObject( temp );

		IntArrays.fill( check, -1 );
		for( int i = s.length; i-- != 0; ) {
			assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
			check[ (int)mph.getLong( s[ i ] ) ] = i;
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
