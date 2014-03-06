package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2014 Sebastiano Vigna 
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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * are guaranteed to find each time a vertex that never appeared in some 3-hyperedge associated with previous hinges). For each hinge, the corresponding values
 * in {@link #vertex1} and {@link #vertex2} complete the 3-hyperedge associated with the hinge, and the corresponding
 * value in {@link #edge} contains the index of the 3-hyperedge (i.e., the index of the key that generated the hyperedge).
 * The computation of the last value can be {@linkplain #HypergraphSorter(int, boolean) disabled} if you do not need it.
 * 
 * <p>The public fields {@link #numEdges} and {@link #numVertices} expose information about the generated
 * 3-hypergraph. For <var>m</var> edges, the number of vertices will be &lceil {@linkplain #GAMMA &gamma;}<var>m</var> &rceil; + 1, rounded up 
 * to the nearest multiple of 3, unless <var>m</var> is zero, in which case the number of vertices will be zero, too.
 * Note that index of the hash function that generated a particular vertex of a 3-hyperedge can be recovered
 * dividing by {@link #partSize}, which is exactly {@link #numVertices}/3. 
 * 
 * <p>To guarantee consistent results when reading a Majewski-Wormald-Havas-Czech-like structure,
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
 * <h3>The XOR trick</h3>
 * 
 * <p>Since the list of edges incident to a node is
 * accessed during the peeling process only when the node has degree one, we can actually
 * store in a single integer the XOR of the indices of all edges incident to the node. This approach
 * significantly simplifies the code and reduces memory usage.
 * 
 * <p>We push further this idea by observing that since one of the vertices of an edge incident to <var>x</var>
 * is exactly <var>x</var>, we can even avoid storing the edges at all and just store for each node
 * two additional values that contain a XOR of the other two nodes of each edge incident on the node. This
 * approach simplifies considerably the code as every 3-hyperedge is presented to us as a distinguished vertex (the
 * hinge) plus two additional vertices.
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
 * class, this class will {@linkplain Logger#debug(String) log at debug level} what's happening.
 *
 * <P>Note that if the the peeling process fails more than twice, you should
 * suspect that there are duplicates in the string list (unless the number of vertices is very small).
 *
 * @author Sebastiano Vigna
 */


public class HypergraphSorter<T> {
	/** The initial size of the queue used to peel the 3-hypergraph. */
	private static final int INITIAL_QUEUE_SIZE = 1024;
	private final static Logger LOGGER = LoggerFactory.getLogger( HypergraphSorter.class );
	
	/** The mythical threshold (or better, a very closed upper bound of): random 3-hypergraphs
	 * are acyclic with high probability if the ratio vertices/edges exceeds this constant. */
	public static final double GAMMA = 1.23;
	/** The number of vertices in the hypergraph (&lceil; {@link #GAMMA} * {@link #numEdges} &rceil; + 1, rounded up to the nearest multiple of 3). */
	public final int numVertices;
	/** {@link #numVertices} / 3. */
	public final int partSize;
	/** The number of edges in the hypergraph. */
	public final int numEdges;
	/** For each vertex, the XOR of the values of the smallest other vertex in each incident 3-hyperedge. */
	public final int[] vertex1;
	/** For each vertex, the XOR of the values of the largest other vertex in each incident 3-hyperedge. */
	public final int[] vertex2;
	/** For each vertex, the XOR of the indices of incident 3-hyperedges. */
	public final int[] edge;
	/** The edge stack. At the end of a successful sorting phase, it contains the hinges in reverse order. */
	public final int[] stack;
	/** The degree of each vertex of the intermediate 3-hypergraph. */
	private final int[] d;
	/** If true, we do not allocate {@link #edge} and to not compute edge indices. */
	private final boolean computeEdges;
	/** Whether we ever called {@link #generateAndSort(Iterator, long)} or {@link #generateAndSort(Iterator, TransformationStrategy, long)}. */
	private boolean neverUsed;
	/** Initial top of the edge stack. */
	private int top;
	/** The stack used for peeling the graph. */
	private final IntArrayList visitStack;

	/** Creates a hypergraph sorter for a given number of edges.
	 * 
	 * @param numEdges the number of edges of this hypergraph sorter.
	 * @param computeEdges if false, the index of the edge associated with each hinge will not be computed.
	 */
	public HypergraphSorter( final int numEdges, final boolean computeEdges ) {
		this.numEdges = numEdges;
		this.computeEdges = computeEdges;
		// The theoretically sufficient number of vertices
		final int m = numEdges == 0 ? 0 : (int)Math.ceil( GAMMA * numEdges ) + 1;
		// This guarantees that the number of vertices is a multiple of 3
		numVertices = m + ( 3 - m % 3 ) % 3;
		partSize = numVertices / 3;
		vertex1 = new int[ numVertices ];
		vertex2 = new int[ numVertices ];
		edge = computeEdges ? new int[ numVertices ] : null;
		stack = new int[ numEdges ];
		d = new int[ numVertices ];
		visitStack = new IntArrayList( INITIAL_QUEUE_SIZE );
		neverUsed = true;
	}

	/** Creates a hypergraph sorter for a given number of edges.
	 * 
	 * @param numEdges the number of edges of this hypergraph sorter.
	 */
	public HypergraphSorter( final int numEdges ) {
		this( numEdges, true );
	}
	
	/** Turns a bit vector into a 3-hyperedge.
	 * 
	 * <p>The returned edge satisfies the property that the <var>i</var>-th vertex is in the interval
	 * [<var>i</var>&middot;{@link #partSize}..<var>i</var>+1&middot;{@link #partSize}). However, if there are no edges
	 * the vector <code>e</code> will be filled with -1.
	 * 
	 * @param bv a bit vector.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param partSize <code>numVertices</code>/3 (to avoid a division).
	 * @param e an array to store the resulting edge.
	 */
	public static void bitVectorToEdge( final BitVector bv, final long seed, final int numVertices, final int partSize, final int e[] ) {
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] hash = new long[ 3 ];
		Hashes.jenkins( bv, seed, hash );
		e[ 0 ] = (int)( ( hash[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 1 ] = (int)( partSize + ( hash[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 2 ] = (int)( ( partSize << 1 ) + ( hash[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
	}
	
	/** Turns a bit vector into a 3-hyperedge.
	 * 
	 * @param bv a bit vector.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param e an array to store the resulting edge.
	 * @see #bitVectorToEdge(BitVector, long, int, int, int[])
	 */
	public static void bitVectorToEdge( final BitVector bv, final long seed, final int numVertices, final int e[] ) {
		bitVectorToEdge( bv, seed, numVertices, (int)( numVertices * 0xAAAAAAABL >>> 33 ), e ); // Fast division by 3
	}
	
	/** Turns a triple of longs into a 3-hyperedge.
	 * 
	 * @param triple a triple of intermediate hashes.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param partSize <code>numVertices</code>/3 (to avoid a division).
	 * @param e an array to store the resulting edge.
	 * @see #bitVectorToEdge(BitVector, long, int, int, int[])
	 */
	public static void tripleToEdge( final long[] triple, final long seed, final int numVertices, final int partSize, final int e[] ) {
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] hash = new long[ 3 ];
		Hashes.jenkins( triple, seed, hash );
		e[ 0 ] = (int)( ( hash[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 1 ] = (int)( partSize + ( hash[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
		e[ 2 ] = (int)( ( partSize << 1 ) + ( hash[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % partSize );
	}
	

	/** Turns a triple of longs into a 3-hyperedge.
	 * 
	 * @param triple a triple of intermediate hashes.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param e an array to store the resulting edge.
	 * @see #bitVectorToEdge(BitVector, long, int, int, int[])
	 */
	public static void tripleToEdge( final long[] triple, final long seed, final int numVertices, final int e[] ) {
		tripleToEdge( triple, seed, numVertices, (int)( numVertices * 0xAAAAAAABL >>> 33 ), e );  // Fast division by 3
	}
	
	private final void cleanUpIfNecessary() {
		if ( ! neverUsed ) { 
			IntArrays.fill( d, 0 );
			IntArrays.fill( vertex1, 0 );
			IntArrays.fill( vertex2, 0 );
			if ( computeEdges ) IntArrays.fill( edge, 0 );
		}
		neverUsed = false;
	}

	private final void xorEdge( final int k, final int x, final int y, final int z, boolean partial ) {
		//if ( partial ) System.err.println( "Stripping <" + x + ", " + y + ", " + z + ">: " + Arrays.toString( edge ) + " " + Arrays.toString( hinge ) );
		if ( computeEdges ) {
			if ( ! partial ) edge[ x ] ^= k;
			edge[ y ] ^= k;
			edge[ z ] ^= k;
		}

		if ( ! partial ) {
			if ( y < z ) {
				vertex1[ x ] ^= y;
				vertex2[ x ] ^= z;
			}
			else {
				vertex1[ x ] ^= z;
				vertex2[ x ] ^= y;
			}
		}

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
		//if ( ! partial ) System.err.println( "After adding <" + x + ", " + y + ", " + z + ">" );
	}

	/** Generates a random 3-hypergraph and tries to sort its edges.
	 * 
	 * @param iterator an iterator returning {@link #numEdges} keys.
	 * @param transform a transformation from keys to bit vectors.
	 * @param seed a 64-bit random seed.
	 * @return true if the sorting procedure succeeded.
	 */
	public boolean generateAndSort( final Iterator<? extends T> iterator, final TransformationStrategy<? super T> transform, final long seed ) {
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
	public boolean generateAndSort( final Iterator<long[]> iterator, final long seed ) {
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
	private boolean sort() {
		// We cache all variables for faster access
		final int[] d = this.d;
		//System.err.println("Visiting...");
		LOGGER.debug( "Peeling hypergraph..." );

		top = 0;
		for( int i = 0; i < numVertices; i++ ) if ( d[ i ] == 1 ) peel( i );

		if ( top == numEdges ) LOGGER.debug( "Peeling completed." );
		else {
			LOGGER.debug( "Visit failed: peeled " + top + " edges out of " + numEdges + "." );
			return false;
		}

		return true;
	}

	private void peel( final int x ) {
		// System.err.println( "Visiting " + x + "..." );
		final int[] vertex1 = this.vertex1;
		final int[] vertex2 = this.vertex2;
		final int[] edge = this.edge;
		final int[] stack = this.stack;
		final int[] d = this.d;
		final IntArrayList visitStack = this.visitStack;
		
		// Queue initialization
		int v;
		visitStack.clear();
		visitStack.push( x );

		while ( ! visitStack.isEmpty() ) {
			v = visitStack.popInt();
			if ( d[ v ] == 1 ) {
				stack[ top++ ] = v;
				--d[ v ];
				// System.err.println( "Stripping <" + v + ", " + vertex1[ v ] + ", " + vertex2[ v ] + ">" );
				xorEdge( computeEdges ? edge[ v ] : -1, v, vertex1[ v ], vertex2[ v ], true );
				if ( --d[ vertex1[ v ] ] == 1 ) visitStack.add( vertex1[ v ] );
				if ( --d[ vertex2[ v ] ] == 1 ) visitStack.add( vertex2[ v ] );				
			}
		}
	}
}

