package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.sux4j.mph.HollowTrie;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Iterator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

public class FunctionSpeedTest {

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP( HollowTrie.class.getName(), "Builds a hollow trie reading a newline-separated list of terms.",
				new Parameter[] {
					new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read terms." ),
					new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					new FlaggedOption( "termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, 'o', "offline", "Read terms from this file (without loading them into core memory) instead of standard input." ),
					new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised function." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final String functionName = jsapResult.getString( "function" );
		final String termFile = jsapResult.getString( "termFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		
		@SuppressWarnings("unchecked")
		final Object2LongFunction<? extends CharSequence> function = (Object2LongFunction<? extends CharSequence>)BinIO.loadObject( functionName );
		final FileLinesCollection flc = new FileLinesCollection( termFile, encoding.name(), zipped );

		for( int k = 10; k-- != 0; ) {
			final Iterator<? extends CharSequence> i = flc.iterator();
			
			long time = -System.currentTimeMillis();
			int j = 0;
			while( i.hasNext() ) {
				function.getLong( i.next() );
				if ( j++ % 10000 == 0 ) System.err.print('.');
			}
			System.err.println();
			time += System.currentTimeMillis();
			System.err.println( time / 1E3 + "s, " + ( time * 1E6 ) / j + " ns/item" );
		}
	}
}
