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
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;

import org.apache.log4j.Logger;

/** A succinct implementation of a binary partial trie based on a recursive bitstream.
 * 
 * <p>Instances of this class represent a <em>partial compacted trie</em>. In such a trie,
 * just a prefix of the path at each node is actually stored: then, we just store the number of missing bits.
 * 
 * <p>The main purpose of partial tries is to serve as <em>distributors</em> for other data structures:
 * given a set of delimiters <var>D</var> of a set <var>S</var>, a partial trie will {@linkplain #getLong(Object) <em>rank</em>}
 * an elements <var>x</var> of <var>S</var> over <var>D</var>, that is, it will return how many elements of
 * <var>D</var> strictly precede <var>x</var>. To do so, a partial trie records at each node the smallest possible
 * prefix that make it possible to rank correctly the whole of <var>S</var>: depending on the strings in
 * <var>S</var>, the savings in space can be more or less significant.
 * 
 * <p>An instance of this class stores a trie as a <em>recursive bitstream</em>: a node <var>x</var> with
 * subtrees <var>A</var> and <var>B</var> is stored as
 *  <div style="text-align: center">
 *  <var>skip</var> <var>pathlen</var> <var>path</var> <var>missing</var> <var>leaves<sub>A</sub></var> <var>leaves<sub>B</sub></var> <var>A</var> <var>B</var>,
 *  </div>
 * where except for <var>path</var>, which is the path at <var>x</var> represented literally,
 * all other components are numbers in {@linkplain OutputBitStream#writeDelta(int) &delta; coding}, and the
 * last two components are the recursive encodings of <var>A</var> and <var>B</var>. Leaves are
 * distinguished by having <var>skip</var> equal to zero (in which case, no information after the path is recorded). 
 * <var>leaves<sub>A</sub></var> <var>leaves<sub>B</sub></var>
 * are the number of leaves of <var>A</var> and <var>B</var>, respectively.
 * 
 * @author Sebastiano Vigna
 */

public class BitstreamImmutableBinaryPartialTrie<T> extends AbstractObject2LongFunction<T> {
	private final static Logger LOGGER = Util.getLogger( BitstreamImmutableBinaryPartialTrie.class );
	private static final long serialVersionUID = 1L;
	private static final boolean DEBUG = false;
	private static final boolean DDEBUG = false;
	
	private static final int MAX_PREFIX = Integer.MAX_VALUE - 1;
	
	/** The bitstream representing the partial trie. */
	private byte[] trie;
	/** The number of leaves in the trie. */
	private int size;
	/** The transformation used to map object to bit vectors. */
	private final TransformationStrategy<? super T> transformationStrategy;
	
	private final static class BitstreamTrie<T> {
		private final static boolean ASSERTS = false;
		
		/** A node in the trie. */
		protected static class Node implements Serializable {
			private static final long serialVersionUID = 1L;
			public Node left, right;
			/** The path compacted in this node (<code>null</code> if there is no compaction at this node). */
			public LongArrayBitVector path;
			public int prefixLeft;
			public int prefixRight;
			
			/** Creates a node. 
			 * 
			 * <p>Note that the long array contained in <code>path</code> will be stored inside the node.
			 * 
			 * @param bitVector the path compacted in this node, or <code>null</code> for the empty path.
			 */
			public Node( final Node left, final Node right, final LongArrayBitVector bitVector ) {
				this.left = left;
				this.right = right;
				this.path = bitVector;
				this.prefixLeft = this.prefixRight = MAX_PREFIX;
			}

			/** Returns true if this node is a leaf.
			 * 
			 * @return true if this node is a leaf.
			 */
			public boolean isLeaf() {
				return right == null && left == null;
			}
			
			public String toString() {
				return "[" + path + "]";
			}
			
		}
			
		/** The root of the trie. */
		protected final Node root;
		
		/** Creates a trie from a set of elements.
		 * 
		 * @param elements a set of elements
		 * @param transformationStrategy a transformation strategy that must turn <code>elements</code> into a list of
		 * distinct, lexicographically increasing (in iteration order) binary words.
		 */
		
		public BitstreamTrie( final Iterable<? extends T> iterable, final int bucketSize, final TransformationStrategy<? super T> transformationStrategy ) {
			Iterator<? extends T> iterator = iterable.iterator(); 
			
			int size = 0;
			
			Node root = null, node;
			BitVector curr;
			int pos, prefix;

			if ( iterator.hasNext() ) {
				LongArrayBitVector prev = LongArrayBitVector.copy( transformationStrategy.toBitVector( iterator.next() ) );
				LongArrayBitVector prevDelimiter = LongArrayBitVector.getInstance();
				
				size++;
				int cmp, numNodes = 0;
				long maxLength = prev.length();
				
				while( iterator.hasNext() ) {
					// Check order
					curr = transformationStrategy.toBitVector( iterator.next() ).fast();
					cmp = prev.compareTo( curr );
					if ( cmp == 0 ) throw new IllegalArgumentException( "The input bit vectors are not distinct" );
					if ( cmp > 0 ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted" );
					if ( curr.longestCommonPrefixLength( prev ) == prev.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free" );

					if ( size % bucketSize == 0 ) {
						if ( root == null ) {
							root = new Node( null, null, prev.copy() );
							//System.err.println( "Root is " + root.path );
							prevDelimiter.replace( prev );
						}
						else {
							//System.err.println( "Comparing " + prev + " against " + prevDelimiter );

							prefix = (int)prev.longestCommonPrefixLength( prevDelimiter );

							//System  .err.println( "Prefix is " + prefix );
							pos = 0;
							node = root;
							Node n = null;
							while( node != null ) {
								final long pathLength = node.path.length();
								//System.err.println( "pos: " + pos + " prefix:" + prefix + " length:" + node.path.length());
								if ( prefix < pathLength ) {
									n = new Node( node.left, node.right, node.path.copy( prefix + 1, pathLength ) );
									node.path = node.path.length( prefix );
									node.path.trim();
									node.left = n;
									node.right = new Node( null, null, prev.copy( pos + prefix + 1, prev.length() ) ); 
									numNodes++;
									break;
								}

								prefix -= pathLength + 1;
								pos += pathLength + 1;
								node = node.right;
								if ( ASSERTS ) assert node == null || prefix >= 0 : prefix + " <= " + 0;
							}

							if ( ASSERTS ) assert node != null;


							/*	MutableString s = new MutableString();
							recToString( root, new MutableString(), s, new MutableString(), 0 );
							System.err.println( s );
							 */

							prevDelimiter.replace( prev );
						}
					}
					prev.replace( curr );
					maxLength = Math.max( maxLength, prev.length() );
					size++;
				}

				this.root = root;

				if ( ASSERTS ) {
					iterator = iterable.iterator();
					int c = 1;
					while( iterator.hasNext() ) {
						curr = transformationStrategy.toBitVector( iterator.next() );
						if ( c++ % bucketSize == 0 ) {
							if ( ! iterator.hasNext() ) break; // The last string is never a delimiter
							node = root;
							pos = 0;
							while( node != null ) {
								prefix = (int)curr.subVector( pos ).longestCommonPrefixLength( node.path );
								assert prefix == node.path.length() : "Error at delimiter " + ( c - 1 ) / bucketSize;
								pos += node.path.length() + 1;
								if ( pos <= curr.length() ) node = curr.getBoolean( pos - 1 ) ? node.right : node.left;
								else {
									assert node.left == null && node.right == null;
									break;
								}
							}
						}
					}
				}

				LOGGER.info( "Reducing paths..." );

				iterator = iterable.iterator();

				final Node stack[] = new Node[ (int)maxLength ];
				final int[] len = new int[ (int)maxLength ];
				int depth = 0;
				stack[ 0 ] = root;
				boolean first = true;
				
				while( iterator.hasNext() ) {
					curr = transformationStrategy.toBitVector( iterator.next() ).fast();
					if ( ! first )  {
						prefix = (int)prev.longestCommonPrefixLength( curr );
						while( depth > 0 && len[ depth ] > prefix ) depth--;
					}
					else first = false;
					node = stack[ depth ];
					pos = len[ depth ];
					for(;;) {
						prefix = (int)curr.subVector( pos ).longestCommonPrefixLength( node.path );
						if ( prefix < node.path.length() ) {
							if ( node.path.getBoolean( prefix ) ) node.prefixLeft = prefix;
							else if ( node.prefixRight == MAX_PREFIX ) node.prefixRight = prefix; 
							break;
						}
						
						pos += node.path.length() + 1;
						if ( pos > curr.length() ) break;
						node = curr.getBoolean( pos - 1 ) ? node.right : node.left;
						len[ ++depth ] = pos;
						stack[ depth ] = node;
					}
					
					prev.replace( curr );
				}
			}
			else this.root = null;

		}

		protected int gain;
		
		/** Builds a trie recursively. 
		 * 
		 * <p>The trie will contain the suffixes of words in <code>words</code> starting at <code>pos</code>.
		 * 
		 * @param elements a list of elements.
		 * @param pos a starting position.
		 * @return a trie containing the suffixes of words in <code>words</code> starting at <code>pos</code>.
		 */

		public int toStream( final OutputBitStream trie ) throws IOException {
			final int result = toStream( root, trie );
			LOGGER.info( "Gain: " + gain );
			return result;
		}
		
		private int toStream( final Node n, final OutputBitStream trie ) throws IOException {
			if ( n == null ) return 0;
			
			if ( ASSERTS ) assert ( n.left != null ) == ( n.right != null );
			
			// We recursively create the stream of the left and right trees
			final FastByteArrayOutputStream leftStream = new FastByteArrayOutputStream();
			final OutputBitStream left = new OutputBitStream( leftStream, 0 );
			int leavesLeft = toStream( n.left, left );
			long leftBits = left.writtenBits();
			left.flush();
			
			final FastByteArrayOutputStream rightStream = new FastByteArrayOutputStream();
			final OutputBitStream right = new OutputBitStream( rightStream, 0 );
			int leavesRight = toStream( n.right, right );
			long rightBits = right.writtenBits();
			right.flush();
			
			trie.writeLongDelta( n.isLeaf() ? 0 : leftBits ); // Skip pointer (nonzero if non leaf)
			
			final int pathLength = (int)Math.min( n.path.length(), Math.max( n.prefixLeft, n.prefixRight ) + 1 );

			final int missing =  (int)( n.path.length() - pathLength );
			gain += missing; 

			trie.writeDelta( pathLength );
			if ( pathLength > 0 ) for( int i = 0; i < pathLength; i += Long.SIZE ) trie.writeLong( n.path.getLong( i, Math.min( i + Long.SIZE, pathLength ) ), Math.min( Long.SIZE, pathLength - i ) );

			if ( n.left != null ) {
				trie.writeDelta( missing );
				
				trie.writeLongDelta( leavesLeft ); // The number of leaves in the left subtree
				trie.writeLongDelta( leavesRight ); // The number of leaves in the right subtree


				// Write left and right tree
				trie.write( leftStream.array, leftBits );
				trie.write( rightStream.array, rightBits );
				// Concatenate right tree
				return leavesLeft + leavesRight;
			}

			return 1;
		}
		
		private void recToString( final Node n, final MutableString printPrefix, final MutableString result, final MutableString path, final int level ) {
			if ( n == null ) return;
			
			//System.err.println( "Called with prefix " + printPrefix );
			
			result.append( printPrefix ).append( '(' ).append( level ).append( ')' );
			
			if ( n.path != null ) {
				path.append( n.path );
				result.append( " path:" ).append( n.path );
			}

			result.append( '\n' );
			
			path.append( '0' );
			recToString( n.left, printPrefix.append( '\t' ).append( "0 => " ), result, path, level + 1 );
			path.charAt( path.length() - 1, '1' ); 
			recToString( n.right, printPrefix.replace( printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1 );
			path.delete( path.length() - 1, path.length() ); 
			printPrefix.delete( printPrefix.length() - 6, printPrefix.length() );
			
			//System.err.println( "Path now: " + path + " Going to delete from " + ( path.length() - n.pathLength));
			
			path.delete( (int)( path.length() - n.path.length() ), path.length() );
		}
		
		public String toString() {
			MutableString s = new MutableString();
			recToString( root, new MutableString(), s, new MutableString(), 0 );
			return s.toString();
		}

	}
	
	public BitstreamImmutableBinaryPartialTrie( final Iterable<? extends T> elements, final int bucketSize, final TransformationStrategy<? super T> transformationStrategy ) throws IOException {

		this.transformationStrategy = transformationStrategy;
		BitstreamTrie<T> immutableBinaryTrie = new BitstreamTrie<T>( elements, bucketSize, transformationStrategy );
		FastByteArrayOutputStream fbStream = new FastByteArrayOutputStream();
		OutputBitStream trie = new OutputBitStream( fbStream, 0 );
		int numLeaves = immutableBinaryTrie.toStream( trie );
		size = numLeaves;
		
		LOGGER.info(  "trie bit size:" + trie.writtenBits() );
		
		trie.flush();
		fbStream.trim();
		this.trie = fbStream.array;

		if ( DDEBUG ) {
			MutableString s = new MutableString();
			recToString( new InputBitStream( this.trie ), new MutableString(), s, new MutableString(), 0 );
			System.err.println( s );
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public long getLong( Object o ) {
		try {
			if ( DEBUG ) System.err.println( "Getting " + o + "...");
			BitVector v = transformationStrategy.toBitVector( (T)o ).fast();
			long length = v.length();
			final InputBitStream trie = new InputBitStream( this.trie );

			long pos = 0;
			int leaf = 0;
			for( ;; ) {
				long skip = trie.readLongDelta();

				int pathLength = trie.readDelta();
				if ( DEBUG ) System.err.println( "Path length: " + pathLength );
				
				long xor = 0, t = 0;
				int i, size;
				
				long readBits = trie.readBits();
				
				//System.err.println( v.subVector(  pos, v.length() ) );
				
				for( i = 0; i < ( pathLength + Long.SIZE - 1 ) / Long.SIZE; i++ ) {
					size = Math.min( Long.SIZE, pathLength - i * Long.SIZE );
					xor = v.getLong( pos, Math.min( length, pos += size ) ) ^ ( t = trie.readLong( size ) );
					
					//System.err.println( "Checking with " + LongArrayBitVector.wrap( new long[] { t }, size ) );
					
					if ( xor != 0 || pos >= length ) break;
				}

				if ( xor != 0 || pos > length ) {
					if ( DEBUG ) System.err.println( "Path mismatch: " +  ( ( ( ( xor & -xor ) & t ) != 0 ) ? "smaller" : "greater" ) + " than trie path at (leaf = " + leaf + ")" );
					if ( ( ( xor & -xor ) & t ) != 0 ) return leaf;
					else {
						if ( skip == 0 ) {
							if ( DEBUG ) System.err.println( "Leaf node" );
							return leaf + 1;
						}
						if ( DEBUG ) System.err.println( "Non-leaf node" );
						// Skip remaining path, if any and missing bits count
						trie.skip( pathLength - ( trie.readBits() - readBits ) );
						trie.readDelta();
						return leaf + trie.readDelta() + trie.readDelta();
					}
				}

				if ( skip == 0 ) {
					if ( DEBUG ) System.err.println( "Exact match (leaf = " + leaf + ")" + pos + " " + length );
					return leaf;
				}
				
				int missing = trie.readDelta();
				if ( DEBUG ) System.err.println( "Missing bits: " + missing );
				
				// Increment pos by missing bits
				pos += missing;
				
				int leavesLeft = trie.readDelta();
				trie.readDelta(); // Skip number of right leaves
				
				if ( pos >= v.length() ) return leaf;
				
				if ( v.getBoolean( pos++ ) ) {
					// Right
					trie.skip( skip );
					leaf += leavesLeft;
					if ( DEBUG ) System.err.println( "Turining right (" + leaf + " leaves on the left)..." );
				}
				else {
					if ( DEBUG ) System.err.println( "Turining left (" + leaf + " leaves on the left)..." );
					// Left
				}

			} 
		}catch( IOException e ) {
			throw new RuntimeException( e );
		}
	}
	

	private void recToString( final InputBitStream trie, final MutableString printPrefix, final MutableString result, final MutableString path, final int level ) throws IOException {
		int skip = trie.readDelta();
		
		//System.err.println( "Called with prefix " + printPrefix );
		
		result.append( printPrefix ).append( '(' ).append( level ).append( ')' );
		
		int pathLength = trie.readDelta();
		LongArrayBitVector p = LongArrayBitVector.getInstance( pathLength );
		
		for( int i = 0; i < ( pathLength + Long.SIZE - 1 ) / Long.SIZE; i++ ) {
			int size = Math.min( Long.SIZE, pathLength - i * Long.SIZE );
			p.append( trie.readLong( size ), size );
		}

		int missing = trie.readDelta();

		path.append( p );
		result.append( " path:" ).append( p );
		while( missing-- != 0 ) result.append( '*' );
		
		result.append( '\n' );

		if ( skip == 0 ) return; // Leaf

		trie.readDelta(); // Skip number of leaves in the left subtree
		trie.readDelta(); // Skip number of leaves in the right subtree

		
		path.append( '0' );
		recToString( trie, printPrefix.append( '\t' ).append( "0 => " ), result, path, level + 1 );
		path.charAt( path.length() - 1, '1' ); 
		recToString( trie, printPrefix.replace( printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1 );
		path.delete( path.length() - 1, path.length() ); 
		printPrefix.delete( printPrefix.length() - 6, printPrefix.length() );
		
		//System.err.println( "Path now: " + path + " Going to delete from " + ( path.length() - n.pathLength));
		
		path.delete( path.length() - pathLength, path.length() );
	}

	public boolean containsKey( Object o ) {
		return true;
	}

	public long numBits() {
		return trie.length * Byte.SIZE + transformationStrategy.numBits();
	}

	public int size() {
		return size;
	}
}
