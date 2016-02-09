package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

public class HypergraphFunctionTest {

	@SuppressWarnings("unchecked")
	@Test
	public void testNumbers() throws IOException, ClassNotFoundException {
		for ( int size : new int[] { 0, 1, 4, 8, 20, 64, 100, 1000 } ) {
			System.err.println( "Size: " + size );

			String[] s = new String[ size ];
			long[] v = new long[ s.length ];
			for ( int i = s.length; i-- != 0; )
				s[ (int)( v[ i ] = i ) ] = Integer.toString( i );

			GOV3Function<String> function = new GOV3Function.Builder<String>().keys( Arrays.asList( s ) ).transform( TransformationStrategies.utf16() ).values( LongArrayList.wrap( v ), 12 ).build();

			for ( int i = s.length; i-- != 0; )
				assertEquals( i, function.getLong( s[ i ] ) );

			File temp = File.createTempFile( getClass().getSimpleName(), "test" );
			temp.deleteOnExit();
			BinIO.storeObject( function, temp );
			function = (GOV3Function<String>)BinIO.loadObject( temp );
			for ( int i = s.length; i-- != 0; )
				assertEquals( i, function.getLong( s[ i ] ) );

			function = new GOV3Function.Builder<String>().keys( Arrays.asList( s ) ).transform( TransformationStrategies.utf16() ).build();
			for ( int i = s.length; i-- != 0; )
				assertEquals( i, function.getLong( s[ i ] ) );
		}
	}

	public static String binary( int l ) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		String[] s = new String[ 10 ];
		long[] v = new long[ s.length ];
		for ( int i = s.length; i-- != 0; )
			s[ (int)( v[ i ] = i ) ] = binary( i );

		GOV3Function<String> function = new GOV3Function.Builder<String>().keys( Arrays.asList( s ) ).transform( TransformationStrategies.utf16() ).values( LongArrayList.wrap( v ), 12 ).build();

		int[] check = new int[ s.length ];
		Arrays.fill( check, -1 );
		for ( int i = s.length; i-- != 0; )
			assertEquals( i, function.getLong( s[ i ] ) );

		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( function, temp );
		function = (GOV3Function<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; )
			assertEquals( i, function.getLong( s[ i ] ) );
	}

}
