package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.sux4j.scratch.BitStreamImmutableBinaryTrie;
import it.unimi.dsi.sux4j.util.TwoSizesLongBigList;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;

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

/** A hollow trie implementation.
 * 
 * <p>Hollow tries compute minimal perfect monotone hashes in little space, but they are rather slow. 
 */

public class SmartHollowTrie<T> extends AbstractHash<T> implements Serializable {
	private static final Logger LOGGER = Util.getLogger( SmartHollowTrie.class );
	private static final long serialVersionUID = 0L;

	private static final boolean ASSERTS = false;
	@SuppressWarnings("unused")
	private static final boolean DEBUG = false;
	
	private int size;
	private int n;
	private MWHCFunction<T> offsetLcpLength;
	private int bucketSize;
	private BitStreamImmutableBinaryTrie<BitVector> trie;
	private int log2BucketSize;
	private TransformationStrategy<? super T> transform;
	private TwoSizesLongBigList skips;
	private long[] tinyTrie;
	
	private final static class Node {
		Node left, right;
		int skip;
		
		public Node( final Node left, final Node right, final int skip ) {
			this.left = left;
			this.right = right;
			this.skip = skip;
			if ( ASSERTS ) assert skip >= 0;
		}
	}
		
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final T key = (T)o;
		final LongArrayBitVector bv = LongArrayBitVector.copy( transform.toBitVector( key ) );
		int bucket = (int)trie.getLong( bv ); 
		return ( bucket << log2BucketSize ) + getLong( bv, tinyTrie[ 2 * bucket ], tinyTrie[ 2 * bucket + 1 ], bucket * ( bucketSize - 1 ) );
	}
	
	
	private final int rank( final long x, final long y, final int count, final int pos ) {
		assert pos < Long.SIZE * 2;
		if ( pos < Long.SIZE ) return Fast.count( x & ( 1L << pos ) - 1 );
		return count + Fast.count( y & ( 1L << pos - Long.SIZE ) - 1 );
	}

	private final int rank( final long x, final long y, final int count, final int from, final int to ) {
		return rank( x, y, count, to ) - rank( x, y, count, from );
	}
	
	private final int select( final long x, final long y, final int count, final int rank ) {
		if ( rank < count ) return Fast.select( x, rank );
		return Long.SIZE + Fast.select( y, rank - count );
	}

	public long getLong( final LongArrayBitVector bitVector, final long x, final long y, final long offset ) {
		int p = 0, r = 0, length = (int)bitVector.length(), index = 0;
		int a = 0, b = 0, t;
		int s = 0;
		int count = Fast.count( x );
		
		for(;;) {
			if ( ( s += (int)skips.getLong( offset + r ) ) >= length ) return 0; // String not in the original set

			//System.out.print( "Turning " + ( bitVector.getBoolean( s ) ? "right" : "left" ) + " at bit " + s + "... " );
			if ( bitVector.getBoolean( s ) ) p = 2 * r + 2;
			else p = 2 * r + 1;

			t = 2 * rank( x, y, count, a, b + 1 );
			a = b + 1;
			b += t;
			
			index += p - a - rank( x, y, count, a, p );

			//System.err.println( "Tiny trie: " + a + " " + b + " " + p + " " + index );
			// get p-th bit
			boolean bit = p < Long.SIZE ? ( x & 1L << p ) != 0 : ( y & 1L << p - Long.SIZE ) != 0;
			
			if ( ! bit ) break;

			r = rank( x, y, count, p + 1 ) - 1;
			
			s++;
		}
		
		// System.out.println();
		// Complete computation of leaf index
		
		for(;;) {
			p = select( x, y, count, ( r = rank( x, y, count, p + 1 ) ) - 1 );
			if ( p < a ) break;
			p = r * 2;
			
			t = 2 * rank( x, y, count, a, b + 1 );
			a = b + 1;
			b += t;
			
			index += p - a + 1 - rank( x, y, count, a, p + 1 );
			
			//System.err.println( "Tiny trie scanning; " + a + " " + b + " " + p + " " + index );
		}

		// System.err.println( "Tiny trie returning " + index );
		
		return index;
	}


	@SuppressWarnings("unused")
		public SmartHollowTrie( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) throws IOException {

		this.transform = transform;
		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		if ( iterable instanceof Collection ) n = ((Collection<? extends T>)iterable).size();
		else {
			int c = 0;
			// Do not add a suppression annotation--it breaks javac
			for( T o: iterable ) c++;
			n = c;
		}
		
		if ( n == 0 ) {
			trie = null;
			return;
		}
		
		log2BucketSize = 6;
		bucketSize = 1 << log2BucketSize;
		LOGGER.debug( "Bucket size: " + bucketSize );

		ObjectArrayList<BitVector> bucket = new ObjectArrayList<BitVector>();
		ObjectArrayList<HollowTrie<BitVector>> tinyTries = new ObjectArrayList<HollowTrie<BitVector>>();
		ObjectArrayList<BitVector> delims = new ObjectArrayList<BitVector>();
		ObjectArrayList<BitVector> ends = new ObjectArrayList<BitVector>();
		Iterator<? extends T> iterator = iterable.iterator();
		BitVector l = null;
		IntList skips = new IntArrayList( n );
		tinyTrie = new long[ 2 * ( ( n + bucketSize - 1 ) / bucketSize ) ];
		for( int i = 0; i < ( n + bucketSize - 1 ) / bucketSize; i++ ) {
			final int b = Math.min( bucketSize, n - i * bucketSize );
			bucket.clear();
			long maxPref = 0, pref;
			int pos = -1;
			for( int j = 0; j < b; j++ ) {
				bucket.add( LongArrayBitVector.copy( transform.toBitVector( iterator.next() ) ) );
				if ( j == 0 ) maxPref = bucket.get( j ).size();
				else {
					pref = bucket.get( j - 1 ).longestCommonPrefixLength( bucket.get( j ) );
					if ( pref < maxPref ) {
						pos = j;
						maxPref = pref;
					}
				}
			}
			
			delims.add( bucket.get( 0 ) );
			ends.add( l == null ? bucket.get( 0 ) : l );
			l = bucket.get( bucket.size() - 1 );
			HollowTrie<BitVector> t = new HollowTrie<BitVector>( bucket, TransformationStrategies.identity() );
			if ( DEBUG ) tinyTries.add( t );
			assert t.trie.length() == b * 2 - 1 : t.trie.length() + " != " + ( b * 2 - 1 );
			tinyTrie[ i * 2 ] = t.trie.getLong( 0, Long.SIZE );
			if ( b * 2 > Long.SIZE ) tinyTrie[ i * 2 + 1 ] = t.trie.getLong( Long.SIZE, b * 2 );
			assert t.skips.length() == b - 1 : t.skips.length() + " != " + ( b - 1 );
			for( long s: t.skips ) skips.add( (int)s );
		}
		
		this.skips = new TwoSizesLongBigList( skips );
		
		trie = new BitStreamImmutableBinaryTrie<BitVector>( delims, ends, TransformationStrategies.identity() );
		this.size = n;

		if ( DEBUG ) {
			iterator = iterable.iterator();
			for( int i = 0; i < ( n + bucketSize - 1 ) / bucketSize; i++ ) {
				final int b = Math.min( bucketSize, n - i * bucketSize );
				for( int j = 0; j < b; j++ ) {
					LongArrayBitVector bv = LongArrayBitVector.copy( transform.toBitVector( iterator.next() ) );
					assert tinyTries.get( i ).getLong( bv ) == j;
					// Same binary tree
					assert tinyTries.get( i ).trie.equals( LongArrayBitVector.wrap( new long[] { tinyTrie[ i * 2 ], tinyTrie[ i * 2 + 1 ] }, b * 2 - 1 ) );
					// Same skips
					for( int k = 0; k < b - 1; k++ ) assert tinyTries.get( i ).skips.getLong( k ) == this.skips.getLong( i * ( bucketSize - 1 ) + k );
					assert getLong( bv, tinyTrie[ i * 2 ], tinyTrie[ i * 2 + 1 ], i * ( bucketSize - 1 ) ) == j : "Bucket " + i + ": " + getLong( bv, tinyTrie[ i * 2 ], tinyTrie[ i * 2 + 1 ], i * ( bucketSize - 1 ) ) + " != " + j;
				}
			}
		}
		
		iterator = iterable.iterator();
		long last = -1, r;
		int c = 0;
		while( iterator.hasNext() ) {
			T e = iterator.next();
			//System.err.print( "Mapping " + c + " = " + e );
			r = trie.getLong( transform.toBitVector( e ) );
			//System.err.println( " to " + r );
			assert r >= 0 : r + " < 0";
			//if( r < last ) throw new AssertionError( "" + last );
			last = r;
			assert r == c / bucketSize : r + " != " + c / bucketSize;
			c++;
		}
		
		long numBits = trie.numBits() + tinyTrie.length * Long.SIZE + this.skips.numBits();
		LOGGER.info( "Bits: " + numBits + " bits/string: " + (double)numBits / size );
		
		LOGGER.info( "Testing speed..." );
		long time = -System.currentTimeMillis();
		int j = 0;
		for( T e : iterable ) {
			if ( getLong( e ) != j++ ) throw new AssertionError(  getLong( e ) + " != " + Integer.toString( j - 1 ) );
			if ( ( j & 0x3FF ) == 0 ) System.err.print('.');
		}
		System.err.println();
		time += System.currentTimeMillis();
		System.err.println( time / 1E3 + "s, " + ( time * 1E6 ) / j + " ns/vector" );

	}
	
	public int size() {
		return size;
	}
	
	private void recToString( final Node n, final StringBuilder printPrefix, final StringBuilder result, final StringBuilder path, final int level ) {
		if ( n == null ) return;
		
		result.append( printPrefix ).append( '(' ).append( level ).append( ')' );
		
		if ( n.skip >= 0 ) result.append( " skip: " ).append( n.skip );

		result.append( '\n' );
		
		path.append( '0' );
		recToString( n.left, printPrefix.append( '\t' ).append( "0 => " ), result, path, level + 1 );
		path.setCharAt( path.length() - 1, '1' ); 
		recToString( n.right, printPrefix.replace( printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1 );
		path.delete( path.length() - 1, path.length() ); 
		printPrefix.delete( printPrefix.length() - 6, printPrefix.length() );
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( SmartHollowTrie.class.getName(), "Builds a hollow trie reading a newline-separated list of terms.",
				new Parameter[] {
					new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read terms." ),
					new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to increase entropy (only available for offline construction)." ),
					new FlaggedOption( "termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read terms from this file (without loading them into core memory) instead of standard input." ),
					new UnflaggedOption( "trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final String trieName = jsapResult.getString( "trie" );
		final String stringFile = jsapResult.getString( "termFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );
		
		if ( huTucker && stringFile == null ) throw new IllegalArgumentException( "Hu-Tucker coding requires offline construction" );
		
		final SmartHollowTrie<CharSequence> hollowTrie;
		
		LOGGER.info( "Building trie..." );

		FileLinesCollection collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		hollowTrie = new SmartHollowTrie<CharSequence>( collection, huTucker ? new HuTuckerTransformationStrategy( collection, true ) : TransformationStrategies.prefixFreeUtf16() );
		
		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( hollowTrie, trieName );
		LOGGER.info( "Completed." );
	}
}
