package test.it.unimi.dsi.sux4j.util;

import it.unimi.dsi.bits.Utf16TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.mph.MWHCFunction;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHash;
import it.unimi.dsi.sux4j.util.ShiftAddXorSignedStringMap;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class ShiftAddXorSignedStringMapTest extends TestCase {
	
	@SuppressWarnings("unchecked")
	public void testNumbers() throws IOException, ClassNotFoundException {
		
		for( int width = 16; width <= Long.SIZE; width += 8 ) {
			String[] s = new String[ 1000 ];
			for( int i = s.length; i-- != 0; ) s[ i ] = Integer.toString( i );

			// Test with mph
			MinimalPerfectHash<CharSequence> mph = new MinimalPerfectHash<CharSequence>( Arrays.asList( s ), new Utf16TransformationStrategy() );
			ShiftAddXorSignedStringMap map = new ShiftAddXorSignedStringMap( Arrays.asList( s ).iterator(), mph, width );

			int[] check = new int[ s.length ];
			IntArrays.fill( check, -1 );
			for( int i = s.length; i-- != 0; ) {
				assertEquals( Integer.toString( i ), -1, check[ (int)map.getLong( s[ i ] ) ] );
				check[ (int)mph.getLong( s[ i ] ) ] = i;
			}

			for( int i = s.length + 100; i-- != s.length; ) assertEquals( -1, map.getLong( Integer.toString( i ) ) );


			File temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( map, temp );
			map = (ShiftAddXorSignedStringMap)BinIO.loadObject( temp );

			IntArrays.fill( check, -1 );
			for( int i = s.length; i-- != 0; ) {
				assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
				check[ (int)mph.getLong( s[ i ] ) ] = i;
			}

			for( int i = s.length + 100; i-- != s.length; ) assertEquals( -1, map.getLong( Integer.toString( i ) ) );

			// Test with function
			MWHCFunction<CharSequence> f = new MWHCFunction<CharSequence>( Arrays.asList( s ), new Utf16TransformationStrategy() );
			map = new ShiftAddXorSignedStringMap( Arrays.asList( s ).iterator(), f, width );

			IntArrays.fill( check, -1 );
			for( int i = s.length; i-- != 0; ) assertEquals( i, map.getLong( Integer.toString( i ) ) );
			for( int i = s.length + 100; i-- != s.length; ) assertEquals( -1, map.getLong( Integer.toString( i ) ) );

			BinIO.storeObject( map, temp );
			map = (ShiftAddXorSignedStringMap)BinIO.loadObject( temp );

			IntArrays.fill( check, -1 );
			for( int i = s.length; i-- != 0; ) assertEquals( i, map.getLong( Integer.toString( i ) ) );
			for( int i = s.length + 100; i-- != s.length; ) assertEquals( -1, map.getLong( Integer.toString( i ) ) );
		
		}
	}
}
