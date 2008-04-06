package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongList;

import java.io.IOException;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;

public class ListSpeedTest {

	public static void main( final String[] arg ) throws IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP( ListSpeedTest.class.getName(), "Test the speed of a list",
				new Parameter[] {
					new Switch( "random", 'r', "random", "Do a random test on at most 1 million strings." ),
					new UnflaggedOption( "list", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised list." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final String listName = jsapResult.getString( "list" );
		
		@SuppressWarnings("unchecked")
		final LongList list = (LongList)BinIO.loadObject( listName );
		long total = 0;
		int n = list.size();
		for( int k = 13; k-- != 0; ) {
			long time = -System.currentTimeMillis();
			for( int i = 0; i < n; i++ ) {
				list.getLong( i );
				if ( i++ % 100000 == 0 ) System.out.print('.');
			}
			System.out.println();
			time += System.currentTimeMillis();
			if ( k < 10 ) total += time;
			System.out.println( time / 1E3 + "s, " + ( time * 1E3 ) / n + " \u00b5s/item" );
		}
		System.out.println( "Average: " + Util.format( total / 10E3 ) + "s, " + Util.format( ( total * 1E3 ) / ( 10 * n ) ) + " \u00b5s/item" );
	}
}
