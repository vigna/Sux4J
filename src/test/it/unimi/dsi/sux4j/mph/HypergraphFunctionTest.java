package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.bits.Utf16TransformationStrategy;
import it.unimi.dsi.sux4j.mph.HypergraphFunction;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class HypergraphFunctionTest extends TestCase {
	
	@SuppressWarnings("unchecked")
	public void testNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 1000 ];
		int[] v = new int[ 1000 ];
		for( int i = s.length; i-- != 0; ) s[ v[ i ] = i ] = Integer.toString( i );
		
		HypergraphFunction<String> mph = new HypergraphFunction<String>( Arrays.asList( s ), new Utf16TransformationStrategy(), v, 12, HypergraphFunction.WEIGHT_UNKNOWN );
		
		int[] check = new int[ s.length ];
		IntArrays.fill( check, -1 );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getInt( s[ i ] ) );
		
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (HypergraphFunction<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getInt( s[ i ] ) );
	}
	
	public static String binary(int l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 10 ];
		int[] v = new int[ s.length ];
		for( int i = s.length; i-- != 0; ) s[ v[ i ] = i ] = binary( i );
		
		HypergraphFunction<String> mph = new HypergraphFunction<String>( Arrays.asList( s ), new Utf16TransformationStrategy(), v, 12, HypergraphFunction.WEIGHT_UNKNOWN_SORTED_STRINGS );
		
		int[] check = new int[ s.length ];
		IntArrays.fill( check, -1 );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getInt( s[ i ] ) );
		
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (HypergraphFunction<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getInt( s[ i ] ) );
	}
	
}
