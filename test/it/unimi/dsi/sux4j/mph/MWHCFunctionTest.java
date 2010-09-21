package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import junit.framework.TestCase;

public class MWHCFunctionTest extends TestCase {
	
	@SuppressWarnings("unchecked")
	public void testNumbers() throws IOException, ClassNotFoundException {

		for( int width = 20; width < Long.SIZE; width += 8 ) {
			for( int size = 1000; size < 10000000; size *= 10 ) {
				String[] s = new String[ size ];
				for( int i = s.length; i-- != 0; ) s[ i ] = Integer.toString( i );

				MWHCFunction<CharSequence> mph = new MWHCFunction<CharSequence>( Arrays.asList( s ), TransformationStrategies.utf16(), null, width );

				int[] check = new int[ s.length ];
				IntArrays.fill( check, -1 );
				for( int i = s.length; i-- != 0; ) {
					assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
					check[ (int)mph.getLong( s[ i ] ) ] = i;
				}

				// Exercise code for negative results
				for( int i = size; i-- != 0; ) mph.getLong( Integer.toString( i * i + size ) );

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
	}
	
	public void testLongNumbers() throws IOException {
		LongArrayList l = new LongArrayList( new long[] { 0x234904309830498L, 0xae049345e9eeeeeL, 0x23445234959234L, 0x239234eaeaeaeL } );
		MWHCFunction<CharSequence> mph = new MWHCFunction<CharSequence>( Arrays.asList( new String[] { "a", "b", "c", "d" } ), TransformationStrategies.utf16(), l, Long.SIZE );
		assertEquals( l.getLong( 0 ), mph.getLong( "a" ) );
		assertEquals( l.getLong( 1 ), mph.getLong( "b" ) );
		assertEquals( l.getLong( 2 ), mph.getLong( "c" ) );
		assertEquals( l.getLong( 3 ), mph.getLong( "d" ) );
	}
	
	public void testDuplicates() throws IOException {
		MWHCFunction<String> mph = new MWHCFunction<String>( 
				new Iterable<String>() {
					int iteration;

					public Iterator<String> iterator() {
						if ( iteration++ > 2 ) return Arrays.asList( new String[] { "a", "b", "c" } ).iterator();
						return Arrays.asList( new String[] { "a", "b", "a" } ).iterator();
					}
				}, 
				TransformationStrategies.utf16() );
		assertEquals( 0, mph.getLong( "a" ) );
		assertEquals( 1, mph.getLong( "b" ) );
		assertEquals( 2, mph.getLong( "c" ) );
	}
}
