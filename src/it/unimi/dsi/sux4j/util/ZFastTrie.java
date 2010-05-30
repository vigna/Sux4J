package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010 Sebastiano Vigna 
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
 * answering in time &#x2113;/<var>w</var> + log(max(&#x2113;, &#x2113;<sup>-</sup>, &#x2113;<sup>+</sup>)) with high probability,
 * where <var>w</var> is the machine word size, and &#x2113;, &#x2113;<sup>-</sup>, and &#x2113;<sup>+</sup> are the
 * lengths of the query string, of its predecessor and of its successor (in the currently stored set), respectively.
 * 
 * <p>In rough terms, the z-fast trie uses &#x2113;/<var>w</var> (which is optimal) to actually look at the string content,
 * and log(max(&#x2113;, &#x2113;<sup>-</sup>, &#x2113;<sup>+</sup>)) to perform the search. This is known to be (essentially) optimal.
 * String lengths are up to {@link Long#MAX_VALUE}, and not limited to be a constant multiple of <var>w</var> for the bounds to hold. 
 * 
 * <p>The linear overhead of a z-fast trie is very low. For <var>n</var> keys we allocate 2<var>n</var> &minus; 1 nodes containing six references and 
 * two longs, plus a dictionary containing <var>n</var> &minus; 1 nodes (thus using around 2<var>n</var> references and 2<var>n</var> longs).  
 * 
 */

public class ZFastTrie<T> extends AbstractObjectSortedSet<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( ZFastTrie.class );
	private static final boolean ASSERTS = true;
	private static final boolean SHORT_SIGNATURES = false;
	private static final boolean DDEBUG = false;
	private static final boolean DDDEBUG = DDEBUG;

	/** The number of elements in the trie. */
	private int size;
	/** The root node. */
	private transient Node root;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A dictionary mapping handles to the corresponding internal nodes. */
	public transient Map map;
	/** The head of the doubly linked list of leaves. */
	private transient Node head;
	/** The tail of the doubly linked list of leaves. */
	private transient Node tail; 

	/** A linear-probing hash map that compares keys using signatures. */
	public final static class Map {
		private static final long serialVersionUID = 1L;
		private static final int INITIAL_LENGTH = 64;

		/** The keys of the table, that is, the signatures of the handles of the associated nodes. */
		private long[] key;
		/** The node associated to each signature. */
		private Node[] node;
		/** Whether a key is a duplicate. In this case, there are more copies of the key along the search path. */
		private boolean dup[];
		/** The mask to transform a signature into a position in the table. */
		private int mask;
		/** The number of keys in the table. */
		private int size;
		/** The number of slots in the table. */
		private int length;	

		private void assertTable() {
			for( int i = key.length; i-- != 0; ) if ( node[ i ] != null ) assert get( node[ i ].handle(), true ) == node[ i ];
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
					assert signaturesSeen.add( key[ pos ] ) ^ dup[ pos ];
					hashesSeen.add( (int)( key[ pos ] & mask ) );
				}
				
				// Hashes in each maximal nonempty subsequence must be disjoint
				for( IntIterator iterator = hashesSeen.iterator(); iterator.hasNext(); ) assert overallHashes.add( iterator.nextInt() );
				
				start = end;
			}
		}

		public Map( int size ) {
			length = Math.max( INITIAL_LENGTH, 1 << Fast.ceilLog2( 1 + size / 3 * 4 ) );
			mask = length - 1;
			key = new long[ length ];
			node = new Node[ length ];
			dup = new boolean[ length ];
		}

		public Map() {
			length = INITIAL_LENGTH;
			mask = length - 1;
			key = new long[ length ];
			node = new Node[ length ];
			dup = new boolean[ length ];
		}

		public long probes = 0;
		public long scans = 0;
		
		private int findPos( final BitVector v, final long prefixLength, final long signature ) {
			probes++;
			int pos = (int)( signature & mask );
			while( node[ pos ] != null ) {
				if ( key[ pos ] == signature && ( ! dup[ pos ] ||
						prefixLength == node[ pos ].handleLength() && v.longestCommonPrefixLength( node[ pos ].key ) >= prefixLength ) )
					break;
				pos = ( pos + 1 ) & mask;
				scans++;
			}
			return pos;
		}
		
		private int findExactPos( final BitVector v, final long prefixLength, final long signature ) {
			int pos = (int)( signature & mask );
			while( node[ pos ] != null ) {
				if ( key[ pos ] == signature &&
						prefixLength == node[ pos ].handleLength() && v.longestCommonPrefixLength( node[ pos ].key ) >= prefixLength )
					break;

				pos = ( pos + 1 ) & mask;
			}
			return pos;
		}
		
		private int findFreePos( final long signature ) {
			int pos = (int)( signature & mask );
			//int i = 0;
			while( node[ pos ] != null ) {
				if ( key[ pos ] == signature ) dup[ pos ] = true;
				pos = ( pos + 1 ) & mask;
				//i++;
			}
			//System.err.println( i );
			return pos;
		}
		
		public void clear() {
			length = INITIAL_LENGTH;
			mask = length - 1;
			size = 0;
			key = new long[ length ];
			node = new Node[ length ];
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
							return LongArrayBitVector.copy( node[ pos ].handle() );
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
					return get( node.handle(), true ) != null;
				}

				@Override
				public int size() {
					return size;
				}
			};
		}

		public void replace( final Node newNode ) {
			final long signature = newNode.handleHash();
			final int pos = findPos( newNode.key, newNode.handleLength(), signature );
			if ( ASSERTS ) assert node[ pos ] != null;
			if ( ASSERTS ) assert node[ pos ].handle().equals( newNode.handle() ) : node[ pos ].handle() + " != " + newNode.handle();
			node[ pos ] = newNode;
			if ( ASSERTS ) assertTable();
		}

		public boolean remove( final Object k ) {
			if ( DDEBUG ) System.err.println( "Map.remove(" + k + ")" );
			final Node v = (Node)k;
			final long signature = v.handleHash();
			final int hash = (int)( signature & mask ); 
			final long prefixLength = v.handleLength();
			
			int pos = hash; // The current position in the table.
			int lastDup = -1;
			
			while( node[ pos ] != null ) {
				if ( key[ pos ] == signature ) {
					if ( ! dup[ pos ] || prefixLength == node[ pos ].handleLength() && v.key.longestCommonPrefixLength( node[ pos ].key ) >= prefixLength ) break;
					else lastDup = pos;
				}
				pos = ( pos + 1 ) & mask;
			}

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
					h = (int)( node[ pos ].handleHash() & mask );
					/* The hash h must lie cyclically between candidateHole and pos: more precisely, h must be after candidateHole
					 * but before the first free entry in the table (which is equivalent to the previous statement). */
				} while( candidateHole <= pos ? candidateHole < h && h <= pos : candidateHole < h || h <= pos );
				
				node[ candidateHole ] = node[ pos ];
				key[ candidateHole ] = key[ pos ];
				dup[ candidateHole ] = dup[ pos ];
		    } while( node[ pos ] != null );

			size--;
			if ( ASSERTS ) assertTable();
			return true;
		}

		public Node addNew( final Node v ) {
			final long signature = v.handleHash();
			int pos = findFreePos( signature );
			if ( ASSERTS ) assert node[ pos ] == null;
			
			size++;
			key[ pos ] = signature;
			node[ pos ] = v;

			if ( ( size / 3 * 4 ) > length ) {
				length *= 2;
				mask = length - 1;
				final long newKey[] = new long[ length ];
				final Node[] newValue = new Node[ length ];
				final boolean[] newCollision = new boolean[ length ];
				final long[] key = this.key;
				final Node[] value = this.node;
				
				for( int i = key.length; i-- != 0; ) {
					if ( value[ i ] != null ) {
						final long s = key[ i ];
						pos = (int)( s & mask ); 
						while( newValue[ pos ] != null ) {
							if ( newKey[ pos ] == s ) newCollision[ pos ] = true;
							pos = ( pos + 1 ) & mask;
						}
						newKey[ pos ] = key[ i ];
						newValue[ pos ] = value[ i ];
					}
				}

				this.key = newKey;
				this.node = newValue;
				this.dup = newCollision;
			}
			
			if ( ASSERTS ) assertTable();
			return null;
		}

		public int size() {
			return size;
		}


		public Node get( long signature, final BitVector v, final boolean exact ) {
			return get( signature, v, v.length(), exact );
		}

		public Node get( long signature, final BitVector v, final long prefixLength, final boolean exact ) {
			if ( SHORT_SIGNATURES ) signature &= 0xF;
			final int pos = exact ? findExactPos( v, prefixLength, signature ) : findPos( v, prefixLength, signature );
			return node[ pos ];
		}

		public Node get( final BitVector v, final boolean exact ) {
			return get( Hashes.murmur( v, 0 ), v, exact );
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

	/** A node of the trie.* */
	protected final static class Node {
		/** The left subtree for internal nodes; the predecessor leaf, otherwise. */
		protected Node left;
		/** The right subtree for internal nodes; the successor leaf, otherwise. */
		protected Node right;
		/** The jump pointer for the left path for internal nodes; <code>null</code>, otherwise (this
		 * makes leaves distinguishable). */
		protected Node jumpLeft;
		/** The jump pointer for the right path for internal nodes; <code>null</code>, otherwise. */
		protected Node jumpRight;
		/** The leaf whose key this node refers to for internal nodes; the internal node that
		 * refers to the key of this leaf, otherwise. Will be <code>null</code> for exactly one leaf. */
		protected Node reference;
		/** The length of the extent of the parent node, or 0 for the root. */
		protected long parentExtentLength;
		/** The length of the extent (for leaves, this is equal to the length of {@link #key}). */
		protected long extentLength;
		/** The key upon which the extent of node is based, for internal nodes; the 
		 * key associated to a leaf, otherwise. */
		protected LongArrayBitVector key;

		public boolean isLeaf() {
			return jumpLeft == null;
		}
		
		public boolean isInternal() {
			return jumpLeft != null;
		}
		
		public boolean intercepts( long h ) {
			return h > parentExtentLength && ( isLeaf() || h <= extentLength );
		}
		
		public long handleLength() {
			return twoFattest( parentExtentLength, extentLength );
		}
		
		public long jumpLength() {
			final long handleLength = twoFattest( parentExtentLength, extentLength );
			return handleLength + ( handleLength & -handleLength );
		}
		
		public BitVector extent() {
			return key.subVector( 0, extentLength );
		}
		
		public BitVector handle() {
			return key.subVector( 0, handleLength() );
		}
		
		public long handleHash() {
			if ( SHORT_SIGNATURES ) return Hashes.murmur( handle(), 0 ) & 0xF;
			else return Hashes.murmur( handle(), 0 );
		}
		
		public String toString() {
			return ( isLeaf() ? "[" : "(" ) + Integer.toHexString( hashCode() & 0xFFFF ) + 
				( key == null ? "" : 
					" " + ( extentLength > 16 ? key.subVector( 0, 8 ) + "..." + key.subVector( extentLength - 8, extentLength ): key.subVector( 0, extentLength ) ) ) +
					" (" + parentExtentLength + ".." + extentLength + "], " + handleLength() + "->" + jumpLength() +
				( isLeaf() ? "]" : ")" );
		}
	}

	/** Creates a new z-fast trie using the given transformation strategy. 
	 * 
	 * @param transform a transformation strategy that must turn distinct elements into distinct, prefix-free bit vectors.
	 */
	public ZFastTrie( final TransformationStrategy<? super T> transform ) {
		this.transform = transform;
		this.map = new Map();
		initHeadTail();
	}
	
	private void initHeadTail() {
		head = new Node();
		tail = new Node();
		head.right = tail;
		tail.left = head;
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
	
	private static void remove( final Node node ) {
		node.right.left = node.left;
		node.left.right = node.right;
	}
	
	private static void addAfter( final Node pred, final Node node ) {
		node.right = pred.right;
		node.left = pred;
		pred.right.left = node;
		pred.right = node;
	}
	
	private static void addBefore( final Node succ, final Node node ) {
		node.left = succ.left;
		node.right = succ;
		succ.left.right = node;
		succ.left = node;
	}
	
	private void assertTrie() {
		/* Shortest key */
		LongArrayBitVector root = null;
		/* Keeps track of which nodes in map are reachable using left/right from the root. */
		ObjectOpenHashSet<Node> nodes = new ObjectOpenHashSet<Node>();
		/* Keeps track of leaves. */
		ObjectOpenHashSet<Node> leaves = new ObjectOpenHashSet<Node>();
		/* Keeps track of reference to leaf keys in internal nodes. */
		ObjectOpenHashSet<BitVector> references = new ObjectOpenHashSet<BitVector>();
		
		assert size == 0 && map.size() == 0 || size == map.size() + 1;
		
		/* Search for the root (shortest handle) and check that nodes and handles do match. */
		for( LongArrayBitVector v : map.keySet() ) {
			final long vHandleLength = map.get( v, false ).handleLength();
			if ( root == null || map.get( root, false ).handleLength() > vHandleLength ) root = v;
			final Node node = map.get( v, false );
			nodes.add( node );
			assert node.reference.reference == node : node + " -> " + node.reference + " -> " + node.reference.reference;
		}
		
		assert nodes.size() == map.size() : nodes.size() + " != " + map.size();
		assert size < 2 || this.root == map.get( root, false );
		
		if ( size > 1 ) {
			/* Verify doubly linked list of leaves. */
			Node toRight = head.right, toLeft = tail.left;
			for( int i = 1; i < size; i++ ) {
				assert toRight.key.compareTo( toRight.right.key ) < 0 : toRight.key + " >= " + toRight.right.key + " " + toRight;
				assert toLeft.key.compareTo( toLeft.left.key ) > 0 : toLeft.key + " >= " + toLeft.left.key + " " + toLeft;
				toRight = toRight.right;
				toLeft = toLeft.left;
			}

			final int numNodes = visit( map.get( root, false ), null, 0, 0, nodes, leaves, references );
			assert numNodes == 2 * size - 1 : numNodes + " != " + ( 2 * size - 1 );
			assert leaves.size() == size;
			int c = 0;

			for( Node leaf: leaves ) if ( references.contains( leaf.key ) ) c++;

			assert c == size - 1 : c + " != " + ( size - 1 );
		}
		else if ( size == 1 ) {
			assert head.right == this.root;
			assert tail.left == this.root;
		}
		assert nodes.isEmpty();
	}
	
	private int visit( final Node n, final Node parent, final long parentExtentLength, final int depth, ObjectOpenHashSet<Node> nodes, ObjectOpenHashSet<Node> leaves, ObjectOpenHashSet<BitVector> references ) {
		if ( n == null ) return 0;
		if ( DDEBUG ) {
			for( int i = depth; i-- != 0; ) System.err.print( '\t' );
			System.err.println( "Node " + n + " (parent extent length: " + parentExtentLength + ") Jump left: " + n.jumpLeft + " Jump right: " + n.jumpRight );
		}

		assert parent == null || parent.extent().equals( n.extent().subVector( 0, parent.extentLength ) );
		assert ( n.jumpLeft == null ) == ( n.jumpRight == null );
		assert parentExtentLength < n.extentLength;
		assert n.parentExtentLength == parentExtentLength : n.parentExtentLength + " != " + parentExtentLength + " " + n;
		assert n.isLeaf() == ( n.extentLength == n.key.length() ); 
		
		if ( n.isInternal() ) {
			assert references.add( n.key );
			assert nodes.remove( n ) : n;
			assert map.keySet().contains( n.handle() ) : n;

			/* Check that jumps are correct. */
			final long jumpLength = n.jumpLength();
			Node jumpLeft = n.left;
			while( jumpLeft.isInternal() && jumpLength > jumpLeft.extentLength ) jumpLeft = jumpLeft.left;
			assert jumpLeft == n.jumpLeft : jumpLeft + " != " + n.jumpLeft + " (node: " + n + ")";

			Node jumpRight = n.right;
			while( jumpRight.isInternal() && jumpLength > jumpRight.extentLength ) jumpRight = jumpRight.right;
			assert jumpRight == n.jumpRight : jumpRight + " != " + n.jumpRight + " (node: " + n + ")";
			return 1 + visit( n.left, n, n.extentLength, depth + 1, nodes, leaves, references ) + visit( n.right, n, n.extentLength, depth + 1, nodes, leaves, references );
		}
		else {
			assert leaves.add( n );
			return 1;
		}
	}

	/** Sets the jump pointers of a node by searching exhaustively for
	 * handles that are jumps of the node handle length.
	 * 
	 * @param node the node whose jump pointers must be set.
	 */
	private static void setJumps( final Node node ) {
		if ( DDEBUG ) System.err.println( "setJumps(" + node + ")" );
		final long jumpLength = node.jumpLength();
		Node jump;

		for( jump = node.left; jump.isInternal() && jumpLength > jump.extentLength; ) jump = jump.jumpLeft;
		if ( ASSERTS ) assert jump.intercepts( jumpLength );
		node.jumpLeft = jump;
		for( jump = node.right; jump.isInternal() && jumpLength > jump.extentLength; ) jump = jump.jumpRight;
		if ( ASSERTS ) assert jump.intercepts( jumpLength );
		node.jumpRight = jump;
	}

	/** Fixes the right jumps of the ancestors of a node after an insertion.
	 * 
	 * @param exitNode the exit node.
	 * @param rightChild 
	 * @param leaf the new leaf.
	 * @param stack a stack containing the 2-fat ancestors of <code>exitNode</code>.
	 */
	private static void fixRightJumpsAfterInsertion( final Node internal, Node exitNode, boolean rightChild, Node leaf, final ObjectArrayList<Node> stack ) {
		if ( DDEBUG ) System.err.println( "fixRightJumpsAfterInsertion(" + internal + ", " + exitNode + ", " + rightChild + ", " + leaf + ", " + stack ); 
		final long lcp = leaf.parentExtentLength;
		Node toBeFixed = null;
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
				while( exitNode != null && toBeFixed.jumpRight != exitNode ) exitNode = exitNode.jumpRight;
				if ( exitNode == null ) return;
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
	private static void fixLeftJumpsAfterInsertion( final Node internal, Node exitNode, boolean rightChild, Node leaf, final ObjectArrayList<Node> stack ) {
		if ( DDEBUG ) System.err.println( "fixLeftJumpsAfterInsertion(" + internal + ", " + exitNode + ", " + rightChild + ", " + leaf + ", " + stack ); 
		final long lcp = leaf.parentExtentLength;
		Node toBeFixed = null;
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
				while( exitNode != null && toBeFixed.jumpLeft != exitNode ) exitNode = exitNode.jumpLeft;
				if ( exitNode == null ) return;
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
	private static void fixRightJumpsAfterDeletion( Node otherNode, Node parentExitNode, boolean rightChild, Node exitNode, final ObjectArrayList<Node> stack ) {
		if ( DDEBUG ) System.err.println( "fixRightJumpsAfterDeletion(" + otherNode + ", " + parentExitNode + ", " + rightChild + ", " + exitNode + ", " + stack );
		Node toBeFixed = null;
		long jumpLength = -1;

		if ( ! stack.isEmpty() ) stack.pop();
		
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
				while( ! otherNode.intercepts( jumpLength ) ) otherNode = otherNode.jumpRight;
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
	private static void fixLeftJumpsAfterDeletion( Node otherNode, Node parentExitNode, boolean rightChild, Node exitNode, final ObjectArrayList<Node> stack ) {
		if ( DDEBUG ) System.err.println( "fixLeftJumpsAfterDeletion(" + otherNode + ", " + parentExitNode + ", " + rightChild + ", " + exitNode + ", " + stack );
		Node toBeFixed = null;
		long jumpLength = -1;

		if ( ! stack.isEmpty() ) stack.pop();

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
				while( ! otherNode.intercepts( jumpLength ) ) otherNode = otherNode.jumpLeft;
				toBeFixed.jumpLeft = otherNode;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public boolean remove( final Object k ) {
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)k ) );
		if ( DDEBUG ) System.err.println( "remove(" + v + ")" );
		if ( DDEBUG ) System.err.println( "Map: " + map + " root: " + root );
		
		if ( size == 0 ) return false;
		
		if ( size == 1 ) {
			if ( ! root.key.equals( v ) ) return false;
			root = null;
			size = 0;
			if ( ASSERTS ) assertTrie();
			return true;
		}

		final ObjectArrayList<Node> stack = new ObjectArrayList<Node>( 64 );
		
		Node parentExitNode;
		boolean rightLeaf, rightChild = false;
		Node exitNode;
		long lcp;

		final long[] state = Hashes.preprocessMurmur( v, 0 );

		parentExitNode = getParentExitNode( v, state, stack, false );
		rightLeaf = parentExitNode != null && parentExitNode.extentLength < v.length() && v.getBoolean( parentExitNode.extentLength );
		exitNode = parentExitNode == null ? root : ( rightLeaf ? parentExitNode.right : parentExitNode.left );
		lcp = exitNode.key.longestCommonPrefixLength( v );
		
		if ( ! exitNode.intercepts( lcp ) ) {
			/* A mistake. We redo the query in exact mode. */
			stack.clear();
			parentExitNode = getParentExitNode( v, state, stack, true );
			rightLeaf = parentExitNode != null && v.getBoolean( parentExitNode.extentLength );
			exitNode = parentExitNode == null ? root : ( rightLeaf ? parentExitNode.right : parentExitNode.left );
			lcp = exitNode.key.longestCommonPrefixLength( v );
			if ( ASSERTS ) assert exitNode.intercepts( lcp );
		}
		
		if ( DDDEBUG ) System.err.println( "Exit node " + exitNode );
		
		if ( ! exitNode.key.equals( v ) ) return false; // Not found
		
		final Node otherNode = rightLeaf ? parentExitNode.left : parentExitNode.right;
		final boolean otherNodeIsInternal = otherNode.isInternal();

		if ( parentExitNode != null && parentExitNode != root ) {
			// Let us fix grandpa's child pointer.
			final int stackSize = stack.size();
			Node grandParentExitNode = getGrandParentExitNode( v, state, stack, false );
			if ( grandParentExitNode == null || grandParentExitNode.left != parentExitNode && grandParentExitNode.right != parentExitNode ) {
				// Clean up stack from garbage left from mistaken invocation
				stack.size( stackSize - 1 );
				stack.push( parentExitNode );
				grandParentExitNode = getGrandParentExitNode( v, state, stack, true );
			}
			if ( rightChild = ( grandParentExitNode.right == parentExitNode ) )  grandParentExitNode.right = otherNode;
			else grandParentExitNode.left = otherNode;
		}		

		final long t = parentExitNode.handleLength() | otherNode.handleLength();
		final boolean cutLow = ( t & -t & otherNode.handleLength() ) != 0;


		if ( DDEBUG ) System.err.println( "lcp: " + lcp );

		if ( parentExitNode == root ) root = otherNode;

		// Fix leaf reference if not null
		final Node refersToExitNode = exitNode.reference;
		if ( refersToExitNode == null ) parentExitNode.reference.reference = null;
		else {
			refersToExitNode.reference = parentExitNode.reference;
			refersToExitNode.reference.reference = refersToExitNode;
			refersToExitNode.key = refersToExitNode.reference.key;
		}

		// Fix doubly-linked list
		remove( exitNode );

		if ( DDDEBUG ) System.err.println( "Cut " + ( cutLow ? "low" : "high") + "; leaf on the " + ( rightLeaf ? "right" : "left") + "; other node is " + ( otherNodeIsInternal ? "internal" : "a leaf") );
		
		if ( rightLeaf ) fixRightJumpsAfterDeletion( otherNode, parentExitNode, rightChild, exitNode, stack );
		else fixLeftJumpsAfterDeletion( otherNode, parentExitNode, rightChild, exitNode, stack );
		
		if ( cutLow && otherNodeIsInternal ) {
			map.remove( otherNode );
			otherNode.parentExtentLength = parentExitNode.parentExtentLength;
			map.replace( otherNode );
			setJumps( otherNode );
		}
		else {
			otherNode.parentExtentLength = parentExitNode.parentExtentLength;
			map.remove( parentExitNode );
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
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( k ) );
		if ( DDEBUG ) System.err.println( "add(" + v + ")" );
		if ( DDEBUG ) System.err.println( "Map: " + map + " root: " + root );
		
		if ( size == 0 ) {
			root = new Node();
			root.key = v;
			root.extentLength = v.length();
			root.parentExtentLength = 0;
			root.reference = root;
			addAfter( head, root );
			size++;
			if ( ASSERTS ) assertTrie();
			return true;
		}

		final ObjectArrayList<Node> stack = new ObjectArrayList<Node>( 64 );
		
		Node parentExitNode;
		boolean rightChild;
		Node exitNode;
		long lcp;

		final long[] state = Hashes.preprocessMurmur( v, 0 );

		parentExitNode = getParentExitNode( v, state, stack, false );
		rightChild = parentExitNode != null && parentExitNode.extentLength < v.length() && v.getBoolean( parentExitNode.extentLength );
		exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		lcp = exitNode.key.longestCommonPrefixLength( v );
		
		if ( ! exitNode.intercepts( lcp ) ) {
			/* A mistake. We redo the query in exact mode. */
			stack.clear();
			parentExitNode = getParentExitNode( v, state, stack, true );
			rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extentLength );
			exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
			lcp = exitNode.key.longestCommonPrefixLength( v );
			if ( ASSERTS ) assert exitNode.intercepts( lcp );
		}

		if ( DDDEBUG ) System.err.println( "Exit node " + exitNode );
		
		if ( exitNode.key.equals( v ) ) return false; // Already there
		
		final boolean exitDirection = v.getBoolean( lcp );
		final boolean cutLow = lcp >= exitNode.handleLength();
		
		if ( DDEBUG ) System.err.println( "lcp: " + lcp );
		Node leaf = new Node();
		Node internal = new Node();

		final boolean exitNodeIsInternal = exitNode.isInternal();
		
		leaf.key = v;
		leaf.extentLength = v.length();
		leaf.parentExtentLength = lcp;
		leaf.reference = internal;
		
		internal.key = leaf.key;
		internal.reference = leaf;
		internal.parentExtentLength = exitNode.parentExtentLength;
		internal.extentLength = lcp;

		if ( exitDirection ) {
			internal.jumpRight = internal.right = leaf;
			internal.left = exitNode;
			internal.jumpLeft = cutLow && exitNodeIsInternal ? exitNode.jumpLeft : exitNode;
		}
		else {
			internal.jumpLeft = internal.left = leaf;
			internal.right = exitNode;
			internal.jumpRight = cutLow && exitNodeIsInternal ? exitNode.jumpRight : exitNode;
		}

		if ( exitNode == root ) root = internal; // Update root
		else {
			if ( rightChild ) parentExitNode.right = internal;
			else parentExitNode.left = internal;
		}

		if ( DDDEBUG ) System.err.println( "Cut " + ( cutLow ? "low" : "high") + "; exit to the " + ( exitDirection ? "right" : "left") );

		if ( exitDirection ) fixRightJumpsAfterInsertion( internal, exitNode, rightChild, leaf, stack );
		else fixLeftJumpsAfterInsertion( internal, exitNode, rightChild, leaf, stack );

		if ( cutLow && exitNodeIsInternal ) {
			map.replace( internal );
			exitNode.parentExtentLength = lcp;
			map.addNew( exitNode );
			setJumps( exitNode );
		}
		else {
			exitNode.parentExtentLength = lcp;
			map.addNew( internal );
		}

		
		if ( DDEBUG ) System.err.println( "After insertion, map: " + map + " root: " + root );

		size++;

		/* We find a predecessor or successor to insert the new leaf in the doubly linked list. */
		if ( exitDirection ) {
			while( exitNode.isInternal() ) exitNode = exitNode.jumpRight;
			addAfter( exitNode, leaf );
		}
		else {
			while( exitNode.isInternal() ) exitNode = exitNode.jumpLeft;
			addBefore( exitNode, leaf );
		}
		
		if ( ASSERTS ) assertTrie();
		if ( ASSERTS ) assert contains( k );
		
		return true;
	}

	
	private static final boolean equals( LongArrayBitVector a, LongArrayBitVector b, long start, long end ) {
		int startWord = (int)( start >>> LongArrayBitVector.LOG2_BITS_PER_WORD );
		final int endWord = (int)( end >>> LongArrayBitVector.LOG2_BITS_PER_WORD );
		final int startBit = (int)( start & LongArrayBitVector.WORD_MASK );
		final int endBit = (int)( end & LongArrayBitVector.WORD_MASK );
		final long[] aBits = a.bits();
		final long[] bBits = b.bits();
		
		if ( startWord == endWord ) return ( ( aBits[ startWord ] ^ bBits[ startWord ] ) & ( ( 1L << ( endBit - startBit ) ) - 1 ) << startBit ) == 0; 
		
		if ( ( ( aBits[ startWord ] ^ bBits[ startWord ] ) & ( -1L << startBit ) ) != 0 ) return false; 

		for( ++startWord; startWord < endWord; startWord++ ) 
			if ( aBits[ startWord ] != bBits[ startWord ] ) return false;
		
		if ( ( ( aBits[ endWord ] ^ bBits[ endWord] ) & ( 1L << endBit) - 1 ) != 0 ) return false;
		
		return true;
	}
	
	/** Returns the parent of the exit node of a given bit vector.
	 * 
	 * @param v a bit vector.
	 * @param stack if not <code>null</code>, a stack that will be filled with the <em>fat nodes</em> along the path to the parent of the exit node.
	 * @param exact if true, the map defining the trie will be accessed in exact mode.
	 * @return the parent of the exit node of <code>v</code>, or <code>null</code> if the exit node is the root; 
	 * if <code>exact</code> is false, with low probability
	 * the result might be wrong. 
	 */
	public Node getParentExitNode( final LongArrayBitVector v, final long[] state, final ObjectArrayList<Node> stack, final boolean exact ) {
		if ( ASSERTS ) assert size > 0;
		if ( size == 1 ) return null;
		if ( DDDEBUG ) System.err.println( "getParentExitNode(" + v + ", " + exact + ")" );
		final Node parent = fatBinarySearch( v, state, stack, exact, 0, v.length() );
		if ( DDDEBUG ) System.err.println( "Parent exit node: " + parent );
		return parent;
	}

	
	/** Returns the parent of the exit node of a given bit vector.
	 * 
	 * @param v a bit vector.
	 * @param stack if not <code>null</code>, a stack that will be filled with the <em>fat nodes</em> along the path to the parent of the exit node.
	 * @param exact if true, the map defining the trie will be accessed in exact mode.
	 * @return the parent of the exit node of <code>v</code>, or <code>null</code> if the exit node is the root; 
	 * if <code>exact</code> is false, with low probability
	 * the result might be wrong. 
	 */
	public Node getGrandParentExitNode( final LongArrayBitVector v, final long[] state, final ObjectArrayList<Node> stack, final boolean exact ) {
		if ( ASSERTS ) assert size > 0;
		if ( size < 3 ) return null;
		if ( DDDEBUG ) System.err.println( "getGrandParentExitNode(" + v + ", " + stack + " , " + exact + ")" );
		// Grandpa is already on the stack.
		if ( stack.size() > 1 && stack.peek( 1 ).extentLength == stack.peek( 0 ).parentExtentLength ) return stack.peek( 1 );
		
		final Node parentExitNode = stack.pop();
		long l = stack.size() > 0 ? stack.top().extentLength : 0, r = parentExitNode.parentExtentLength + 1;
		final Node parent = fatBinarySearch( v, state, stack, exact, l, r );
		
		if ( ASSERTS ) assert parent == null || parent.right == parentExitNode || parent.left == parentExitNode;
		stack.push( parentExitNode );
		return parent;
	}

	private Node fatBinarySearch( final LongArrayBitVector v, final long[] state, final ObjectArrayList<Node> stack, final boolean exact, long l, long r ) {
		final int logLength = Fast.mostSignificantBit( r );
		long checkMask = 1L << logLength;
		long computeMask = -1L << logLength;
		Node node = null, parent = null;

		while( r - l > 1 ) {
			if ( ASSERTS ) assert logLength > -1;
			if ( DDDEBUG ) System.err.println( "(" + l + ".." + r + "); checking for fatness " + checkMask );
			if ( ( l & checkMask ) != ( r - 1 & checkMask ) ) { // Quick test for a 2-fattest number divisible by 2^i in (l..r).
				final long f = ( r - 1 ) & computeMask;

				if ( DDDEBUG ) System.err.println( "Inquiring with key " + v.subVector( 0, f ) + " (" + f + ")" );
				
				node = map.get( Hashes.murmur( v, f, state ), v, f, exact );
				
				if ( node == null ) {
					if ( DDDEBUG ) System.err.println( "Missing" );
					r = f;
				}
				else {
					long g = node.extentLength;
					if ( DDDEBUG ) System.err.println( "Found extent of length " + g );

					if ( g >= f && g < r && equals( node.key, v, f, g ) ) {
						if ( stack != null ) stack.push( node );
						parent = node;
						l = g;
					}
					else r = f;
				}
			}
				
			computeMask >>= 1;
			checkMask >>= 1;
		}
		
		if ( DDDEBUG ) System.err.println( "Final length " + l + " node: " + parent );
		
		if ( ASSERTS ) {
			boolean rightChild;
			Node exitNode;
			long lcp;

			rightChild = parent != null && parent.extentLength < v.length() && v.getBoolean( parent.extentLength );
			exitNode = parent == null ? root : ( rightChild ? parent.right : parent.left );
			lcp = exitNode.key.longestCommonPrefixLength( v );

			if ( exitNode.intercepts( lcp ) ) { // We can do asserts only if the result is correct
				/* If parent is null, the extent of the root must not be a prefix of v. */
				if ( parent == null ) assert root.key.longestCommonPrefixLength( v ) < root.extentLength;
				else {
					assert parent.extentLength == l;

					/* If parent is not null, the extent of the parent must be a prefix of v, 
					 * and the extent of the exit node must be either v, or not a prefix of v. */
					assert ! exact || parent.extent().longestCommonPrefixLength( v ) == parent.extentLength;

					if ( stack != null ) {
						/** We check that the stack contains exactly all handles that are backjumps
						 * of the length of the extent of the parent. */
						l = parent.extentLength;
						while( l != 0 ) {
							final Node t = map.get( parent.key.subVector( 0, l ), true );
							if ( t != null ) assert stack.contains( t );
							l ^= ( l & -l );
						}

						/** We check that the stack contains the nodes you would obtain by searching from
						 * the top for nodes to fix. */
						long left = 0;
						for( int i = 0; i < stack.size(); i++ ) {
							assert stack.get( i ).handleLength() == twoFattest( left, parent.extentLength ) :
								stack.get( i ).handleLength() + " != " + twoFattest( left, parent.extentLength ) + " " + i + " " + stack ;
							left = stack.get( i ).extentLength;
						}
					}

				}
			}
		}
		
		return parent;
	}
	
	
	@SuppressWarnings("unchecked")
	public boolean contains( final Object o ) {
		if ( DDEBUG ) System.err.println( "contains(" + o + ")" );
		if ( size == 0 ) return false;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );
		final long[] state = Hashes.preprocessMurmur( v, 0 );
		Node parentExitNode = getParentExitNode( v, state, null, false );
		boolean rightChild = parentExitNode != null && parentExitNode.extentLength < v.length() && v.getBoolean( parentExitNode.extentLength );
		Node exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		final long lcp = exitNode.key.longestCommonPrefixLength( v );
		
		if ( ! exitNode.intercepts( lcp ) ) {
			parentExitNode = getParentExitNode( v, state, null, true );
			rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extentLength );
			exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
			if ( ASSERTS ) assert exitNode.intercepts( exitNode.key.longestCommonPrefixLength( v ) );
		}
		
		return exitNode.key.equals( v );
	}

	@SuppressWarnings("unchecked")
	public Node pred( final Object o ) {
		if ( size == 0 ) return null;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );
		final long[] state = Hashes.preprocessMurmur( v, 0 );
		Node parentExitNode = getParentExitNode( v, state, null, false );
		boolean rightChild = parentExitNode != null && parentExitNode.extentLength < v.length() && v.getBoolean( parentExitNode.extentLength );
		Node exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		
		final long lcp = exitNode.key.longestCommonPrefixLength( v );
		
		if ( ! exitNode.intercepts( lcp ) ) {
			parentExitNode = getParentExitNode( v, state, null, true );
			rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extentLength );
			exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
			if ( ASSERTS ) assert exitNode.intercepts( exitNode.key.longestCommonPrefixLength( v ) );
		}
		
		if ( rightChild ) {
			while( exitNode.jumpRight != null ) exitNode = exitNode.jumpRight;
			return exitNode;
		}
		else {
			while( exitNode.jumpLeft != null ) exitNode = exitNode.jumpLeft;
			return exitNode.left;
		}
		
	}

	@SuppressWarnings("unchecked")
	public Node succ( final Object o ) {
		if ( size == 0 ) return null;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );
		final long[] state = Hashes.preprocessMurmur( v, 0 );
		Node parentExitNode = getParentExitNode( v, state, null, false );
		boolean rightChild = parentExitNode != null && parentExitNode.extentLength < v.length() && v.getBoolean( parentExitNode.extentLength );
		Node exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		
		final long lcp = exitNode.key.longestCommonPrefixLength( v );
		
		if ( ! exitNode.intercepts( lcp ) ) {
			parentExitNode = getParentExitNode( v, state, null, true );
			rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extentLength );
			exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
			if ( ASSERTS ) assert exitNode.intercepts( exitNode.key.longestCommonPrefixLength( v ) );
		}

		if ( rightChild ) {
			while( exitNode.jumpRight != null ) exitNode = exitNode.jumpRight;
			return exitNode.right;
		}
		else {
			while( exitNode.jumpLeft != null ) exitNode = exitNode.jumpLeft;
			return exitNode;
		}
	}

	private void writeObject( final ObjectOutputStream s ) throws IOException {
		s.defaultWriteObject();
		if ( size > 0 ) writeNode( root, s );
	}
	
	private static void writeNode( final Node node, final ObjectOutputStream s ) throws IOException {
		s.writeBoolean( node.isInternal() );
		s.writeLong( node.extentLength - node.parentExtentLength );
		if ( node.isInternal() ) {
			writeNode( node.left, s );
			writeNode( node.right, s );
		}
		else BitVectors.writeFast( node.key, s );
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		initHeadTail();
		map = new Map( size );
		System.err.println( map.length );
		if ( size > 0 ) root = readNode( s, 0, 0, map, new ObjectArrayList<Node>(), new ObjectArrayList<Node>(), new IntArrayList(), new IntArrayList(), new BooleanArrayList() );
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
	private Node readNode( final ObjectInputStream s, final int depth, final long parentExtentLength, final Map map, final ObjectArrayList<Node> leafStack, final ObjectArrayList<Node> jumpStack, final IntArrayList depthStack, final IntArrayList segmentStack, final BooleanArrayList dirStack ) throws IOException, ClassNotFoundException {
		final boolean isInternal = s.readBoolean();
		final long pathLength = s.readLong();
		final Node node = new Node();
		node.parentExtentLength = parentExtentLength;
		node.extentLength = parentExtentLength + pathLength;

		if ( ! dirStack.isEmpty() ) {
			/* We cannot fix the jumps of nodes that are more than this number of levels up in the tree. */
			final int maxDepthDelta = segmentStack.topInt();
			final boolean dir = dirStack.topBoolean();
			Node anc;
			int d;
			long jumpLength;
			do {
				jumpLength = ( anc = jumpStack.top() ).jumpLength();
				d = depthStack.topInt();
				/* To be fixable, a node must be within the depth limit, and we must intercept its jump length (note that
				 * we cannot use .intercept() as the state of node is not yet consistent). If a node cannot be fixed, no
				 * node higher in the stack can. */
				if ( depth - d <= maxDepthDelta && jumpLength > parentExtentLength && ( ! isInternal || jumpLength <= node.extentLength ) ) {
					if ( DDEBUG ) System.err.println( "Setting " + ( dir ? "right" : "left" ) + " jump pointer of " + anc + " to " + node );
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
			jumpStack.push( node );
			depthStack.push( depth );
			
			if ( DDEBUG ) System.err.println( "Recursing into left node... " );
			node.left = readNode( s, depth + 1, node.extentLength, map, leafStack, jumpStack, depthStack, segmentStack, dirStack );
			
			int top = segmentStack.popInt();
			if ( top != 1 ) segmentStack.push( top - 1 );
			else dirStack.popBoolean();
			
			if ( dirStack.isEmpty() || dirStack.topBoolean() != true ) {
				segmentStack.push( 1 );
				dirStack.push( true );
			}
			else segmentStack.push( segmentStack.popInt() + 1 );
			jumpStack.push( node );
			depthStack.push( depth );
			
			if ( DDEBUG ) System.err.println( "Recursing into right node... " );
			node.right = readNode( s, depth + 1, node.extentLength, map, leafStack, jumpStack, depthStack, segmentStack, dirStack );
			
			top = segmentStack.popInt();
			if ( top != 1 ) segmentStack.push( top - 1 );
			else dirStack.popBoolean();

			/* We assign the reference leaf, and store the associated key. */
			final Node referenceLeaf = leafStack.pop(); 
			node.key = referenceLeaf.key;
			node.reference = referenceLeaf;
			referenceLeaf.reference = node;

			map.addNew( node );

			if ( ASSERTS ) { // Check jump pointers.
				Node t;
				t = node.left; 
				while( t.isInternal() && ! t.intercepts( node.jumpLength() ) ) t = t.left;
				assert node.jumpLeft == t : node.jumpLeft + " != " + t + " (" + node + ")";
				t = node.right;
				while( t.isInternal() && ! t.intercepts( node.jumpLength() ) ) t = t.right;
				assert node.jumpRight == t : node.jumpRight + " != " + t + " (" + node + ")";
			}
		}
		else {
			node.key = BitVectors.readFast( s );
			leafStack.push( node );
			addBefore( tail, node );
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
				zFastTrie.add( LongArrayBitVector.copy( transformationStrategy.toBitVector( lineIterator.next() ) ) );
				pl.lightUpdate();
			}
			pl.done();
			BinIO.storeObject( zFastTrie, functionName );
		}
		else {
			ZFastTrie<CharSequence> zFastTrie = new ZFastTrie<CharSequence>( transformationStrategy );
			while( lineIterator.hasNext() ) {
				zFastTrie.add( lineIterator.next() );
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
