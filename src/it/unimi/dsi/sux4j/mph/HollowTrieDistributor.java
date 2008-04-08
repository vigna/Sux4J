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
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.NullOutputStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.sux4j.bits.Rank9;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;
import it.unimi.dsi.util.LongBigList;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.log4j.Logger;

/** A distributor based on a hollow trie.
 * 
 * @author Sebastiano Vigna
 */

public class HollowTrieDistributor<T> extends AbstractObject2LongFunction<T> {
	private final static Logger LOGGER = Util.getLogger( HollowTrieDistributor.class );
	private static final long serialVersionUID = 1L;
	private static final boolean DEBUG = false;
	private static final boolean DDEBUG = false;
	/** Infinity-like value for initialising node prefixes. It's one less than {@link Integer#MAX_VALUE} because we need to be able to add one
	 * without overflowing. */
	private static final boolean ASSERTS = false;
	/** The bitstream representing the PaCo trie. */
	private final BitVector trie;
	/** The transformation used to map object to bit vectors. */
	private final TransformationStrategy<? super T> transformationStrategy;
	private final Rank9 rank9;
	private final SimpleSelect select;
	private final EliasFanoLongBigList skips;
	private final MWHCFunction<BitVector> behaviour;
	private Object2LongFunction<BitVector> testFunction;
	private Object2LongFunction<BitVector> lTestFunction;
	private MWHCFunction<BitVector> lbehaviour;
	private final int size;
	
	/** A class representing explicitly a partial trie. The {@link IntermediateTrie#toStream(OutputBitStream)} method
	 * writes an instance of this class to a bit stream. 
	 */
	private final static class IntermediateTrie<T> {
		private Object2LongFunction<BitVector> testFunction = new Object2LongOpenHashMap<BitVector>();
		private Object2LongFunction<BitVector> lTestFunction = new Object2LongOpenHashMap<BitVector>();
		{
			testFunction.defaultReturnValue( -1 );
			lTestFunction.defaultReturnValue( -1 );
		}
		
		/** A node in the trie. */
		protected static class Node {
			/** Left child. */
			public Node left;
			/** Right child. */
			public Node right;
			/** The path compacted in this node (<code>null</code> if there is no compaction at this node). */
			public final LongArrayBitVector path;
			/** Whether we have already seen this node during the computation of the behaviour. */
			public boolean seen;
			/** The index of this node in breadth-first order. */
			public int index;
			
			/** Creates a node. 
			 * 
			 * @param left the left child.
			 * @param right the right child.
			 * @param path the path compacted at this node.
			 */
			public Node( final Node left, final Node right, final LongArrayBitVector path ) {
				this.left = left;
				this.right = right;
				this.path = path;
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
		/** The number of nodes. */
		protected final int size;
		/** The number of nodes. */
		protected final int numElements;
		private int writtenKeys;

		private LongBigList lvalues;

		private int writtenLKeys;
		
		/** Creates a partial compacted trie using given elements, bucket size and transformation strategy.
		 * 
		 * @param elements the elements among which the trie must be able to rank.
		 * @param bucketSize the size of a bucket.
		 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
		 * distinct, lexicographically increasing (in iteration order) bit vectors.
		 * @throws IOException 
		 */
		
		public IntermediateTrie( final Iterable<? extends T> elements, final int bucketSize, final TransformationStrategy<? super T> transformationStrategy ) throws IOException {
			Iterator<? extends T> iterator = elements.iterator(); 
			
			Node node;
			BitVector curr;
			int pos, prefix;

			if ( iterator.hasNext() ) {
				LongArrayBitVector prev = LongArrayBitVector.copy( transformationStrategy.toBitVector( iterator.next() ) );
				// The last delimiter seen, if root is not null.
				LongArrayBitVector prevDelimiter = LongArrayBitVector.getInstance();
				
				int count = 1;
				Node root = null;
				int cmp;
				long maxLength = prev.length();
				
				while( iterator.hasNext() ) {
					// Check order
					curr = transformationStrategy.toBitVector( iterator.next() ).fast();
					cmp = prev.compareTo( curr );
					if ( cmp == 0 ) throw new IllegalArgumentException( "The input bit vectors are not distinct" );
					if ( cmp > 0 ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted" );
					if ( curr.longestCommonPrefixLength( prev ) == prev.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free" );

					if ( count % bucketSize == 0 ) {
						// Found delimiter. Insert into trie.
						if ( root == null ) {
							root = new Node( null, null, prev.copy() );
							prevDelimiter.replace( prev );
						}
						else {
							prefix = (int)prev.longestCommonPrefixLength( prevDelimiter );

							pos = 0;
							node = root;
							Node n = null;
							while( node != null ) {
								final long pathLength = node.path.length();
								if ( prefix < pathLength ) {
									n = new Node( node.left, node.right, node.path.copy( prefix + 1, pathLength ) );
									node.path.length( prefix );
									node.path.trim();
									node.left = n;
									node.right = new Node( null, null, prev.copy( pos + prefix + 1, prev.length() ) ); 
									break;
								}

								prefix -= pathLength + 1;
								pos += pathLength + 1;
								node = node.right;
								if ( ASSERTS ) assert node == null || prefix >= 0 : prefix + " <= " + 0;
							}

							if ( ASSERTS ) assert node != null;

							prevDelimiter.replace( prev );
						}
					}
					prev.replace( curr );
					maxLength = Math.max( maxLength, prev.length() );
					count++;
				}

				this.root = root;
				this.numElements = count;

				LOGGER.info( "Numbering nodes..." );

				ObjectArrayList<Node> queue = new ObjectArrayList<Node>();
				int p = 0;
				if ( root != null ) {
					queue.add( root );
					while( p < queue.size() ) {
						node = queue.get( p );
						node.index = p;
						if ( node.left != null ) queue.add( node.left );
						if ( node.right != null ) queue.add( node.right );
						p++;
					}
					queue = null;
					size = p;

					LOGGER.info( "Computing function keys..." );

					OutputBitStream keys = new OutputBitStream( "tmp.keys" );
					OutputBitStream lkeys = new OutputBitStream( "tmp.lkeys" );
					new File( "tmp.keys" ).deleteOnExit();
					new File( "tmp.lkeys" ).deleteOnExit();
					values = LongArrayBitVector.getInstance().asLongBigList( 2 );
					lvalues = LongArrayBitVector.getInstance().asLongBigList( 1 );
					iterator = elements.iterator();

					// The stack of nodes visited the last time
					final Node stack[] = new Node[ (int)maxLength ];
					// The length of the path compacted in the trie up to the corresponding node, excluded
					final int[] len = new int[ (int)maxLength ];
					stack[ 0 ] = root;
					int depth = 0;
					boolean first = true;
					int writtenKeys = 0, writtenLKeys = 0;
					Node lastNode = null;
					BitVector lastPath = null;

					while( iterator.hasNext() ) {
						curr = transformationStrategy.toBitVector( iterator.next() ).fast();
						if ( DEBUG ) System.err.println( curr );
						if ( ! first )  {
							// Adjust stack using lcp between present string and previous one
							prefix = (int)prev.longestCommonPrefixLength( curr );
							while( depth > 0 && len[ depth ] > prefix ) depth--;
						}
						else first = false;
						node = stack[ depth ];
						pos = len[ depth ];

						for(;;) {
							final LongArrayBitVector nodePath = node.path;
							final BitVector currFromPos = curr.subVector( pos ); 
							prefix = (int)currFromPos.longestCommonPrefixLength( nodePath );
							if ( prefix < nodePath.length() || ! node.seen ) {

								final BitVector path;
								final int value;
								if ( prefix == nodePath.length() ) {
									node.seen = true;
									value = node.isLeaf() ? 0 : 2;
									if ( ASSERTS ) assert ! node.isLeaf() || currFromPos.length() == nodePath.length();
									path = nodePath;
								}
								else {
									if ( nodePath.getBoolean( prefix ) ) value = 0;
									else value = 1;
									path = node.isLeaf() ? currFromPos.copy() :	currFromPos.subVector( 0, Math.min( currFromPos.length(), nodePath.length() ) ).copy();
								}

								if ( lastNode != node || ! path.equals( lastPath ) ) {
									if ( node.isLeaf() ) {
										writtenLKeys++;
										lvalues.add( value );
										lkeys.writeLong( node.index, 64 );
									}
									else {
										writtenKeys++;
										values.add( value );
										keys.writeLong( node.index, 64 );
									}


									final int pathLength = (int)path.length();
									lastNode = node;
									lastPath = path;
									if ( node.isLeaf() ) {
										lkeys.writeDelta( pathLength );
										for( int i = 0; i < pathLength; i += Long.SIZE ) lkeys.writeLong( path.getLong( i, Math.min( i + Long.SIZE, pathLength) ), Math.min( Long.SIZE, pathLength - i ) );
									}
									else {
										keys.writeDelta( pathLength );
										for( int i = 0; i < pathLength; i += Long.SIZE ) keys.writeLong( path.getLong( i, Math.min( i + Long.SIZE, pathLength) ), Math.min( Long.SIZE, pathLength - i ) );
									}

									if ( ASSERTS ) {
										long key[] = new long[ ( pathLength + Long.SIZE - 1 ) / Long.SIZE + 1 ];
										key[ 0 ] = node.index;
										for( int i = 0; i < pathLength; i += Long.SIZE ) key[ i / Long.SIZE + 1 ] = path.getLong( i, Math.min( i + Long.SIZE, pathLength ) );
										if ( node.isLeaf() ) lTestFunction.put( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ), value );
										else testFunction.put( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ), value );
									}

									if ( DEBUG ) {
										System.err.println( "Computed " + ( node.isLeaf() ? "leaf " : "" ) + "mapping <" + node.index + ", " + path + "> -> " + value );
										System.err.println( testFunction );
										System.err.println( lTestFunction );
									}

								}
								if ( value != 2 ) break;

							}

							pos += nodePath.length() + 1;
							if ( pos > curr.length() ) break;
							node = curr.getBoolean( pos - 1 ) ? node.right : node.left;
							// Update stack
							len[ ++depth ] = pos;
							stack[ depth ] = node;
						}

						prev.replace( curr );
					}

					this.writtenKeys = writtenKeys;
					this.writtenLKeys = writtenLKeys;
					keys.close();
					lkeys.close();
				}
				else size = 0;
			}
			else{
				this.root = null;
				this.numElements = 0;
				this.size = 0;
				this.writtenKeys = 0;
			}

		}

		/** Accumulates the gain in bits w.r.t. a standard trie (just for statistical purposes). */
		protected int gain;

		private final OutputBitStream bitCount = new OutputBitStream( NullOutputStream.getInstance(), 0 );
		private LongBigList values;
		

		private void recToString( final Node n, final MutableString printPrefix, final MutableString result, final MutableString path, final int level ) {
			if ( n == null ) return;
			
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
	
	/** Creates a partial compacted trie using given elements, bucket size and transformation strategy.
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param bucketSize the size of a bucket.
	 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, lexicographically increasing (in iteration order) bit vectors.
	 */
	public HollowTrieDistributor( final Iterable<? extends T> elements, final int bucketSize, final TransformationStrategy<? super T> transformationStrategy ) throws IOException {
		this.transformationStrategy = transformationStrategy;
		final IntermediateTrie<T> immutableBinaryTrie = new IntermediateTrie<T>( elements, bucketSize, transformationStrategy );

		final int numKeys = immutableBinaryTrie.writtenKeys;
		final int numLKeys = immutableBinaryTrie.writtenLKeys;
		size = immutableBinaryTrie.size;
		final BitVector bitVector = LongArrayBitVector.getInstance( immutableBinaryTrie.size );
		final ObjectArrayList<IntermediateTrie.Node> queue = new ObjectArrayList<IntermediateTrie.Node>();
		final IntArrayList skips = new IntArrayList();
		int p = 0;

		if ( immutableBinaryTrie.root != null ) {
			queue.add( immutableBinaryTrie.root );

			if ( DDEBUG ) System.err.println( immutableBinaryTrie );

			IntermediateTrie.Node n;
			testFunction = immutableBinaryTrie.testFunction;
			lTestFunction = immutableBinaryTrie.lTestFunction;

			while( p < queue.size() ) {
				n = queue.get( p );
				if ( ! n.isLeaf() ) skips.add( (int)n.path.length() );
				bitVector.add( ! n.isLeaf() );
				if ( ASSERTS ) assert ( n.left == null ) == ( n.right == null );
				if ( n.left != null ) queue.add( n.left );
				if ( n.right != null ) queue.add( n.right );
				p++;
			}

			if ( ASSERTS ) assert p == immutableBinaryTrie.size : p + " != " + immutableBinaryTrie.size;
		}
		
		trie = bitVector;
		rank9 = new Rank9( bitVector );
		select = new SimpleSelect( bitVector );

		this.skips = new EliasFanoLongBigList( skips );
		
		LOGGER.info( "Bits per skip: " + (double)this.skips.numBits() / this.skips.length() );
		
		if ( DDEBUG ) {
			MutableString s = new MutableString();
		//	recToString( new InputBitStream( this.trie ), new MutableString(), s, new MutableString(), 0 );
			System.err.println( s );
		}
		
		class IterableStream implements Iterable<BitVector> {
			private InputBitStream ibs;
			private int n;
			private Object2LongFunction<BitVector> test;
			private LongBigList values;
			
			public IterableStream( final InputBitStream ibs, final int n, Object2LongFunction<BitVector> test, LongBigList values ) {
				this.ibs = ibs;
				this.n = n;
				this.test = test;
				this.values = values;
			}

			public Iterator<BitVector> iterator() {
				try {
					ibs.position( 0 );
					return new AbstractObjectIterator<BitVector>() {
						private int pos = 0;
						
						public boolean hasNext() {
							return pos < n;
						}

						public BitVector next() {
							if ( ! hasNext() ) throw new NoSuchElementException();
							try {
								final long index = ibs.readLong( 64 );
								final int pathLength = ibs.readDelta();
								int size;
								final long key[] = new long[ ( ( pathLength + Long.SIZE - 1 ) / Long.SIZE + 1 ) ];
								key[ 0 ] = index;
								for( int i = 0; i < ( pathLength + Long.SIZE - 1 ) / Long.SIZE; i++ ) {
									size = Math.min( Long.SIZE, pathLength - i * Long.SIZE );
									key[ i + 1 ] = ibs.readLong( size );
								}

								if ( DEBUG ) {
									System.err.println( "Adding mapping <" + index + ", " +  LongArrayBitVector.wrap( key, pathLength + Long.SIZE ).subVector( Long.SIZE ) + "> -> " + values.getLong( pos ));
									System.err.println(  LongArrayBitVector.wrap( key, pathLength + Long.SIZE ) );
								}

								if ( ASSERTS ) assert test.getLong( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ) ) == values.getLong( pos ) : test.getLong( LongArrayBitVector.wrap( key, pathLength + Long.SIZE ) ) + " != " + values.getLong( pos ) ;
								
								pos++;
								return LongArrayBitVector.wrap( key, pathLength + Long.SIZE );
							}
							catch ( IOException e ) {
								throw new RuntimeException( e );
							}
						}
					};
				}
				catch ( IOException e ) {
					throw new RuntimeException( e );
				}
			}
			
		};
		
		behaviour = new MWHCFunction<BitVector>( new IterableStream( new InputBitStream( "tmp.keys" ), numKeys, testFunction, immutableBinaryTrie.values ) ,TransformationStrategies.identity(), immutableBinaryTrie.values, 2 );
		lbehaviour = new MWHCFunction<BitVector>( new IterableStream( new InputBitStream( "tmp.lkeys" ), numLKeys, lTestFunction, immutableBinaryTrie.lvalues ) ,TransformationStrategies.identity(), immutableBinaryTrie.lvalues, 1 );

		if ( ASSERTS ) {
			if ( size > 0 ) {
				Iterator<BitVector>iterator = TransformationStrategies.wrap( elements.iterator(), transformationStrategy );
				int c = 0;
				while( iterator.hasNext() ) {
					BitVector curr = iterator.next();
					if ( DEBUG ) System.err.println( "Checking element number " + c + ( ( c + 1 ) % bucketSize == 0 ? " (bucket)" : "" ));
					long t = getLong( curr );
					assert t == c / bucketSize : t + " != " + c / bucketSize;
					c++;
				}		
			}
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( size == 0 ) return 0;
		final BitVector bitVector = transformationStrategy.toBitVector( (T)o ).fast();
		LongArrayBitVector key = LongArrayBitVector.getInstance();
		long p = 0, r = 0, length = bitVector.length(), index = 0, a = 0, b = 0, t;
		int s = 0, skip = 0, exit;
		boolean isInternal;
			
		if ( DEBUG ) System.err.println( "Distributing " + bitVector + "\ntrie:" + trie );
		
		for(;;) {
			isInternal = trie.getBoolean( p );
			if ( isInternal ) skip = (int)skips.getLong( r );
			// Just for testing!!
			//System.err.println( "Interrogating" + ( trie.getBoolean( p ) ? "" : " leaf" ) + " <" + p + ", " + bitVector.subVector( s, Math.min( length, s + skip ) ) + "> (skip: " + skip + ")" );
			exit = isInternal ? (int)behaviour.getLong( key.length( 0 ).append( p, Long.SIZE ).append( bitVector.subVector( s, Math.min( length, s + skip ) ) ) )
					: (int)lbehaviour.getLong( key.length( 0 ).append( p, Long.SIZE ).append( bitVector.subVector( s ) ) );
			
			if ( ASSERTS ) {
				final long result; 
				if ( isInternal ) result = testFunction.getLong( key.length( 0 ).append( p, Long.SIZE ).append( bitVector.subVector( s, Math.min( length, s + skip ) ) ) );
				else result = lTestFunction.getLong( key.length( 0 ).append( p, Long.SIZE ).append( bitVector.subVector( s ) ) ); 
				
				if ( result != -1 ) assert result == exit : result + " != " + exit; 
			}
			
			if ( DEBUG ) System.err.println( "Exit behaviour: " + exit );

			if ( exit != 2 || ! trie.getBoolean( p ) || ( s += skip ) >= length ) break;

			if ( DEBUG ) System.err.print( "Turning " + ( bitVector.getBoolean( s ) ? "right" : "left" ) + " at bit " + s + "... " );
			if ( bitVector.getBoolean( s ) ) p = 2 * r + 2;
			else p = 2 * r + 1;
			
			t = 2 * rank9.rank( a, b + 1 );
			a = b + 1;
			b += t;

			//System.err.println( "Rank incr: " + p + " " + a + " " +  rank9.rank( a, p ));
			
			index += p - a - rank9.rank( a, p );


			//System.err.println( a + " " + b + " " + p + " " + index );
			
			if ( ASSERTS ) assert p < trie.length();

			r = rank9.rank( p + 1 ) - 1;
			
			s++;
		}
		
		if ( exit == 0 ) {
			if ( DEBUG ) System.err.println( "Moving on the leftmost path at p=" + p + " (index=" + index + ")" );
			for(;;) {
				if ( ! trie.getBoolean( p ) ) break;

				t = 2 * rank9.rank( a, b + 1 );
				a = b + 1;
				b += t;

				p = 2 * r + 1;
				index += p - a - rank9.rank( a, p );
				r = rank9.rank( p + 1 ) - 1;

			}
		}
		else if ( exit == 1 ) {
			index++;
			
			if ( DEBUG ) System.err.println( "Moving on the rightmost path at p=" + p + " (index=" + index + ")" );
			for(;;) {
				if ( ! trie.getBoolean( p ) ) break;

				t = 2 * rank9.rank( a, b + 1 );
				a = b + 1;
				b += t;

				p = 2 * r + 2;
				index += p - a - rank9.rank( a, p );
				r = rank9.rank( p + 1 ) - 1;

			}
		}
		
		// System.out.println();
		// Complete computation of leaf index
		
		if ( DEBUG ) System.err.println( "Completing at p=" + p + " (index=" + index + ")" );
		
		for(;;) {
			r = rank9.rank( p + 1 );
			if ( r == 0 || ( p = select.select( r - 1 ) ) < a ) break;
			// We follow the leftmost or rightmost path, depending on where we jumped out.
			p = r * 2;
			
			t = 2 * rank9.rank( a, b + 1 );
			a = b + 1;
			b += t;
			
			index += p - a + 1 - rank9.rank( a, p + 1 );
			
			//System.err.println( "Scanning; " + a + " " + b + " " + p + " " + index );
		}

		if ( DEBUG ) System.err.println( "Returning " + index );
		
		return index;	
	}
	
	public long numBits() {
		return trie.length() + rank9.numBits() + skips.numBits() + select.numBits() + behaviour.numBits() + lbehaviour.numBits() + transformationStrategy.numBits();
	}
	
	public boolean containsKey( Object o ) {
		return true;
	}

	public int size() {
		return -1;
	}
}
