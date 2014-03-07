package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.sux4j.scratch.EliasFanoMonotoneLongBigListTables;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import org.apache.commons.math3.random.RandomGenerator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class EliasFanoMonotoneBigListSpeedTest {

	public static void main( final String[] arg ) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( EliasFanoMonotoneBigListSpeedTest.class.getName(), "Tests the speed of rank/select implementations.",
				new Parameter[] {
					new UnflaggedOption( "numElements", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of elements." ),
					new UnflaggedOption( "density", JSAP.DOUBLE_PARSER, ".5", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The density." ),
					new FlaggedOption( "numPos", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, 'p', "positions", "The number of positions to test" ),
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final int numElements = jsapResult.getInt( "numElements" );
		final double density = jsapResult.getDouble( "density" );
		final int numPos = jsapResult.getInt( "numPos" );

		RandomGenerator random = new XorShift1024StarRandomGenerator( 42 );
		final IntArrayList list = new IntArrayList( numElements );
		for( long i = numElements; i-- != 0; ) list.add( random.nextDouble() < density ? 0 : 100 );
		
		final int[] position = new int[ numPos ];
		
		for( int i = numPos; i-- != 0; ) position[ i ] = ( random.nextInt() & 0x7FFFFFFF ) % numElements;
		final int[] elements = list.elements();
		for( int i = 1; i < list.size(); i++ ) elements[ i ] += elements[ i - 1 ];
		EliasFanoMonotoneLongBigListTables eliasFanoMonotoneLongBigList = new EliasFanoMonotoneLongBigListTables( list );
		long time;
		for( int k = 10; k-- != 0; ) {
			time = - System.currentTimeMillis();
			for( int i = 0; i < numPos; i++ ) eliasFanoMonotoneLongBigList.getLong( position[ i ] );
			time += System.currentTimeMillis();
			System.err.println( time / 1000.0 + "s, " + ( time * 1E6 ) / numPos + " ns/get" );
		}
	}
}
