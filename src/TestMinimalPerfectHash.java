import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.mph.GOVMinimalPerfectHashFunction;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

public class TestMinimalPerfectHash {

	
	public static void main( String a[] ) throws IOException, ClassNotFoundException {
		@SuppressWarnings("rawtypes")
		GOVMinimalPerfectHashFunction<?> mph = (GOVMinimalPerfectHashFunction)BinIO.loadObject( a[ 0 ] );
		Collection<MutableString> c = new FileLinesCollection( a[ 1 ], "UTF-8" ).allLines();
		
		long start;
		
		for( int k = 10; k-- != 0; ) {
			start = -System.currentTimeMillis();
			
			Iterator<MutableString> s = c.iterator();
			for( long i = mph.size64(); i-- != 0; ) mph.getLong( s.next() ); 
			
			start += System.currentTimeMillis();
			
			System.err.println( "Elapsed: " + start + ", " + 1000.0 * start / mph.size64() + " \u00b5s/calls" );
		}
	}
}
