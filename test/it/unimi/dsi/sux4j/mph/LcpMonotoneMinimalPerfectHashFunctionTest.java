package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class LcpMonotoneMinimalPerfectHashFunctionTest {


	public static String binary( int l ) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	private void check( String[] s, int size, LcpMonotoneMinimalPerfectHashFunction<String> mph, int signatureWidth ) {
		for ( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

		// Exercise code for negative results
		if ( signatureWidth == 0 ) for ( int i = size; i-- != 0; ) mph.getLong( binary( i + size ) );
		else for ( int i = size; i-- != 0; ) assertEquals( -1, mph.getLong( binary( i + size ) ) );
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for ( int size = 1000; size < 10000000; size *= 10 ) {
			for ( int signatureWidth: new int[] { 0, 32, 64 } ) {
				System.err.println( "Size: " + size + " Signature width: " + signatureWidth );
				String[] s = new String[ size ];
				int[] v = new int[ s.length ];
				for ( int i = s.length; i-- != 0; )
					s[ v[ i ] = i ] = binary( i );

				LcpMonotoneMinimalPerfectHashFunction<String> mph = new LcpMonotoneMinimalPerfectHashFunction.Builder<String>().keys( Arrays.asList( s ) ).transform( TransformationStrategies.prefixFreeUtf16() ).signed( signatureWidth ).build();

				check( s, size, mph, signatureWidth );

				File temp = File.createTempFile( getClass().getSimpleName(), "test" );
				temp.deleteOnExit();
				BinIO.storeObject( mph, temp );
				mph = (LcpMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );

				check( s, size, mph, signatureWidth );

				mph = new LcpMonotoneMinimalPerfectHashFunction.Builder<String>().keys( Arrays.asList( s ) ).transform( new HuTuckerTransformationStrategy( Arrays.asList( s ), true ) ).signed( signatureWidth ).build();

				check( s, size, mph, signatureWidth );

				temp = File.createTempFile( getClass().getSimpleName(), "test" );
				temp.deleteOnExit();
				BinIO.storeObject( mph, temp );

				check( s, size, mph, signatureWidth );
			}
		}
	}

	@Test
	public void testEmpty() throws IOException {
		LcpMonotoneMinimalPerfectHashFunction<String> mph = new LcpMonotoneMinimalPerfectHashFunction.Builder<String>().keys( Arrays.asList( new String[] {} ) ).transform( TransformationStrategies.prefixFreeUtf16() ).build();
		assertEquals( -1, mph.getLong( "" ) );
	}
}
