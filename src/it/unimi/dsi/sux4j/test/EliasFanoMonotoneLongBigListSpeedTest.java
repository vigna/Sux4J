package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import org.apache.commons.math3.random.RandomGenerator;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

public class EliasFanoMonotoneLongBigListSpeedTest {

	public static void main( final String[] arg ) throws JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( EliasFanoMonotoneLongBigListSpeedTest.class.getName(), "Tests the speed Elias-Fano monotone lists.",
				new Parameter[] {
					new UnflaggedOption( "numElements", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The number of elements." ),
					new UnflaggedOption( "density", JSAP.DOUBLE_PARSER, ".5", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The density." ),
					new FlaggedOption( "numPos", JSAP.INTSIZE_PARSER, "1Mi", JSAP.NOT_REQUIRED, 'p', "positions", "The number of positions to test" ),
					new FlaggedOption( "bulk", JSAP.INTSIZE_PARSER, "10", JSAP.NOT_REQUIRED, 'b', "bulk", "The number of positions to read with the bulk method" ),
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final int numElements = jsapResult.getInt( "numElements" );
		final double density = jsapResult.getDouble( "density" );
		final int numPos = jsapResult.getInt( "numPos" );
		final int bulk = jsapResult.getInt( "bulk" );

		RandomGenerator random = new XorShift1024StarRandomGenerator( 42 );
		final IntArrayList list = new IntArrayList( numElements );
		for( long i = numElements; i-- != 0; ) list.add( random.nextDouble() < density ? 0 : 100 );
		
		final int[] position = new int[ numPos ];
		
		for( int i = numPos; i-- != 0; ) position[ i ] = ( random.nextInt() & 0x7FFFFFFF ) % ( numElements - bulk );
		final long[] elements = new long[ list.size() ];
		elements[ 0 ] = list.getInt( 0 );
		for( int i = 1; i < list.size(); i++ ) elements[ i ] = list.getInt( i ) + elements[ i - 1 ];
		EliasFanoMonotoneLongBigList eliasFanoMonotoneLongBigList = new EliasFanoMonotoneLongBigList( LongArrayList.wrap( elements ) );
		long time;
		System.err.println( "getLong():" );
		for( int k = 10; k-- != 0; ) {
			time = - System.nanoTime();
			for( int i = 0; i < numPos; i++ ) eliasFanoMonotoneLongBigList.getLong( position[ i ] );
			time += System.nanoTime();
			System.err.println( time / 1E9 + "s, " + time / (double)numPos + " ns/element" );
		}

		final long[] dest = new long[ bulk ];
		System.err.println( "get():" );
		for( int k = 10; k-- != 0; ) {
			time = - System.nanoTime();
			for( int i = 0; i < numPos; i++ ) eliasFanoMonotoneLongBigList.get( position[ i ], dest );
			time += System.nanoTime();
			System.err.println( time / 1E9 + "s, " + time / (double)( numPos * bulk ) + " ns/element" );
		}
	}
}
