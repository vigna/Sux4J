package it.unimi.dsi.sux4j.scratch;

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.util.ImmutableBinaryTrie;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

public class BitStreamImmutableBinaryTrie<T> extends AbstractObject2LongFunction<T> {
	private final static Logger LOGGER = Util.getLogger( BitStreamImmutableBinaryTrie.class );
	private static final long serialVersionUID = 1L;
	private static final boolean DEBUG = false;
	private static final boolean DDEBUG = false;
	private byte[] trie;
	private int size;
	private final TransformationStrategy<? super T> transformationStrategy;
	
	private final static class BitstreamTrie<T> {
		private final static boolean ASSERTS = false;
		
		/** A node in the trie. */
		protected static class Node implements Serializable {
			private static final long serialVersionUID = 1L;
			public Node left, right;
			/** An array containing the path compacted in this node (<code>null</code> if there is no compaction at this node). */
			final public long[] path;
			/** The length of the path compacted in this node (0 if there is no compaction at this node). */
			final public int pathLength;
			
			final public int skip;
			
			/** Creates a node representing a word. 
			 * 
			 * <p>Note that the long array contained in <code>path</code> will be stored inside the node.
			 * 
			 * @param path the path compacted in this node, or <code>null</code> for the empty path.
			 */
			
			public Node( final BitVector path, final int skip ) {
				if ( path == null ) {
					this.path = null;
					this.pathLength = 0;
				}
				else {
					this.path = path.bits();
					this.pathLength = path.size();
				}
				this.skip = skip;
			}

			/** Returns true if this node is a leaf.
			 * 
			 * @return  true if this node is a leaf.
			 */
			public boolean isLeaf() {
				return right == null && left == null;
			}
			
			public String toString() {
				return "[" + path + ", " + skip + "]";
			}
			
		}
			
		/** The root of the trie. */
		protected final Node root;
		/** The number of words in this trie. */
		private final TransformationStrategy<? super T> transformationStrategy;
		
		/** Creates a trie from a set of elements.
		 * 
		 * @param elements a set of elements
		 * @param transformationStrategy a transformation strategy that must turn <code>elements</code> into a list of
		 * distinct, lexicographically increasing (in iteration order) binary words.
		 */
		
		public BitstreamTrie( final List<? extends T> elements, final int bucketSize, final TransformationStrategy<? super T> transformationStrategy ) {
			this.transformationStrategy = transformationStrategy;
			// Check order
			final List<BitVector> bitVectors = TransformationStrategies.wrap( elements, transformationStrategy );
			final Iterator<BitVector> iterator = bitVectors.iterator();
			if ( iterator.hasNext() ) {
				final LongArrayBitVector prev = LongArrayBitVector.copy( iterator.next() );
				BitVector curr;

				while( iterator.hasNext() ) {
					curr = iterator.next();
					final int cmp = prev.compareTo( curr );
					if ( cmp == 0 ) throw new IllegalArgumentException( "The trie elements are not unique" );
					if ( cmp > 0 ) throw new IllegalArgumentException( "The trie elements are not sorted" );
					prev.replace( curr );
				}
			}
			
			root = buildTrie( bitVectors, bucketSize, Math.min( elements.size(), bucketSize ) - 1, 0 );
			LOGGER.info( "Gain: " + gain );
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
			
		protected Node buildTrie( final List<BitVector> elements, final int bucketSize, final int firstIndex, final int pos ) {
			final int numElements = elements.size();
			//System.err.println( "Size:" + numElements + " First delimiter: " + firstIndex );
			
			if ( numElements == 0 ) return null;

			final LongArrayBitVector first = LongArrayBitVector.copy( elements.get( firstIndex ).subVector( pos ) );
			int prefix = first.size(), change = 0, j = -1, lastIndex = firstIndex, middleIndex = firstIndex;
			BitVector curr;

			if ( firstIndex + bucketSize < numElements ) {
				// 	Find maximum common prefix. change records the point of change (for splitting the delimiter set).
				for( int i = firstIndex + bucketSize; i < numElements; i += bucketSize ) {
					curr = elements.get( i );
					lastIndex = i;
					j = (int)first.longestCommonPrefixLength( curr.subVector( pos ) );
					if ( j < prefix ) {
						middleIndex = i;
						prefix = j;
					}
				}

				if ( ASSERTS ) assert middleIndex > firstIndex;
				
				// Find exact change point
				for( change = middleIndex - bucketSize + 1; change <= middleIndex; change++ ) if ( elements.get( change ).getBoolean( pos + prefix ) ) break;

				if ( ASSERTS ) {
					assert change < middleIndex + 1;
					assert ! elements.get( change - 1 ).getBoolean( pos + prefix );
					assert elements.get( change ).getBoolean( pos + prefix );
				}
			}
			
			int startElement = firstIndex;
			int startPrefix = prefix;
			while( --startElement >= 0 ) {
				curr = elements.get( startElement );
				// TODO: use longestCommonPrefix
				j = (int)first.longestCommonPrefixLength( curr.subVector( pos ) );
				if ( j < prefix ) {
					j++;
					startPrefix = j;
					break;
				}
			}

			startElement++;
			
			int endElement = lastIndex;
			int endPrefix = prefix;
			while( ++endElement < numElements ) {
				curr = elements.get( endElement );
				// TODO: use longestCommonPrefix
				j = (int)first.longestCommonPrefixLength( curr.subVector( pos ) );
				if ( j < prefix ) {
					j++;
					endPrefix = j;
					break;
				}
			}
			
			
			final Node n;

			int k;
			
			if ( ASSERTS ) {
				for( k = 0; k < startElement; k++ ) assert first.longestCommonPrefixLength( elements.get( k ).subVector( pos ) ) < startPrefix; 

				for( k = startElement; k < endElement; k++ ) 
					assert first.longestCommonPrefixLength( elements.get( k ).subVector( pos ) ) >= prefix :
						"At " + k + " out of " + numElements + ": " + first.longestCommonPrefixLength( elements.get( k ).subVector( pos ) ) + " < " + prefix;

				for( k = endElement; k < numElements; k++ ) assert first.longestCommonPrefixLength( elements.get( k ).subVector( pos ) ) < endPrefix;
					
			}

			final int reducedPrefix = Math.max( startPrefix, endPrefix );
			if ( reducedPrefix < prefix ) gain += prefix - reducedPrefix;

			n = new Node( reducedPrefix > 0 ? LongArrayBitVector.copy( first.subVector( 0, reducedPrefix ) ) : null, prefix ); // There's some common prefix
			if ( firstIndex + bucketSize < numElements ) {
				n.left = buildTrie( elements.subList( startElement, change ), bucketSize, firstIndex - startElement, pos + prefix + 1 );
				n.right = buildTrie( elements.subList( change, endElement ), bucketSize, middleIndex - change, pos + prefix + 1 );
			}
			return n;
		}

		public int toStream( OutputBitStream trie ) throws IOException {
			return toStream( root, trie );
		}
		
		private static int toStream( Node n, OutputBitStream trie ) throws IOException {
			if ( n == null ) return 0;
			
			if ( ( n.left != null ) != ( n.right != null ) ) throw new IllegalStateException();
			
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
			
			trie.writeLongDelta( n.left == null ? 0 : leftBits ); // Skip pointer (nonzero if non leaf)
			
			trie.writeDelta( n.pathLength );
			final long wb = trie.writtenBits();
			if ( n.pathLength > 0 ) for( int i = 0; i < n.path.length; i++ ) trie.writeLong( n.path[ i ], Math.min( Long.SIZE, n.pathLength - i * Long.SIZE ) );
			assert trie.writtenBits() == wb + n.pathLength;

			trie.writeDelta( n.skip - n.pathLength );
			
			if ( n.left != null ) {
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
	}
	
	public BitStreamImmutableBinaryTrie( List<? extends T> elements, int bucketSize, TransformationStrategy<? super T> transformationStrategy ) throws IOException {

		this.transformationStrategy = transformationStrategy;
		BitstreamTrie<T> immutableBinaryTrie = new BitstreamTrie<T>( elements, bucketSize, transformationStrategy );
		FastByteArrayOutputStream fbStream = new FastByteArrayOutputStream();
		OutputBitStream trie = new OutputBitStream( fbStream, 0 );
		int numLeaves = immutableBinaryTrie.toStream( trie );
		size = numLeaves;
		
		LOGGER.info(  "trie bit size:" + trie.writtenBits() );
		
		if ( DDEBUG ) System.err.println( new ImmutableBinaryTrie<T>( elements, transformationStrategy ) );
		trie.flush();
		fbStream.trim();
		this.trie = fbStream.array;

		MutableString s = new MutableString();
		if ( DDEBUG ) recToString( new InputBitStream( this.trie ), new MutableString(), s, new MutableString(), 0 );
		if ( DDEBUG ) System.err.println( s );

	}
	
	
	@SuppressWarnings("unchecked")
	public long getLong( Object o ) {
		try {
			if ( DEBUG ) System.err.println( "Getting " + o + "...");
			BitVector v = transformationStrategy.toBitVector( (T)o );
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

				int missing = trie.readDelta();
				if ( DEBUG ) System.err.println( "Missing bits: " + missing );
				
				if ( skip == 0 ) {
					if ( DEBUG ) System.err.println( "Exact match (leaf = " + leaf + ")" + pos+ " " + missing + " " + length );
					return leaf;
				}
				
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
		return trie.length * Byte.SIZE;
	}

	public int size() {
		return size;
	}
}
