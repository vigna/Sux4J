package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class ZFastTrieDistributorMonotoneMinimalPerfectHashFunctionTest {


	public static String binary( int l ) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@Test
	public void testEmpty() throws IOException {
		String[] s = {};
		for ( int b = -1; b < 3; b++ ) {
			ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ),
					TransformationStrategies.prefixFreeIso(), b, 0, null );
			assertEquals( "Bucket size: " + ( 1 << b ), 0, mph.size64() );
			mph.numBits();
		}
	}

	@Test
	public void testSingleton() throws IOException {
		String[] s = { "a" };
		for ( int b = -1; b < 3; b++ ) {
			ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ),
					TransformationStrategies.prefixFreeIso(), b, 0, null );
			for ( int i = s.length; i-- != 0; )
				assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );
			mph.numBits();
		}
	}

	@Test
	public void testDoubleton() throws IOException {
		String[] s = { "a", "b" };
		for ( int b = -1; b < 3; b++ ) {
			ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ),
					TransformationStrategies.prefixFreeIso(), b, 0, null );
			for ( int i = s.length; i-- != 0; )
				assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );
			mph.numBits();
		}
	}


	@Test
	public void testSmallest() throws IOException {
		String[] s = { "a", "b", "c", "d", "e", "f", "g" };
		for ( int b = 1; b < 2; b++ ) {
			ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ),
					TransformationStrategies.prefixFreeIso(), b, 0, null );
			for ( int i = s.length; i-- != 0; )
				assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );
			mph.numBits();
		}
	}

	@Test
	public void testSmall() throws IOException {
		String[] s = { "-", "0", "1", "4", "5", "a", "b", "c", "d", "e", "f", "g", "}" };
		for ( int b = -1; b < 5; b++ ) {
			ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ),
					TransformationStrategies.prefixFreeIso(), b, 0, null );
			for ( int i = s.length; i-- != 0; )
				assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for ( int b = -1; b < 6; b++ ) {
			for ( int d = 100; d < 10000; d *= 10 ) {
				String[] s = new String[ d ];
				int[] v = new int[ s.length ];
				for ( int i = s.length; i-- != 0; )
					s[ v[ i ] = i ] = binary( i );

				ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ),
						TransformationStrategies.prefixFreeIso(), b, 0, null );
				mph.numBits();

				for ( int i = s.length; i-- != 0; )
					assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );

				// Exercise code for negative results
				for ( int i = 1000; i-- != 0; )
					mph.getLong( binary( i * i + d ) );

				File temp = File.createTempFile( getClass().getSimpleName(), "test" );
				temp.deleteOnExit();
				BinIO.storeObject( mph, temp );
				mph = (ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
				for ( int i = s.length; i-- != 0; )
					assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );


				mph = new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ), true ), b, 0, null );
				mph.numBits();

				for ( int i = s.length; i-- != 0; )
					assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );

				temp = File.createTempFile( getClass().getSimpleName(), "test" );
				temp.deleteOnExit();
				BinIO.storeObject( mph, temp );
				mph = (ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject( temp );
				for ( int i = s.length; i-- != 0; )
					assertEquals( "Bucket size: " + ( 1 << b ), i, mph.getLong( s[ i ] ) );
			}
		}
	}
}
