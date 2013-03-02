package it.unimi.dsi.sux4j.scratch;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2013 Sebastiano Vigna 
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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.fastutil.longs.LongBigArrays;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;
import it.unimi.dsi.sux4j.mph.AbstractHashFunction;
import it.unimi.dsi.sux4j.mph.Hashes;
import it.unimi.dsi.sux4j.mph.HypergraphSorter;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.sux4j.util.EliasFanoMonotoneLongBigList;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
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

/**
 * A minimal perfect hash function.
 * 
 * <P>Given a list of elements without duplicates, the constructors of this class finds a minimal
 * perfect hash function for the list. Subsequent calls to the {@link #getLong(Object)} method will
 * return a distinct number for each elements in the list. For elements out of the list, the
 * resulting number is not specified. In some (rare) cases it might be possible to establish that an
 * element was not in the original list, and in that case -1 will be returned. The class can then be
 * saved by serialisation and reused later. 
 * 
 * <p>This class uses a {@linkplain ChunkedHashStore chunked hash store} to provide highly scalable constructors. Note that at construction time
 * you can {@linkplain #MinimalPerfectHashFunction(Iterable, TransformationStrategy, File, ChunkedHashStore) pass}
 * a {@link ChunkedHashStore} containing the elements associated with any value; note that, however, that if the store is rebuilt because of a
 * {@link it.unimi.dsi.sux4j.io.ChunkedHashStore.DuplicateException} it will be rebuilt associating with each key its ordinal position.
 * 
 * <P>The theoretical memory requirements for the algorithm we use are 2{@link HypergraphSorter#GAMMA &gamma;}=2.46 +
 * o(<var>n</var>) bits per element, plus the bits for the random hashes (which are usually
 * negligible). The o(<var>n</var>) part is due to an embedded ranking scheme that increases space
 * occupancy by 0.625%, bringing the actual occupied space to around 2.68 bits per element.
 * 
 * <P>As a commodity, this class provides a main method that reads from standard input a (possibly
 * <samp>gzip</samp>'d) sequence of newline-separated strings, and writes a serialised minimal
 * perfect hash function for the given list.
 * 
 * <h3>How it Works</h3>
 * 
 * <p>The technique used is very similar (but developed independently) to that described by Fabiano
 * C. Botelho, Rasmus Pagh and Nivio Ziviani in &ldquo;Simple and Efficient Minimal Perfect Hashing
 * Functions&rdquo;, <i>Algorithms and data structures: 10th international workshop, WADS 2007</i>,
 * number 4619 of Lecture Notes in Computer Science, pages 139&minus;150, 2007. In turn, the mapping
 * technique described therein was actually first proposed by Bernard Chazelle, Joe Kilian, Ronitt
 * Rubinfeld and Ayellet Tal in &ldquo;The Bloomier Filter: an Efficient Data Structure for Static
 * Support Lookup Tables&rdquo;, <i>Proc. SODA 2004</i>, pages 30&minus;39, 2004, as one of the
 * steps to implement a mutable table.
 * 
 * <p>The basic ingredient is the Majewski-Wormald-Havas-Czech
 * {@linkplain HypergraphSorter 3-hypergraph technique}. After generating a random 3-hypergraph, we
 * {@linkplain HypergraphSorter sort} its 3-hyperedges to that a distinguished vertex in each
 * 3-hyperedge, the <em>hinge</em>, never appeared before. We then assign to each vertex a
 * two-bit number in such a way that for each 3-hyperedge the sum of the values associated to its
 * vertices modulo 3 gives the index of the hash function generating the hinge. As as a result we
 * obtain a perfect hash of the original set (one just has to compute the three hash functions,
 * collect the three two-bit values, add them modulo 3 and take the corresponding hash function
 * value).
 * 
 * <p>To obtain a minimal perfect hash, we simply notice that we whenever we have to assign a value
 * to a vertex, we can take care of using the number 3 instead of 0 if the vertex is actually the
 * output value for some element. The final value of the minimal perfect hash function is the number
 * of nonzero pairs of bits that precede the perfect hash value for the element. To compute this
 * number, we use a simple table-free ranking scheme, recording the number of nonzero pairs each
 * {@link #BITS_PER_BLOCK} bits and modifying the standard broadword algorithm for computing the
 * number of ones in a word into an algorithm that {@linkplain #countNonzeroPairs(long) counts the
 * number of nonzero pairs of bits in a word}.
 * 
 * @author Sebastiano Vigna
 * @since 0.1
 */

public class MinimalPerfectHashFunction2<T> extends AbstractHashFunction<T> implements Serializable {
	private static final Logger LOGGER = LoggerFactory.getLogger( MinimalPerfectHashFunction2.class );


	/**
	 * Creates a new minimal perfect hash table for the given elements.
	 */

	@SuppressWarnings("unchecked")
	public MinimalPerfectHashFunction2( final Collection<? extends T> elements, final int keysPerBucket, final TransformationStrategy<T> transformationStrategy ) throws IOException {
		final long size = elements.size();
		final int r = (int)( ( size + keysPerBucket - 1 ) / keysPerBucket );
		long m = (long)( size / .90 ) + 1;
		if ( m % 2 == 0 ) m++;
		for(; ! BigInteger.valueOf( m ).isProbablePrime( 30 ); m += 2 );
		
		final Object[] array = elements.toArray();
		LongArrayBitVector bv = LongArrayBitVector.ofLength( m );
		
		final int[] bucketSize = new int[ r ];
		for( T s: elements ) bucketSize[ (int)( ( Hashes.jenkins( transformationStrategy.toBitVector( s ), 0 ) >>> 1 ) % r ) ]++;
		
		final int[] bucketPerm = Util.identity( bucketSize.length );
		IntArrays.radixSortIndirect( bucketPerm, bucketSize, true );
		IntArrays.reverse( bucketPerm );

		final int[] invBucketPerm = Util.invertPermutation( bucketPerm );
		
		final int[] keyPerm = Util.identity( (int) size );
		IntArrays.quickSort( keyPerm, new AbstractIntComparator() {
			@Override
			public int compare( final int k1, final int k2 ) {
				return invBucketPerm[ (int)( ( Hashes.jenkins( transformationStrategy.toBitVector( (T)array[ k1 ] ), 0 ) >>> 1 ) % r ) ] - 
						invBucketPerm[ (int)( ( Hashes.jenkins( transformationStrategy.toBitVector( (T)array[ k2 ] ), 0 ) >>> 1 ) % r ) ]; 
			}
		} );
		
		final long[] cumulative = new long[ r + 1 ];
		for( int i = 1; i < cumulative.length; i++ ) cumulative[ i ] = cumulative[ i - 1 ] + bucketSize[ bucketPerm[ i - 1 ] ];
		
		
		final LongOpenHashSet set = new LongOpenHashSet();
		final long index[] = new long[ r ];
		ProgressLogger progressLogger = new ProgressLogger();
		progressLogger.expectedUpdates = r;
		progressLogger.start( "Fixing...");
		for( int i = 0; i < r; i++ ) {
			tryIt: for( long f = 0;  f < m * m; f++ ) {
				int a = (int)( f % m );
				int b = (int)( f / m );
				
				set.clear();

				for( int j = (int)cumulative[ i ]; j < cumulative[ i + 1 ]; j++ ) {
					long[] hash = new long[ 3 ];
					Hashes.jenkins( transformationStrategy.toBitVector( (T)array[ keyPerm[ j ] ] ), 0, hash );
					assert ( hash[ 2 ] >>> 1 ) % r == bucketPerm[ i ] : ( hash[ 2 ] >>> 1 ) % r  + " != " + bucketPerm[ i ];
					
					//assert ( hashFunction.newHasher().putObject( (T)array[ keyPerm[ (int)j ] ], funnel ).hash().asLong() & -1L >>> 1 ) % r == i; 
					if ( ! set.add( ( ( hash[ 0 ] >>> 1 ) % m  + ( ( hash [ 1 ] >>> 1 ) % ( m - 1 ) + 1 ) * a + b ) % m ) ) continue tryIt;
					
				}
				
				for( LongIterator bits = set.iterator(); bits.hasNext(); ) if ( bv.getBoolean( bits.nextLong() ) ) continue tryIt;
				for( LongIterator bits = set.iterator(); bits.hasNext(); ) bv.set( bits.nextLong() );
				index[ bucketPerm[ i ] ] = f;
				break;
			}

			progressLogger.update();
		}
		progressLogger.done();	
		
		int[] bin = new int[ 300000 ];
		for( long x : index ) bin[ (int)x ]++;
		for( int t : bin ) System.out.println( t );

		/*long tot = 0;
		for( long x: index ) tot += Fast.mostSignificantBit( x + 1 );
		System.err.println( "Pure cost per element: " + tot / (double)size );
		*/
		EliasFanoLongBigList indices = new EliasFanoLongBigList( LongArrayList.wrap( index ) );
		System.err.println( "Cost per element: " + indices.numBits() / (double)size );

		LongOpenHashSet test = new LongOpenHashSet();
		for( T s: elements ) {
			long[] h = new long[ 3 ];
			Hashes.jenkins( transformationStrategy.toBitVector( s ), 0, h );
			long f = indices.getLong( ( h[ 2 ] >>> 1 ) % r );
			int a = (int)( f % m );
			int b = (int)( f / m );
			assert test.add( ( ( h[ 0 ] >>> 1 ) % m  + ( ( h[ 1 ] >>> 1 ) % ( m - 1 ) + 1 ) * a + b ) % m );
		}

		long[] positions = test.toLongArray();
		Arrays.sort( positions );
		System.err.println( "Positions: " + positions.length );
		EliasFanoMonotoneLongBigList pos = new EliasFanoMonotoneLongBigList( LongArrayList.wrap( positions ) );
		System.err.println( "Cost per element: " + pos.numBits() / (double)size );

		
		/* System.err.println("Lower bound: " + ( 1. + (r/(double)size - 1.0 + 1.0/(2.0*size))*Math.log(1 - size/(double)r))/Math.log(2) );
		
		for( int modulus = 0; modulus < 8; modulus++ ) {
			long total = 0;
			for( long p: index ) total += p >> modulus;
			
			final int l = Math.max( 0, Fast.mostSignificantBit( total / r ) );
			System.out.println( "buckets: " + r + " tot: " + total );
			final int numUpperBits = (int)( r + ( total >>> l ) );
			System.err.println( "Modulus: " + modulus );
			System.out.println( "Lower part Rice code: " + r * ( 1 << modulus ) );
			System.out.println( "Upper bits: " + numUpperBits );
			System.out.println( "Lower bits: " + l * r );
			System.out.println( "Total: " + ( r * ( 1 << modulus ) + numUpperBits + l * r ) );
			System.out.println( "Total per element: " + ( r * ( 1 << modulus ) + numUpperBits + l * r ) / (double)size );
		}*/
		
	}



	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectHashFunction2.class.getName(), "Builds a minimal perfect hash function reading a newline-separated list of strings.", new Parameter[] {
				new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
				new FlaggedOption( "tempDir", FileStringParser.getParser(), JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'T', "temp-dir", "A directory for temporary files." ),
				new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
				new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
				new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash function." ),
				new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY,
						"The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ), } );

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );

		BinIO.storeObject( new MinimalPerfectHashFunction2<CharSequence>( collection, 8, TransformationStrategies.utf16() ), functionName );
		LOGGER.info( "Completed." );
	}



	@Override
	public long getLong( Object key ) {
		// TODO Auto-generated method stub
		return 0;
	}
}
