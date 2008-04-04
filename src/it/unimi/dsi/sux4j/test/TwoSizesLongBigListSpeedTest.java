package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.sux4j.util.TwoSizesLongBigList;

import java.util.Random;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class TwoSizesLongBigListSpeedTest {

	public static void main( final String[] arg ) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( TwoSizesLongBigListSpeedTest.class.getName(), "Tests the speed of rank/select implementations.",
				new Parameter[] {
					new UnflaggedOption( "numElements", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of elements." ),
					new UnflaggedOption( "density", JSAP.DOUBLE_PARSER, ".5", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The density." ),
					new FlaggedOption( "numPos", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, 'p', "positions", "The number of positions to test" ),
					//new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					//new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					//new FlaggedOption( "termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read terms from this file (without loading them into core memory) instead of standard input." ),
					//new UnflaggedOption( "trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final int numElements = jsapResult.getInt( "numElements" );
		final double density = jsapResult.getDouble( "density" );
		final int numPos = jsapResult.getInt( "numPos" );

		Random random = new Random( 42 );
		final IntArrayList list = new IntArrayList( numElements );
		for( long i = numElements; i-- != 0; ) list.add( random.nextDouble() < density ? 0 : 100 );
		
		final int[] position = new int[ numPos ];
		
		for( int i = numPos; i-- != 0; ) position[ i ] = ( random.nextInt() & 0x7FFFFFFF ) % numElements;
		TwoSizesLongBigList twoSizes = new TwoSizesLongBigList( list );
		EliasFanoLongBigList eliasFano = new EliasFanoLongBigList( list );
		final int[] elements = list.elements();
		for( int i = 1; i < list.size(); i++ ) elements[ i ] += elements[ i - 1 ];
		EliasFanoMonotoneLongBigList monotone = new EliasFanoMonotoneLongBigList( list );
		long time;
		for( int k = 10; k-- != 0; ) {
			System.out.println( "=== IntArrayList ===");
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) list.getInt( position[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1000.0 + "s, " + ( time * 1E6 ) / numPos + " ns/get" );

			System.out.println( "=== TwoSizesLongBigList ===");
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) twoSizes.getLong( position[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1000.0 + "s, " + ( time * 1E6 ) / numPos + " ns/get" );

			System.out.println( "=== EliasFanoLongBigList ===");
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) eliasFano.getLong( position[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1000.0 + "s, " + ( time * 1E6 ) / numPos + " ns/get" );


			System.out.println( "=== EliasFanoMonotoneLongBigList ===");
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) monotone.getLong( position[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1000.0 + "s, " + ( time * 1E6 ) / numPos + " ns/get" );

		}
	}
}
