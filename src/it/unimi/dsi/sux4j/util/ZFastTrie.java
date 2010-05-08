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
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
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
	private static final boolean ASSERTS = false;
	private static final boolean DDEBUG = false;
	private static final boolean DDDEBUG = false;
	
	
	private final static class Node implements Serializable {
		private static final long serialVersionUID = 1L;
		Node left, right;
		Node jumpLeft, jumpRight;
		LongArrayBitVector extent;
		long nameLength;
		
		public long handleLength() {
			return twoFattest( nameLength - 1, extent.length() );
		}
		
		public String toString() {
			return "[Extent: " + extent + " (" + nameLength + ")]";
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
			System.err.println( "Node " + n + " (parent extent length: " + parentExtentLength + ")" );
		}
		assert ( n.left == null ) == ( n.right == null );
		assert n.left == null || nodes.remove( n ) : n;
		assert n.left == null || map.keySet().contains( Hashes.jenkins( n.extent.subVector( 0, twoFattest( n.nameLength - 1, n.extent.length() ) ) ) ) : n;
		assert parent == null || parent.extent.equals( n.extent.subVector( 0, parent.extent.length() ) );
		assert parentExtentLength < n.extent.length();
		assert n.nameLength == parentExtentLength + 1 : n.nameLength + " != " + ( parentExtentLength + 1 ) + " " + n;
		return 1 + visit( n.left, n, n.extent.length(), depth + 1, nodes ) + visit( n.right, n, n.extent.length(), depth + 1, nodes );
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

		final Node parentExitNode = getParentExitNode( v );
		final boolean rightChild = parentExitNode != null && v.getBoolean( parentExitNode.extent.length() );
		final Node exitNode = parentExitNode == null ? root : ( rightChild ? parentExitNode.right : parentExitNode.left );
		if ( DDDEBUG ) System.err.println( "Exit node " + exitNode );
		
		if ( exitNode.extent.equals( v ) ) return false; // Already there
		
		final long lcp = exitNode.extent.longestCommonPrefixLength( v );
		final boolean exitDirection = v.getBoolean( lcp );
		final boolean cutLow = lcp >= twoFattest( exitNode.nameLength - 1, exitNode.extent.length() );
		
		if ( DDEBUG ) System.err.println( "lcp: " + lcp );
		Node leaf = new Node();
		Node internal = new Node();

		leaf.extent = LongArrayBitVector.copy( v );
		leaf.nameLength = lcp + 1;

		if ( DDDEBUG ) System.err.println( "Cut " + ( cutLow ? "low" : "high") + "; exit to the " + ( exitDirection ? "right" : "left") );
		
		if ( cutLow ) {
			// internal is the node below
			internal.left = exitNode.left;
			internal.right = exitNode.right;

			internal.extent = exitNode.extent;
			exitNode.extent = internal.extent.copy( 0, lcp );
			internal.nameLength = lcp + 1;

			/* Depending on whether the exit node is a leaf, we might need to insert into the table
			 * either the exit node or the new internal node. */
			if ( exitNode.right == null ) map.put( Hashes.jenkins( exitNode.extent.subVector( 0, twoFattest( exitNode.nameLength - 1, lcp ) ) ), exitNode );
			else map.put( Hashes.jenkins( internal.extent.subVector( 0, twoFattest( lcp, internal.extent.length() ) ) ), internal );

			if ( exitDirection ) {
				exitNode.right = leaf;
				exitNode.left = internal;
			}
			else {				
				exitNode.right = internal;				
				exitNode.left = leaf;
			}	
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
			
			map.put( Hashes.jenkins( internal.extent.subVector( 0, twoFattest( internal.nameLength - 1, lcp ) ) ), internal );

			if ( exitDirection ) {
				internal.left = exitNode;
				internal.right = leaf;
			}
			else {
				internal.left = leaf;
				internal.right = exitNode;
			}			
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

	public Node getParentExitNode( final BitVector v ) {
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
			}
		}
		
		if ( DDDEBUG ) System.err.println( "Parent exit node: " + parent );
		
		return parent;
	}

	@SuppressWarnings("unchecked")
	public boolean contains( final Object o ) {
		if ( size == 0 ) return false;
		final BitVector v = transform.toBitVector( (T)o ).fast();
		final Node parentExitNode = getParentExitNode( v );
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
