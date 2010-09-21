package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class VLLcpMonotoneMinimalPerfectHashFunctionTest extends TestCase {
	
	
	public static String binary(int l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 1000 ];
		int[] v = new int[ s.length ];
		for( int i = s.length; i-- != 0; ) s[ v[ i ] = i ] = binary( i );

		VLLcpMonotoneMinimalPerfectHashFunction<String> mph = new VLLcpMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeUtf16() );
		
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

		// Exercise code for negative results
		for( int i = 1000; i-- != 0; ) mph.getLong( binary( i * i + 1000 ) );

		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (VLLcpMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

	
		mph = new VLLcpMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ), true ) );
		
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );
		
		temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (VLLcpMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

	}
}
