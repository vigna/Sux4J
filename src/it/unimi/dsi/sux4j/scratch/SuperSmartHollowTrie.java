package it.unimi.dsi.sux4j.scratch;

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.sux4j.mph.AbstractHashFunction;
import it.unimi.dsi.sux4j.mph.HollowTrie;
import it.unimi.dsi.sux4j.mph.MWHCFunction;

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

public class SuperSmartHollowTrie<T> extends AbstractHashFunction<T> implements Serializable {
	private static final Logger LOGGER = Util.getLogger( SuperSmartHollowTrie.class );
	private static final long serialVersionUID = 0L;

	private static final boolean ASSERTS = false;
	
	private int size;
	private int n;
	private MWHCFunction<T> offsetLcpLength;
	private int bucketSize;
	private HollowTrie<?> trie;
	private int log2BucketSize;
	
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
		return trie.getLong( key ) * bucketSize + offsetLcpLength.getLong( key );
	}

	
	@SuppressWarnings("unused")
	public SuperSmartHollowTrie( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) {

		//this.transform = transform;
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
		
		log2BucketSize =(int) Math.ceil( Fast.log2( 1 + Math.log( 1 + Math.log( n ) ) ) );
		bucketSize = 1 << log2BucketSize;
		LOGGER.debug( "Bucket size: " + bucketSize );
		
		Iterator<? extends T> iterator = iterable.iterator();
		
		LongArrayBitVector prev = LongArrayBitVector.copy( transform.toBitVector( iterator.next() ) );
		BitVector curr;
		int[] s = new int[ 10000 ];
		for( int i = 0; i < s.length; i++ ) s[ i ] = 1;
		int lcp;
		boolean taken;
		ObjectArrayList<LongArrayBitVector> borders = new ObjectArrayList<LongArrayBitVector>();
		borders.add( prev.copy() );
		
		int k = 1, lastTaken = 0, cantTakeUnlessShorterLcp = Integer.MAX_VALUE;
		while( iterator.hasNext() ) {
			curr = transform.toBitVector( iterator.next() );
			lcp = (int)curr.longestCommonPrefixLength( prev );
			taken = false;
			if ( s[ lcp ] > 1 ) taken = true;
			else {
				if ( lcp < cantTakeUnlessShorterLcp ) {
					cantTakeUnlessShorterLcp = Integer.MAX_VALUE;
					if ( k - lastTaken >= bucketSize ) taken = true;
					else {
						if ( s[ lcp ] == 0 ) {
							taken = true; 
							cantTakeUnlessShorterLcp = lcp;
						}
					}
				}
			}
			
			for( int i = lcp; i < s.length; i++ ) s[ i ] = 0;
			if ( taken ) {
				borders.add( LongArrayBitVector.copy( curr ) );
				System.out.println( "Bucket of: " + ( k - lastTaken ) );
				lastTaken = k;
				for( int i = 0; i < lcp; i++ ) s[ i ]++;
			}
			k++;
			prev.replace( curr );
		}
		
		trie = new HollowTrie<LongArrayBitVector>( borders.iterator(), TransformationStrategies.identity() );
		
		iterator = iterable.iterator();
		long last = -1, r;
		int c = 0;
		while( iterator.hasNext() ) {
			r = trie.getLong( transform.toBitVector( iterator.next() ) );
			//System.out.println( "Mapping " + c + " to " + r );
			if( r < last ) throw new AssertionError( "" + last );
			last = r;
			c++;
		}
		
		// Build function assigning the lcp length and the bucketing data to each element.
		offsetLcpLength = new MWHCFunction<T>( iterable, transform, new AbstractLongList() {
			public long getLong( int index ) {
				return index % ( 2 * bucketSize ); 
			}
			public int size() {
				return n;
			}
		}, Fast.ceilLog2( 2 * bucketSize ) );

		
		final long numBits = 0;//trie.numBits() + offsetLcpLength.numBits();
		LOGGER.info( "Bits: " + numBits + " bits/string: " + (double)numBits / size );
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

		final SimpleJSAP jsap = new SimpleJSAP( SuperSmartHollowTrie.class.getName(), "Builds a hollow trie reading a newline-separated list of terms.",
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
		
		final SuperSmartHollowTrie<CharSequence> hollowTrie;
		
		LOGGER.info( "Building trie..." );

			FileLinesCollection collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
			hollowTrie = new SuperSmartHollowTrie<CharSequence>( collection, huTucker ? new HuTuckerTransformationStrategy( collection, true ) : TransformationStrategies.prefixFreeUtf16() );
		
		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( hollowTrie, trieName );
		LOGGER.info( "Completed." );
	}
}
