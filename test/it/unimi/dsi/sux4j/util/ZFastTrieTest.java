package it.unimi.dsi.sux4j.util;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.util.ZFastTrie;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class ZFastTrieTest extends TestCase {


	public static String binary( int l ) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	public void testEmpty() {
		String[] s = {};
		ZFastTrie<String> mph = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		assertFalse( mph.contains( "" ) );
	}

	public void testSingleton() {
		String[] s = { "a" };
		ZFastTrie<String> mph = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
			assertTrue( s[ i ], mph.contains( s[ i ] ) );
	}

	public void testDoubleton() {
		String[] s = { "a", "b" };
		ZFastTrie<String> mph = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
			assertTrue( s[ i ], mph.contains( s[ i ] ) );
	}


	public void testSmallest() {
		String[] s = { "a", "b", "c", "d", "e", "f", "g" };
		ZFastTrie<String> mph = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
			assertTrue( s[ i ], mph.contains( s[ i ] ) );
	}

	public void testSmall() {
		String[] s = { "-", "0", "1", "4", "5", "a", "b", "c", "d", "e", "f", "g", "}" };
		ZFastTrie<String> mph = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
			assertTrue( s[ i ], mph.contains( s[ i ] ) );
	}

	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for ( int d = 10; d < 10000; d *= 10 ) {
			String[] s = new String[ d ];
			int[] v = new int[ s.length ];
			for ( int i = s.length; i-- != 0; )
				s[ v[ i ] = i ] = binary( i );

			ZFastTrie<String> mph = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
			mph.numBits();

			for ( int i = s.length; i-- != 0; ) assertTrue( mph.contains( s[ i ] ) );

			// Exercise code for negative results
			for ( int i = 1000; i-- != 0; )
				mph.contains( binary( i * i + d ) );

			File temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( mph, temp );
			mph = (ZFastTrie<String>)BinIO.loadObject( temp );
			for ( int i = s.length; i-- != 0; ) assertTrue( mph.contains( s[ i ] ) );

			mph = new ZFastTrie<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ), true ) );
			mph.numBits();

			for ( int i = s.length; i-- != 0; ) assertTrue( mph.contains( s[ i ] ) );

			temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( mph, temp );
			mph = (ZFastTrie<String>)BinIO.loadObject( temp );
			for ( int i = s.length; i-- != 0; ) assertTrue( mph.contains( s[ i ] ) );
		}
	}
}
