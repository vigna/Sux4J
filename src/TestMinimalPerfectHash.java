import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.mg4j.io.FileLinesCollection;
import it.unimi.dsi.mg4j.util.MutableString;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHash;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class TestMinimalPerfectHash {

	
	@SuppressWarnings("unchecked")
	public static void main( String a[] ) throws IOException, ClassNotFoundException {
		MinimalPerfectHash mph = (MinimalPerfectHash)BinIO.loadObject( a[ 0 ] );
		Collection<MutableString> c = new FileLinesCollection( a[ 1 ], "UTF-8" ).allLines();
		
		long start;
		
		for( int k = 10; k-- != 0; ) {
			start = -System.currentTimeMillis();
			
			Iterator<MutableString> s = c.iterator();
			for( int i = mph.size(); i-- != 0; ) mph.getLong( s.next() ); 
			
			start += System.currentTimeMillis();
			
			System.err.println( "Elapsed: " + start + ", " + 1000.0 * start / mph.size() + " \u00b5s/calls" );
		}
	}
}
