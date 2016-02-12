package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2016 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

import static it.unimi.dsi.bits.Fast.log2;
import static java.lang.Math.E;
import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.ints.IntBigArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.io.OfflineIterable;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * longest common prefixes as distributors.
 * 
 * <p>See the {@linkplain it.unimi.dsi.sux4j.mph package overview} for a comparison with other implementations.
 * Similarly to a {@link GOV3Function}, an instance of this class may be <em>{@linkplain Builder#signed(int) signed}</em>.
 */

public class LcpMonotoneMinimalPerfectHashFunction<T> extends AbstractHashFunction<T> implements Size64, Serializable {
    public static final long serialVersionUID = 4L;
	private static final Logger LOGGER = LoggerFactory.getLogger( LcpMonotoneMinimalPerfectHashFunction.class );
	private static final boolean DEBUG = false;
	private static final boolean ASSERTS = false;
	
	/** The number of keys. */
	protected final long n;
	/** The size of a bucket. */
	protected final int bucketSize;
	/** {@link Fast#ceilLog2(int)} of {@link #bucketSize}. */
	protected final int log2BucketSize;
	/** The mask for {@link #log2BucketSize} bits. */
	protected final int bucketSizeMask;
	/** A function mapping each key to the offset inside its bucket (lowest {@link #log2BucketSize} bits) and
	 * to the length of the longest common prefix of its bucket (remaining bits). */
	protected final GOV3Function<BitVector> offsetLcpLength;
	/** A function mapping each longest common prefix to its bucket. */
	protected final GOV3Function<BitVector> lcp2Bucket;
	/** The transformation strategy. */
	protected final TransformationStrategy<? super T> transform;
	/** The seed returned by the {@link ChunkedHashStore}. */
	protected final long seed;
	/** The mask to compare signatures, or zero for no signatures. */
	protected final long signatureMask;
	/** The signatures. */
	protected final LongBigList signatures; 
	
	/** A builder class for {@link LcpMonotoneMinimalPerfectHashFunction}. */
	public static class Builder<T> {
		protected Iterable<? extends T> keys;
		protected TransformationStrategy<? super T> transform;
		protected long numKeys = -1;
		protected int signatureWidth;
		protected File tempDir;
		/** Whether {@link #build()} has already been called. */
		protected boolean built;
		
		/** Specifies the keys to hash.
		 * 
		 * @param keys the keys to hash.
		 * @return this builder.
		 */
		public Builder<T> keys( final Iterable<? extends T> keys ) {
			this.keys = keys;
			return this;
		}
		
		/** Specifies the number of keys.
		 * 
		 * <p>The argument must be equal to the number of keys returned by an iterator
		 * generated by {@link #keys(Iterable) the set of keys}. Without this information,
		 * a first scan of the key set will be necessary to compute its cardinality,
		 * unless the set of keys implements {@link Size64} or {@link Collection}.
		 * 
		 * @param numKeys the keys to hash.
		 * @return this builder.
		 */
		public Builder<T> numKeys( final long numKeys ) {
			this.numKeys = numKeys;
			return this;
		}
		
		/** Specifies the transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 * 
		 * @param transform a transformation strategy for the {@linkplain #keys(Iterable) keys to hash}.
		 * @return this builder.
		 */
		public Builder<T> transform( final TransformationStrategy<? super T> transform ) {
			this.transform = transform;
			return this;
		}
		
		/** Specifies that the resulting {@link LcpMonotoneMinimalPerfectHashFunction} should be signed using a given number of bits per key.
		 * 
		 * @param signatureWidth a signature width, or 0 for no signature.
		 * @return this builder.
		 */
		public Builder<T> signed( final int signatureWidth ) {
			this.signatureWidth = signatureWidth;
			return this;
		}
		
		/** Specifies a temporary directory for the {@link ChunkedHashStore}.
		 * 
		 * @param tempDir a temporary directory for the {@link ChunkedHashStore}. files, or {@code null} for the standard temporary directory.
		 * @return this builder.
		 */
		public Builder<T> tempDir( final File tempDir ) {
			this.tempDir = tempDir;
			return this;
		}
		
		/** Builds an LCP monotone minimal perfect hash function.
		 * 
		 * @return an {@link LcpMonotoneMinimalPerfectHashFunction} instance with the specified parameters.
		 * @throws IllegalStateException if called more than once.
		 */
		public LcpMonotoneMinimalPerfectHashFunction<T> build() throws IOException {
			if ( built ) throw new IllegalStateException( "This builder has been already used" );
			built = true;
			return new LcpMonotoneMinimalPerfectHashFunction<T>( keys, numKeys, transform, signatureWidth, tempDir );
		}
	}

	/**
	 * Creates a new LCP monotone minimal perfect hash function for the given keys.
	 * 
	 * @param keys the keys to hash.
	 * @param numKeys the number of keys, or -1 if the number of keys is not known (will be computed).
	 * @param transform a transformation strategy for the keys.
	 * @param signatureWidth a signature width, or 0 for no signature.
	 * @param tempDir a temporary directory for the store files, or {@code null} for the standard temporary directory.
	 */
	@SuppressWarnings("unused")
	protected LcpMonotoneMinimalPerfectHashFunction( final Iterable<? extends T> keys, final long numKeys, final TransformationStrategy<? super T> transform, final int signatureWidth, final File tempDir ) throws IOException {
		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		this.transform = transform;

		if ( numKeys == -1 ) {
			if ( keys instanceof Size64 ) n = ((Size64)keys).size64();
			else if ( keys instanceof Collection ) n = ((Collection<?>)keys).size();
			else {
				long c = 0;
				for( T dummy: keys ) c++;
				n = c;
			}
		}
		else n = numKeys;
		
		defRetValue = -1; // For the very few cases in which we can decide

		if ( n == 0 ) {
			bucketSize = bucketSizeMask = log2BucketSize = 0;
			lcp2Bucket = null;
			offsetLcpLength = null;
			seed = signatureMask = 0;
			signatures = null;
			return;
		}
		
		final int theoreticalBucketSize = (int)Math.ceil( 1 + GOV3Function.C * Math.log( 2 ) + Math.log( n ) - Math.log( 1 + Math.log( n ) ) );
		log2BucketSize = Fast.ceilLog2( theoreticalBucketSize );
		bucketSize = 1 << log2BucketSize;
		bucketSizeMask = bucketSize - 1;
		LOGGER.debug( "Bucket size: " + bucketSize );

		final long numBuckets = ( n + bucketSize - 1 ) / bucketSize;
		
		LongArrayBitVector prev = LongArrayBitVector.getInstance();
		LongArrayBitVector curr = LongArrayBitVector.getInstance();
		int currLcp = 0;
		@SuppressWarnings("resource")
		final OfflineIterable<BitVector, LongArrayBitVector> lcps = new OfflineIterable<BitVector, LongArrayBitVector>( BitVectors.OFFLINE_SERIALIZER, LongArrayBitVector.getInstance() );
		final int[][] lcpLengths = IntBigArrays.newBigArray( numBuckets );
		int maxLcp = 0;
		long maxLength = 0;

		pl.expectedUpdates = n;
		
		@SuppressWarnings("resource")
		final ChunkedHashStore<BitVector> chunkedHashStore = new ChunkedHashStore<BitVector>( TransformationStrategies.identity(), tempDir, pl );
		chunkedHashStore.reset( Util.randomSeed() );

		pl.start( "Scanning collection..." );
		
		final Iterator<? extends T> iterator = keys.iterator();
		for( long b = 0; b < numBuckets; b++ ) {
			prev.replace( transform.toBitVector( iterator.next() ) );
			chunkedHashStore.add( prev );
			pl.lightUpdate();
			maxLength = Math.max( maxLength, prev.length() );
			currLcp = (int)prev.length();
			final int currBucketSize = (int)Math.min( bucketSize, n - b * bucketSize );
			
			for( int i = 0; i < currBucketSize - 1; i++ ) {
				curr.replace( transform.toBitVector( iterator.next() ) );
				chunkedHashStore.add( curr );
				pl.lightUpdate();
				final int prefix = (int)curr.longestCommonPrefixLength( prev );
				if ( prefix == prev.length() && prefix == curr.length()  ) throw new IllegalArgumentException( "The input bit vectors are not distinct@" + ( b * bucketSize + i ) + " (\"" + curr + "\" = \"" + prev + "\")" );
				if ( prefix == prev.length() || prefix == curr.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free@" + ( b * bucketSize + i ) + " (\"" + curr + "\" is a prefix or a suffix of \"" + prev + "\")" );
				if ( prev.getBoolean( prefix ) ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted @" + ( b * bucketSize + i ) + " (\"" + curr + "\" < \"" + prev + "\")" );
				
				currLcp = Math.min( prefix, currLcp );
				prev.replace( curr );
				
				maxLength = Math.max( maxLength, prev.length() );
			}

			lcps.add( prev.subVector( 0, currLcp  ) );
			IntBigArrays.set( lcpLengths, b, currLcp );
			maxLcp = Math.max( maxLcp, currLcp );
		}
		
		pl.done();
		
		if ( ASSERTS ) {
			ObjectOpenHashSet<BitVector> s = new ObjectOpenHashSet<BitVector>();
			for( LongArrayBitVector bv: lcps ) s.add( bv.copy() );
			assert s.size() == lcps.size() : s.size() + " != " + lcps.size(); // No duplicates.
		}

		LOGGER.info( "Generating the map from keys to LCP lengths and offsets..." );
		// Build function assigning the lcp length and the bucketing data to each element.
		offsetLcpLength = new GOV3Function.Builder<BitVector>().keys( TransformationStrategies.wrap( keys, transform ) ).transform( TransformationStrategies.identity() ).store( chunkedHashStore ).values( new AbstractLongBigList() {
			public long getLong( long index ) {
				return IntBigArrays.get( lcpLengths, index >>> log2BucketSize ) << log2BucketSize | index & bucketSizeMask; 
			}
			public long size64() {
				return n;
			}
		}, log2BucketSize + Fast.length( maxLcp ) ).indirect().build();
		
		LOGGER.info( "Generating the map from LCPs to buckets..." );
		// Build function assigning each lcp to its bucket.
		lcp2Bucket = new GOV3Function.Builder<BitVector>().keys( lcps ).transform( TransformationStrategies.identity() ).tempDir( tempDir ).build();

		if ( DEBUG ) {
			int p = 0;
			for( BitVector v: lcps ) System.err.println( v  + " " + v.length() );
			for( BitVector v: lcps ) {
				final long value = lcp2Bucket.getLong( v );
				if ( p++ != value ) {
					System.err.println( "p: " + (p-1) + "  value: " + value + " key:" + v );
					throw new AssertionError();
				}
			}
		}

		lcps.close();
		this.seed = chunkedHashStore.seed();
		
		if ( DEBUG ) {
			int p = 0;
			for( T key: keys ) {
				final long value = offsetLcpLength.getLong( key );
				if ( p++ != lcp2Bucket.getLong( transform.toBitVector( key ).subVector( 0, value >>> log2BucketSize ) ) * bucketSize + ( value & bucketSizeMask ) ) {
					System.err.println( "p: " + ( p - 1 ) 
							+ "  Key: " + key 
							+ " bucket size: " + bucketSize 
							+ " lcp " + transform.toBitVector( key ).subVector( 0, value >>> log2BucketSize ) 
							+ " lcp length: " + ( value >>> log2BucketSize ) 
							+ " bucket " + lcp2Bucket.getLong( transform.toBitVector( key ).subVector( 0, value >>> log2BucketSize ) ) 
							+ " offset: " + ( value & bucketSizeMask ) );
					throw new AssertionError();
				}
			}
		}
		
		LOGGER.debug( "Forecast bit cost per element: " + ( log2( E ) + GOV3Function.C - log2( log2( E ) ) + log2( 1 + log2( n ) ) + log2( maxLength - log2( 1 + log2( n ) ) ) ) ); 
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );

		if ( signatureWidth != 0 ) {
			signatureMask = -1L >>> Long.SIZE - signatureWidth;
			signatures = chunkedHashStore.signatures( signatureWidth, pl );
		}
		else {
			signatureMask = 0;
			signatures = null;
		}

		chunkedHashStore.close();
	}

	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( n == 0 ) return defRetValue;
		final BitVector bitVector = transform.toBitVector( (T)o );
		final long[] triple = new long[ 3 ];
		Hashes.spooky4( bitVector, seed, triple );
		final long value = offsetLcpLength.getLongByTriple( triple );
		final long prefix = value >>> log2BucketSize; 
		if ( prefix > bitVector.length() ) return defRetValue;
		final long result = ( lcp2Bucket.getLong( bitVector.subVector( 0, prefix ) ) << log2BucketSize ) + ( value & bucketSizeMask );
		if ( signatureMask != 0 ) return result < 0 || result >= n || signatures.getLong( result ) != ( triple[ 0 ] & signatureMask ) ? defRetValue : result;
		// Out-of-set strings can generate bizarre 3-hyperedges.
		return result < 0 || result >= n ? defRetValue : result;
	}

	public long size64() {
		return n;
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return offsetLcpLength.numBits() + lcp2Bucket.numBits() + transform.numBits();
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( LcpMonotoneMinimalPerfectHashFunction.class.getName(), "Builds an LCP-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new FlaggedOption( "tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
			new Switch( "utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)." ),
			new FlaggedOption( "signatureWidth", JSAP.INTEGER_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 's', "signature-width", "If specified, the signature width in bits; if negative, the generated function will be a dictionary." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final File tempDir = jsapResult.getFile( "tempDir" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean utf32 = jsapResult.getBoolean( "utf32" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );
		final int signatureWidth = jsapResult.getInt( "signatureWidth", 0 ); 

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.displayLocalSpeed = true;
			pl.displayFreeMemory = true;
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final TransformationStrategy<CharSequence> transformationStrategy = huTucker 
			? new HuTuckerTransformationStrategy( collection, true )
			: iso
				? TransformationStrategies.prefixFreeIso() 
				: utf32
					? TransformationStrategies.prefixFreeUtf32()
					: TransformationStrategies.prefixFreeUtf16();
		BinIO.storeObject( new LcpMonotoneMinimalPerfectHashFunction<CharSequence>( collection, -1, transformationStrategy, signatureWidth, tempDir ), functionName );
		LOGGER.info( "Completed." );
	}
}
