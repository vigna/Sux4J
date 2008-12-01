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

import static it.unimi.dsi.bits.Fast.length;
import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.Rank9;
import it.unimi.dsi.sux4j.bits.GRRRBalancedParentheses;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Iterator;
import java.util.ListIterator;
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

/** A hollow trie, that is, a compacted trie recording just the length of the paths associated to the internal nodes.
 *
 * <p>Instances of this class can be used to compute a monotone minimal perfect hashing of the keys.
 */

public class HollowTrie<T> extends AbstractHashFunction<T> implements Serializable {
	private static final Logger LOGGER = Util.getLogger( HollowTrie.class );
	private static final long serialVersionUID = 0L;

	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;
	
	private EliasFanoLongBigList skips;
	/** The bit vector containing Jacobson's representation of the trie. */
	private final BitVector trie;
	/** A ranking structure over {@link #trie}. */
	private final Rank9 rank9;
	/** A balanced parentheses structure over {@link #trie}. */
	private GRRRBalancedParentheses balParen;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** The number of elements in this hollow trie. */
	private int size;
	
	private final static class Node {
		Node left, right;
		int skip;
		int openParentheses;
		
		public Node( final Node left, final Node right, final int skip ) {
			this.left = left;
			this.right = right;
			this.skip = skip;
			if ( ASSERTS ) assert skip >= 0;
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object object ) {
		//System.err.println( "Hashing " + object + "..." );
		if ( size <= 1 ) return size - 1;
		final BitVector bitVector = transform.toBitVector( (T)object ).fast();
		long p = 1, length = bitVector.length(), index = 0;
		int s = 0;
		
		for(;;) {
			if ( ( s += (int)skips.getLong( rank9.rank( p ) - 1 ) ) >= length ) return -1;
			//System.err.println( "Skipping " + rank9.rank( p ) + " bits..." );
			
			//System.err.print( "Turning " + ( bitVector.getBoolean( s ) ? "right" : "left" ) + " at bit " + s + "... \n" );
			if ( bitVector.getBoolean( s ) ) {
				final long q = balParen.findClose( p ) + 1;
				index += ( q - p ) / 2;
				//System.err.println( "Increasing index by " + ( q - p + 1 ) / 2 + " to " + index + "..." );
				if ( ! trie.getBoolean( q ) ) return index;
				p = q;
			}
			else if ( ! trie.getBoolean( ++p ) ) return index;
			
			s++;
		}
	}
	
	private final static class SkipInfo {
		private int maxSkip;
		private long skipsLength;
	}
	
	long visit( final Node node, LongArrayBitVector bitVector, long pos, IntArrayList skips, SkipInfo skipInfo ) {
		if ( node == null ) return pos;

		bitVector.set( pos++ ); // This adds the open parentheses
		
		if ( skipInfo.maxSkip < node.skip ) skipInfo.maxSkip = node.skip;
		skipInfo.skipsLength += length( node.skip );
		skips.add( node.skip );
		
		pos = visit( node.left, bitVector, pos, skips, skipInfo );

		return visit( node.right, bitVector, pos + 1, skips, skipInfo ); // This adds the closing parenthesis
	}
	
	public HollowTrie( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) {
		this( iterable.iterator(), transform );
	}
		
	public HollowTrie( final Iterator<? extends T> iterator, final TransformationStrategy<? super T> transform ) {

		this.transform = transform;

		int size = 0;
		
		Node root = null, node, parent;
		int prefix, numNodes = 0, cmp;

		if ( iterator.hasNext() ) {
			BitVector prev = transform.toBitVector( iterator.next() ).copy(), curr;
			size++;

			while( iterator.hasNext() ) {
				size++;
				curr = transform.toBitVector( iterator.next() );
				cmp = prev.compareTo( curr );
				if ( cmp == 0 ) throw new IllegalArgumentException( "The input bit vectors are not distinct" );
				if ( cmp > 0 ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted" );
				prefix = (int)curr.longestCommonPrefixLength( prev );
				if ( prefix == prev.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free" );

				node = root;
				parent = null;
				Node n = null;
				while( node != null ) {
					if ( prefix < node.skip ) {
						n = new Node( node, null, prefix );
						numNodes++;
						if ( parent == null ) {
							root.skip -= prefix + 1;
							if ( ASSERTS ) assert root.skip >= 0;
							root = n;
						}
						else {
							parent.right = n;
							node.skip -= prefix + 1;
							if ( ASSERTS ) assert node.skip >= 0;
						}
						break;
					}

					prefix -= node.skip + 1;
					parent = node;
					node = node.right;
				}

				if ( node == null ) {
					if ( parent == null ) root = new Node( null, null, prefix );
					else parent.right = new Node( null, null, prefix );
					numNodes++;
				}

				if ( ASSERTS ) {
					long s = 0;
					Node m = root;
					while( m != null ) {
						s += m.skip;
						if ( curr.getBoolean( s ) ) {
							if ( m.right == null ) break;
						}
						else if ( m.left == null ) break;
						m = curr.getBoolean( s ) ? m.right : m.left;
						s++;
					}
					assert parent == null || ( node == null ? m == parent.right : m == n );
				}

				prev = curr.copy();
			}
		}
		
		this.size = size;

		if ( size <= 1 ) {
			rank9 = new Rank9( LongArrays.EMPTY_ARRAY, 0 );
			balParen = new GRRRBalancedParentheses( BitVectors.EMPTY_VECTOR );
			trie = BitVectors.EMPTY_VECTOR;
			return;
		}

		final LongArrayBitVector bitVector = LongArrayBitVector.ofLength( 2 * numNodes + 2 );
		final IntArrayList skips = new IntArrayList();
		SkipInfo skipInfo = new SkipInfo();
		
		/*StringBuilder result = new StringBuilder();
		recToString( root, new StringBuilder(), result, new StringBuilder(), 0 );
		System.out.println( result );*/
		
		bitVector.set( 0 ); // Fake open parenthesis
		visit( root, bitVector, 1, skips, skipInfo );

		//System.err.println( bitVector );
		//System.err.println( skips );
		
		root = null;		
			
		trie = bitVector;
		rank9 = new Rank9( bitVector );
		balParen = new GRRRBalancedParentheses( bitVector, false, true, false );
		final int skipWidth = Fast.ceilLog2( skipInfo.maxSkip );

/*		LOGGER.info( "Max depth: " + maxDepth );
		LOGGER.info( "Average depth: " + (double)sumOfDepths / size  );*/
		LOGGER.info( "Max skip: " + skipInfo.maxSkip );
		LOGGER.info( "Max skip width: " + skipWidth );
		LOGGER.info( "Bits per skip: " + ( skipInfo.skipsLength * 2.0 ) / ( numNodes - 1 ) );
		
		this.skips = new EliasFanoLongBigList( skips );
		
		if ( DEBUG ) {
			System.err.println( skips );
			System.err.println( this.skips );
		}
		
		final long numBits = rank9.numBits() + balParen.numBits() + trie.length() + this.skips.numBits() + transform.numBits();
		LOGGER.info( "Bits: " + numBits + " bits/string: " + (double)numBits / size );
	}
	
	
	/** Builds a trie recursively. 
	 * 
	 * <p>The trie will contain the suffixes of words in <code>words</code> starting at <code>pos</code>.
	 * 
	 * @param elements a list of elements.
	 * @param pos a starting position.
	 * @return a trie containing the suffixes of words in <code>words</code> starting at <code>pos</code>.
	 */
		
	protected Node buildTrie( final ObjectList<BitVector> elements, final ObjectList<BitVector> ends, final int pos, final Reference2LongMap<BitVector> original ) {
		// TODO: on-the-fly check for lexicographical order
		
		for( int i = 0; i < elements.size() - 1; i++ ) assert elements.get( i ).compareTo( elements.get( i + 1 ) ) < 0;
		
		if ( elements.size() == 1 ) return null;

		//System.err.println( elements );
		
		BitVector first = elements.get( 0 ), curr;
		int prefix = first.size(), change = -1, j;

		// 	Find maximum common prefix. change records the point of change (for splitting the word set).
		for( ListIterator<BitVector> i = elements.listIterator( 1 ); i.hasNext(); ) {
			curr = i.next();
			assert ! curr.equals( first );
			assert curr.longestCommonPrefixLength( first ) != curr.length();
			assert curr.longestCommonPrefixLength( first ) != first.length();
			
			if ( curr.size() < prefix ) prefix = curr.size(); 
			for( j = pos; j < prefix; j++ ) if ( first.get( j ) != curr.get( j ) ) break;
			if ( j < prefix ) {
				change = i.previousIndex();
				prefix = j;
			}
		}
		
		assert change >= 0;

		ObjectArrayList<BitVector> leftList = new ObjectArrayList<BitVector>( elements.subList( 0, change ) );
		assert ! ends.isEmpty();
		assert ! leftList.isEmpty();
		
		//assert elements.get( change - 1 ).subVector( 0, prefix ).equals( elements.get( change ).subVector( 0, prefix ) );
		assert ! elements.get( change - 1 ).subVector( 0, prefix + 1 ).equals( elements.get( change ).subVector( 0, prefix + 1 ) );
		
		System.err.println( "prefix: " + prefix );
		
		int index = (int)original.removeLong( elements.get( change - 1 ) ); 
		if ( index >= 0 ) leftList.add( ends.get( index ) );
		
		if ( index >= 0 ) System.err.println( "Adding end of index " + index );
		
		assert index == -1 || ends.get( index ).longestCommonPrefixLength( leftList.get( 0 ) ) >= prefix;
		
		return new Node( buildTrie( leftList, ends, prefix + 1, original ), 
				buildTrie( elements.subList( change, elements.size() ), ends, prefix + 1, original ), prefix - pos );
	}

	public int size() {
		return size;
	}

	public long numBits() {
		return rank9.numBits() + balParen.numBits() + trie.length() + this.skips.numBits() + transform.numBits();
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

		final SimpleJSAP jsap = new SimpleJSAP( HollowTrie.class.getName(), "Builds a hollow trie reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to reduce string length." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String trieName = jsapResult.getString( "trie" );
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

		BinIO.storeObject( new HollowTrie<CharSequence>( collection, transformationStrategy ), trieName );
		LOGGER.info( "Completed." );
	}
}
