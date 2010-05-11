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
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.AbstractObjectSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.Hashes;
import it.unimi.dsi.sux4j.mph.ZFastTrieDistributor;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Comparator;
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
	private static final boolean DDEBUG = true;
	private static final boolean DDDEBUG = true;
	
	
	private final static class Node implements Serializable {
		private static final long serialVersionUID = 1L;
		Node left, right;
		Node jumpLeft, jumpRight;
		LongArrayBitVector extent;
		long nameLength;

		public boolean isLeaf() {
			return left == null;
		}
		
		public boolean isInternal() {
			return left != null;
		}
		
		public long handleLength() {
			return twoFattest( nameLength - 1, extent.length() );
		}
		
		public long jumpLength() {
			final long handleLength = twoFattest( nameLength - 1, extent.length() );
			return handleLength + ( handleLength & -handleLength );
		}
		
		public boolean intercepts( long h ) {
			return h >= nameLength && h < extent.length();
		}
		
		public String toString() {
			return ( isLeaf() ? "[" : "(" ) + Integer.toHexString( hashCode() & 0xFFFF ) + 
				( extent == null ? "" : 
					( extent.length() > 16 ? extent.subVector( 0, 8 ) + "..." + extent.subVector( extent.length() - 8 ): extent ) +
					" (" + ( nameLength - 1 ) + ".." + extent.length() + "], " + handleLength() + "->" + jumpLength() ) +
				( isLeaf() ? "]" : ")" );
		}
	}
	
	/** The number of elements. */
	private long size;
	private Node root;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	private final Long2ObjectOpenHashMap<Node> map; 
	
	public ZFastTrie( final TransformationStrategy<? super T> transform ) {
		this.transform = transform;
		this.map = new Long2ObjectOpenHashMap<Node>();
	}

	/** Creates a new hollow-trie-based monotone minimal perfect hash function using the given
	 * elements and transformation strategy. 
	 * 
	 * @param elements the elements among which the trie must be able to rank.
	 * @param transform a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, prefix-free, lexicographically increasing (in iteration order) bit vectors.
	 */
	public ZFastTrie( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) {
		this( transform );
		for( T e : elements ) add( e );
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
	
	private void assertTrie() {
		// Shortest key
		long root = -1;
		ObjectOpenHashSet<Node> nodes = new ObjectOpenHashSet<Node>();
		assert size == 0 && map.size() == 0 || size == map.size() + 1;
		for( long v : map.keySet() ) {
			final long vHandleLength = map.get( v ).handleLength();
			if ( root == -1 || map.get( root ).handleLength() > vHandleLength ) root = v;
			final Node node = map.get( v );
			nodes.add( node );
			assert v == Hashes.jenkins( node.extent.subVector( 0, vHandleLength ), 0 );
		}
		assert nodes.size() == map.size();
		assert size == 1 || this.root == map.get( root );
		if ( size > 1 ) {
			final int numNodes = visit( map.get( root ), null, 0, 0, nodes );
			assert numNodes == 2 * size - 1 : numNodes + " != " + ( 2 * size - 1 );
		}
		assert nodes.isEmpty();
	}
	
	private int visit( final Node n, final Node parent, final long parentExtentLength, final int depth, ObjectOpenHashSet<Node> nodes ) {
		if ( n == null ) return 0;
		if ( DDEBUG ) {
			for( int i = depth; i-- != 0; ) System.err.print( '\t' );
			System.err.println( "Node " + n + " (parent extent length: " + parentExtentLength + ") Jump left: " + n.jumpLeft + " Jump right: " + n.jumpRight );
		}
		assert ( n.left == null ) == ( n.right == null );
		
		if ( n.left != null ) {
			assert nodes.remove( n ) : n;
			assert map.keySet().contains( Hashes.jenkins( n.extent.subVector( 0, twoFattest( n.nameLength - 1, n.extent.length() ) ) ) ) : n;
			
			final long crossPoint = n.handleLength() + ( n.handleLength() & -n.handleLength() );
			Node jumpLeft = n.left;
			while( jumpLeft.left != null && crossPoint >= jumpLeft.extent.length() ) jumpLeft = jumpLeft.left;
			assert jumpLeft == n.jumpLeft : jumpLeft + " != " + n.jumpLeft + " (node: " + n + ")";

			Node jumpRight = n.right;
			while( jumpRight.right != null && crossPoint >= jumpRight.extent.length() ) jumpRight = jumpRight.right;
			assert jumpRight == n.jumpRight : jumpRight + " != " + n.jumpRight + " (node: " + n + ")";
		}

		assert parent == null || parent.extent.equals( n.extent.subVector( 0, parent.extent.length() ) );
		assert parentExtentLength < n.extent.length();
		assert n.nameLength == parentExtentLength + 1 : n.nameLength + " != " + ( parentExtentLength + 1 ) + " " + n;
		return 1 + visit( n.left, n, n.extent.length(), depth + 1, nodes ) + visit( n.right, n, n.extent.length(), depth + 1, nodes );
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

		for( jump = node.left; jump.isInternal() && jump.extent.length() <= jumpLength; ) {
			System.err.println( "Moving to node " + jump + "..." );
			jump = jump.jumpLeft;
		}
		System.err.println( "Exiting with node " + jump );
		if ( ASSERTS ) assert jump.nameLength - 1 <= jumpLength;
		node.jumpLeft = jump;
		for( jump = node.right; jump.isInternal() && jump.extent.length() <= jumpLength; ) jump = jump.jumpRight;
		if ( ASSERTS ) assert jump.nameLength - 1 <= jumpLength;
		node.jumpRight = jump;
	}

	/** Fixes the right jumps of the ancestors of a node.
	 * 
	 * @param exitNode the exit node.
	 * @param above the above node in the new trie.
	 * @param leaf the new leaf.
	 * @param stack a stack containing the fat ancestors of <code>exitNode</code>.
	 */
	private void fixRightJumps( Node exitNode, final Node above, final Node leaf, final ObjectArrayList<Node> stack ) {
		if ( DDEBUG ) System.err.println( "fixRightJumps(" + exitNode + ", " + above + ", " + leaf + ", " + stack );
		final long lcp = leaf.nameLength - 1;
		Node toBeFixed = null;
		long jumpLength = -1;
		
		while( ! stack.isEmpty() ) {
			toBeFixed = stack.pop();
			jumpLength = toBeFixed.jumpLength();
			if ( toBeFixed.jumpRight != exitNode || jumpLength >= lcp ) {
				stack.push( toBeFixed );
				break;
			}
			toBeFixed.jumpRight = above;
		}

		while( ! stack.isEmpty() ) {
			toBeFixed = stack.pop();
			jumpLength = toBeFixed.jumpLength();
			System.err.println( "node " + toBeFixed + " el: " + exitNode.extent.length() + " jl:" + jumpLength + " jumpRight: " + toBeFixed.jumpRight ) ;
			while( exitNode != null && toBeFixed.jumpRight != exitNode ) {
				exitNode = exitNode.jumpRight;
				System.err.println( "Moving to node " + exitNode );
			}
			if ( exitNode == null ) return;
			System.err.println( "Fixing" + toBeFixed + " jumpLength:"  + jumpLength + " lcp: "  + lcp );
			toBeFixed.jumpRight = leaf;
		}
	}
	
	/** Fixes the left jumps of the ancestors of a node.
	 * 
	 * @param exitNode the exit node.
	 * @param above the above node in the new trie.
	 * @param leaf the new leaf.
	 * @param stack a stack containing the fat ancestors of <code>exitNode</code>.
	 */
	private void fixLeftJumps( Node exitNode, final Node above, final Node leaf, final ObjectArrayList<Node> stack ) {
		if ( DDEBUG ) System.err.println( "fixLeftJumps(" + exitNode + ", " + above + ", " + leaf + ", " + stack ); 
		final long lcp = leaf.nameLength - 1;
		Node toBeFixed = null;
		long jumpLength = -1;
		
		while( ! stack.isEmpty() ) {
			toBeFixed = stack.pop();
			jumpLength = toBeFixed.jumpLength();
			if ( toBeFixed.jumpLeft != exitNode || jumpLength >= lcp ) {
				stack.push( toBeFixed );
				break;
			}
			toBeFixed.jumpLeft = above;
		}

		while( ! stack.isEmpty() ) {
			toBeFixed = stack.pop();
			jumpLength = toBeFixed.jumpLength();
			System.err.println( "node " + toBeFixed + " el: " + exitNode.extent.length() + " jl:" + jumpLength + " jumpLeft: " + toBeFixed.jumpLeft ) ;
			while( exitNode != null && toBeFixed.jumpLeft != exitNode ) {
				exitNode = exitNode.jumpLeft;
				System.err.println( "Moving to node " + exitNode );
			}
			if ( exitNode == null ) return;
			System.err.println( "Fixing" + toBeFixed + " jumpLength:"  + jumpLength + " lcp: "  + lcp );
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
			root.extent = LongArrayBitVector.copy( v );
			root.nameLength = 1; // A trick to get 0 the first time
			size++;
			assertTrie();
			return true;
		}

		final ObjectArrayList<Node> stack = new ObjectArrayList<Node>( 64 );
		final Node parentExitNode = getParentExitNode( v, stack );
		final boolean rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extent.length() );
		final Node exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		if ( DDDEBUG ) System.err.println( "Exit node " + exitNode );
		
		if ( exitNode.extent.equals( v ) ) return false; // Already there
		
		final long lcp = exitNode.extent.longestCommonPrefixLength( v );
		final boolean exitDirection = v.getBoolean( lcp );
		final boolean cutLow = lcp >= exitNode.handleLength();
		
		if ( DDEBUG ) System.err.println( "lcp: " + lcp );
		Node leaf = new Node();
		Node internal = new Node();

		leaf.extent = LongArrayBitVector.copy( v );
		leaf.nameLength = lcp + 1;

		if ( DDDEBUG ) System.err.println( "Cut " + ( cutLow ? "low" : "high") + "; exit to the " + ( exitDirection ? "right" : "left") );

		final Node above = cutLow ? exitNode : internal;
		if ( exitDirection ) fixRightJumps( exitNode, above, leaf, stack );
		else fixLeftJumps( exitNode, above, leaf, stack );
		
		if ( cutLow ) {
			// internal is the node below

			internal.extent = exitNode.extent;
			exitNode.extent = internal.extent.copy( 0, lcp );
			internal.nameLength = lcp + 1;

			internal.left = exitNode.left;
			internal.right = exitNode.right;
			setJumps( internal );
			
			if ( exitDirection ) {
				exitNode.right = exitNode.jumpRight = leaf;
				exitNode.left = internal;
			}
			else {				
				exitNode.right = internal;				
				exitNode.left = exitNode.jumpLeft = leaf;
			}	

			/* Depending on whether the exit node is a leaf, we might need to insert into the table
			 * either the exit node or the new internal node. */
			if ( exitNode.right == null ) map.put( Hashes.jenkins( exitNode.extent.subVector( 0, twoFattest( exitNode.nameLength - 1, lcp ) ) ), exitNode );
			else map.put( Hashes.jenkins( internal.extent.subVector( 0, twoFattest( lcp, internal.extent.length() ) ) ), internal );

		}
		else {
			// internal is the node above
			if ( exitNode == root ) root = internal; // Update root
			else {
				if ( rightChild ) parentExitNode.right = internal;
				else parentExitNode.left = internal;
			}
			
			internal.extent = exitNode.extent.copy( 0, lcp );

			internal.nameLength = exitNode.nameLength;
			exitNode.nameLength = lcp + 1;

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

			
			map.put( Hashes.jenkins( internal.extent.subVector( 0, twoFattest( internal.nameLength - 1, lcp ) ) ), internal );
		}

		if ( DDEBUG ) System.err.println( "After insertion, map: " + map + " root: " + root );

		size++;

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
					long g = node.extent.length();
					if ( DDDEBUG ) System.err.println( "Found extent of length " + g );

					if ( g <= length && node.extent.subVector( f, g ).equals( v.subVector( f, g ) ) ) {
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
			if ( parent == null ) assert root.extent.longestCommonPrefixLength( v ) < root.extent.length();
			else {
				/* If parent is not null, the extent of the parent must be a prefix of v, 
				 * and the extent of the exit node must be either v, or not a prefix of v. */
				assert parent.extent.length() == l;
				final Node exitNode = v.getBoolean( l ) ? parent.right : parent.left;
				assert parent.extent.longestCommonPrefixLength( v ) == parent.extent.length();
				if ( ! exitNode.extent.equals( v ) && exitNode.extent.longestCommonPrefixLength( v ) == exitNode.extent.length() ) {
					boolean nextBit = v.getBoolean( exitNode.extent.length() );
					Node exitExitNode = nextBit ? exitNode.right : exitNode.left;
					System.err.println( "The exit node is " + exitNode + ", but its child " + exitExitNode + " is instead" );
					throw new AssertionError();
				}

				if ( stack != null ) {

					/** We check that the stack contains exactly all handles that are backjumps
					 * of the length of the extent of the parent. */
					l = parent.extent.length();
					while( l != 0 ) {
						final Node t = map.get( Hashes.jenkins( parent.extent.subVector( 0, l ) ) );
						if ( t != null ) assert stack.contains( t );
						l ^= ( l & -l );
					}
					
					/** We check that the stack contains the nodes you would obtain by searching from
					 * the top for nodes to fix. */
					long left = 0;
					for( i = 0; i < stack.size(); i++ ) {
						System.err.println( left + " " + parent.extent.length() );
						assert stack.get( i ).handleLength() == twoFattest( left, parent.extent.length() ) :
							stack.get( i ).handleLength() + " != " + twoFattest( left, parent.extent.length() ) + " " + i + " " + stack ;
						left = stack.get( i ).extent.length();
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
		if ( parentExitNode == null ) return root.extent.equals( v );
		return ( v.getBoolean( parentExitNode.extent.length() ) ? parentExitNode.right : parentExitNode.left ).extent.equals( v );
	}

	
	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( ZFastTrie.class.getName(), "Builds an PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised monotone minimal perfect hash function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final TransformationStrategy<CharSequence> transformationStrategy = iso
				? TransformationStrategies.prefixFreeIso() 
				: TransformationStrategies.prefixFreeUtf16();

		ZFastTrie<CharSequence> zFastTrie = new ZFastTrie<CharSequence>( transformationStrategy );
		ProgressLogger pl = new ProgressLogger();
		pl.itemsName = "keys";
		pl.start( "Adding keys..." );
		for( MutableString s : collection ) {
			zFastTrie.add ( s );
			pl.lightUpdate();
		}
		pl.done();
		BinIO.storeObject( zFastTrie, functionName );
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
