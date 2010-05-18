package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2010 Sebastiano Vigna 
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
import it.unimi.dsi.fastutil.io.BinIO;
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
import it.unimi.dsi.sux4j.mph.ZFastTrieDistributor;

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

/** A monotone minimal perfect hash implementation based on fixed-size bucketing that uses 
 * a {@linkplain ZFastTrieDistributor z-fast trie} as a distributor.
 * 
 */

public class ZFastTrie<T> extends AbstractObjectSortedSet<T> implements Serializable {
    public static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Util.getLogger( ZFastTrie.class );
	private static final boolean ASSERTS = false;
	private static final boolean SHORT_SIGNATURES = false;
	private static final boolean DDEBUG = false;
	private static final boolean DDDEBUG = false;
	
		
	protected final static class Map {
		private static final long serialVersionUID = 1L;

		protected long[] key;
		protected Node[] value;
		protected boolean collision[];

		protected int mask;
		protected int size;
		protected int length;	

		protected final static long PRIME = ( 1L << 61 ) - 1;
		protected final static long LOW = ( 1L << 32 ) - 1;
		protected final static long HIGH = LOW << 32;
		
		private final static long multAdd( int x, long a, long b ) {
			final long a0 = ( a & LOW ) * x;
			final long a1 = ( a & HIGH ) * x;
			final long c0 = a0 + ( a1 << 32 );
			final long c1 = ( a0 >>> 32 ) + a1;
			return ( c0 & PRIME ) + ( c1 >>> 29 ) + b;
		}
		
		private final static long rehash( final int x ) {
			// TODO: we should really use tabulation-based 5-way independent hashing.
			final long h = multAdd( x, 104659742703825433L, 8758810104009432107L );  
			return ( h & PRIME ) + ( h >>> 61 );
		}
		
		private void assertTable() {
			for( int i = key.length; i-- != 0; ) if ( value[ i ] != null ) assert get( value[ i ].handle(), false ) == value[ i ];
		}
		
		
		public Map( int size ) {
			length = 1 << Fast.ceilLog2( 1 + size * 4 / 3 );
			mask = length - 1;
			key = new long[ length ];
			value = new Node[ length ];
			collision = new boolean[ length ];
		}

		public Map() {
			length = 4;
			mask = length - 1;
			key = new long[ length ];
			value = new Node[ length ];
			collision = new boolean[ length ];
		}

		private int findPos( final BitVector v, final long signature ) {
			int pos = (int)( rehash( (int)( signature ^ signature >>> 32 ) ) & mask );
			//int i = 0;
			while( value[ pos ] != null ) {
				if ( key[ pos ] == signature && ( ! collision[ pos ] || v.equals( value[ pos ].handle() ) ) ) break;
				pos = ( pos + 1 ) & mask;
				//i++;
			}
			//System.err.println( i );
			return pos;
		}
		
		private int findExactPos( final BitVector v, final long signature ) {
			int pos = (int)( rehash( (int)( signature ^ signature >>> 32 ) ) & mask );
			//int i = 0;
			while( value[ pos ] != null ) {
				if ( key[ pos ] == signature && v.equals( value[ pos ].handle() ) ) break;
				pos = ( pos + 1 ) & mask;
				//i++;
			}
			//System.err.println( i );
			return pos;
		}
		
		private int findFreePos( final long signature ) {
			int pos = (int)( rehash( (int)( signature ^ signature >>> 32 ) ) & mask );
			//int i = 0;
			while( value[ pos ] != null ) {
				if ( key[ pos ] == signature ) collision[ pos ] = true;
				pos = ( pos + 1 ) & mask;
				//i++;
			}
			//System.err.println( i );
			return pos;
		}
		
		public void clear() {
			length = 1024;
			mask = length - 1;
			size = 0;
			key = new long[ length ];
			value = new Node[ length ];
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
							while( value[ ++pos ] == null );
							i++;
							return LongArrayBitVector.copy( value[ pos ].handle() );
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
							while( value[ ++pos ] == null );
							i++;
							return value[ pos ];
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

		public Node addNew( final Node v ) {
			long signature = v.handleHash();
			if ( SHORT_SIGNATURES ) signature &= 0xF;
			int pos = findFreePos( signature );
			if ( ASSERTS ) assert value[ pos ] == null;
			
			size++;
			key[ pos ] = signature;
			value[ pos ] = v;

			if ( size * 4 / 3 > length ) {
				length *= 2;
				mask = length - 1;
				final long newKey[] = new long[ length ];
				final Node[] newValue = new Node[ length ];
				final boolean[] newCollision = new boolean[ length ];
				final long[] key = this.key;
				final Node[] value = this.value;
				
				for( int i = key.length; i-- != 0; ) {
					if ( value[ i ] != null ) {
						signature = key[ i ];
						pos = (int)rehash( (int)( signature ^ signature >>> 32 ) ) & mask; 
						while( newValue[ pos ] != null ) {
							if ( newKey[ pos ] == signature ) newCollision[ pos ] = true;
							pos = ( pos + 1 ) & mask;
						}
						newKey[ pos ] = key[ i ];
						newValue[ pos ] = value[ i ];
					}
				}

				this.key = newKey;
				this.value = newValue;
				this.collision = newCollision;
			}
			
			if ( ASSERTS ) assertTable();
			return null;
		}

		public int size() {
			return size;
		}


		public Node get( long signature, final BitVector v, final boolean exact ) {
			if ( SHORT_SIGNATURES ) signature &= 0xF;
			final int pos = exact ? findExactPos( v, signature ) : findPos( v, signature );
			return value[ pos ];
		}

		public Node get( final BitVector v, final boolean exact ) {
			return get( Hashes.murmur( v, 0 ), v, exact );
		}
	}

	
	protected final static class Node implements Serializable {
		protected static final long serialVersionUID = 1L;
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

		public BitVector extent() {
			return key.subVector( 0, extentLength );
		}
		
		public BitVector handle() {
			return key.subVector( 0, handleLength() );
		}
		
		public long handleHash() {
			return Hashes.murmur( handle(), 0 );
		}
		
		public boolean isLeaf() {
			return jumpLeft == null;
		}
		
		public boolean isInternal() {
			return jumpLeft != null;
		}
		
		public long handleLength() {
			return twoFattest( parentExtentLength, extentLength );
		}
		
		public long jumpLength() {
			final long handleLength = twoFattest( parentExtentLength, extentLength );
			return handleLength + ( handleLength & -handleLength );
		}
		
		public boolean intercepts( long h ) {
			return h > parentExtentLength && ( isLeaf() || h <= extentLength );
		}
		
		public String toString() {
			return ( isLeaf() ? "[" : "(" ) + Integer.toHexString( hashCode() & 0xFFFF ) + 
				( key == null ? "" : 
					" " + ( extentLength > 16 ? key.subVector( 0, 8 ) + "..." + key.subVector( extentLength - 8, extentLength ): key.subVector( 0, extentLength ) ) ) +
					" (" + parentExtentLength + ".." + extentLength + "], " + handleLength() + "->" + jumpLength() +
				( isLeaf() ? "]" : ")" );
		}
	}
	
	/** The number of elements. */
	private int size;
	private transient Node root;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	private transient Map map;
	private transient Node head;
	private transient Node tail; 
	
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

	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements and transformation strategy. 
	 * 
	 * @param elements an iterator returning the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public ZFastTrie( final Iterator<? extends T> elements, final TransformationStrategy<? super T> transform ) {
		this( transform );
		while( elements.hasNext() ) add( elements.next() );
	}

	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements and transformation strategy. 
	 * 
	 * @param elements an iterable containing the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public ZFastTrie( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) {
		this( elements.iterator(), transform );
	}

	public int size() {
		return size > Integer.MAX_VALUE ? -1 : (int)size;
	}

	public long numBits() {
		return 0;
	}

	@Override
	public boolean remove( Object k ) {
		// TODO Auto-generated method stub
		return super.remove( k );
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
	public final static long twoFattest( final long l, final long r ) {
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
		// Shortest key
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
			assert node.reference.reference == node;
		}
		
		assert nodes.size() == map.size();
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

			assert c++ == size - 1;
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
	private void setJumps( final Node node ) {
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

	/** Fixes the right jumps of the ancestors of a node.
	 * 
	 * @param exitNode the exit node.
	 * @param above the above node in the new trie.
	 * @param leaf the new leaf.
	 * @param leaf2 
	 * @param stack a stack containing the fat ancestors of <code>exitNode</code>.
	 * @param cutLow 
	 */
	private void fixRightJumps( Node exitNode, final Node above, final Node below, Node leaf, final ObjectArrayList<Node> stack, boolean cutLow ) {
		if ( DDEBUG ) System.err.println( "fixRightJumps(" + exitNode + ", " + above + ", " + leaf + ", " + stack );
		final long lcp = leaf.parentExtentLength;
		Node toBeFixed = null;
		long jumpLength = -1;

		/* There could be nodes whose left jumps point to exit node above the lcp. In this
		 * case, they must point to the node above. This is a no-op for low cuts, but it moves
		 * the jump pointer to the new internal node for high cuts. */
		if ( cutLow ) 
			for( int i = stack.size(); i-- != 0; ) {
				toBeFixed = stack.get( i );
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpLeft == exitNode && jumpLength > lcp ) toBeFixed.jumpLeft = below;
			}
		else
			for( int i = stack.size(); i-- != 0; ) {
				toBeFixed = stack.get( i );
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpLeft != exitNode || jumpLength > lcp ) break;
				toBeFixed.jumpLeft = above;
			}
		
		while( ! stack.isEmpty() ) {
			toBeFixed = stack.top();
			jumpLength = toBeFixed.jumpLength();
			if ( toBeFixed.jumpRight != exitNode || jumpLength > lcp ) break;
			toBeFixed.jumpRight = above;
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
	
	/** Fixes the left jumps of the ancestors of a node.
	 * 
	 * @param exitNode the exit node.
	 * @param above the above node in the new trie.
	 * @param leaf the new leaf.
	 * @param leaf2 
	 * @param stack a stack containing the fat ancestors of <code>exitNode</code>.
	 * @param cutLow 
	 */
	private void fixLeftJumps( Node exitNode, final Node above, final Node below, Node leaf, final ObjectArrayList<Node> stack, boolean cutLow ) {
		if ( DDEBUG ) System.err.println( "fixLeftJumps(" + exitNode + ", " + above + ", " + leaf + ", " + stack ); 
		final long lcp = leaf.parentExtentLength;
		Node toBeFixed = null;
		long jumpLength = -1;
		
		/* There could be nodes whose right jumps point to exit node above the lcp. In this
		 * case, they must point to the node above. This is a no-op for low cuts, but it moves
		 * the jump pointer to the new internal node for high cuts. */
		if ( cutLow ) 
			for( int i = stack.size(); i-- != 0; ) {
				toBeFixed = stack.get( i );
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpRight == exitNode && jumpLength > lcp ) toBeFixed.jumpRight = below;
			}
		else 
			for( int i = stack.size(); i-- != 0; ) {
				toBeFixed = stack.get( i );
				jumpLength = toBeFixed.jumpLength();
				if ( toBeFixed.jumpRight != exitNode || jumpLength > lcp ) break;
				toBeFixed.jumpRight = above;
			}

		while( ! stack.isEmpty() ) {
			toBeFixed = stack.top();
			jumpLength = toBeFixed.jumpLength();
			if ( toBeFixed.jumpLeft != exitNode || jumpLength > lcp ) break;
			toBeFixed.jumpLeft = above;
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
	
	@Override
	public boolean add( final T k ) {
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( k ) );
		if ( DDEBUG ) System.err.println( "add(" + v + ")" );
		if ( DDEBUG ) System.err.println( "Map: " + map + " root: " + root );
		
		if ( size == 0 ) {
			root = new Node();
			root.key = LongArrayBitVector.copy( v );
			root.extentLength = v.length();
			root.parentExtentLength = 0;
			root.reference = root;
			addAfter( head, root );
			size++;
			assertTrie();
			return true;
		}

		final ObjectArrayList<Node> stack = new ObjectArrayList<Node>( 64 );
		
		Node parentExitNode;
		boolean rightChild;
		Node exitNode;
		long lcp;

		parentExitNode = getParentExitNode( v, stack, false );
		rightChild = parentExitNode != null && parentExitNode.extentLength < v.length() && v.getBoolean( parentExitNode.extentLength );
		exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		lcp = exitNode.key.longestCommonPrefixLength( v );
		
		if ( ! exitNode.intercepts( lcp ) ) {
			stack.clear();
			parentExitNode = getParentExitNode( v, stack, true );
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

		leaf.key = LongArrayBitVector.copy( v );
		leaf.extentLength = v.length();
		leaf.parentExtentLength = lcp;

		if ( DDDEBUG ) System.err.println( "Cut " + ( cutLow ? "low" : "high") + "; exit to the " + ( exitDirection ? "right" : "left") );

		final Node above = cutLow ? exitNode : internal;
		Node below = cutLow ? internal : exitNode;
		
		if ( exitDirection ) fixRightJumps( exitNode, above, below, leaf, stack, cutLow );
		else fixLeftJumps( exitNode, above, below, leaf, stack, cutLow );

		if ( cutLow ) {
			// internal is the node below

			internal.key = exitNode.key;
			internal.reference = exitNode.reference;
			internal.reference.reference = internal;
			internal.extentLength = exitNode.extentLength;
			exitNode.key = leaf.key;
			exitNode.reference = leaf;
			leaf.reference = exitNode;
			exitNode.extentLength = internal.parentExtentLength = lcp;

			
			/* Depending on whether the exit node is a leaf, we might need 
			 * to insert into the table either the exit node or the new internal node,
			 * and we might need to extract the exit node from the leaf list. */
			if ( exitNode.isLeaf() ) {
				remove( exitNode );
				addAfter( exitNode.left, internal );
				map.addNew( exitNode );
				if ( exitDirection ) exitNode.jumpLeft = internal;
				else exitNode.jumpRight = internal;
			}
			else {
				internal.left = exitNode.left;
				internal.right = exitNode.right;
				setJumps( internal );
				map.addNew( internal );
			}

			if ( exitDirection ) {
				exitNode.right = exitNode.jumpRight = leaf;
				exitNode.left = internal;
			}
			else {				
				exitNode.right = internal;				
				exitNode.left = exitNode.jumpLeft = leaf;
			}
			
			
		}
		else {
			// internal is the node above
			if ( exitNode == root ) root = internal; // Update root
			else {
				if ( rightChild ) parentExitNode.right = internal;
				else parentExitNode.left = internal;
			}
			
			internal.key = leaf.key;
			internal.reference = leaf;
			leaf.reference = internal;
			
			internal.parentExtentLength = exitNode.parentExtentLength;
			internal.extentLength = exitNode.parentExtentLength = lcp;

			/** Since we cut high, the jump of the handle length of the new internal
			 *  node must necessarily fall into exitNode's skip interval. */
			
			if ( exitDirection ) {
				internal.left = internal.jumpLeft = exitNode;
				internal.right = internal.jumpRight = leaf;
			}
			else {
				internal.left = internal.jumpLeft = leaf;
				internal.right = internal.jumpRight = exitNode;
			}			
			
			map.addNew( internal );

		}

		if ( DDEBUG ) System.err.println( "After insertion, map: " + map + " root: " + root );

		size++;

		if ( exitDirection ) {
			while( below.isInternal() ) below = below.jumpRight;
			addAfter( below, leaf );
		}
		else {
			while( below.isInternal() ) below = below.jumpLeft;
			addBefore( below, leaf );
		}
		
		if ( ASSERTS ) assertTrie();
		if ( ASSERTS ) assert contains( k );
		
		return true;
	}

	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		// TODO Auto-generated method stub
		return super.clone();
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
	
	public Node getParentExitNode( final LongArrayBitVector v, final ObjectArrayList<Node> stack, final boolean exact ) {
		if ( ASSERTS ) assert size > 0;
		if ( size == 1 ) return null;
		if ( DDDEBUG ) {
			System.err.println( "getParentExitNode(" + v + ")" );
			//System.err.println( "Map: " + map );
		}
		final long length = v.length();
		long r = length;
		
		final long state[] = Hashes.preprocessMurmur( v, 0 );

		long l = 0;
		int i = Fast.mostSignificantBit( r );
		long mask = 1L << i;
		Node node = null, parent = null;
		while( r - l > 1 ) {
			if ( ASSERTS ) assert i > -1;
			if ( DDDEBUG ) System.err.println( "[" + l + ".." + r + "]; i = " + i );
			if ( ( l & mask ) != ( r - 1 & mask ) ) {
				final long f = ( r - 1 ) & ( -1L << i );

				if ( DDDEBUG ) System.err.println( "Inquiring with key " + v.subVector( 0, f ) + " (" + f + ")" );
				
				node = map.get( Hashes.murmur( v, f, state ), v.subVector( 0, f ), exact );
				
				if ( node == null ) {
					if ( DDDEBUG ) System.err.println( "Missing" );
					r = f;
				}
				else {
					long g = node.extentLength;
					if ( DDDEBUG ) System.err.println( "Found extent of length " + g );

					if ( g >= f && g <= length && equals( node.key, v, f, g ) ) {
						if ( stack != null ) stack.push( node );
						parent = node;
						l = g;
					}
					else r = f;
				}
			}
				
			i--;
			mask >>= 1;
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
					/* If parent is not null, the extent of the parent must be a prefix of v, 
					 * and the extent of the exit node must be either v, or not a prefix of v. */
					assert parent.extentLength == l;
					assert ! exact || parent.extent().longestCommonPrefixLength( v ) == parent.extentLength;
					if ( ! exitNode.key.equals( v ) && exitNode.key.longestCommonPrefixLength( v ) == exitNode.extentLength ) {
						boolean nextBit = v.getBoolean( exitNode.extentLength );
						Node exitExitNode = nextBit ? exitNode.right : exitNode.left;
						System.err.println( "The exit node is " + exitNode + ", but its child " + exitExitNode + " is instead" );
						throw new AssertionError();
					}

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
						for( i = 0; i < stack.size(); i++ ) {
							assert stack.get( i ).handleLength() == twoFattest( left, parent.extentLength ) :
								stack.get( i ).handleLength() + " != " + twoFattest( left, parent.extentLength ) + " " + i + " " + stack ;
							left = stack.get( i ).extentLength;
						}
					}
				}
			}
		}
		
		if ( DDDEBUG ) System.err.println( "Parent exit node: " + parent );
		
		return parent;
	}

	@SuppressWarnings("unchecked")
	public boolean contains( final Object o ) {
		if ( size == 0 ) return false;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );

		Node parentExitNode;
		boolean rightChild;
		Node exitNode;
		long lcp;

		parentExitNode = getParentExitNode( v, null, false );
		rightChild = parentExitNode != null && parentExitNode.extentLength < v.length() && v.getBoolean( parentExitNode.extentLength );
		exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		lcp = exitNode.key.longestCommonPrefixLength( v );
		
		if ( ! exitNode.intercepts( lcp ) ) {
			parentExitNode = getParentExitNode( v, null, true );
			rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extentLength );
			exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
			lcp = exitNode.key.longestCommonPrefixLength( v );
			if ( ASSERTS ) assert exitNode.intercepts( lcp );
		}
		
		return exitNode.key.equals( v );
	}

	@SuppressWarnings("unchecked")
	public Node pred( final Object o ) {
		if ( size == 0 ) return null;
		final LongArrayBitVector v = LongArrayBitVector.copy( transform.toBitVector( (T)o ) );
		final Node parentExitNode = getParentExitNode( v, null, false );
		final boolean exitDirection = v.getBoolean( parentExitNode.extentLength );
		Node exitNode = parentExitNode == null ? root : ( exitDirection ? parentExitNode.right : parentExitNode.left );
		
		if ( exitDirection ) {
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
		final Node parentExitNode = getParentExitNode( v, null, false );
		final boolean exitDirection = v.getBoolean( parentExitNode.extentLength );
		Node exitNode = parentExitNode == null ? root : ( exitDirection ? parentExitNode.right : parentExitNode.left );
		
		if ( exitDirection ) {
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
		else {
			BitVectors.writeFast( node.key, s );
		}
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		initHeadTail();
		ObjectArrayList<Node> leafStack = new ObjectArrayList<Node>();
		ObjectArrayList<Node> jumpStack = new ObjectArrayList<Node>();
		BooleanArrayList dirStack = new BooleanArrayList();
		map = new Map( size );
		if ( size > 0 ) root = readNode( s, 0, leafStack, map, jumpStack, dirStack );
		
		if ( ASSERTS ) assert dirStack.isEmpty();
		if ( ASSERTS ) assert jumpStack.isEmpty();
		if ( ASSERTS ) assertTrie();
	}

	private Node readNode( final ObjectInputStream s, final long parentExtentLength, final ObjectArrayList<Node> leafStack, final Map map, final ObjectArrayList<Node> jumpStack, final BooleanArrayList dirStack ) throws IOException, ClassNotFoundException {
		final boolean isInternal = s.readBoolean();
		final long pathLength = s.readLong();
		final Node node = new Node();
		node.parentExtentLength = parentExtentLength;
		node.extentLength = parentExtentLength + pathLength;

/*		if ( ! dirStack.isEmpty() ) {
			final boolean dir = dirStack.popBoolean();
			Node anc;
			long jumpLength;
			for(;;) {
				jumpLength = ( anc = jumpStack.pop() ).jumpLength();
				if ( jumpLength > parentExtentLength && ( ! isInternal || jumpLength <= node.extentLength ) ) {
					if ( dir ) anc.jumpRight = node; 
					else anc.jumpLeft = node;
				}
				else if ( jumpLength > node.extentLength ) {
					dirStack.push( dir );
					jumpStack.push( anc );
					break;
				}
				
				if ( dirStack.isEmpty() ) break;
				
				if ( dirStack.popBoolean() != dir ) {
					dirStack.push( ! dir );
					break;
				}
			}
		}
*/		
		if ( isInternal ) {
			//jumpStack.push( node );
			//dirStack.push( false );
			node.left = readNode( s, node.extentLength, leafStack, map, jumpStack, dirStack );
			if ( ASSERTS ) assert ! jumpStack.contains( node );
			//jumpStack.push( node );
			//dirStack.push( true );
			node.right = readNode( s, node.extentLength, leafStack, map, jumpStack, dirStack );
			if ( ASSERTS ) assert ! jumpStack.contains( node );
			final Node referenceLeaf = leafStack.pop(); 
			node.key = referenceLeaf.key;
			node.reference = referenceLeaf;
			referenceLeaf.reference = node;
			
				Node t;
				t = node.left; 
				while( t.isInternal() && ! t.intercepts( node.jumpLength() ) ) t = t.left;
				node.jumpLeft = t;
				t = node.right;
				while( t.isInternal() && ! t.intercepts( node.jumpLength() ) ) t = t.right;
				node.jumpRight = t;

			map.addNew( node );
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
