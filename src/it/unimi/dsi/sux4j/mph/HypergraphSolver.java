package it.unimi.dsi.sux4j.mph;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2015 Sebastiano Vigna 
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
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.sux4j.mph.Modulo2System.Modulo2Equation;

import java.util.Arrays;
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
 * The computation of the last value can be {@linkplain #HypergraphSolver(int, boolean) disabled} if you do not need it.
 * 
 * <p>The public fields {@link #numEdges} and {@link #numVertices} expose information about the generated
 * 3-hypergraph. For <var>m</var> edges, the number of vertices will be &lceil; {@linkplain #GAMMA &gamma;}<var>m</var> &rceil; + 1, rounded up 
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
 * significantly simplifies the code and reduces memory usage. It is described in detail
 * in &ldquo;<a href="http://vigna.di.unimi.it/papers.php#BBOCOPRH">Cache-oblivious peeling of random hypergraphs</a>&rdquo;, by
 * Djamal Belazzougui, Paolo Boldi, Giuseppe Ottaviano, Rossano Venturini, and Sebastiano Vigna, <i>Proc.&nbsp;Data 
 * Compression Conference 2014</i>, 2014.
 * 
 * <p>We push further this idea by observing that since one of the vertices of an edge incident to <var>x</var>
 * is exactly <var>x</var>, we can even avoid storing the edges at all and just store for each node
 * two additional values that contain a XOR of the other two nodes of each edge incident on the node. This
 * approach further simplifies the code as every 3-hyperedge is presented to us as a distinguished vertex (the
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


public class HypergraphSolver<T> {
	/** The initial size of the queue used to peel the 3-hypergraph. */
	private static final int INITIAL_QUEUE_SIZE = 1024;
	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;
	private final static Logger LOGGER = LoggerFactory.getLogger( HypergraphSolver.class );
	
	/** The mythical threshold (or better, a very closed upper bound of): the 2-core of a random 
	 * 3-hypergraph is directable and the underlying system is solvable with high probability if the ratio
	 * vertices/edges exceeds this constant. */
	public static final double GAMMA = 1.09 + 0.01;
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
	/** The vector of solutions. */
	public long[] solution;
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
	private int[][] edge2Vertex;

	/** Creates a hypergraph sorter for a given number of edges.
	 * 
	 * @param numEdges the number of edges of this hypergraph sorter.
	 * @param computeEdges if false, the index of the edge associated with each hinge will not be computed.
	 */
	public HypergraphSolver( final int numEdges, final boolean computeEdges ) {
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
	public HypergraphSolver( final int numEdges ) {
		this( numEdges, true );
	}
	
	private final void cleanUpIfNecessary() {
		if ( ! neverUsed ) { 
			Arrays.fill( d, 0 );
			Arrays.fill( vertex1, 0 );
			Arrays.fill( vertex2, 0 );
			if ( computeEdges ) Arrays.fill( edge, 0 );
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

	/** Generates a random 3-hypergraph and tries to solve the system defined by its edges.
	 * 
	 * @param iterable an iterable returning {@link #numEdges} triples of longs.
	 * @param seed a 64-bit random seed.
	 * @return true if the sorting procedure succeeded.
	 */
	public boolean generateAndSolve( final Iterable<long[]> iterable, final long seed ) {
		return generateAndSolve( iterable, seed, null );
	}

	/** Generates a random 3-hypergraph and tries to solve the system defined by its edges.
	 * 
	 * @param iterable an iterable returning {@link #numEdges} triples of longs.
	 * @param seed a 64-bit random seed.
	 * @param valueList a value list for indirect resolution, or {@code null}.
	 * @return true if the sorting procedure succeeded.
	 */
	public boolean generateAndSolve( final Iterable<long[]> iterable, final long seed, final LongBigList valueList ) {
		edge2Vertex = new int[ 3 ][ numEdges ];
		// We cache all variables for faster access
		final int[] d = this.d;
		final int[] e = new int[ 3 ];
		cleanUpIfNecessary();
		
		final int[] edgeList0 = edge2Vertex[ 0 ], edgeList1 = edge2Vertex[ 1 ], edgeList2 = edge2Vertex[ 2 ];
		
		/* We build the edge list and compute the degree of each vertex. */
		final Iterator<long[]> iterator = iterable.iterator();
		for( int i = 0; i < numEdges; i++ ) {
			HypergraphSorter.tripleToEdge( iterator.next(), seed, numVertices, partSize, e );
			if ( DEBUG ) System.err.println("Edge <" + e[ 0 ] + "," + e[ 1 ] + "," + e[ 2 ] + ">" );

			xorEdge( i, e[ 0 ], e[ 1 ], e[ 2 ], false );
			edgeList0[ i ] = e[ 0 ];
			edgeList1[ i ] = e[ 1 ];
			edgeList2[ i ] = e[ 2 ];
			d[ e[ 0 ] ]++;
			d[ e[ 1 ] ]++;
			d[ e[ 2 ] ]++;
		}

		if ( iterator.hasNext() ) throw new IllegalStateException( "This " + HypergraphSolver.class.getSimpleName() + " has " + numEdges + " edges, but the provided iterator returns more" );

		return solve( valueList );
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
			LOGGER.debug( "Peeled " + top + " edges out of " + numEdges + "." );
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
	
	private boolean solve( final LongBigList valueList ) {
		final boolean peeled = sort();
		solution = new long[ numVertices ];

		if ( computeEdges ) {
			if ( ! peeled ) {
				final int[] edgeList0 = edge2Vertex[ 0 ], edgeList1 = edge2Vertex[ 1 ], edgeList2 = edge2Vertex[ 2 ];
				Modulo2System system = new Modulo2System( numVertices );

				for ( int i = 0; i < numEdges; i++ ) {
					if ( d[ edgeList0[ i ] ] > 0 && d[ edgeList1[ i ] ] > 0 && d[ edgeList2[ i ] ] > 0 ) {
						assert d[ edgeList0[ i ] ] > 1 & d[ edgeList1[ i ] ] > 1 & d[ edgeList2[ i ] ] > 1;

						system.add( new Modulo2Equation( valueList.getLong( i ), numVertices ).add( edgeList0[ i ] ).add( edgeList1[ i ] ).add( edgeList2[ i ] ) );
					}
				}
				
				if ( ! system.structuredGaussianElimination( solution ) ) {
					LOGGER.debug( "System is unsolvable" );
					return false;
				}
				assert system.check( solution );
			}

			while( top > 0 ) {
				final int x = stack[ --top ];
				final int k = edge[ x ];
				final long s = solution[ vertex1[ x ] ] ^ solution[ vertex2[ x ] ];
				solution[ x ] = valueList.getLong( k ) ^ s;

				assert ( valueList.getLong( k ) == ( solution[ x ] ^ solution[ vertex1[ x ] ] ^ solution[ vertex2[ x ] ] ) ) :
					"<" + x + "," + vertex1[ x ] + "," + vertex2[ x ] + ">: " + valueList.getLong( k ) + " != " + ( solution[ x ] ^ solution[ vertex1[ x ] ] ^ solution[ vertex2[ x ] ] );
			}
				
			return true;
		}
		else {
			if ( ! peeled ) {
				final int[] edgeList0 = edge2Vertex[ 0 ], edgeList1 = edge2Vertex[ 1 ], edgeList2 = edge2Vertex[ 2 ];

				// Compress the edge representation eliminating peeled edges.
				int length = 0;
				for ( int i = 0; i < numEdges; i++ ) {
					if ( d[ edgeList0[ i ] ] > 0 && d[ edgeList1[ i ] ] > 0 && d[ edgeList2[ i ] ] > 0 ) {
						assert d[ edgeList0[ i ] ] > 1 & d[ edgeList1[ i ] ] > 1 & d[ edgeList2[ i ] ] > 1;
						edgeList0[ length ] = edgeList0[ i ];
						edgeList1[ length ] = edgeList1[ i ];
						edgeList2[ length ] = edgeList2[ i ];
						length++;
					}
				}

				final int[][] edges = new int[ d.length ][];
				for( int i = edges.length; i-- != 0; ) edges[ i ] = new int[ d[ i ] ];

				Arrays.fill( d, 0 );
				for ( int i = 0; i < length; i++ ) {
					final int v0 = edgeList0[ i ];
					edges[ v0 ][ d[ v0 ]++ ] = i;
					final int v1 = edgeList1[ i ];
					edges[ v1 ][ d[ v1 ]++ ] = i;
					final int v2 = edgeList2[ i ];
					edges[ v2 ][ d[ v2 ]++ ] = i;
				}

				final int[] hinge = new int[ length ];
				if ( ! directHyperedges( edges, d, edgeList0, edgeList1, edgeList2, hinge, length ) ) return false;
				
				if ( DEBUG ) for( int i = 0; i < length; i++ ) System.err.println( "<" + edgeList0[ i ] + "," + edgeList1[ i ] + "," + edgeList2[ i ] + "> => " + hinge[ i ] );

				final int[] c = new int[ length ];
				for ( int i = 0; i < length; i++ ) {
					final int h = hinge[ i ];
					c[ i ] = h == edgeList1[ i ] ? 1 : h == edgeList2[ i ] ? 2 : 0;
					assert c[ i ] != 0 || edgeList0[ i ] == hinge[ i ];
					assert c[ i ] != 1 || edgeList1[ i ] == hinge[ i ];
					assert c[ i ] != 2 || edgeList2[ i ] == hinge[ i ];
				}
				
				if ( ! Modulo3System.structuredGaussianElimination( edges, c, hinge, solution ) ) {
					LOGGER.debug( "System is unsolvable" );
					return false;
				}


				if ( ASSERTS ) {
					for( int i = 0; i < length; i++ ) {
						final int k = hinge[ i ] == edgeList1[ i ] ? 1 : hinge[ i ] == edgeList2[ i ] ? 2 : 0;
						assert k != 0 || edgeList0[ i ] == hinge[ i ];
						assert k != 1 || edgeList1[ i ] == hinge[ i ];
						assert k != 2 || edgeList2[ i ] == hinge[ i ];
						assert ( k == ( solution[ edgeList0[ i ] ] + solution[ edgeList1[ i ] ] + solution[ edgeList2[ i ] ] ) % 3 ) :
							"<" + edgeList0[ i ] + "," + edgeList1[ i ] + "," + edgeList2[ i ] + ">: " + i + " != " + ( solution[ edgeList0[ i ] ] + solution[ edgeList1[ i ] ] + solution[ edgeList2[ i ] ] ) % 3;
					}
				}
				
				for( int i = 0; i < length; i++ ) if ( solution[ hinge[ i ] ] == 0 ) solution[ hinge[ i ] ] = 3;
			}

			if ( DEBUG ) System.err.println( "Peeled: " );

			// Complete with peeled hyperedges
			while ( top > 0 ) {
				final int v = stack[ --top ];
				final int k = ( v > vertex1[ v ] ? 1 : 0 ) + ( v > vertex2[ v ] ? 1 : 0 );
				if ( DEBUG ) System.err.println( "<" + v + "," + vertex1[ v ] + "," + vertex2[ v ] + "> => @" + k );  
				assert k >= 0 && k < 3 : Integer.toString( k );
				//System.err.println( "<" + v + ", " + vertex1[v] + ", " + vertex2[ v ]+ "> (" + k + ")" );
				final long s = solution[ vertex1[ v ] ] + solution[ vertex2[ v ] ];
				assert solution[ v ] == 0;
				final long value = ( k - s + 9 ) % 3;
				solution[ v ] = value == 0 ? 3 : value;

				assert ( k == ( solution[ v ] + solution[ vertex1[ v ] ] + solution[ vertex2[ v ] ] ) % 3 ) :
					"<" + v + "," + vertex1[ v ] + "," + vertex2[ v ] + ">: " + k + " != " + ( solution[ v ] + solution[ vertex1[ v ] ] + solution[ vertex2[ v ] ] ) % 3;
			}
			
			return true;
		}
	}
	
	/** Directs the edges of a 3-hypergraph.
	 * 
	 * @param d the degree array.
	 * @param vertex0 the first vertex of each edge.
	 * @param vertex1 the second vertex of each edge.
	 * @param vertex2 the third vertex of each edge.
	 * @param hinges the vector where hinges will be stored.
	 * @param length the number of hyperedges.
	 * @return true if direction was successful.
	 */
	public static boolean directHyperedges( int[][] edges, int[] d, int[] vertex0, int[] vertex1, int[] vertex2, int[] hinges, int length ) {
		final int numVertices = d.length;
		final int[] weight = new int[ length ];
		final boolean[] isHinge = new boolean[ numVertices ];
		final boolean[] isDone = new boolean[ length ];
		Arrays.fill( weight, 3 );

		if ( ASSERTS ) {
			final int[] testd = new int[ d.length ];
			for ( int i = 0; i < length; i++ ) {
				testd[ vertex0[ i ] ]++;
				testd[ vertex1[ i ] ]++;
				testd[ vertex2[ i ] ]++;
			}
			if ( ! Arrays.equals( d, testd ) ) throw new AssertionError( "Degree array not valid: " + Arrays.toString( testd ) + " != " + Arrays.toString( d ) );
		}
		
		// Priorities, multiplied by 6 (so they are all integers)
		final int[] priority = new int[ numVertices ];
		for ( int i = 0; i < numVertices; i++ ) priority[ i ] += 2 * d[ i ];
		
		final int[] heap = new int[ d.length ];
		int heapSize = 0;
		for ( int i = 0; i < d.length; i++ ) if ( d[ i ] > 0 ) heap[ heapSize++ ] = i;
		final IntHeapIndirectPriorityQueue vertices = new IntHeapIndirectPriorityQueue( priority, heap, heapSize );

		for ( int t = 0; t < length; t++ ) {		
			// Find hinge by looking at the node with minimum priority
			if ( vertices.isEmpty() ) return false;
			final int hinge = vertices.dequeue();
			int edge = -1;
			int minWeight = Integer.MAX_VALUE;
			for( int i = edges[ hinge ].length; i-- != 0; ) {
				final int e = edges[ hinge ][ i ];
				if ( ! isDone[ e ] && weight[ e ] < minWeight ) {
					edge = e;
					minWeight = weight[ e ];
				}
			}
			
			assert edge != -1;

			if ( ASSERTS ) {
				int minEdgeWeight = Integer.MAX_VALUE;
				int minEdge = -1;
				for( int i = 0; i < length; i++ ) if ( ! isDone[ i ] ) {
					if ( vertex0[ i ] == hinge || vertex1[ i ] == hinge || vertex2[ i ] == hinge ) {
						if ( weight[ i ] < minEdgeWeight ) {
							minEdgeWeight = weight[ i ];
							minEdge = i;
						}
					}
					
				}
				if ( weight[ edge ] != weight[ minEdge ] ) throw new AssertionError( "Min edge " + t + ": " + minEdge + " != " + edge );
			}
			
			
			//System.err.println( "Round " + ( t - offset ) + ": found hinge " + hinge + " for edge " + edge + " (" + vertex0[ edge ] + ", " + vertex1[ edge ] + ", " + vertex2[ edge ] + ")" );
			if ( priority[ hinge ] > 6 ) {
				//System.err.println( "Hinge " + hinge + " priority: " + priority[ hinge ] );
				return false;
			}
			hinges[ edge ] = hinge;
			isHinge[ hinge ] = true;
			isDone[ edge ] = true;
			
			for( int i = edges[ hinge ].length; i-- != 0; ) {
				final int e = edges[ hinge ][ i ];
				if ( isDone[ e ] ) continue;
				final int v0 = vertex0[ e ];
				final int v1 = vertex1[ e ];
				final int v2 = vertex2[ e ];
				assert hinge == v0 || hinge == v1 || hinge == v2 : hinge + " != " + v0 + ", " + v1 + ", " + v2;

				final int update = -6 / weight[ e ] + 6 / --weight[ e ];
				
				if ( ! isHinge[ v0 ] ) {
					priority[ v0 ] += update;
					assert priority[ v0 ] > 0;
					vertices.changed( v0 );
				}
				if ( ! isHinge[ v1 ] ) {
					priority[ v1 ] += update;
					assert priority[ v1 ] > 0;
					vertices.changed( v1 );
				}
				if ( ! isHinge[ v2 ] ) {
					priority[ v2 ] += update;
					assert priority[ v2 ] > 0;
					vertices.changed( v2 );
				}
			}

			final int v0 = vertex0[ edge ];
			final int v1 = vertex1[ edge ];
			final int v2 = vertex2[ edge ];

			assert hinge == v0 || hinge == v1 || hinge == v2 : hinge + " != " + v0 + ", " + v1 + ", " + v2;

			d[ v0 ]--;
			if ( ! isHinge[ v0 ] ) {
				assert priority[ v0 ] > 0 || d[ v0 ] == 0;
				if ( d[ v0 ] == 0 ) vertices.remove( v0 );
				else {
					priority[ v0 ] -= 6 / weight[ edge ];
					if ( d[ v0 ] == 1 ) priority[ v0 ] = 0;
					vertices.changed( v0 );
				}
			}

			d[ v1 ]--;
			if ( ! isHinge[ v1 ] ) {
				assert priority[ v1 ] > 0 || d[ v1 ] == 0;
				if ( d[ v1 ] == 0 ) vertices.remove( v1 );
				else {
					priority[ v1 ] -= 6 / weight[ edge ];
					if ( d[ v1 ] == 1 ) priority[ v1 ] = 0;
					vertices.changed( v1 );
				}
			}

			d[ v2 ]--;
			if ( ! isHinge[ v2 ] ) {
				assert priority[ v2 ] > 0 || d[ v2 ] == 0;
				if ( d[ v2 ] == 0 ) vertices.remove( v2 );
				else {
					priority[ v2 ] -= 6 / weight[ edge ];
					if ( d[ v2 ] == 1 ) priority[ v2 ] = 0;
					vertices.changed( v2 );
				}
			}

			if ( ASSERTS ) {
				final double[] pri = new double[ numVertices ];
				for( int i = 0; i < length; i++ ) if ( ! isDone[ i ] ) {
					int w = 0;
					if ( ! isHinge[ vertex0[ i ] ] ) w++;
					if ( ! isHinge[ vertex1[ i ] ] ) w++;
					if ( ! isHinge[ vertex2[ i ] ] ) w++;
					pri[ vertex0[ i ] ] += 6 / w;
					pri[ vertex1[ i ] ] += 6 / w;
					pri[ vertex2[ i ] ] += 6 / w;
					if ( weight[ i ] != w ) throw new AssertionError( "Edge " + i + ": " + w + " != " + weight[ i ] );
				}
				for( int i = 0; i < numVertices; i++ ) if ( ! isHinge[ i ] && d[ i ] > 1 && pri[ i ] != priority[ i ] ) throw new AssertionError( "Vertex " + i + ": " + pri[ i ] + " != " + priority[ i ] );

				/*double sum = 0;
				for( int i = 0; i < priority.length; i++ ) {
					if ( vertices.contains( i ) ) {
						if ( priority[ i ] != 0 ) sum += priority[ i ];
						else if ( !edges[ i ].isEmpty() ) sum += 1. / weight[ edges[ i ].first() ]; // Special case for force priority == 0
					}
				}
				assert Math.abs( sum - ( hinges.length - t - 1 ) ) < 1E-9 : sum + " != " + ( hinges.length - t - 1 );*/  


			}

			if ( DEBUG ) {
				System.err.println( "Weights: " + Arrays.toString( weight ) );
				System.err.println( "Priorities: " + Arrays.toString( priority ) );
				System.err.println( "Queue size: " + vertices.size() );
			}
			
		}
		return true;
	}
}

