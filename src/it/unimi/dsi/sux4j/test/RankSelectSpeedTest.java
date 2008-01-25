package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank9;
import it.unimi.dsi.sux4j.bits.Select9;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.sux4j.mph.HollowTrie;

import java.io.IOException;
import java.util.Random;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class RankSelectSpeedTest {

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException, ClassNotFoundException {

		final SimpleJSAP jsap = new SimpleJSAP( HollowTrie.class.getName(), "Builds a hollow trie reading a newline-separated list of terms.",
				new Parameter[] {
					new UnflaggedOption( "numBits", JSAP.LONGSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of bits." ),
					new UnflaggedOption( "density", JSAP.DOUBLE_PARSER, ".5", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The density." ),
					//new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					//new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					//new FlaggedOption( "termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read terms from this file (without loading them into core memory) instead of standard input." ),
					//new UnflaggedOption( "trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final long numBits = jsapResult.getLong( "numBits" );
		final double density = jsapResult.getDouble( "density" );

		Random random = new Random( 42 );
		final LongArrayBitVector bitVector = LongArrayBitVector.getInstance( numBits ).length( numBits );
		long c = 0;
		for( long i = numBits; i-- != 0; ) 
			if ( random.nextDouble() < density ) {
				bitVector.set( i );
				c++;
			}
		
		final int numPos = 10000000;
		final long[] rankPosition = new long[ numPos ];
		final long[] selectPosition = new long[ numPos ];
		
		for( int i = numPos; i-- != 0; ) {
			rankPosition[ i ] = ( random.nextLong() & 0x7FFFFFFFFFFFFFFFL ) % numBits;
			selectPosition[ i ] = ( random.nextLong() & 0x7FFFFFFFFFFFFFFFL ) % c;
		}

		long time;
		for( int k = 10; k-- != 0; ) {
			Rank9 rank9 = new Rank9( bitVector );
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) rank9.rank( rankPosition[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1E6 + "s, " + ( time * 1E6 ) / numPos + " ns/rank" );

			Select9 select9 = new Select9( rank9 );
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) select9.select( selectPosition[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1E6 + "s, " + ( time * 1E6 ) / numPos + " ns/select" );

			SimpleSelect simpleSelect = new SimpleSelect( bitVector );
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) simpleSelect.select( selectPosition[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1E6 + "s, " + ( time * 1E6 ) / numPos + " ns/select" );
		}
	}
}
