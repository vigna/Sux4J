package it.unimi.dsi.sux4j.io;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import junit.framework.TestCase;

public class FileLinesListTest extends TestCase {

	public void test() throws IOException {
		final File t = File.createTempFile( FileLinesListTest.class.getName(), "tmp" );
		t.deleteOnExit();
		
		FileWriter fw = new FileWriter( t );
		fw.write( "\naa\naaaa\n\naa\n".toCharArray() );
		fw.close();
		
		FileLinesList fll = new FileLinesList( t.toString(), "ASCII" );
		assertEquals( "", fll.get( 0 ).toString() );
		assertEquals( "aa", fll.get( 1 ).toString() );
		assertEquals( "aaaa", fll.get( 2 ).toString() );
		assertEquals( "", fll.get( 3 ).toString() );
		assertEquals( "aa", fll.get( 4 ).toString() );
		
		fw = new FileWriter( t );
		fw.write( "\n\n\n".toCharArray() );
		fw.close();
		
		fll = new FileLinesList( t.toString(), "ASCII" );
		assertEquals( "", fll.get( 0 ).toString() );
		assertEquals( "", fll.get( 1 ).toString() );
		assertEquals( "", fll.get( 2 ).toString() );
		
		fw = new FileWriter( t );
		fw.write( "\n\na".toCharArray() );
		fw.close();
		
		fll = new FileLinesList( t.toString(), "ASCII" );
		assertEquals( "", fll.get( 0 ).toString() );
		assertEquals( "", fll.get( 1 ).toString() );
		assertEquals( "a", fll.get( 2 ).toString() );
		
		/*fw = new FileWriter( t );
		fw.write( "".toCharArray() );
		fw.close();
		
		fll = new FileLinesList( t.toString(), "ASCII" );
		assertEquals( "", fll.get( 0 ).toString() );
		*/
	}
}
