package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.zip.GZIPInputStream;

import org.apache.log4j.Logger;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses 
 * a {@linkplain BitstreamImmutablePaCoTrie partial compacted binary trie (PaCo trie)} as distributor.
 */

public class PacoMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( PacoMonotoneMinimalPerfectHashFunction.class );
	
	/** The number of elements. */
	private final int n;
	/** The size of a bucket. */
	private final int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	private final int log2BucketSize;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A PaCo trie assigning keys to buckets. */
	private final BitstreamImmutablePaCoTrie<BitVector> distributor;
	/** The offset of each element into his bucket. */
	private final MWHCFunction<BitVector> offset;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final BitVector bv = transform.toBitVector( (T)o ).fast();
		final long bucket = distributor.getLong( bv );
		return ( bucket << log2BucketSize ) + offset.getLong( bv );
	}

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public PacoMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) throws IOException {

		this.transform = transform;

		long maxLength = 0;
		long totalLength = 0;
		int c = 0;
		BitVector bv;
		for( T s: iterable ) {
			bv = transform.toBitVector( s );
			maxLength = Math.max( maxLength, bv.length() );
			totalLength += bv.length();
			c++;
		}
		
		n = c;
		
		if ( n == 0 )	{
			bucketSize = log2BucketSize = 0;
			distributor = null;
			offset = null;
			return;
		}

		final long averageLength = ( totalLength + n - 1 ) / n;
		
		int t = Fast.mostSignificantBit( (int)Math.floor( averageLength - Math.log( n ) - Math.log( averageLength - Math.log( n ) ) - 1 ) );
		final int firstbucketSize = 1 << t;
		LOGGER.debug( "First bucket size estimate: " +  firstbucketSize );
		
		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap(  iterable, transform );
		
		BitstreamImmutablePaCoTrie<BitVector> firstDistributor = new BitstreamImmutablePaCoTrie<BitVector>( bitVectors, firstbucketSize, TransformationStrategies.identity() );

		// Reassign bucket size based on empirical estimation
		log2BucketSize = t - Fast.mostSignificantBit( (int)Math.ceil( n / ( firstDistributor.numBits() * Math.log( 2 ) ) ) );
		bucketSize = 1 << log2BucketSize;
		LOGGER.debug( "Second bucket size estimate: " + bucketSize );

		if ( firstbucketSize == bucketSize ) distributor = firstDistributor;
		else {
			firstDistributor = null;
			distributor = new BitstreamImmutablePaCoTrie<BitVector>( bitVectors, bucketSize, TransformationStrategies.identity() );
		}

		/*
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize / 4, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize - 2 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize / 2, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize - 1 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize * 2, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize + 1 ) );
		System.err.println( new BitStreamImmutableBinaryTrie<BitVector>( vectors, bucketSize * 4, TransformationStrategies.identity() ).numBits() / (double)n + ( HypergraphSorter.GAMMA + log2BucketSize + 2 ) );
		*/	
		
		offset = new MWHCFunction<BitVector>( TransformationStrategies.wrap( iterable, transform ), TransformationStrategies.identity(), new AbstractLongList() {
			public long getLong( int index ) {
				return index % bucketSize; 
			}
			public int size() {
				return n;
			}
		}, log2BucketSize );

		
		LOGGER.debug( "Bucket size: " + bucketSize );
		LOGGER.debug( "Forecast distributor bit cost: " + ( n / bucketSize ) * ( maxLength + log2BucketSize - Math.log( n ) ) );
		LOGGER.debug( "Actual distributor bit cost: " + distributor.numBits() );
		LOGGER.debug( "Forecast bit cost per element: " + ( HypergraphSorter.GAMMA + Fast.log2( Math.E ) + 2 * Fast.log2( maxLength - Fast.log2( n ) ) ) );
		LOGGER.debug( "Actual bit cost per element: " + (double)numBits() / n );
		
	}


	public int size() {
		return n;
	}

	public long numBits() {
		return distributor.numBits() + offset.numBits() + transform.numBits();
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( PacoMonotoneMinimalPerfectHashFunction.class.getName(), "Builds an PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final TransformationStrategy<CharSequence> transformationStrategy = huTucker 
			? new HuTuckerTransformationStrategy( collection, true )
			: iso
				? TransformationStrategies.prefixFreeIso() 
				: TransformationStrategies.prefixFreeUtf16();

		BinIO.storeObject( new PacoMonotoneMinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy ), functionName );
		LOGGER.info( "Completed." );
	}
}
