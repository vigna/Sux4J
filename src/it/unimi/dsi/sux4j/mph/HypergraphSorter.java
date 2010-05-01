package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2009 Sebastiano Vigna 
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
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.Iterator;

import org.apache.log4j.Logger;

/** A class implementing the 3-hypergraph edge sorting procedure that is necessary for the
 * Majewski-Wormald-Havas-Czech technique.
 * 
 * <p>Bohdan S. Majewski, Nicholas C. Wormald, George Havas, and Zbigniew J. Czech have
 * described in &ldquo;A family of perfect hashing methods&rdquo;, 
 * <i>Comput. J.</i>, 39(6):547&minus;554, 1996,
 * a 3-hypergraph based technique to store functions
 * (actually, the paper uses the technique just to store a permutation of the key set, but
 * it is clear it can be used to store any function). More generally, the procedure 
 * first generates a random 3-partite 3-hypergraph whose edges correspond to elements of the function domain.
 * Then, it sorts the edges of the random 3-hypergraph so that for each edge at least one vertex, the <em>hinge</em>, never
 * appeared before in the sorted edge list (this happens with high probability if the number of vertices is at
 * least {@linkplain #GAMMA &gamma;} times the number of edges).
 * 
 * <p>Instances of this class contain the data necessary to generate the random hypergraph
 * and apply the sorting procedure. At construction time, you provide just the desired number
 * of edges; then, each call to {@link #generateAndSort(Iterator, TransformationStrategy, long) generateAndSort()}
 * will generate a new 3-hypergraph using a 64-bit seed, an iterator returning the key set,
 * and a corresponding {@link TransformationStrategy}. If the method returns true, the sorting was
 * successful and in the public field {@link #stack} you can retrieve hinges in the <em>opposite</em>
 * of the desired order (so enumerating hinges starting from the last in {@link #stack} you
 * are guaranteed to find each time a vertex that never appeared before). For each hinge, the corresponding values
 * in {@link #vertex1} and {@link #vertex2} complete the 3-hyperedge associated to the hinge.
 * 
 * <p>This class is able to compute two kind of data associated to each hinge: the index of the {@linkplain #edge edge} associated
 * to the hinge, or the index of {@linkplain #hinge} (i.e., by which of the three hash functions it was generated).
 * The kind of computation is chosen at {@linkplain #HypergraphSorter(int, boolean) construction time}: 
 * for instance, {@link MWHCFunction} uses the first
 * kind of data, but {@link MinimalPerfectHashFunction} uses the second kind.
 * 
 * <p>The public fields {@link #numEdges} and {@link #numVertices} expose information about the generated
 * 3-hypergraph. For <var>m</var> edges, the number of vertices will be &lceil {@linkplain #GAMMA &gamma;}<var>m</var> &rceil; + 1, rounded up 
 * to the nearest multiple of 3, unless <var>m</var> is zero, in which case the number of vertices will be zero, too. 
 * 
 * <p>To guarantee the same results when reading a Majewski-Wormald-Havas-Czech-like structure,
 * the method {@link #bitVectorToEdge(BitVector, long, int, int, int[]) bitVectorToEdge()} can be used to retrieve, starting from
 * a bit vector, the corresponding edge. While having a function returning the edge starting
 * from a key would be more object-oriented and avoid hidden dependencies, it would also require
 * storing the transformation provided at construction time, which would make this class non-thread-safe.
 * Just be careful to transform the keys into bit vectors using
 * the same {@link TransformationStrategy} used to generate the random 3-hypergraph.
 * 
 * <h2>Support for preprocessed keys</h2>
 * 
 * <p>This class provides two special access points for classes that have pre-digested their keys. The methods
 * {@link #generateAndSort(Iterator, long)} and {@link #tripleToEdge(long[], long, int, int, int[])} use
 * fixed-length 192-bit keys under the form of triples of longs. The intended usage is that of 
 * turning the keys into such a triple using {@linkplain Hashes#jenkins(BitVector) Jenkins's hash} and
 * then operating directly on the hash codes. This is particularly useful in chunked constructions, where
 * the keys are replaced by their 192-bit hashes in the first place. Note that the hashes are actually
 * rehashed using {@link Hashes#jenkins(long[], long, long[])}&mdash;this is necessary to vary the associated edges whenever
 * the generated 3-hypergraph is not acyclic.
 * 
 * <p><strong>Warning</strong>: you cannot mix the bitvector-based and the triple-based constructors and static
 * methods. It is your responsibility to pair them correctly.
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>We use {@linkplain Hashes#jenkins(BitVector, long, long[]) Jenkin's hash} in its 64-bit incarnation: beside
 * providing an excellent hash function, it actually computes <em>three</em> 64-bit hash values,
 * which is exactly what we need.
 * 
 * <h3>Djamel's XOR trick</h3>
 * 
 * <p>Djamel Belazzougui has suggested that since the list of edges incident to a node is
 * accessed during the peeling process only when the node has degree one, we can actually
 * store in a single integer the XOR of the indices of all edges incident to the node. This approach
 * significantly simplifies the code and reduces memory usage.
 * 
 * <p>We push further this idea by observing that since one of the vertices of an edge incident to <var>x</var>
 * is exactly <var>x</var>, we can even avoid storing the edges at all and just store for each node
 * two additional values that contain a XOR of the other two nodes of each edge incident on the node. Some
 * care must be taken, because at that point a lot of information is no longer available.
 * 
 * <p>When computing hinge indices, however, the situation is slightly more complicated, as we keep track
 * of the XOR over all edges incident on a node of the index of the hash function that generated the vertex.
 * This makes it possible to get, in the end, the index for the edges associated to hinges.
 * 
 * <h3>Rounds and Logging</h3>
 * 
 * <P>Building and sorting a large 3-hypergraph may take hours. As it happens with all probabilistic algorithms,
 * one can just give estimates of the expected time.
 * 
 * <P>There are two probabilistic sources of problems: duplicate edges and non-acyclic hypergraphs.
 * However, the probability of duplicate edges is vanishing when <var>n</var> approaches infinity,
 * and once the hypergraph has been generated, the stripping procedure succeeds in an expected number
 * of trials that tends to 1 as <var>n</var> approaches infinity.
 *  
 * <P>To help diagnosing problem with the generation process
 * class, this class will log at {@link org.apache.log4j.Level#DEBUG DEBUG} level
 * what's happening.
 *
 * <P>Note that if during the generation process the log warns more than once about duplicate edges (this happens
 * at {@link org.apache.log4j.Level#INFO INFO} level), you should
 * suspect that there are duplicates in the string list, as duplicate edges are <em>extremely</em> unlikely.
 *
 * @author Sebastiano Vigna
 */


public class HypergraphSorter<T> {
	private static final boolean ASSERTS = true;
	private static final int INITIAL_QUEUE_SIZE = 1024;
	private final static Logger LOGGER = Util.getLogger( HypergraphSorter.class );
	
	public static enum Result { OK, DUPLICATE, CYCLIC }; 
	
	/** The mythical threshold (or better, a very closed upper bound of): random 3-hypergraphs
	 * are acyclic with high probability if the ratio vertices/edges exceeds this constant. */
	public static final double GAMMA = 1.23;
	/** The number of vertices in the hypergraph ( &lceil; {@link #GAMMA} * {@link #numEdges} &rceil; + 1 ). */
	public final int numVertices;
	/** {@link #numVertices} / 3. */
	public final int partSize;
	/** The number of edges in the hypergraph. */
	public final int numEdges;
	/** An 2&times;n array recording the edges incident to a vertex. It is *reversed*
		   w.r.t. what you would expect to reduce object creation. The first value is the 
		   XOR of the smallest other vertex of the edges, the second value the XOR of the largest other vertex of the edges. */
	public final int[] vertex1;
	public final int[] vertex2;
	public final int[] edge;
	public final byte[] hinge;
	/** The edge stack. Used also to invert the edge permutation. */
	public final int[] stack;
	/** The degree of each vertex of the intermediate hypergraph. */
	private final int[] d;
	/** Whether we ever called {@link #generateAndSort(Iterator, long)} or {@link #generateAndSort(Iterator, TransformationStrategy, long)}. */
	private boolean neverUsed;
	/** Initial top of the edge stack. */
	private int top;
	/** The queue used for visiting the graph. */
	private final IntArrayList queue;
	private final boolean computeHinges;

	/** Creates a hypergraph sorter for a given number of edges.
	 * 
	 * @param numEdges the number of edges of this hypergraph sorter.
	 */
	public HypergraphSorter( final int numEdges, final boolean computeHinges ) {
		this.numEdges = numEdges;
		this.computeHinges = computeHinges;
		// The theoretically sufficient number of vertices
		final int m = numEdges == 0 ? 0 : (int)Math.ceil( GAMMA * numEdges ) + 1;
		// This guarantees that the number of vertices is a multiple of 3
		numVertices = m + ( 3 - m % 3 ) % 3;
		if ( ASSERTS ) assert numVertices % 3 == 0;
		partSize = numVertices / 3;
		vertex1 = new int[ numVertices ];
		vertex2 = new int[ numVertices ];
		edge = computeHinges ? null : new int[ numVertices ];
		hinge = computeHinges ? new byte[ numVertices ] : null;
		stack = new int[ numEdges ];
		d = new int[ numVertices ];
		queue = new IntArrayList( INITIAL_QUEUE_SIZE );
		neverUsed = true;
	}

	/** Turns a bit vector into an edge.
	 * 
	 * <p>This method will never return a degenerate edge. However, if there are no edges
	 * the vector <code>e</code> will be filled with -1.
	 * 
	 * @param bv a bit vector.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param partSize <code>numVertices</code>/3 (to avoid a division).
	 * @param e an array to store the resulting edge.
	 */
	public static void bitVectorToEdge( final BitVector bv, final long seed, final int numVertices, final int partSize, final int e[] ) {
		// TODO: eliminate numVertices at some point
		if ( ASSERTS ) assert numVertices % 3 == 0;
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] hash = new long[ 3 ];
		Hashes.jenkins( bv, seed, hash );
		e[ 0 ] = (int)( ( hash[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 1 ] = (int)( partSize + ( hash[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 2 ] = (int)( partSize * 2 + ( hash[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
	}
	
	/** Turns a bit vector into an edge.
	 * 
	 * <p>This method will never return a degenerate edge. However, if there are no edges
	 * the vector <code>e</code> will be filled with -1.
	 * 
	 * @param bv a bit vector.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param e an array to store the resulting edge.
	 * @deprecated As of Sux4J 2.1, replaced by {@link #bitVectorToEdge(BitVector, long, int, int, int[])}.
	 */
	@Deprecated
	public static void bitVectorToEdge( final BitVector bv, final long seed, final int numVertices, final int e[] ) {
		bitVectorToEdge( bv, seed, numVertices, (int)( numVertices * 0xAAAAAAABL >>> 33 ), e ); // Fast division by 3
	}
	
	/** Turns a triple of longs into an edge.
	 * 
	 * <p>This method will never return a degenerate edge. However, if there are no edges
	 * the vector <code>e</code> will be filled with -1.
	 * 
	 * @param triple a triple of intermediate hashes.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param partSize <code>numVertices</code>/3 (to avoid a division).
	 * @param e an array to store the resulting edge.
	 */
	public static void tripleToEdge( final long[] triple, final long seed, final int numVertices, final int partSize, final int e[] ) {
		// TODO: eliminate numVertices at some point
		if ( ASSERTS ) assert numVertices % 3 == 0;
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] hash = new long[ 3 ];
		Hashes.jenkins( triple, seed, hash );
		e[ 0 ] = (int)( ( hash[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 1 ] = (int)( partSize + ( hash[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 2 ] = (int)( partSize * 2 + ( hash[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
	}

	

	/** Turns a triple of longs into an edge.
	 * 
	 * <p>This method will never return a degenerate edge. However, if there are no edges
	 * the vector <code>e</code> will be filled with -1.
	 * 
	 * @param triple a triple of intermediate hashes.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param e an array to store the resulting edge.
	 * @deprecated As of Sux4J 2.1, replaced by {@link #tripleToEdge(long[], long, int, int, int[])}.
	 */
	@Deprecated
	public static void tripleToEdge( final long[] triple, final long seed, final int numVertices, final int e[] ) {
		tripleToEdge( triple, seed, numVertices, (int)( numVertices * 0xAAAAAAABL >>> 33 ), e );  // Fast division by 3
	}

	
	private final void cleanUpIfNecessary() {
		if ( ! neverUsed ) { 
			IntArrays.fill( d, 0 );
			IntArrays.fill( vertex1, 0 );
			IntArrays.fill( vertex2, 0 );
			if ( computeHinges ) ByteArrays.fill( hinge, (byte)0 );
			else IntArrays.fill( edge, 0 );
		}
		neverUsed = false;
	}

	private final void xorEdge( final int k, final int x, final int y, final int z, boolean partial ) {
		//if ( partial ) System.err.println( "Stripping <" + x + ", " + y + ", " + z + ">: " + Arrays.toString( edge ) + " " + Arrays.toString( hinge ) );
		if ( computeHinges ) {
			if ( ! partial || k != 0 ) {
				vertex1[ x ] ^= y;
				vertex2[ x ] ^= z;
			}
			if ( ! partial || k != 1 ) {
				hinge[ y ] ^= 1;
				vertex1[ y ] ^= x;
				vertex2[ y ] ^= z;
			}
			if ( ! partial || k != 2 ) {
				hinge[ z ] ^= 2;
				vertex1[ z ] ^= x;
				vertex2[ z ] ^= y;
			}
		}
		else {
			if ( ! partial ) {
				edge[ x ] ^= k;
				if ( y < z ) {
					vertex1[ x ] ^= y;
					vertex2[ x ] ^= z;
				}
				else {
					vertex1[ x ] ^= z;
					vertex2[ x ] ^= y;
				}
			}

			edge[ y ] ^= k;
			edge[ z ] ^= k;

			if ( x < z ) {
				vertex1[ y ] ^= x;
				vertex2[ y ] ^= z;
			}
			else {
				vertex1[ y ] ^= z;
				vertex2[ y ] ^= x;
			}
			if ( x < y ) {
				vertex1[ z ] ^= x;
				vertex2[ z ] ^= y;
			}
			else {
				vertex1[ z ] ^= y;
				vertex2[ z ] ^= x;
			}
		}
		//if ( ! partial ) System.err.println( "After adding <" + x + ", " + y + ", " + z + ">: " + Arrays.toString( edge ) + " " + Arrays.toString( hinge ) );
	}
	/** Generates a random 3-hypergraph and tries to sort its edges.
	 * 
	 * @param iterator an iterator returning {@link #numEdges} keys.
	 * @param transform a transformation from keys to bit vectors.
	 * @param seed a 64-bit random seed.
	 * @return true if the sorting procedure succeeded.
	 */
	public Result generateAndSort( final Iterator<? extends T> iterator, final TransformationStrategy<? super T> transform, final long seed ) {
		// We cache all variables for faster access
		final int[] d = this.d;
		final int[] e = new int[ 3 ];
		cleanUpIfNecessary();
		
		/* We build the XOR'd edge list and compute the degree of each vertex. */
		for( int k = 0; k < numEdges; k++ ) {
			bitVectorToEdge( transform.toBitVector( iterator.next() ), seed, numVertices, partSize, e );
			xorEdge( k, e[ 0 ], e[ 1 ], e[ 2 ], false );
			d[ e[ 0 ] ]++;
			d[ e[ 1 ] ]++;
			d[ e[ 2 ] ]++;
		}

		if ( iterator.hasNext() ) throw new IllegalStateException( "This " + HypergraphSorter.class.getSimpleName() + " has " + numEdges + " edges, but the provided iterator returns more" );

		return sort();
	}

	
	/** Generates a random 3-hypergraph and tries to sort its edges.
	 * 
	 * @param iterator an iterator returning {@link #numEdges} triples of longs.
	 * @param seed a 64-bit random seed.
	 * @return true if the sorting procedure succeeded.
	 */
	public Result generateAndSort( final Iterator<long[]> iterator, final long seed ) {
		// We cache all variables for faster access
		final int[] d = this.d;
		final int[] e = new int[ 3 ];
		cleanUpIfNecessary();
		
		/* We build the XOR'd edge list and compute the degree of each vertex. */
		for( int k = 0; k < numEdges; k++ ) {
			tripleToEdge( iterator.next(), seed, numVertices, partSize, e );
			xorEdge( k, e[ 0 ], e[ 1 ], e[ 2 ], false );
			d[ e[ 0 ] ]++;
			d[ e[ 1 ] ]++;
			d[ e[ 2 ] ]++;
		}

		if ( iterator.hasNext() ) throw new IllegalStateException( "This " + HypergraphSorter.class.getSimpleName() + " has " + numEdges + " edges, but the provided iterator returns more" );

		return sort();
	}

	/** Sorts the edges of a random 3-hypergraph in &ldquo;leaf peeling&rdquo; order.
	 * 
	 * @return true if the sorting procedure succeeded.
	 */
	private Result sort() {
		// We cache all variables for faster access
		final int[] d = this.d;
		//System.err.println("Visiting...");
		LOGGER.debug( "Visiting hypergraph..." );

		top = 0;
		for( int i = 0; i < numVertices; i++ ) if ( d[ i ] == 1 ) visit( i );

		if ( top == numEdges ) LOGGER.debug( "Visit completed." );
		else {
			LOGGER.debug( "Visit failed: stripped " + top + " edges out of " + numEdges + "." );
			return Result.CYCLIC;
		}

		return Result.OK;
	}


	private void visit( int x ) {
		//System.err.println( "Visiting " + x + "..." );
		final int[] vertex1 = this.vertex1;
		final int[] vertex2 = this.vertex2;
		final int[] edge = this.edge;
		final int[] stack = this.stack;
		final int[] d = this.d;
		final IntArrayList queue = this.queue;
		
		// Queue initialization
		int v, start = 0; // Initial top of the recursion stack.
		queue.clear();
		queue.add( x );

		while ( start < queue.size() ) {
			v = queue.getInt( start++ );
			if ( d[ v ] == 1 ) {
				
				stack[ top++ ] = v;
				--d[ v ];
				if ( computeHinges ) {
					if ( ASSERTS ) assert hinge[ v ] >= 0 && hinge[ v ] < 3 : Byte.toString( hinge[ v ] );
					switch( hinge[ v ] ) {
					case 0: xorEdge( hinge[ v ], v, vertex1[ v ], vertex2[ v ], true ); break;
					case 1: xorEdge( hinge[ v ], vertex1[ v ], v, vertex2[ v ], true ); break;
					case 2: xorEdge( hinge[ v ], vertex1[ v ], vertex2[ v ], v, true ); break;
					default: throw new IllegalStateException();
					}
					
				}
				else {
					//System.err.println( "Stripping <" + k + ", " + vertex1[ k ] + ", " + vertex2[ k ] + ">" );
					xorEdge( edge[ v ], v, vertex1[ v ], vertex2[ v ], true );
				}
				if ( --d[ vertex1[ v ] ] == 1 ) queue.add( vertex1[ v ] );
				if ( --d[ vertex2[ v ] ] == 1 ) queue.add( vertex2[ v ] );				
			}
		}
	}
}

