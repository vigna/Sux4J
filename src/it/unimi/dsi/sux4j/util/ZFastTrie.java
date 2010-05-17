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
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
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
	private static final boolean ASSERTS = true;
	private static final boolean DDEBUG = false;
	private static final boolean DDDEBUG = false;
	
	
	private final static class Node implements Serializable {
		private static final long serialVersionUID = 1L;
		Node left, right;
		Node jumpLeft, jumpRight;
		long extentLength;
		LongArrayBitVector key;
		long parentExtentLength;

		public BitVector extent() {
			return key.subVector( 0, extentLength );
		}
		
		public BitVector handle() {
			return key.subVector( 0, handleLength() );
		}
		
		public long handleHash() {
			return Hashes.jenkins( handle(), 0 );
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
	private transient Long2ObjectOpenHashMap<Node> map;
	private transient Node head;
	private transient Node tail; 
	
	public ZFastTrie( final TransformationStrategy<? super T> transform ) {
		this.transform = transform;
		this.map = new Long2ObjectOpenHashMap<Node>();
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
		long root = -1;
		/* Keeps track of which nodes in map are reachable using left/right from the root. */
		ObjectOpenHashSet<Node> nodes = new ObjectOpenHashSet<Node>();
		/* Keeps track of leaves. */
		ObjectOpenHashSet<Node> leaves = new ObjectOpenHashSet<Node>();
		/* Keeps track of reference to leaf keys in internal nodes. */
		ObjectOpenHashSet<BitVector> references = new ObjectOpenHashSet<BitVector>();
		
		assert size == 0 && map.size() == 0 || size == map.size() + 1;
		
		/* Search for the root (shortest handle) and check that nodes and handles do match. */
		for( long v : map.keySet() ) {
			final long vHandleLength = map.get( v ).handleLength();
			if ( root == -1 || map.get( root ).handleLength() > vHandleLength ) root = v;
			final Node node = map.get( v );
			nodes.add( node );
			assert v == node.handleHash() : node;
		}
		
		assert nodes.size() == map.size();
		assert size == 1 || this.root == map.get( root );
		
		if ( size > 1 ) {
			/* Verify doubly linked list of leaves. */
			Node toRight = head.right, toLeft = tail.left;
			for( int i = 1; i < size; i++ ) {
				assert toRight.key.compareTo( toRight.right.key ) < 0 : toRight.key + " >= " + toRight.right.key + " " + toRight;
				assert toLeft.key.compareTo( toLeft.left.key ) > 0 : toLeft.key + " >= " + toLeft.left.key + " " + toLeft;
				toRight = toRight.right;
				toLeft = toLeft.left;
			}

			final int numNodes = visit( map.get( root ), null, 0, 0, nodes, leaves, references );
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
			assert map.keySet().contains( n.handleHash() ) : n;

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
		final BitVector v = transform.toBitVector( k ).fast();
		if ( DDEBUG ) System.err.println( "add(" + v + ")" );
		if ( DDEBUG ) System.err.println( "Map: " + map + " root: " + root );
		
		if ( size == 0 ) {
			root = new Node();
			root.key = LongArrayBitVector.copy( v );
			root.extentLength = v.length();
			root.parentExtentLength = 0; // A trick to get 0 the first time
			addAfter( head, root );
			size++;
			assertTrie();
			return true;
		}

		final ObjectArrayList<Node> stack = new ObjectArrayList<Node>( 64 );
		final Node parentExitNode = getParentExitNode( v, stack );
		final boolean rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extentLength );
		final Node exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		if ( DDDEBUG ) System.err.println( "Exit node " + exitNode );
		
		if ( exitNode.key.equals( v ) ) return false; // Already there
		
		final long lcp = exitNode.key.longestCommonPrefixLength( v );
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
			internal.extentLength = exitNode.extentLength;
			exitNode.key = leaf.key;
			exitNode.extentLength = internal.parentExtentLength = lcp;

			
			/* Depending on whether the exit node is a leaf, we might need 
			 * to insert into the table either the exit node or the new internal node. */
			if ( exitNode.isLeaf() ) {
				remove( exitNode );
				addAfter( exitNode.left, internal );
				map.put( exitNode.handleHash(), exitNode );
				if ( exitDirection ) exitNode.jumpLeft = internal;
				else exitNode.jumpRight = internal;
			}
			else {
				internal.left = exitNode.left;
				internal.right = exitNode.right;
				setJumps( internal );
				map.put( internal.handleHash(), internal );
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
			
			map.put( internal.handleHash(), internal );

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

	public Node getParentExitNode( final BitVector v, final ObjectArrayList<Node> stack ) {
		if ( ASSERTS ) assert size > 0;
		if ( size == 1 ) return null;
		if ( DDDEBUG ) {
			System.err.println( "getParentExitNode(" + v + ")" );
			//System.err.println( "Map: " + map );
		}
		final long length = v.length();
		long r = length;
		
		final long state[][] = Hashes.preprocessJenkins( v, 0 );
		final long[] a = state[ 0 ], b = state[ 1 ], c = state[ 2 ];

		long l = 0;
		int i = Fast.mostSignificantBit( r );
		long mask = 1L << i;
		Node node = null, parent = null;
		while( r - l > 1 ) {
			if ( ASSERTS ) assert i > -1;
			if ( DDDEBUG ) System.err.println( "[" + l + ".." + r + "]; i = " + i );
			if ( ( l & mask ) != ( r - 1 & mask ) ) {
				final long f = ( r - 1 ) & ( -1L << i );
				node = map.get( Hashes.jenkins( v, f, a, b, c ) );
				if ( DDDEBUG ) System.err.println( "Inquiring with key " + v.subVector( 0, f ) + " (" + f + ")" );
				
				if ( node == null ) {
					if ( DDDEBUG ) System.err.println( "Missing" );
					r = f;
				}
				else {
					long g = node.extentLength;
					if ( DDDEBUG ) System.err.println( "Found extent of length " + g );

					if ( g <= length && node.key.subVector( f, g ).equals( v.subVector( f, g ) ) ) {
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
			/* If parent is null, the extent of the root must not be a prefix of v. */
			if ( parent == null ) assert root.key.longestCommonPrefixLength( v ) < root.extentLength;
			else {
				/* If parent is not null, the extent of the parent must be a prefix of v, 
				 * and the extent of the exit node must be either v, or not a prefix of v. */
				assert parent.extentLength == l;
				final Node exitNode = v.getBoolean( l ) ? parent.right : parent.left;
				assert parent.extent().longestCommonPrefixLength( v ) == parent.extentLength;
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
						final Node t = map.get( Hashes.jenkins( parent.key.subVector( 0, l ) ) );
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
		
		if ( DDDEBUG ) System.err.println( "Parent exit node: " + parent );
		
		return parent;
	}

	@SuppressWarnings("unchecked")
	public boolean contains( final Object o ) {
		if ( size == 0 ) return false;
		final BitVector v = transform.toBitVector( (T)o ).fast();
		final Node parentExitNode = getParentExitNode( v, null );
		if ( parentExitNode == null ) return root.key.equals( v );
		return ( v.getBoolean( parentExitNode.extentLength ) ? parentExitNode.right : parentExitNode.left ).key.equals( v );
	}

	@SuppressWarnings("unchecked")
	public Node pred( final Object o ) {
		if ( size == 0 ) return null;
		final BitVector v = transform.toBitVector( (T)o ).fast();
		final Node parentExitNode = getParentExitNode( v, null );
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
		final BitVector v = transform.toBitVector( (T)o ).fast();
		final Node parentExitNode = getParentExitNode( v, null );
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
		ObjectArrayList<LongArrayBitVector> leafStack = new ObjectArrayList<LongArrayBitVector>();
		ObjectArrayList<Node> jumpStack = new ObjectArrayList<Node>();
		BooleanArrayList dirStack = new BooleanArrayList();
		map = new Long2ObjectOpenHashMap<Node>( size );
		if ( size > 0 ) root = readNode( s, 0, leafStack, map, jumpStack, dirStack );
		
		if ( ASSERTS ) assert dirStack.isEmpty();
		if ( ASSERTS ) assert jumpStack.isEmpty();
		if ( ASSERTS ) assertTrie();
	}

	private Node readNode( final ObjectInputStream s, final long parentExtentLength, final ObjectArrayList<LongArrayBitVector> leafStack, final Long2ObjectOpenHashMap<Node> map, final ObjectArrayList<Node> jumpStack, final BooleanArrayList dirStack ) throws IOException, ClassNotFoundException {
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
			node.key = leafStack.pop();			
			
				Node t;
				t = node.left; 
				while( t.isInternal() && ! t.intercepts( node.jumpLength() ) ) t = t.left;
				node.jumpLeft = t;
				t = node.right;
				while( t.isInternal() && ! t.intercepts( node.jumpLength() ) ) t = t.right;
				node.jumpRight = t;

			map.put( node.handleHash(), node );
		}
		else {
			node.key = BitVectors.readFast( s );
			leafStack.push( node.key );
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
