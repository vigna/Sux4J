package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.mph.MWHCFunction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class MWHCFunctionTest extends TestCase {
	
	@SuppressWarnings("unchecked")
	public void testNumbers() throws IOException, ClassNotFoundException {

		for( int width = 16; width < Long.SIZE; width += 8 ) {
			String[] s = new String[ 1000 ];
			for( int i = s.length; i-- != 0; ) s[ i ] = Integer.toString( i );

			MWHCFunction<CharSequence> mph = new MWHCFunction<CharSequence>( Arrays.asList( s ), TransformationStrategies.utf16(), null, width );

			int[] check = new int[ s.length ];
			IntArrays.fill( check, -1 );
			for( int i = s.length; i-- != 0; ) {
				assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
				check[ (int)mph.getLong( s[ i ] ) ] = i;
			}

			// Exercise code for negative results
			for( int i = 1000; i-- != 0; ) mph.getLong( Integer.toString( i * i + 1000 ) );

			File temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( mph, temp );
			mph = (MWHCFunction<CharSequence>)BinIO.loadObject( temp );

			IntArrays.fill( check, -1 );
			for( int i = s.length; i-- != 0; ) {
				assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
				check[ (int)mph.getLong( s[ i ] ) ] = i;
			}
		}
	}
	
	public void testLongNumbers() {
		LongArrayList l = new LongArrayList( new long[] { 0x234904309830498L, 0xae049345e9eeeeeL, 0x23445234959234L, 0x239234eaeaeaeL } );
		MWHCFunction<CharSequence> mph = new MWHCFunction<CharSequence>( Arrays.asList( new String[] { "a", "b", "c", "d" } ), TransformationStrategies.utf16(), l, Long.SIZE );
		assertEquals( l.getLong( 0 ), mph.getLong( "a" ) );
		assertEquals( l.getLong( 1 ), mph.getLong( "b" ) );
		assertEquals( l.getLong( 2 ), mph.getLong( "c" ) );
		assertEquals( l.getLong( 3 ), mph.getLong( "d" ) );
	}
}
