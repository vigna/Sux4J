package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.mph.PaCoTrieDistributorMonotoneMinimalPerfectHashFunction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class PaCoTrieDistributorMonotoneMinimalPerfectHashFunctionTest extends TestCase {
	
	
	public static String binary(int l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}


	public void testSmall() throws IOException {
		String[] s = { "a", "b", "c", "d", "e", "f" };
		PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );
	}

	public void testPrefix() throws IOException {

		String[] s = { "0", "00", "000", "0000", "00000", "000000", "00000001", "0000000101", "00000002" };
		PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeUtf16() );
		
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );
		
	}
	

	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for( int d = 10; d < 10000; d *= 10 ) {

			String[] s = new String[ d ];
			int[] v = new int[ s.length ];
			for( int i = s.length; i-- != 0; ) s[ v[ i ] = i ] = binary( i );

			PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeUtf16() );

			for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

			// Exercise code for negative results
			for( int i = d; i-- != 0; ) mph.getLong( binary( i * i + d ) );

			File temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( mph, temp );
			mph = (PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
			for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );


			mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ), true ) );

			for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

			temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( mph, temp );
			mph = (PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
			for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

		}
	}
}
