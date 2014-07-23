package it.unimi.dsi.sux4j.scratch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.sux4j.scratch.MinimalPerfectHashFunction.Builder;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class MinimalPerfectHashFunctionTest {

	private void check( int size, String[] s, MinimalPerfectHashFunction<CharSequence> mph, int w ) {
		final int[] check = new int[ s.length ];
		IntArrays.fill( check, -1 );
		for ( int i = s.length; i-- != 0; ) {
			assertEquals( Integer.toString( i ), -1, check[ (int)mph.getLong( s[ i ] ) ] );
			check[ (int)mph.getLong( s[ i ] ) ] = i;
		}

		// Exercise code for negative results
		for ( int i = 1000; i-- != 0; )
			if ( w != 0 ) assertEquals( -1, mph.getLong( Integer.toString( i + size ) ) );
			else mph.getLong( Integer.toString( i + size ) );
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {

		for ( int size : new int[] { 100, 1000, 10000, 100000, 1000000 } ) {
			for( int signatureWidth: new int[] { 0 } ) {
				System.err.println( "Size: " + size );
				String[] s = new String[ size ];
				for ( int i = s.length; i-- != 0; )	s[ i ] = Integer.toString( i );

				MinimalPerfectHashFunction<CharSequence> mph = new Builder<CharSequence>().keys( Arrays.asList( s ) ).transform( TransformationStrategies.utf16() ).signed( signatureWidth ).build();
				
				final LongOpenHashSet found = new LongOpenHashSet();
				for( String t: s ) {
					assertTrue( found.add( mph.getLong( t ) ) );
				}
			}
		}
	}
}
