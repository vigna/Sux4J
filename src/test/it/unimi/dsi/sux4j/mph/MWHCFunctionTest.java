package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.bits.Utf16TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
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

			MWHCFunction<CharSequence> mph = new MWHCFunction<CharSequence>( Arrays.asList( s ), new Utf16TransformationStrategy(), null, width );

			int[] check = new int[ s.length ];
			IntArrays.fill( check, -1 );
			for( int i = s.length; i-- != 0; ) {
				assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
				check[ (int)mph.getLong( s[ i ] ) ] = i;
			}

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
