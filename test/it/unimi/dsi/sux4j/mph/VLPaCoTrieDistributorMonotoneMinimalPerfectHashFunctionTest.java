package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunctionTest extends TestCase {
	
	
	public static String binary2(int l) {
		String s = "1" + Integer.toBinaryString( l ) + "10000000000000000000000000000000000000000000000000000000000000000000000000";
		return s.substring( 0, 32 );
	}

	public static String binary(int l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}


	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		// TODO: test on very small instances
		for( int n: new int[] { 100, 1000, 100000 } ) {
			for( int pass = 0; pass < 2; pass++ ) {
				String[] s = new String[ n ];
				int[] v = new int[ s.length ];
				for( int i = s.length; i-- != 0; ) s[ v[ i ] = i ] = pass == 0 ? binary( i ) : binary2( i );
				Arrays.sort( s );

				System.err.println( n );
				VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeUtf16() );

				for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

				// Exercise code for negative results
				for( int i = n; i-- != 0; ) mph.getLong( binary( i * i + n ) );

				File temp = File.createTempFile( getClass().getSimpleName(), "test" );
				temp.deleteOnExit();
				BinIO.storeObject( mph, temp );
				mph = (VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
				for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );


				mph = new VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ), true ) );

				for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

				temp = File.createTempFile( getClass().getSimpleName(), "test" );
				temp.deleteOnExit();
				BinIO.storeObject( mph, temp );
				mph = (VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
				for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );
			}
		}
	}
}
