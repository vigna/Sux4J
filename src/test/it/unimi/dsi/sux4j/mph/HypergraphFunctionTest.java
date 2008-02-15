package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.bits.Utf16TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.mph.MWHCFunction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class HypergraphFunctionTest extends TestCase {
	
	@SuppressWarnings("unchecked")
	public void testNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 1000 ];
		long[] v = new long[ 1000 ];
		for( int i = s.length; i-- != 0; ) s[ (int)( v[ i ] = i  )] = Integer.toString( i );
		
		MWHCFunction<String> function = new MWHCFunction<String>( Arrays.asList( s ), new Utf16TransformationStrategy(), LongArrayList.wrap( v ), 12 );
		
		for( int i = s.length; i-- != 0; ) assertEquals( i, function.getLong( s[ i ] ) );
		
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( function, temp );
		function = (MWHCFunction<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, function.getLong( s[ i ] ) );
		
		function = new MWHCFunction<String>( Arrays.asList( s ), new Utf16TransformationStrategy(), null, 12 );
		for( int i = s.length; i-- != 0; ) assertEquals( i, function.getLong( s[ i ] ) );
	}
	
	public static String binary(int l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 10 ];
		long[] v = new long[ s.length ];
		for( int i = s.length; i-- != 0; ) s[ (int)( v[ i ] = i  )] = binary( i );
		
		MWHCFunction<String> function = new MWHCFunction<String>( Arrays.asList( s ), new Utf16TransformationStrategy(), LongArrayList.wrap( v ), 12 );
		
		int[] check = new int[ s.length ];
		IntArrays.fill( check, -1 );
		for( int i = s.length; i-- != 0; ) assertEquals( i, function.getLong( s[ i ] ) );
		
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( function, temp );
		function = (MWHCFunction<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, function.getLong( s[ i ] ) );
	}
	
}
