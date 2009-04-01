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
import it.unimi.dsi.sux4j.io.ChunkedHashStore;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Random;
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
import static it.unimi.dsi.sux4j.mph.HypergraphSorter.GAMMA;
import static it.unimi.dsi.bits.Fast.log2;
import static java.lang.Math.log;
import static java.lang.Math.E;

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses 
 * a {@linkplain ZFastTrieDistributor z-fast trie} as a distributor.
 * 
 */

public class ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( ZFastTrieDistributorMonotoneMinimalPerfectHashFunction.class );
	
	/** The number of elements. */
	private final int size;
	/** The logarithm of the bucket size. */
	private final int log2BucketSize;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A hollow trie distributor assigning keys to buckets. */
	private final ZFastTrieDistributor<BitVector> distributor;
	/** The offset of each element into his bucket. */
	private final MWHCFunction<BitVector> offset;
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( size == 0 ) return -1;
		final BitVector bv = transform.toBitVector( (T)o ).fast();
		final long bucket = distributor.getLong( bv );
		return ( bucket << log2BucketSize ) + offset.getLong( bv );
	}

	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements and transformation strategy. 
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public ZFastTrieDistributorMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) throws IOException {
		this( elements, transform, -1 );
	}
	
	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements, transformation strategy and bucket size. 
	 * 
	 * <p>This constructor is mainly for debugging and testing purposes.
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 * @param log2BucketSize the logarithm of the bucket size.
	 */
	public ZFastTrieDistributorMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final int log2BucketSize ) throws IOException {

		this.transform = transform;

		long maxLength = 0;
		long totalLength = 0;
		Random r = new Random();
		final ChunkedHashStore<BitVector> chunkedHashStore = new ChunkedHashStore<BitVector>( TransformationStrategies.identity() );
		chunkedHashStore.reset( r.nextLong() );
		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap( elements, transform );
		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayFreeMemory = true;
		pl.itemsName = "keys";
		pl.start( "Scanning collection..." );
		for( BitVector bv: bitVectors ) {
			maxLength = Math.max( maxLength, bv.length() );
			totalLength += bv.length();
			chunkedHashStore.add( bv );
			pl.lightUpdate();
		}
		
		pl.done();
		
		chunkedHashStore.checkAndRetry( bitVectors );
		size = chunkedHashStore.size();
		
		if ( size == 0 ) {
			this.log2BucketSize = -1;
			distributor = null;
			offset = null;
			return;
		}

		final long averageLength = ( totalLength + size - 1 ) / size;

		final long forecastBucketSize = (long)Math.ceil( 10.5 + 4.05 * log( averageLength ) + 2.43 * log( log ( size ) + 1 ) + 2.43 * log( log( averageLength ) + 1 ) );
		this.log2BucketSize = log2BucketSize == -1 ? Fast.mostSignificantBit( forecastBucketSize ) : log2BucketSize;
		
		LOGGER.debug( "Average length: " + averageLength );
		LOGGER.debug( "Max length: " + maxLength );
		LOGGER.debug( "Bucket size: " + ( 1L << this.log2BucketSize ) );
		LOGGER.info( "Computing z-fast trie distributor..." );
		distributor = new ZFastTrieDistributor<BitVector>( bitVectors, this.log2BucketSize, TransformationStrategies.identity(), chunkedHashStore );

		LOGGER.info( "Computing offsets..." );
		offset = new MWHCFunction<BitVector>( null, TransformationStrategies.identity(), new AbstractLongList() {
			final long bucketSizeMask = ( 1L << ZFastTrieDistributorMonotoneMinimalPerfectHashFunction.this.log2BucketSize ) - 1; 
			public long getLong( int index ) {
				return index & bucketSizeMask; 
			}
			public int size() {
				return size;
			}
		}, this.log2BucketSize, chunkedHashStore );
		//System.err.println( "*********" + chunkedHashStore.seed() );
		
		chunkedHashStore.close();
		
		double logU = averageLength * log( 2 );
		LOGGER.info( "Forecast bit cost per element: "
				+ 1.0 / forecastBucketSize
				* ( -6 * log2( log( 2 ) ) + 5 * log2( logU ) + 2 * log2( forecastBucketSize ) + 
						log2( log( logU ) - log( log( 2 ) ) ) + 6 * GAMMA + 3 * log2 ( E ) + 3 * log2( log( 3.0 * size ) ) 
						+ 3 + GAMMA * forecastBucketSize + GAMMA * forecastBucketSize * log2( forecastBucketSize ) ) ); 
		
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / size );
		
	}

	public int size() {
		return size;
	}

	public long numBits() {
		if ( size == 0 ) return 0;
		return distributor.numBits() + offset.numBits() + transform.numBits();
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( ZFastTrieDistributorMonotoneMinimalPerfectHashFunction.class.getName(), "Builds an PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new FlaggedOption( "log2bucket", JSAP.INTEGER_PARSER, "-1", JSAP.NOT_REQUIRED, 'b', "log2bucket", "The base 2 logarithm of the bucket size (mainly for testing)." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final int log2BucketSize = jsapResult.getInt( "log2bucket" );
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

		BinIO.storeObject( new ZFastTrieDistributorMonotoneMinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy, log2BucketSize ), functionName );
		LOGGER.info( "Completed." );
	}
}
