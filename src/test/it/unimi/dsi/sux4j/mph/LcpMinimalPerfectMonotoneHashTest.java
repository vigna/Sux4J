package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.sux4j.mph.HuTuckerTransformationStrategy;
import it.unimi.dsi.sux4j.mph.LcpMinimalPerfectMonotoneHash;
import it.unimi.dsi.sux4j.mph.Utf16TransformationStrategy;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import junit.framework.TestCase;

public class LcpMinimalPerfectMonotoneHashTest extends TestCase {
	
	
	public static String binary(int l) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@SuppressWarnings("unchecked")
	public void testSortedNumbers() throws IOException, ClassNotFoundException {
		
		String[] s = new String[ 1000 ];
		int[] v = new int[ s.length ];
		for( int i = s.length; i-- != 0; ) s[ v[ i ] = i ] = binary( i );

		LcpMinimalPerfectMonotoneHash<String> mph = new LcpMinimalPerfectMonotoneHash<String>( Arrays.asList( s ), new Utf16TransformationStrategy() );
		
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );
		
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (LcpMinimalPerfectMonotoneHash<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

	
		mph = new LcpMinimalPerfectMonotoneHash<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ) ) );
		
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );
		
		temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( mph, temp );
		mph = (LcpMinimalPerfectMonotoneHash<String>)BinIO.loadObject( temp );
		for( int i = s.length; i-- != 0; ) assertEquals( i, mph.getLong( s[ i ] ) );

	}
	
	public void testThis() {
		String[] s = new String[] {
			"http://neighbourhood.statistics.gov.uk/dissemination/Info.do?page=ethTUS.htm",
			"http://neighbourhood.statistics.gov.uk/dissemination/Info.do?page=ethWERS.htm",
			"http://neighbourhood.statistics.gov.uk/dissemination/Info.do?page=ethYCS.htm",
			"http://neighbourhood.statistics.gov.uk/dissemination/Info.do?page=ethYLS.htm" 
		};
		LcpMinimalPerfectMonotoneHash<String> mph = new LcpMinimalPerfectMonotoneHash<String>( Arrays.asList( s ), new Utf16TransformationStrategy() );
	}
}
