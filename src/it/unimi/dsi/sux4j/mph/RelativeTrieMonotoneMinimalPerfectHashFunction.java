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

import java.io.File;
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
import com.martiansoftware.jsap.stringparsers.FileStringParser;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses 
 * a {@linkplain RelativeTrieDistributor relative trie} as a distributor.
 * 
 */

public class RelativeTrieMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( RelativeTrieMonotoneMinimalPerfectHashFunction.class );
	
	/** The number of elements. */
	private final int size;
	/** The size of a bucket. */
	private final int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	private final int log2BucketSize;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A hollow trie distributor assigning keys to buckets. */
	private final RelativeTrieDistributor<BitVector> distributor;
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
	 * elements and transformation strategy, using the default temporary directory. 
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public RelativeTrieMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) throws IOException {
		this( elements, transform, null );
	}
	
	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements, transformation strategy, and temporary directory. 
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 * @param tempDir a directory for the temporary files created during construction 
	 * by the {@link HollowTrieDistributor}, or <code>null</code> for the default temporary directory. 
	 */
	public RelativeTrieMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, File tempDir ) throws IOException {

		this.transform = transform;

		long maxLength = 0;
		long totalLength = 0;
		int c = 0;
		BitVector bv;
		for( T s: elements ) {
			bv = transform.toBitVector( s );
			maxLength = Math.max( maxLength, bv.length() );
			totalLength += bv.length();
			c++;
		}
		
		size = c;
		
		if ( size == 0 ) {
			bucketSize = log2BucketSize = 0;
			distributor = null;
			offset = null;
			return;
		}

		final long averageLength = ( totalLength + size - 1 ) / size;
		
		log2BucketSize = Fast.mostSignificantBit( 11 + 9 * Fast.ceilLog2( averageLength ) + Fast.ceilLog2( Fast.ceilLog2( averageLength ) ) );
		bucketSize = 1 << log2BucketSize;
		
		final Iterable<BitVector> bitVectors = TransformationStrategies.wrap( elements, transform );
		LOGGER.debug( "Average length: " + averageLength );
		LOGGER.debug( "Bucket size: " + bucketSize );
		
		distributor = new RelativeTrieDistributor<BitVector>( bitVectors, bucketSize, TransformationStrategies.identity(), tempDir );
		offset = new MWHCFunction<BitVector>( bitVectors, TransformationStrategies.identity(), new AbstractLongList() {
			public long getLong( int index ) {
				return index % bucketSize; 
			}
			public int size() {
				return size;
			}
		}, log2BucketSize );

		
		LOGGER.debug( "Actual bit cost per element: " + (double)numBits() / size );
		
	}

	public int size() {
		return size;
	}

	public long numBits() {
		return distributor.numBits() + offset.numBits() + transform.numBits();
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( RelativeTrieMonotoneMinimalPerfectHashFunction.class.getName(), "Builds an PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new FlaggedOption( "tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 't', "temp-dir", "A temporary directory for the files created during the construction." ),
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
		final File tempDir = jsapResult.getFile( "tempDir" );

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

		BinIO.storeObject( new RelativeTrieMonotoneMinimalPerfectHashFunction<CharSequence>( collection, transformationStrategy, tempDir ), functionName );
		LOGGER.info( "Completed." );
	}
}
