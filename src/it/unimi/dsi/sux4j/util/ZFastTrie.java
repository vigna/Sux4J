package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010 Sebastiano Vigna 
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
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.Hashes;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
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

/** A z-fast trie, that is, a predecessor/successor data structure using low linear (in the number of keys) additional space and
 * answering to the query string
 * <var>x</var> in time |<var>x</var>|/<var>w</var> + log(max{|<var>x</var>|, |<var>x</var><sup>-</sup>|, |<var>x</var><sup>+</sup>|}) with high probability,
 * where <var>w</var> is the machine word size, and <var>x</var><sup>-</sup>/<var>x</var><sup>+</sup> are the predecessor/successor of <var>x</var> in the currently stored set, respectively.
 * 
 * <p>In rough terms, the z-fast trie uses time |<var>x</var>|/<var>w</var> (which is optimal) to actually look at the string content,
 * and log(max{|<var>x</var>|, |<var>x</var><sup>-</sup>|, |<var>x</var><sup>+</sup>|}) to perform the search. This is known to be (essentially) optimal.
 * String lengths are up to {@link Integer#MAX_VALUE}, and not limited to be a constant multiple of <var>w</var> for the bounds to hold. 
 * 
 * <p>The linear overhead of a z-fast trie is very low. For <var>n</var> keys we allocate 2<var>n</var> &minus; 1 nodes containing six references and 
 * two longs, plus a dictionary containing <var>n</var> &minus; 1 nodes (thus using around 2<var>n</var> references and 2<var>n</var> longs).  
 * 
 */

public class ZFastTrie<T> extends AbstractObjectSortedSet<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( ZFastTrie.class );
	private static final boolean ASSERTS = false;
	private static final boolean SHORT_SIGNATURES = false;
	private static final boolean DEBUG = false;
	private static final boolean DDEBUG = DEBUG;

	/** The number of elements in the trie. */
	private int size;
	/** The root node. */
	private transient Node root;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A dictionary mapping handles to the corresponding internal nodes. */
	public transient Map map;
	/** The head of the doubly linked list of leaves. */
	private transient Leaf head;
	/** The tail of the doubly linked list of leaves. */
	private transient Leaf tail; 

	/** A linear-probing hash map that compares keys using signatures as a first try. */
	public final static class Map {
		private static final long serialVersionUID = 1L;
		private static final int INITIAL_LENGTH = 64;

		/** The transformation strategy. */
		private final TransformationStrategy<Object> transform;
		/** The node table. */
		private InternalNode[] node;
		/** The signature of the handle of the corresponding entry {@link #node}. */
		private long[] signature;
		/** An array parallel to  {@link #node} specifying whether a signature is a duplicate. 
		 * If true, there are more copies of the signature along the search path. */
		private boolean dup[];
		/** The number of elements in the table. */
		private int size;
		/** The number of slots in the table (always a power of two). */
		private int length;	
		/** {@link #length} &minus; 1. */
		private int mask;

		private void assertTable() {
			for( int i = signature.length; i-- != 0; ) if ( node[ i ] != null ) assert get( node[ i ].handle( transform ), true ) == node[ i ];
			if ( size == 0 ) return;
			final IntOpenHashSet overallHashes = new IntOpenHashSet();
			int start = 0;
			int first = -1;
			while( node[ start ] != null ) start = ( start + 1 ) & mask;
			// We are on an empty entry
			for( ;; ) {
				while( node[ start ] == null ) start = ( start + 1 ) & mask;
				// We are on a nonempty entry
				if ( first == -1 ) first = start;
				else if ( first == start ) break;
				int end = start;
				while( node[ end ] != null ) end = ( end + 1 ) & mask;
				// [start..end) is a maximal nonempty subsequence
				LongOpenHashSet signaturesSeen = new LongOpenHashSet();
				IntOpenHashSet hashesSeen = new IntOpenHashSet();

				for( int pos = end; pos != start; ) {
					pos = ( pos - 1 ) & mask;
					assert signaturesSeen.add( signature[ pos ] ) ^ dup[ pos ];
					hashesSeen.add( hash( signature[ pos ] ) );
				}
				
				// Hashes in each maximal nonempty subsequence must be disjoint
				for( IntIterator iterator = hashesSeen.iterator(); iterator.hasNext(); ) assert overallHashes.add( iterator.nextInt() );
				
				start = end;
			}
		}

		public Map( final int size, TransformationStrategy<Object> transform ) {
			this.transform = transform;
			length = Math.max( INITIAL_LENGTH, 1 << Fast.ceilLog2( 1 + ( 3L * size / 2 ) ) );
			mask = length - 1;
			signature = new long[ length ];
			node = new InternalNode[ length ];
			dup = new boolean[ length ];
		}

		public Map( TransformationStrategy<Object> transform ) {
			this.transform = transform;
			length = INITIAL_LENGTH;
			mask = length - 1;
			signature = new long[ length ];
			node = new InternalNode[ length ];
			dup = new boolean[ length ];
		}

		//public transient long probes = 0;
		//public transient long scans = 0;

		/** Generates a hash table position starting from the signature.
		 * 
		 * @param s a signature.
		 */
		private int hash( final long s ) {
			return (int)( s ^ s >>> 32 ) & mask;
		}
		
		/** Find the position in the table of a given handle using signatures.
		 * 
		 * <p>Note that this function just compares signatures (except for duplicates, which are
		 * checked explicitly). Thus, it might return false positives.
		 * 
		 * @param s the signature of the prefix of <code>v</code> of <code>handleLength</code> bits.
		 * @param v a bit vector.
		 * @param handleLength the length of the prefix of <code>v</code> that will be used as a handle.
		 * @return the position in the table where the specified handle can be found, or <code>null</code>.
		 */
		private int findPos( final long s, final BitVector v, final long handleLength ) {
			int pos = hash( s );
			while( node[ pos ] != null ) {
				if ( signature[ pos ] == s && ( ! dup[ pos ] ||
						handleLength == node[ pos ].handleLength() && v.longestCommonPrefixLength( node[ pos ].reference.key( transform ) ) >= handleLength ) )
					break;
				pos = ( pos + 1 ) & mask;
			}
			return pos;
		}
		
		/** Find the position in the table of a given handle using handles.
		 * 
		 * <p>Note that this function compares handles. Thus, it always returns a correct value.
		 * 
		 * @param s the signature of the prefix of <code>v</code> of <code>handleLength</code> bits.
		 * @param v a bit vector.
		 * @param handleLength the length of the prefix of <code>v</code> that will be used as a handle.
		 * @return the position in the table where the specified handle can be found, or <code>null</code>.
		 */
		private int findExactPos( final long s, final BitVector v, final long handleLength ) {
			int pos = hash( s );
			while( node[ pos ] != null ) {
				if ( signature[ pos ] == s &&
						handleLength == node[ pos ].handleLength() && v.longestCommonPrefixLength( node[ pos ].reference.key( transform ) ) >= handleLength )
					break;

				pos = ( pos + 1 ) & mask;
			}
			return pos;
		}
		
		public void clear() {
			length = INITIAL_LENGTH;
			mask = length - 1;
			size = 0;
			signature = new long[ length ];
			node = new InternalNode[ length ];
			dup = new boolean[ length ];
		}

		public ObjectSet<LongArrayBitVector> keySet() {
			return new AbstractObjectSet<LongArrayBitVector>() {

				@Override
				public ObjectIterator<LongArrayBitVector> iterator() {
					return new AbstractObjectIterator<LongArrayBitVector>() {
						private int i = 0;
						private int pos = -1;

						@Override
						public boolean hasNext() {
							return i < size;
						}

						@Override
						public LongArrayBitVector next() {
							if ( ! hasNext() ) throw new NoSuchElementException();
							while( node[ ++pos ] == null );
							i++;
							return LongArrayBitVector.copy( node[ pos ].handle( transform ) );
						}
					};
				}

				@Override
				public boolean contains( Object o ) {
					BitVector v = (BitVector)o;
					return get( v, true ) != null;
				}

				@Override
				public int size() {
					return size;
				}
				
			};
		}

		public ObjectSet<Node> values() {
			return new AbstractObjectSet<Node>() {

				@Override
				public ObjectIterator<Node> iterator() {
					return new AbstractObjectIterator<Node>() {
						private int i = 0;
						private int pos = -1;

						@Override
						public boolean hasNext() {
							return i < size;
						}

						@Override
						public Node next() {
							if ( ! hasNext() ) throw new NoSuchElementException();
							while( node[ ++pos ] == null );
							i++;
							return node[ pos ];
						}
					};
				}

				@Override
				public boolean contains( Object o ) {
					final Node node = (Node)o;
					return get( node.handle( transform ), true ) != null;
				}

				@Override
				public int size() {
					return size;
				}
			};
		}

		/** Replaces an entry with a given node.
		 * 
		 * @param newNode a node with a handle already existing in the table; the corresponding
		 * node will be replaced.
		 * @see #replace(long, InternalNode)
		 */
		public void replace( final InternalNode newNode ) {
			replace( newNode.handleHash( transform ), newNode );
		}

		/** Replaces an entry with a given node.
		 * 
		 * <p>Note that as long as the handle of <code>newNode</code> is actually in the
		 * table this function will always perform correctly. Otherwise, the result is unpredictable.
		 * 
		 * @param s the signature of <code>newNode</code>.
		 * @param newNode a node with a handle already appearing in the table; the corresponding
		 * node will be replaced.
		 */
		public void replace( long s, final InternalNode newNode ) {
			if ( SHORT_SIGNATURES ) s &= 0x3;
			final int pos = findPos( s, newNode.reference.key( transform ), newNode.handleLength() );
			if ( ASSERTS ) assert node[ pos ] != null;
			if ( ASSERTS ) assert node[ pos ].handle( transform ).equals( newNode.handle( transform ) ) : node[ pos ].handle( transform ) + " != " + newNode.handle( transform );
			node[ pos ] = newNode;
			if ( ASSERTS ) assertTable();
		}
		
		/** Removes an existing entry from the table.
		 * 
		 * <p>Note that as long as the given handle is actually in the
		 * table this function will always perform correctly. Otherwise, the result is unpredictable.
		 * 
		 * @param s the signature of the prefix of <code>v</code> of <code>handleLength</code> bits.
		 * @param v a bit vector.
		 * @param handleLength the length of the prefix of <code>v</code> that will be used as a handle.
		 * @return true if some key was removed, but if the given handle was not in the table another handle
		 * with the same signature might have been removed instead.
		 */
		public boolean remove( long s, final InternalNode v, final long handleLength ) {
			if ( DEBUG ) System.err.println( "Map.remove(" + s + ", " + v + ", " + handleLength + ")" );
			if ( SHORT_SIGNATURES ) s &= 0x3;
			final int hash = hash( s ); 
			
			int pos = hash;
			int lastDup = -1; // Keeps track of the last duplicate entry with the same signature.
			
			while( node[ pos ] != null ) {
				if ( signature[ pos ] == s ) {
					if ( ! dup[ pos ] || handleLength == node[ pos ].handleLength() && v.reference.key( transform ).longestCommonPrefixLength( node[ pos ].reference.key( transform ) ) >= handleLength ) break;
					else lastDup = pos;
				}
				pos = ( pos + 1 ) & mask;
			}

			// This should NOT happen, but let's return a sensible value anyway.
			if ( node[ pos ] == null ) return false;
			if ( ! dup[ pos ] && lastDup != -1 ) dup[ lastDup ] = false; // We are removing the only non-duplicate entry.
				
			// Move entries, compatibly with their hash code, to fill the hole.
		    int candidateHole, h;
		    do {
				candidateHole = pos;				
		    	// Find candidate for a move (possibly empty).
				do {
					pos = ( pos + 1 ) & mask;
					if ( node[ pos ] == null ) break;
					h = hash( node[ pos ].handleHash( transform ) );
					/* The hash h must lie cyclically between candidateHole and pos: more precisely, h must be after candidateHole
					 * but before the first free entry in the table (which is equivalent to the previous statement). */
				} while( candidateHole <= pos ? candidateHole < h && h <= pos : candidateHole < h || h <= pos );
				
				node[ candidateHole ] = node[ pos ];
				signature[ candidateHole ] = signature[ pos ];
				dup[ candidateHole ] = dup[ pos ];
		    } while( node[ pos ] != null );

			size--;
			if ( ASSERTS ) assertTable();
			return true;
		}

		/** Adds a new entry to the table.
		 * 
		 * @param v a node.
		 * 
		 * @see #addNew(long, InternalNode)
		 */
		public void addNew( final InternalNode v ) {
			addNew( v.handleHash( transform ), v );
		}
			
		/** Adds a new entry to the table.
		 * 
		 * <p>Note that as long as the handle of the given node is not in the
		 * table this function will always perform correctly. Otherwise, the result is unpredictable.
		 * 
		 * @param s the signature of the handle of <code>v</code>.
		 * @param v a node.
		 */
		public void addNew( long s, final InternalNode v ) {
			if ( SHORT_SIGNATURES ) s &= 0x3;
			if ( DEBUG ) System.err.println( "Map.addNew(" + s + ", " + v + ")" );
			int pos = hash( s );
			
			// Finds a free position, marking all keys with the same signature along the search path as duplicates.
			while( node[ pos ] != null ) {
				if ( signature[ pos ] == s ) dup[ pos ] = true;
				pos = ( pos + 1 ) & mask;
			}
			
			if ( ASSERTS ) assert node[ pos ] == null;
			
			size++;
			signature[ pos ] = s;
			node[ pos ] = v;

			if ( 3L * size > 2L * length ) {
				// Rehash.
				length *= 2;
				mask = length - 1;
				final long newKey[] = new long[ length ];
				final InternalNode[] newValue = new InternalNode[ length ];
				final boolean[] newDup = new boolean[ length ];
				final long[] key = this.signature;
				final InternalNode[] value = this.node;
				
				for( int i = key.length; i-- != 0; ) {
					if ( value[ i ] != null ) {
						s = key[ i ];
						pos = hash( s ); 
						while( newValue[ pos ] != null ) {
							if ( newKey[ pos ] == s ) newDup[ pos ] = true;
							pos = ( pos + 1 ) & mask;
						}
						newKey[ pos ] = key[ i ];
						newValue[ pos ] = value[ i ];
					}
				}

				this.signature = newKey;
				this.node = newValue;
				this.dup = newDup;
			}
			
			if ( ASSERTS ) assertTable();
		}

		public int size() {
			return size;
		}

		/** Retrives a node given its handle.
		 * 
		 * @param v a bit vector.
		 * @param handleLength the prefix of <code>v</code> that must be used as a handle.
		 * @param s the signature of the specified handle.
		 * @param exact whether the search should be exact; if false, and the given handle does not
		 * appear in the table, it is possible that a wrong node is returned.
		 * @return the node with given handle, or <code>null</code> if there is no such node.
		 */
		public InternalNode get( final BitVector v, final long handleLength, final long s, final boolean exact ) {
			if ( SHORT_SIGNATURES ) {
				final int pos = exact ? findExactPos( s & 0x3, v, handleLength ) : findPos( s & 0x3, v, handleLength );
				return node[ pos ];
			}
			else {
				final int pos = exact ? findExactPos( s, v, handleLength ) : findPos( s, v, handleLength );
				return node[ pos ];
			}
		}

		/** Retrives a node given its handle.
		 * 
		 * @param handle a handle.
		 * @param exact whether the search should be exact; if false, and the given handle does not
		 * appear in the table, it is possible that a wrong node is returned.
		 * @return the node with given handle, or <code>null</code> if there is no such node.
		 * @see #get(BitVector, long, long, boolean)
		 */
		public InternalNode get( final BitVector handle, final boolean exact ) {
			return get( handle, handle.length(), Hashes.murmur( handle, 0 ), exact );
		}
		
		public String toString() {
			StringBuilder s = new StringBuilder();
			s.append( '{' );
			for( LongArrayBitVector v: keySet() ) s.append( v ).append( " => " ).append( get( v, false ) ).append( ", " );
			if ( s.length() > 1 ) s.setLength( s.length() - 2 );
			s.append( '}' );
			return s.toString();
		}
	}

	/** A node of the trie. */
	protected abstract static class Node {
		/** The length of the extent of the parent node, or 0 for the root. */
		protected long parentExtentLength;

		public boolean isLeaf() {
			return this instanceof Leaf;
		}
		
		public boolean isInternal() {
			return this instanceof InternalNode;
		}

		public long nameLength() {
			return parentExtentLength == 0 ? 0 : parentExtentLength + 1;
		}

		public long handleLength( TransformationStrategy<Object> transform ) {
			return twoFattest( parentExtentLength, extentLength( transform ) );
		}


		public abstract BitVector key( TransformationStrategy<Object> transform );
		public abstract BitVector handle( TransformationStrategy<Object> transform );
		public abstract long extentLength( TransformationStrategy<Object> transform );
		public abstract BitVector extent( TransformationStrategy<Object> transform );
		public abstract boolean intercepts( final long h );

		public long handleHash(  TransformationStrategy<Object> transform ) {
			if ( SHORT_SIGNATURES ) return Hashes.murmur( handle( transform ), 0 ) & 0x3;
			else return Hashes.murmur( handle( transform ), 0 );
		}

		/** Returns true if this node is the exit node of a string.
		 * 
		 * @param v the string.
		 * @param transform the transformation strategy used to build the trie this node belongs to.
		 * @return true if the string exits at this node.
		 */
		public boolean isExitNodeOf( final LongArrayBitVector v, TransformationStrategy<Object> transform ) {
			return isExitNodeOf( v.length(), v.longestCommonPrefixLength( extent( transform ) ), transform );
		}

		/** Returns true if this node is the exit node of a string given its length and the length of the longest
		 * common prefix with the node extent.
		 * 
		 * @param length the length of a string.
		 * @param lcpLength the length of the longest common prefix between the string and the extent of this node.
		 * @return true if the string exits at this node.
		 */
		public boolean isExitNodeOf( final long length, final long lcpLength, TransformationStrategy<Object> transform  ) {
			return parentExtentLength < lcpLength && ( lcpLength < extentLength( transform ) || lcpLength == length );
		}

		public String toString() {
			final TransformationStrategy transform = TransformationStrategies.prefixFreeIso();
			final long extentLength = extentLength( transform );
			return ( isLeaf() ? "[" : "(" ) + Integer.toHexString( hashCode() & 0xFFFF ) + 
				( key( transform ) == null ? "" : 
					" " + ( extentLength > 16 ? key( transform ).subVector( 0, 8 ) + "..." + key(  transform ).subVector( extentLength - 8, extentLength ): key( transform ).subVector( 0, extentLength ) ) ) +
					" (" + parentExtentLength + ".." + extentLength + "], " + handleLength( transform ) + "->" + // TODO jumpLength() +
				( isLeaf() ? "]" : ")" );
		}
	}
	
	/** A node of the trie. */
	protected final static class InternalNode extends Node {
		/** The length of the extent (for leaves, this is equal to the length of the transformed {@link #key}). */
		protected long extentLength;
		/** The left subtree. */
		protected Node left;
		/** The right subtree. */
		protected Node right;
		/** The jump pointer for the left path for internal nodes; <code>null</code>, otherwise (this
		 * makes leaves distinguishable). */
		protected Node jumpLeft;
		/** The jump pointer for the right path for internal nodes; <code>null</code>, otherwise. */
		protected Node jumpRight;
		/** The leaf whose key this node refers to for internal nodes; the internal node that
		 * refers to the key of this leaf, otherwise. Will be <code>null</code> for exactly one leaf. */
		protected Leaf reference;

		public long handleLength() {
			return twoFattest( parentExtentLength, extentLength );
		}

		public long jumpLength() {
			final long handleLength = handleLength();
			return handleLength + ( handleLength & -handleLength );
		}

		public boolean isLeaf() {
			return false;
		}
		
		public boolean isInternal() {
			return true;
		}
		
		public boolean intercepts( final long h ) {
			return h > parentExtentLength && h <= extentLength;
		}

		public BitVector extent( TransformationStrategy<Object> transform ) {
			return reference.key( transform ).subVector( 0, extentLength );
		}

		public long extentLength( final TransformationStrategy<Object> transform ) {
			return extentLength;
		}
				
		@Override
		public BitVector key( TransformationStrategy<Object> transform ) {
			return reference.key( transform );
		}

		public BitVector handle( TransformationStrategy<Object> transform ) {
			return reference.key( transform ).subVector( 0, handleLength() );
		}
	}		

	/** A node of the trie. */
	protected final static class Leaf extends Node {
		/** The previous leaf. */
		protected Leaf prev;
		/** The next leaf. */
		protected Leaf next;
		/** The key upon which the extent of node is based, for internal nodes; the 
		 * key associated to a leaf, otherwise. */
		protected CharSequence key;
		/** The leaf whose key this node refers to for internal nodes; the internal node that
		 * refers to the key of this leaf, otherwise. Will be <code>null</code> for exactly one leaf. */
		protected InternalNode reference;

		public BitVector handle( TransformationStrategy<Object> transform ) {
			return reference.key( transform ).subVector( 0, handleLength( transform ) );
		}
		public boolean isLeaf() {
			return true;
		}
		
		public boolean isInternal() {
			return false;
		}
		
		public boolean intercepts( final long h ) {
			return h > parentExtentLength;
		}

		public BitVector extent( final TransformationStrategy<Object> transform ) {
			return key( transform );
		}
				
		public long extentLength( final TransformationStrategy<Object> transform ) {
			return transform.length( key );
		}
				
		@Override
		public BitVector key( final TransformationStrategy<Object> transform ) {
			return transform.toBitVector( key );
		}

	}

	/** Creates a new z-fast trie using the given transformation strategy. 
	 * 
	 * @param transform a transformation strategy that must turn distinct elements into distinct, prefix-free bit vectors.
	 */
	@SuppressWarnings("unchecked")
	public ZFastTrie( final TransformationStrategy<? super T> transform ) {
		this.transform = transform;
		this.map = new Map( (TransformationStrategy<Object>)transform );
		initHeadTail();
	}
	
	private void initHeadTail() {
		head = new Leaf();
		tail = new Leaf();
		head.next = tail;
		tail.prev = head;
	}

	/** Creates a new z-fast trie using the given elements and transformation strategy. 
	 * 
	 * @param elements an iterator returning the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn distinct elements into distinct, prefix-free bit vectors.
	 */
	public ZFastTrie( final Iterator<? extends T> elements, final TransformationStrategy<? super T> transform ) {
		this( transform );
		while( elements.hasNext() ) add( elements.next() );
	}

	/** Creates a new z-fast trie using the given elements and transformation strategy. 
	 * 
	 * @param elements an iterator returning the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn distinct elements into distinct, prefix-free bit vectors.
	 */
	public ZFastTrie( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) {
		this( elements.iterator(), transform );
	}

	public int size() {
		return size > Integer.MAX_VALUE ? -1 : (int)size;
	}
	
	/** Returns the 2-fattest number in an interval.
	 *
	 * <p>Note that to get the length of the handle of a node you must
	 * call this function passing the length of the extent of the parent (one less
	 * than the node name) and the length of the extent of the node.
	 * 
	 * @param l left extreme (excluded).
	 * @param r right extreme (included).
	 * @return the 2-fattest number in (<code>l</code>..<code>r</code>].
	 */
	private final static long twoFattest( final long l, final long r ) {
		return ( -1L << Fast.mostSignificantBit( l ^ r ) & r );
	}
	
	private static void removeLeaf( final Leaf node ) {
		node.next.prev = node.prev;
		node.prev.next = node.next;
	}
	
	private static void addAfter( final Leaf pred, final Leaf node ) {
		node.next = pred.next;
		node.prev = pred;
		pred.next.prev = node;
		pred.next = node;
	}
	
	private static void addBefore( final Leaf succ, final Leaf node ) {
		node.prev = succ.prev;
		node.next = succ;
		succ.prev.next = node;
		succ.prev = node;
	}
	
	private void assertTrie() {
		/* Shortest key */
		LongArrayBitVector root = null;
		/* Keeps track of which nodes in map are reachable using left/right from the root. */
		ObjectOpenHashSet<Node> nodes = new ObjectOpenHashSet<Node>();
		/* Keeps track of leaves. */
		ObjectOpenHashSet<Leaf> leaves = new ObjectOpenHashSet<Leaf>();
		/* Keeps track of reference to leaf keys in internal nodes. */
		ObjectOpenHashSet<Object> references = new ObjectOpenHashSet<Object>();
		
		assert size == 0 && map.size() == 0 || size == map.size() + 1;
		
		/* Search for the root (shortest handle) and check that nodes and handles do match. */
		for( LongArrayBitVector v : map.keySet() ) {
			final long vHandleLength = map.get( v, false ).handleLength();
			if ( root == null || map.get( root, false ).handleLength() > vHandleLength ) root = v;
			final InternalNode node = map.get( v, false );
			nodes.add( node );
			assert node.reference.reference == node : node + " -> " + node.reference + " -> " + node.reference.reference;
		}
		
		assert nodes.size() == map.size() : nodes.size() + " != " + map.size();
		assert size < 2 || this.root == map.get( root, false );
		
		if ( size > 1 ) {
			/* Verify doubly linked list of leaves. */
			Leaf toRight = head.next, toLeft = tail.prev;
			for( int i = 1; i < size; i++ ) {
				assert new MutableString( toRight.key ).compareTo( toRight.next.key ) < 0 : toRight.key + " >= " + toRight.next.key + " " + toRight + " " + toRight.next;
				assert new MutableString( toLeft.key ).compareTo( toLeft.prev.key ) > 0 : toLeft.key + " >= " + toLeft.prev.key + " " + toLeft + " " + toLeft.prev;
				toRight = toRight.next;
				toLeft = toLeft.prev;
			}

			final int numNodes = visit( map.get( root, false ), null, 0, 0, nodes, leaves, references );
			assert numNodes == 2 * size - 1 : numNodes + " != " + ( 2 * size - 1 );
			assert leaves.size() == size;
			int c = 0;

			for( Leaf leaf: leaves ) if ( references.contains( leaf.key ) ) c++;

			assert c == size - 1 : c + " != " + ( size - 1 );
		}
		else if ( size == 1 ) {
			assert head.next == this.root;
			assert tail.prev == this.root;
		}
		assert nodes.isEmpty();
	}
	
	private int visit( final Node n, final Node parent, final long parentExtentLength, final int depth, ObjectOpenHashSet<Node> nodes, ObjectOpenHashSet<Leaf> leaves, ObjectOpenHashSet<Object> references ) {
		if ( n == null ) return 0;
		if ( DEBUG ) {
			for( int i = depth; i-- != 0; ) System.err.print( '\t' );
			System.err.println( "Node " + n + " (parent extent length: " + parentExtentLength + ")" + ( n.isInternal() ? " Jump left: " + ((InternalNode)n).jumpLeft + " Jump right: " + ((InternalNode)n).jumpRight : "" ) );
		}

		assert parent == null || parent.extent( (TransformationStrategy<Object>)transform ).equals( n.extent( (TransformationStrategy<Object>)transform ).subVector( 0, ((InternalNode)parent).extentLength ) );
		assert parentExtentLength < n.extentLength( (TransformationStrategy<Object>)transform );
		assert n.parentExtentLength == parentExtentLength : n.parentExtentLength + " != " + parentExtentLength + " " + n;
		
		if ( n.isInternal() ) {
			assert references.add( ((InternalNode)n).reference.key );
			assert nodes.remove( n ) : n;
			assert map.keySet().contains( n.handle( (TransformationStrategy<Object>)transform ) ) : n;

			/* Check that jumps are correct. */
			final long jumpLength = ((InternalNode)n).jumpLength();
			Node jumpLeft = ((InternalNode)n).left;
			while( jumpLeft.isInternal() && jumpLength > ((InternalNode)jumpLeft).extentLength ) jumpLeft = ((InternalNode)jumpLeft).left;
			assert jumpLeft == ((InternalNode)n).jumpLeft : jumpLeft + " != " + ((InternalNode)n).jumpLeft + " (node: " + n + ")";

			Node jumpRight = ((InternalNode)n).right;
			while( jumpRight.isInternal() && jumpLength > ((InternalNode)jumpRight).extentLength ) jumpRight = ((InternalNode)jumpRight).right;
			assert jumpRight == ((InternalNode)n).jumpRight : jumpRight + " != " + ((InternalNode)n).jumpRight + " (node: " + n + ")";
			return 1 + visit( ((InternalNode)n).left, n, ((InternalNode)n).extentLength, depth + 1, nodes, leaves, references ) + visit( ((InternalNode)n).right, n, n.extentLength( (TransformationStrategy<Object>)transform ), depth + 1, nodes, leaves, references );
		}
		else {
			assert leaves.add( (Leaf)n );
			assert n.extentLength( (TransformationStrategy<Object>)transform ) == n.key( (TransformationStrategy<Object>)transform ).length();
			return 1;
		}
	}

	/** Sets the jump pointers of a node by searching exhaustively for
	 * handles that are jumps of the node handle length.
	 * 
	 * @param node the node whose jump pointers must be set.
	 */
	private static void setJumps( final InternalNode node ) {
		if ( DEBUG ) System.err.println( "setJumps(" + node + ")" );
		final long jumpLength = node.jumpLength();
		Node jump;

		for( jump = node.left; jump.isInternal() && jumpLength > ((InternalNode)jump).extentLength; ) jump = ((InternalNode)jump).jumpLeft;
		if ( ASSERTS ) assert jump.intercepts( jumpLength ) : jumpLength + " not in " + "(" + jump.parentExtentLength + ".." + ((InternalNode)jump).extentLength + "] " + jump;
		node.jumpLeft = jump;
		for( jump = node.right; jump.isInternal() && jumpLength > ((InternalNode)jump).extentLength; ) jump = ((InternalNode)jump).jumpRight;
		if ( ASSERTS ) assert jump.intercepts( jumpLength ) : jumpLength + " not in " + "(" + jump.parentExtentLength + ".." + ((InternalNode)jump).extentLength + "] " + jump;
		node.jumpRight = jump;
	}

	/** Fixes the right jumps of the ancestors of a node after an insertion.
	 * 
	 * @param exitNode the exit node.
	 * @param rightChild 
	 * @param leaf the new leaf.
	 * @param stack a stack containing the 2-fat ancestors of <code>exitNode</code>.
	 */
	private static void fixRightJumpsAfterInsertion( final InternalNode internal, Node exitNode, boolean rightChild, Leaf leaf, final ObjectArrayList<InternalNode> stack ) {
		if ( DEBUG ) System.err.println( "fixRightJumpsAfterInsertion(" + internal + ", " + exitNode + ", " + rightChild + ", " + leaf + ", " + stack ); 
		final long lcp = leaf.parentExtentLength;
		InternalNode toBeFixed = null;
		long jumpLength = -1;

		if ( ! rightChild ) {
			/* Nodes jumping to the left into the exit node but above the lcp must point to internal. */
			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpLeft != exitNode ) break;
				if ( jumpLength <= lcp ) toBeFixed.jumpLeft = internal;
			}
		}
		else {
			while( ! stack.isEmpty() ) {
				toBeFixed = stack.top();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpRight != exitNode || jumpLength > lcp ) break;
				toBeFixed.jumpRight = internal;
				stack.pop();
			}

			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				jumpLength = toBeFixed.jumpLength();
				while( exitNode.isInternal() && toBeFixed.jumpRight != exitNode ) exitNode = ((InternalNode)exitNode).jumpRight;
				if ( toBeFixed.jumpRight != exitNode ) return;
				toBeFixed.jumpRight = leaf;
			}
		}
	}
	
	/** Fixes the left jumps of the ancestors of a node after an insertion.
	 * 
	 * @param exitNode the exit node.
	 * @param rightChild 
	 * @param leaf the new leaf.
	 * @param stack a stack containing the fat ancestors of <code>exitNode</code>.
	 */
	private static void fixLeftJumpsAfterInsertion( final InternalNode internal, Node exitNode, boolean rightChild, Leaf leaf, final ObjectArrayList<InternalNode> stack ) {
		if ( DEBUG ) System.err.println( "fixLeftJumpsAfterInsertion(" + internal + ", " + exitNode + ", " + rightChild + ", " + leaf + ", " + stack ); 
		final long lcp = leaf.parentExtentLength;
		InternalNode toBeFixed = null;
		long jumpLength = -1;
		
		if ( rightChild ) {
			/* Nodes jumping to the right into the exit node but above the lcp must point to internal. */
			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpRight != exitNode ) break;
				if ( jumpLength <= lcp ) toBeFixed.jumpRight = internal;
			}
		}
		else {

			while( ! stack.isEmpty() ) {
				toBeFixed = stack.top();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpLeft != exitNode || jumpLength > lcp ) break;
				toBeFixed.jumpLeft = internal;
				stack.pop();
			}

			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				jumpLength = toBeFixed.jumpLength();
				while( exitNode.isInternal() && toBeFixed.jumpLeft != exitNode ) exitNode = ((InternalNode)exitNode).jumpLeft;
				if ( toBeFixed.jumpLeft != exitNode ) return;
				toBeFixed.jumpLeft = leaf;
			}
		}
	}

	/** Fixes the right jumps of the ancestors of a node after a deletion.
	 * 
	 * @param parentExitNode the exit node.
	 * @param rightChild 
	 * @param exitNode the new leaf.
	 * @param stack a stack containing the 2-fat ancestors of <code>exitNode</code>.
	 */
	private static void fixRightJumpsAfterDeletion( Node otherNode, InternalNode parentExitNode, boolean rightChild, Leaf exitNode, final ObjectArrayList<InternalNode> stack ) {
		if ( DEBUG ) System.err.println( "fixRightJumpsAfterDeletion(" + otherNode + ", " + parentExitNode + ", " + rightChild + ", " + exitNode + ", " + stack );
		InternalNode toBeFixed = null;
		long jumpLength = -1;

		if ( ! rightChild ) {
			/* Nodes jumping to the left into the exit node but above the lcp must point to internal. */
			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpLeft != parentExitNode ) break;
				toBeFixed.jumpLeft = otherNode;
			}
		}
		else {
			while( ! stack.isEmpty() ) {
				toBeFixed = stack.top();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpRight != parentExitNode ) break;
				toBeFixed.jumpRight = otherNode;
				stack.pop();
			}

			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				if ( toBeFixed.jumpRight != exitNode ) break;
				jumpLength = toBeFixed.jumpLength();
				while( ! otherNode.intercepts( jumpLength ) ) otherNode = ((InternalNode)otherNode).jumpRight;
				toBeFixed.jumpRight = otherNode;
			}
		}
	}

	/** Fixes the left jumps of the ancestors of a node after a deletion.
	 * 
	 * @param parentExitNode the exit node.
	 * @param rightChild 
	 * @param exitNode the new leaf.
	 * @param stack a stack containing the 2-fat ancestors of <code>exitNode</code>.
	 */
	private static void fixLeftJumpsAfterDeletion( Node otherNode, InternalNode parentExitNode, boolean rightChild, Leaf exitNode, final ObjectArrayList<InternalNode> stack ) {
		if ( DEBUG ) System.err.println( "fixLeftJumpsAfterDeletion(" + otherNode + ", " + parentExitNode + ", " + rightChild + ", " + exitNode + ", " + stack );
		InternalNode toBeFixed = null;
		long jumpLength = -1;

		if ( rightChild ) {
			/* Nodes jumping to the left into the exit node but above the lcp must point to internal. */
			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpRight != parentExitNode ) break;
				toBeFixed.jumpRight = otherNode;
			}
		}
		else {
			while( ! stack.isEmpty() ) {
				toBeFixed = stack.top();
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpLeft != parentExitNode ) break;
				toBeFixed.jumpLeft = otherNode;
				stack.pop();
			}

			while( ! stack.isEmpty() ) {
				toBeFixed = stack.pop();
				if ( toBeFixed.jumpLeft != exitNode ) break;
				jumpLength = toBeFixed.jumpLength();
				while( ! otherNode.intercepts( jumpLength ) ) otherNode = ((InternalNode)otherNode).jumpLeft;
				toBeFixed.jumpLeft = otherNode;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public boolean remove( final Object k ) {
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)k ) );
		if ( DEBUG ) System.err.println( "remove(" + v + ")" );
		if ( DDEBUG ) System.err.println( "Map: " + map + " root: " + root );
		
		if ( size == 0 ) return false;
		
		if ( size == 1 ) {
			if ( ! ((Leaf)root).key.equals( k ) ) return false;
			root = null;
			size = 0;
			if ( ASSERTS ) assertTrie();
			return true;
		}

		final ObjectArrayList<InternalNode> stack = new ObjectArrayList<InternalNode>( 64 );
		
		InternalNode parentExitNode;
		boolean rightLeaf, rightChild = false;
		Node exitNode;
		long lcp;

		final long[] state = Hashes.preprocessMurmur( v, 0 );

		ParexData parexData = getParentExitNode( v, state, stack );
		parentExitNode = parexData.parexNode;
		exitNode = parexData.exitNode;
		lcp = parexData.lcp;
		rightLeaf = parentExitNode != null && parentExitNode.right == exitNode;
		
		if ( DDEBUG ) System.err.println( "Parex node: " + parentExitNode + " Exit node: " + exitNode  + " LCP: " + lcp );
		
		if ( ! ( exitNode.isLeaf() && ((Leaf)exitNode).key.equals( k ) ) ) return false; // Not found
		
		final Node otherNode = rightLeaf ? parentExitNode.left : parentExitNode.right;
		final boolean otherNodeIsInternal = otherNode.isInternal();

		if ( parentExitNode != null && parentExitNode != root ) {
			// Let us fix grandpa's child pointer.
			InternalNode grandParentExitNode = getGrandParentExitNode( v, state, stack );
			if ( rightChild = ( grandParentExitNode.right == parentExitNode ) )  grandParentExitNode.right = otherNode;
			else grandParentExitNode.left = otherNode;
		}		

		final long parentExitNodehandleLength = parentExitNode.handleLength();
		final long otherNodeHandleLength = otherNode.handleLength( (TransformationStrategy<Object>)transform );
		final long t = parentExitNodehandleLength | otherNodeHandleLength;
		final boolean cutLow = ( t & -t & otherNodeHandleLength ) != 0;


		if ( parentExitNode == root ) root = otherNode;

		// Fix leaf reference if not null
		final InternalNode refersToExitNode = ((Leaf)exitNode).reference;
		if ( refersToExitNode == null ) parentExitNode.reference.reference = null;
		else {
			refersToExitNode.reference = parentExitNode.reference;
			refersToExitNode.reference.reference = refersToExitNode;
		}

		// Fix doubly-linked list
		removeLeaf( (Leaf)exitNode );

		if ( DDEBUG ) System.err.println( "Cut " + ( cutLow ? "low" : "high") + "; leaf on the " + ( rightLeaf ? "right" : "left") + "; other node is " + ( otherNodeIsInternal ? "internal" : "a leaf") );
		
		if ( rightLeaf ) fixRightJumpsAfterDeletion( otherNode, parentExitNode, rightChild, (Leaf)exitNode, stack );
		else fixLeftJumpsAfterDeletion( otherNode, parentExitNode, rightChild, (Leaf)exitNode, stack );
		
		if ( cutLow && otherNodeIsInternal ) {
			map.remove( Hashes.murmur( otherNode.key( (TransformationStrategy<Object>)transform ), otherNodeHandleLength, state, parentExitNode.extentLength ), (InternalNode)otherNode, otherNodeHandleLength );
			otherNode.parentExtentLength = parentExitNode.parentExtentLength;
			map.replace( Hashes.murmur( v, parentExitNodehandleLength, state ), (InternalNode)otherNode );
			setJumps( (InternalNode)otherNode );
		}
		else {
			otherNode.parentExtentLength = parentExitNode.parentExtentLength;
			map.remove( Hashes.murmur( v, parentExitNodehandleLength, state ), parentExitNode, parentExitNodehandleLength );
		}
	
		size--;

		if ( ASSERTS ) {
			assertTrie();
			assert ! contains( k );
		}
		return true;
	}
	
	@Override
	public boolean add( final T k ) {
		if ( DEBUG ) System.err.println( "add(" + k + ")" );
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( k ) );
		if ( DEBUG ) System.err.println( "add(" + v + ")" );
		if ( DEBUG ) System.err.println( "Map: " + map + " root: " + root );
		
		if ( size == 0 ) {
			root = new Leaf();
			((Leaf)root).key = (CharSequence)k;
			root.parentExtentLength = 0;
			((Leaf)root).reference = null;
			addAfter( head, (Leaf)root );
			size++;
			if ( ASSERTS ) assertTrie();
			return true;
		}

		final ObjectArrayList<InternalNode> stack = new ObjectArrayList<InternalNode>( 64 );
		
		InternalNode parentExitNode;
		boolean rightChild;
		Node exitNode;
		long lcp;

		final long[] state = Hashes.preprocessMurmur( v, 0 );

		ParexData parexData = getParentExitNode( v, state, stack );
		parentExitNode = parexData.parexNode;
		exitNode = parexData.exitNode;
		lcp = parexData.lcp;
		rightChild = parentExitNode != null && parentExitNode.right == exitNode;
		
		if ( DDEBUG ) System.err.println( "Parex node: " + parentExitNode + " Exit node: " + exitNode  + " LCP: " + lcp );
				
		if ( exitNode.isLeaf() && ((Leaf)exitNode).key.equals( k ) ) return false; // Already there
		
		final boolean exitDirection = v.getBoolean( lcp );
		final long exitNodeHandleLength = exitNode.handleLength( (TransformationStrategy<Object>)transform );
		final boolean cutLow = lcp >= exitNodeHandleLength;
		
		Leaf leaf = new Leaf();
		InternalNode internal = new InternalNode();

		final boolean exitNodeIsInternal = exitNode.isInternal();
		
		leaf.key = (CharSequence)k;
		leaf.parentExtentLength = lcp;
		leaf.reference = internal;
		
		internal.reference = leaf;
		internal.parentExtentLength = exitNode.parentExtentLength;
		internal.extentLength = lcp;

		if ( exitDirection ) {
			internal.jumpRight = internal.right = leaf;
			internal.left = exitNode;
			internal.jumpLeft = cutLow && exitNodeIsInternal ? ((InternalNode)exitNode).jumpLeft : exitNode;
		}
		else {
			internal.jumpLeft = internal.left = leaf;
			internal.right = exitNode;
			internal.jumpRight = cutLow && exitNodeIsInternal ? ((InternalNode)exitNode).jumpRight : exitNode;
		}

		if ( exitNode == root ) root = internal; // Update root
		else {
			if ( rightChild ) parentExitNode.right = internal;
			else parentExitNode.left = internal;
		}

		if ( DDEBUG ) System.err.println( "Cut " + ( cutLow ? "low" : "high") + "; exit to the " + ( exitDirection ? "right" : "left") );

		if ( exitDirection ) fixRightJumpsAfterInsertion( internal, exitNode, rightChild, leaf, stack );
		else fixLeftJumpsAfterInsertion( internal, exitNode, rightChild, leaf, stack );

		if ( cutLow && exitNodeIsInternal ) {
			map.replace( Hashes.murmur( v, exitNodeHandleLength, state ), internal );
			exitNode.parentExtentLength = lcp;
			map.addNew( Hashes.murmur( exitNode.key( (TransformationStrategy<Object>)transform ), exitNode.handleLength( (TransformationStrategy<Object>)transform ), state, lcp ), (InternalNode)exitNode );
			setJumps( (InternalNode)exitNode );
		}
		else {
			exitNode.parentExtentLength = lcp;
			map.addNew( Hashes.murmur( v, internal.handleLength(), state ), internal );
		}

		
		if ( DEBUG ) System.err.println( "After insertion, map: " + map + " root: " + root );

		size++;

		/* We find a predecessor or successor to insert the new leaf in the doubly linked list. */
		if ( exitDirection ) {
			while( exitNode.isInternal() ) exitNode = ((InternalNode)exitNode).jumpRight;
			addAfter( (Leaf)exitNode, leaf );
		}
		else {
			while( exitNode.isInternal() ) exitNode = ((InternalNode)exitNode).jumpLeft;
			addBefore( (Leaf)exitNode, leaf );
		}
		
		if ( ASSERTS ) assertTrie();
		if ( ASSERTS ) assert contains( k );
		
		return true;
	}

	/** Returns the exit node of a given bit vector.
	 * 
	 * @param v a bit vector.
	 * @param state the hash state of <code>v</code> precomputed by {@link Hashes#preprocessMurmur(BitVector, long)}.
	 * @return the exit node of <code>v</code>. 
	 */
	private Node getExitNode( final LongArrayBitVector v, final long[] state ) {
		if ( size == 0 ) throw new IllegalStateException();
		if ( size == 1 ) return root;
		if ( DDEBUG ) System.err.println( "getExitNode(" + v + ")" );
		final long length = v.length();
		// This can be the exit node of v, the parex node of v, or something completely wrong.
		InternalNode parexOrExitNode = fatBinarySearch( v, state, null, false, 0, length );
		// This will contain the exit node if parexOrExitNode contains the correct parex node.
		Node candidateExitNode;
		
		if ( parexOrExitNode == null ) candidateExitNode = root;
		else candidateExitNode = parexOrExitNode.extentLength < length && v.getBoolean( parexOrExitNode.extentLength ) ? parexOrExitNode.right : parexOrExitNode.left;
		/* This lcp length makes it possible to compute the length of the lcp between v and 
		 * parexOrExitNode by minimisation with the extent length, as necessarily the extent of 
		 * candidateExitNode is an extension of the extent of parexOrExitNode. */
		final long lcpLength = v.longestCommonPrefixLength( candidateExitNode.extent( (TransformationStrategy<Object>)transform ) );
		
		// In this case the fat binary search gave us the correct parex node.
		if ( candidateExitNode.isExitNodeOf( length, lcpLength, (TransformationStrategy<Object>)transform ) ) return candidateExitNode;
		// In this case the fat binary search gave us the correct exit node.
		if ( parexOrExitNode.isExitNodeOf( length, Math.min( parexOrExitNode.extentLength, lcpLength ), (TransformationStrategy<Object>)transform ) ) return parexOrExitNode;

		// Otherwise, something went horribly wrong. We restart in exact mode.
		parexOrExitNode = fatBinarySearch( v, state, null, true, 0, length );
		if ( parexOrExitNode == null ) return root;
		// TODO: In principle we can check just the compacted path.
		return parexOrExitNode.extent( (TransformationStrategy<Object>)transform ).isProperPrefix( v ) ?
				parexOrExitNode.extentLength < length && v.getBoolean( parexOrExitNode.extentLength ) ? parexOrExitNode.right : parexOrExitNode.left : parexOrExitNode;
	}

	
	protected final static class ParexData {
		long lcp;
		InternalNode parexNode;
		Node exitNode;

		protected ParexData( final InternalNode parexNode, final Node exitNode, final long lcp ) {
			this.lcp = lcp;
			this.parexNode = parexNode;
			this.exitNode = exitNode;
		}
	}

	/** Returns the parent of the exit node of a given bit vector.
	 * 
	 * @param v a bit vector.
	 * @param state the hash state of <code>v</code> precomputed by {@link Hashes#preprocessMurmur(BitVector, long)}.
	 * @param stack if not <code>null</code>, a stack that will be filled with the <em>fat nodes</em> along the path to the parent of the exit node.
	 * @return the parent of the exit node of <code>v</code>, or <code>null</code> if the exit node is the root.
	 */
	public ParexData getParentExitNode( final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode> stack ) {
		if ( size == 0 ) throw new IllegalStateException();
		if ( size == 1 ) return new ParexData( null, root, v.longestCommonPrefixLength( root.extent( (TransformationStrategy<Object>)transform ) ) );
		
		final long length = v.length();
		// This can be the exit node of v, the parex node of v, or something completely wrong.
		InternalNode parexOrExitNode = fatBinarySearch( v, state, stack, false, 0, length );
		// This will contain the exit node if parexOrExitNode contains the correct parex node.
		Node candidateExitNode;
		
		if ( parexOrExitNode == null ) candidateExitNode = root;
		else candidateExitNode = parexOrExitNode.extentLength < length && v.getBoolean( parexOrExitNode.extentLength ) ? parexOrExitNode.right : parexOrExitNode.left;
		/* This lcp length makes it possible to compute the length of the lcp between v and 
		 * parexOrExitNode by minimisation with the extent length, as necessarily the extent of 
		 * candidateExitNode is an extension of the extent of parexOrExitNode. */
		long lcpLength = v.longestCommonPrefixLength( candidateExitNode.extent( (TransformationStrategy<Object>)transform ) );
		
		// In this case the fat binary search gave us the correct parex node, and we have all the data we need.
		if ( candidateExitNode.isExitNodeOf( length, lcpLength, (TransformationStrategy<Object>)transform ) ) return new ParexData( parexOrExitNode, candidateExitNode, lcpLength );
		// Now this is the length of the longest common prefix between v and the extent of parexOrExitNode.
		lcpLength = Math.min( parexOrExitNode.extentLength, lcpLength );
		
		if ( parexOrExitNode.isExitNodeOf( length, lcpLength, (TransformationStrategy<Object>)transform ) ) {
			// In this case the fat binary search gave us the correct *exit* node. We must pop it from the stack and maybe restart the search.
			stack.pop();
			final long startingPoint = stack.isEmpty() ? 0 : stack.top().extentLength;
			// We're lucky: the second element on the stack is the parex node.
			if ( startingPoint == parexOrExitNode.parentExtentLength ) return new ParexData( stack.isEmpty() ? null : stack.top(), parexOrExitNode, lcpLength );
			
			final int stackSize = stack.size();
			// Unless there are mistakes, this is really the parex node.
			final InternalNode parexNode = fatBinarySearch( v, state, stack, false, startingPoint, parexOrExitNode.parentExtentLength );
			if ( parexNode.left == parexOrExitNode || parexNode.right == parexOrExitNode ) return new ParexData( parexNode, parexOrExitNode, lcpLength );
			// Something went wrong with the last search. We can just, at this point, restart in exact mode.
			return new ParexData( fatBinarySearch( v, state, stack, true, startingPoint, parexOrExitNode.parentExtentLength ), parexOrExitNode, lcpLength );
		}

		// The search failed. This even is so rare that we can afford to handle it inefficiently.
		stack.clear();
		parexOrExitNode = fatBinarySearch( v, state, stack, true, 0, length );
		if ( parexOrExitNode == null ) candidateExitNode = root;
		else candidateExitNode = parexOrExitNode.extentLength < length && v.getBoolean( parexOrExitNode.extentLength ) ? parexOrExitNode.right : parexOrExitNode.left;
		lcpLength = v.longestCommonPrefixLength( candidateExitNode.extent( (TransformationStrategy<Object>)transform ) );
		
		// In this case the fat binary search gave us the correct parex node, and we have all the data we need.
		if ( candidateExitNode.isExitNodeOf( length, lcpLength, (TransformationStrategy<Object>)transform ) ) return new ParexData( parexOrExitNode, candidateExitNode, lcpLength );

		// In this case the fat binary search gave us the correct *exit* node. We must pop it from the stack and maybe restart the search.
		stack.pop();
		final long startingPoint = stack.isEmpty() ? 0 : stack.top().extentLength;
		// We're lucky: the second element on the stack is the parex node.
		if ( startingPoint == parexOrExitNode.parentExtentLength ) return new ParexData( stack.isEmpty() ? null : stack.top(), parexOrExitNode, lcpLength );
		// The fat binary search will certainly return the parex node.
		return new ParexData( fatBinarySearch( v, state, stack, true, startingPoint, parexOrExitNode.parentExtentLength ), parexOrExitNode, lcpLength );
		
	}

	
	/** Returns the grandparent of the exit node of a given bit vector.
	 * 
	 * @param v a bit vector.
	 * @param stack as filled by {@link #getParentExitNode(LongArrayBitVector, long[], ObjectArrayList)}.
	 */
	public InternalNode getGrandParentExitNode( final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode> stack ) {
		final InternalNode parentExitNode = stack.pop();
		final long startingPoint = stack.isEmpty() ? 0 : stack.top().extentLength;
		// We're lucky: the second element on the stack is the grandparent of the exit node.
		if ( startingPoint == parentExitNode.parentExtentLength ) return stack.isEmpty() ? null : stack.top();

		final int stackSize = stack.size();
		// Unless there are mistakes, this is really the grandparent of the exit node.
		InternalNode grandParentExitNode = fatBinarySearch( v, state, stack, false, startingPoint, parentExitNode.parentExtentLength );
		/* To check that the last fat binary search completed correctly, we have just to check that the
		 * substring of the extent of the grandparent starting at startingPoint is equal to v in the same 
		 * positions. We already know that necessarily  
		 */
		if ( v.equals( grandParentExitNode.extent( (TransformationStrategy<Object>)transform ), startingPoint, grandParentExitNode.extentLength ) ) return grandParentExitNode;
		// Something went wrong with the last search. We can just, at this point, restart in exact mode.
		stack.size( stackSize );
		return fatBinarySearch( v, state, stack, true, startingPoint, parentExitNode.parentExtentLength );
	}


	private InternalNode fatBinarySearch( final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode> stack, final boolean exact, long a, long b ) {
		InternalNode node = null, top = stack == null || stack.isEmpty() ? null : stack.top();
		//System.err.println( "Fat binary " + v + " " + stack  + " (" + l + ".." + r + ") " + exact );

		final int logLength = Fast.mostSignificantBit( b );

		while( b - a > 0 ) {
			if ( ASSERTS ) assert logLength > -1;
			if ( DDEBUG ) System.err.println( "(" + a + ".." + b + "]" );

			final long f = twoFattest( a, b );
			if ( DDEBUG ) System.err.println( "Inquiring with key " + v.subVector( 0, f ) + " (" + f + ")" );

			node = map.get( v, f, Hashes.murmur( v, f, state ), exact );

			final long g;
			// Note that this test is just to catch false positives
			if ( node == null || ( g = node.extentLength ) < f ) {
				if ( DDEBUG ) System.err.println( "Missing" );
				b = f - 1;
			}
			else {
				if ( DDEBUG ) System.err.println( "Found extent of length " + g );
				if ( stack != null ) stack.push( node );
				top = node;
				a = g;
			}
		}
			
		if ( DDEBUG ) System.err.println( "Final length " + a + " node: " + top );
		
		if ( false ) if ( ASSERTS ) {
			boolean rightChild;
			Node exitNode;
			long lcp;

			rightChild = top != null && top.extentLength < v.length() && v.getBoolean( top.extentLength );
			exitNode = top == null ? root : ( rightChild ? top.right : top.left );
			lcp = exitNode.key( (TransformationStrategy<Object>)transform ).longestCommonPrefixLength( v );

			if ( exitNode.intercepts( lcp ) ) { // We can do asserts only if the result is correct
				/* If parent is null, the extent of the root must not be a prefix of v. */
				if ( top == null ) assert root.key( (TransformationStrategy<Object>)transform ).longestCommonPrefixLength( v ) < root.extentLength( (TransformationStrategy<Object>)transform );
				else {
					assert top.extentLength == a;

					/* If parent is not null, the extent of the parent must be a prefix of v, 
					 * and the extent of the exit node must be either v, or not a prefix of v. */
					assert ! exact || top.extent( (TransformationStrategy<Object>)transform ).longestCommonPrefixLength( v ) == top.extentLength;

					if ( stack != null ) {
						/** We check that the stack contains exactly all handles that are backjumps
						 * of the length of the extent of the parent. */
						a = top.extentLength;
						while( a != 0 ) {
							final Node t = map.get( top.key( (TransformationStrategy<Object>)transform ).subVector( 0, a ), true );
							if ( t != null ) assert stack.contains( t );
							a ^= ( a & -a );
						}

						/** We check that the stack contains the nodes you would obtain by searching from
						 * the top for nodes to fix. */
						long left = 0;
						for( int i = 0; i < stack.size(); i++ ) {
							assert stack.get( i ).handleLength() == twoFattest( left, top.extentLength ) :
								stack.get( i ).handleLength() + " != " + twoFattest( left, top.extentLength ) + " " + i + " " + stack ;
							left = stack.get( i ).extentLength;
						}
					}

				}
			}
		}
		
		return top;
	}
	
	
	@SuppressWarnings("unchecked")
	public boolean contains( final Object o ) {
		if ( DEBUG ) System.err.println( "contains(" + o + ")" );
		if ( size == 0 ) return false;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );
		final long[] state = Hashes.preprocessMurmur( v, 0 );
		final Node exitNode = getExitNode( v, state );
		return exitNode.isLeaf() && ((Leaf)exitNode).key.equals( o );
	}

	@SuppressWarnings("unchecked")
	public CharSequence pred( final Object o ) {
		if ( size == 0 ) return null;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );
		final long[] state = Hashes.preprocessMurmur( v, 0 );
		Node exitNode = getExitNode( v, state );
		
		if ( v.compareTo( exitNode.extent( (TransformationStrategy<Object>)transform ) ) <= 0 ) {
			while( exitNode.isInternal() && ((InternalNode)exitNode).jumpRight != null ) exitNode = ((InternalNode)exitNode).jumpRight;
			return ((Leaf)exitNode).key;
		}
		else {
			while( exitNode.isInternal() && ((InternalNode)exitNode).jumpLeft != null ) exitNode = ((InternalNode)exitNode).jumpLeft;
			return ((Leaf)exitNode).prev.key;
		}
		
	}

	@SuppressWarnings("unchecked")
	public CharSequence succ( final Object o ) {
		if ( size == 0 ) return null;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );
		final long[] state = Hashes.preprocessMurmur( v, 0 );
		Node exitNode = getExitNode( v, state );

		if ( v.compareTo( exitNode.extent( (TransformationStrategy<Object>)transform ) ) <= 0 ) {
			while( exitNode.isInternal() && ((InternalNode)exitNode).jumpLeft != null ) exitNode = ((InternalNode)exitNode).jumpLeft;
			return ((Leaf)exitNode).key;
		}
		else {
			while( exitNode.isInternal() && ((InternalNode)exitNode).jumpRight != null ) exitNode = ((InternalNode)exitNode).jumpRight;
			return ((Leaf)exitNode).next.key;
		}
	}

	private void writeObject( final ObjectOutputStream s ) throws IOException {
		s.defaultWriteObject();
		if ( size > 0 ) writeNode( root, (TransformationStrategy<Object>)transform, s );
	}
	
	private static void writeNode( final Node node, final TransformationStrategy<Object> transform, final ObjectOutputStream s ) throws IOException {
		s.writeBoolean( node.isInternal() );
		if ( node.isInternal() ) {
			s.writeLong( ((InternalNode)node).extentLength - node.parentExtentLength );
			writeNode( ((InternalNode)node).left, transform, s );
			writeNode( ((InternalNode)node).right, transform, s );
		}
		else s.writeObject( ((Leaf)node).key );
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		initHeadTail();
		map = new Map( size, (TransformationStrategy<Object>)transform );
		if ( size > 0 ) root = readNode( s, 0, 0, map, new ObjectArrayList<Leaf>(), new ObjectArrayList<InternalNode>(), new IntArrayList(), new IntArrayList(), new BooleanArrayList() );
		if ( ASSERTS ) assertTrie();
	}

	/** Reads recursively a node of the trie.
	 * 
	 * @param s the object input stream.
	 * @param depth the depth of the node to be read.
	 * @param parentExtentLength the length of the extent of the parent node.
	 * @param map the map representing the trie.
	 * @param leafStack a stack that cumulates leaves as they are found: internal nodes extract references from this stack when their visit is completed. 
	 * @param jumpStack a stack that cumulates nodes that need jump pointer fixes.
	 * @param depthStack a stack parallel to <code>jumpStack</code>, providing the depth of the corresponding node.  
	 * @param segmentStack a stack of integers representing the length of maximal constant subsequences of the string of directions taken up to the current node; for instance, if we reached the current node by 1/1/0/0/0/1/0/0, the stack will contain 2,3,1,2.
	 * @param dirStack a stack parallel to <code>segmentStack</code>: for each element, whether it counts left or right turns.
	 * @return the subtree rooted at the next node in the stream.
	 */
	private Node readNode( final ObjectInputStream s, final int depth, final long parentExtentLength, final Map map, final ObjectArrayList<Leaf> leafStack, final ObjectArrayList<InternalNode> jumpStack, final IntArrayList depthStack, final IntArrayList segmentStack, final BooleanArrayList dirStack ) throws IOException, ClassNotFoundException {
		final boolean isInternal = s.readBoolean();
		final Node node = isInternal ? new InternalNode() : new Leaf();
		node.parentExtentLength = parentExtentLength;
		if ( node.isInternal()) ((InternalNode)node).extentLength = parentExtentLength + s.readLong();

		if ( ! dirStack.isEmpty() ) {
			/* We cannot fix the jumps of nodes that are more than this number of levels up in the tree. */
			final int maxDepthDelta = segmentStack.topInt();
			final boolean dir = dirStack.topBoolean();
			InternalNode anc;
			int d;
			long jumpLength;
			do {
				jumpLength = ( anc = jumpStack.top() ).jumpLength();
				d = depthStack.topInt();
				/* To be fixable, a node must be within the depth limit, and we must intercept its jump length (note that
				 * we cannot use .intercept() as the state of node is not yet consistent). If a node cannot be fixed, no
				 * node higher in the stack can. */
				if ( depth - d <= maxDepthDelta && jumpLength > parentExtentLength && ( ! isInternal || jumpLength <= ((InternalNode)node).extentLength ) ) {
					//if ( DDEBUG ) System.err.println( "Setting " + ( dir ? "right" : "left" ) + " jump pointer of " + anc + " to " + node );
					if ( dir ) anc.jumpRight = node; 
					else anc.jumpLeft = node;
					jumpStack.pop();
					depthStack.popInt();
				}
				else break;
			} while( ! jumpStack.isEmpty() );
		}
		
		if ( isInternal ) {
			if ( dirStack.isEmpty() || dirStack.topBoolean() != false ) {
				segmentStack.push( 1 );
				dirStack.push( false );
			}
			else segmentStack.push( segmentStack.popInt() + 1 );
			jumpStack.push( (InternalNode)node );
			depthStack.push( depth );
			
			if ( DEBUG ) System.err.println( "Recursing into left node... " );
			((InternalNode)node).left = readNode( s, depth + 1, ((InternalNode)node).extentLength, map, leafStack, jumpStack, depthStack, segmentStack, dirStack );
			
			int top = segmentStack.popInt();
			if ( top != 1 ) segmentStack.push( top - 1 );
			else dirStack.popBoolean();
			
			if ( dirStack.isEmpty() || dirStack.topBoolean() != true ) {
				segmentStack.push( 1 );
				dirStack.push( true );
			}
			else segmentStack.push( segmentStack.popInt() + 1 );
			jumpStack.push( (InternalNode)node );
			depthStack.push( depth );
			
			if ( DEBUG ) System.err.println( "Recursing into right node... " );
			((InternalNode)node).right = readNode( s, depth + 1, ((InternalNode)node).extentLength, map, leafStack, jumpStack, depthStack, segmentStack, dirStack );
			
			top = segmentStack.popInt();
			if ( top != 1 ) segmentStack.push( top - 1 );
			else dirStack.popBoolean();

			/* We assign the reference leaf, and store the associated key. */
			final Leaf referenceLeaf = leafStack.pop(); 
			((InternalNode)node).reference = referenceLeaf;
			referenceLeaf.reference = (InternalNode)node;

			map.addNew( (InternalNode)node );

			if ( ASSERTS ) { // Check jump pointers.
				Node t;
				t = ((InternalNode)node).left; 
				while( t.isInternal() && ! t.intercepts( ((InternalNode)node).jumpLength() ) ) t = ((InternalNode)t).left;
				assert ((InternalNode)node).jumpLeft == t : ((InternalNode)node).jumpLeft + " != " + t + " (" + node + ")";
				t = ((InternalNode)node).right;
				while( t.isInternal() && ! t.intercepts( ((InternalNode)node).jumpLength() ) ) t = ((InternalNode)t).right;
				assert ((InternalNode)node).jumpRight == t : ((InternalNode)node).jumpRight + " != " + t + " (" + node + ")";
			}
		}
		else {
			((Leaf)node).key = (CharSequence)s.readObject();
			leafStack.push( (Leaf)node );
			addBefore( tail, (Leaf)node );
		}

		return node;
	}
	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( ZFastTrie.class.getName(), "Builds an PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
			new Switch( "bitVector", 'b', "bit-vector", "Build a trie of bit vectors, rather than a trie of strings." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised z-fast trie." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "trie" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );
		final boolean bitVector = jsapResult.getBoolean( "bitVector" );

		final InputStream inputStream = "-".equals( stringFile ) ? System.in : new FileInputStream( stringFile );

		final LineIterator lineIterator = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( inputStream ) : inputStream, encoding ) ) );
		
		final TransformationStrategy<CharSequence> transformationStrategy = iso
		? TransformationStrategies.prefixFreeIso() 
				: TransformationStrategies.prefixFreeUtf16();

		ProgressLogger pl = new ProgressLogger();
		pl.itemsName = "keys";
		pl.displayFreeMemory = true;
		pl.start( "Adding keys..." );

		if ( bitVector ) {
			ZFastTrie<LongArrayBitVector> zFastTrie = new ZFastTrie<LongArrayBitVector>( TransformationStrategies.identity() );
			while( lineIterator.hasNext() ) {
				zFastTrie.add( LongArrayBitVector.copy( transformationStrategy.toBitVector( lineIterator.next().copy() ) ) );
				pl.lightUpdate();
			}
			pl.done();
			BinIO.storeObject( zFastTrie, functionName );
		}
		else {
			ZFastTrie<CharSequence> zFastTrie = new ZFastTrie<CharSequence>( transformationStrategy );
			while( lineIterator.hasNext() ) {
				zFastTrie.add( lineIterator.next().copy() );
				pl.lightUpdate();
			}
			pl.done();
			BinIO.storeObject( zFastTrie, functionName );
		}
		LOGGER.info( "Completed." );
	}

	@Override
	public ObjectBidirectionalIterator<T> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObjectSortedSet<T> headSet( T arg0 ) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObjectBidirectionalIterator<T> iterator( T arg0 ) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObjectSortedSet<T> subSet( T arg0, T arg1 ) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ObjectSortedSet<T> tailSet( T arg0 ) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Comparator<? super T> comparator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public T first() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public T last() {
		// TODO Auto-generated method stub
		return null;
	}
}
