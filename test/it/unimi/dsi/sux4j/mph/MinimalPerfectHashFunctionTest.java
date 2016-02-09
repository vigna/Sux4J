package it.unimi.dsi.sux4j.mph;

import static it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.countNonzeroPairs;
import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHashFunction.Builder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class MinimalPerfectHashFunctionTest {

	private void check( int size, String[] s, MinimalPerfectHashFunction<CharSequence> mph, int w ) {
		final int[] check = new int[ s.length ];
		Arrays.fill( check, -1 );
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

		for ( int size : new int[] { 0, 1, 4, 8, 20, 64, 100, 1000, 10000, 100000 } ) {
			for( int signatureWidth: new int[] { 0, 32, 64 } ) {
				System.err.println( "Size: " + size  + " w: " + signatureWidth );
				String[] s = new String[ size ];
				for ( int i = s.length; i-- != 0; )
					s[ i ] = Integer.toString( i );

				MinimalPerfectHashFunction<CharSequence> mph = new Builder<CharSequence>().keys( Arrays.asList( s ) ).transform( TransformationStrategies.utf16() ).signed( signatureWidth ).build();
						
				check( size, s, mph, signatureWidth );

				File temp = File.createTempFile( getClass().getSimpleName(), "test" );
				temp.deleteOnExit();
				BinIO.storeObject( mph, temp );
				mph = (MinimalPerfectHashFunction<CharSequence>)BinIO.loadObject( temp );

				check( size, s, mph, signatureWidth );

				// From store
				ChunkedHashStore<CharSequence> chunkedHashStore = new ChunkedHashStore<CharSequence>( TransformationStrategies.utf16(), null, signatureWidth < 0 ? -signatureWidth : 0, null );
				chunkedHashStore.addAll( Arrays.asList( s ).iterator() );
				chunkedHashStore.checkAndRetry( Arrays.asList( s ) );
				mph = new MinimalPerfectHashFunction.Builder<CharSequence>().store( chunkedHashStore ).signed( signatureWidth ).build();
				chunkedHashStore.close();

				check( size, s, mph, signatureWidth );
			}
		}
	}

	@Test
	public void testCountNonZeroPairs() {
		assertEquals( 0, countNonzeroPairs( 0 ) );
		assertEquals( 1, countNonzeroPairs( 1 ) );
		assertEquals( 1, countNonzeroPairs( 2 ) );
		assertEquals( 1, countNonzeroPairs( 3 ) );
		assertEquals( 2, countNonzeroPairs( 0xA ) );
		assertEquals( 2, countNonzeroPairs( 0x5 ) );
		assertEquals( 2, countNonzeroPairs( 0xF ) );
		assertEquals( 4, countNonzeroPairs( 0x1111 ) );
		assertEquals( 4, countNonzeroPairs( 0x3333 ) );
		assertEquals( 8, countNonzeroPairs( 0xFFFF ) );
	}
}
