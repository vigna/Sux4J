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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongBigList;

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
	
	/** The number of vertices in the hypergraph (&lceil; {@link #GAMMA} * {@link #numEdges} &rceil; + 1, rounded up to the nearest multiple of 3). */
	public final int numVertices;
	/** The number of edges in the hypergraph. */
	public final int numEdges;
	/** For each vertex, the XOR of the indices of incident 3-hyperedges. */
	public final int[] edge;
	/** The hinge stack. At the end of a peeling phase, it contains the hinges in reverse order. */
	public final int[] stack;
	/** The degree of each vertex of the intermediate 3-hypergraph. */
	private final int[] d;
	/** Whether we ever called {@link #generateAndSort(Iterator, long)} or {@link #generateAndSort(Iterator, TransformationStrategy, long)}. */
	private boolean neverUsed;
	/** Initial top of the edge stack. */
	private int top;
	/** The stack used for peeling the graph. */
	private final IntArrayList visitStack;
	/** Three parallel arrays containing each one of the three vertices of a hyperedge. */ 
	private final int[][] edge2Vertex;
	/** For each edge, whether it has been peeled. */
	private final boolean[] peeled;
	/** The vector of solutions. */
	public long[] solution;
	/** The number of generated unsolvable systems. */
	public int unsolvable;
	/** The number of generated undirectable graphs. */
	public int undirectable;

	/** Creates a hypergraph solver for a given number of vertices and edges.
	 * 
	 * @param numVertices the number of vertices of this hypergraph solver.
	 * @param numEdges the number of edges of this hypergraph solver.
	 */
	public HypergraphSolver( final int numVertices, final int numEdges ) {
		this.numVertices = numVertices;
		this.numEdges = numEdges;
		peeled = new boolean[ numEdges ];
		edge = new int[ numVertices ];
		edge2Vertex = new int[ 3 ][ numEdges ];
		stack = new int[ numEdges ];
		d = new int[ numVertices ];
		visitStack = new IntArrayList( INITIAL_QUEUE_SIZE );
		neverUsed = true;
	}
	
	private final void cleanUpIfNecessary() {
		if ( ! neverUsed ) { 
			Arrays.fill( d, 0 );
			Arrays.fill( edge, 0 );
			Arrays.fill( peeled, false );
			undirectable = unsolvable = 0;
		}
		neverUsed = false;
	}

	private final void xorEdge( final int e, final int hinge ) {
		if ( hinge != edge2Vertex[ 0 ][ e ] ) edge[ edge2Vertex[ 0 ][ e ] ] ^= e;
		if ( hinge != edge2Vertex[ 1 ][ e ] ) edge[ edge2Vertex[ 1 ][ e ] ] ^= e;
		if ( hinge != edge2Vertex[ 2 ][ e ] ) edge[ edge2Vertex[ 2 ][ e ] ] ^= e;
	}

	private final void xorEdge( final int e ) {
		edge[ edge2Vertex[ 0 ][ e ] ] ^= e;
		edge[ edge2Vertex[ 1 ][ e ] ] ^= e;
		edge[ edge2Vertex[ 2 ][ e ] ] ^= e;
	}


	/** Turns a triple of longs into a 3-hyperedge.
	 * 
	 * <p>If there are no vertices the vector <code>e</code> will be filled with -1.
	 * 
	 * @param triple a triple of intermediate hashes.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param e an array to store the resulting edge.
	 * @see #bitVectorToEdge(BitVector, long, int, int[])
	 */
	public static void tripleToEdge( final long[] triple, final long seed, final int numVertices, final int e[] ) {
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] hash = new long[ 3 ];
		Hashes.jenkins( triple, seed, hash );
		e[ 0 ] = (int)( ( hash[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
		e[ 1 ] = (int)( ( hash[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
		e[ 2 ] = (int)( ( hash[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
	}

	/** Turns a bit vector into a 3-hyperedge.
	 * 
	 * <p>If there are no vertices the vector <code>e</code> will be filled with -1.
	 * 
	 * @param bv a bit vector.
	 * @param seed the seed for the hash function.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 * @param e an array to store the resulting edge.
	 */
	public static void bitVectorToEdge( final BitVector bv, final long seed, final int numVertices, final int e[] ) {
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] hash = new long[ 3 ];
		Hashes.jenkins( bv, seed, hash );
		e[ 0 ] = (int)( ( hash[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
		e[ 1 ] = (int)( ( hash[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
		e[ 2 ] = (int)( ( hash[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
	}
	
	private String edge2String( final int e ) {
		return "<" + edge2Vertex[ 0 ][ e ] + "," + edge2Vertex[ 1 ][ e ] + "," + edge2Vertex[ 2 ][ e ] + ">";
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
	 * <p>The known part is provided by {@code valueList}. If it is {@code null}, the known
	 * part will be obtained by directing the hyperedges.
	 * 
	 * @param iterable an iterable returning {@link #numEdges} triples of longs.
	 * @param seed a 64-bit random seed.
	 * @param valueList a value list for indirect resolution, or {@code null}.
	 * @return true if the sorting procedure succeeded.
	 */
	public boolean generateAndSolve( final Iterable<long[]> iterable, final long seed, final LongBigList valueList ) {
		// We cache all variables for faster access
		final int[] d = this.d;
		final int[] edge2Vertex0 = edge2Vertex[ 0 ], edge2Vertex1 = edge2Vertex[ 1 ], edge2Vertex2 = edge2Vertex[ 2 ];

		cleanUpIfNecessary();
		
		/* We build the edge list and compute the degree of each vertex. */
		final int[] e = new int[ 3 ];
		final Iterator<long[]> iterator = iterable.iterator();
		for( int i = 0; i < numEdges; i++ ) {
			tripleToEdge( iterator.next(), seed, numVertices, e );
			if ( DEBUG ) System.err.println("Edge <" + e[ 0 ] + "," + e[ 1 ] + "," + e[ 2 ] + ">" );
			d[ edge2Vertex0[ i ] = e[ 0 ] ]++;
			d[ edge2Vertex1[ i ] = e[ 1 ] ]++;
			d[ edge2Vertex2[ i ] = e[ 2 ] ]++;
			xorEdge( i );
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
		LOGGER.debug( "Peeling hypergraph (" + numVertices + " vertices, " + numEdges + " edges)..." );

		top = 0;
		for( int i = 0; i < numVertices; i++ ) if ( d[ i ] == 1 ) peel( i );

		if ( top == numEdges ) {
			LOGGER.debug( "Peeling completed." );
			return true;
		}
		
		LOGGER.debug( "Peeled " + top + " edges out of " + numEdges + "." );
		return false;
	}

	private void peel( final int x ) {
		// System.err.println( "Visiting " + x + "..." );
		final int[] edge = this.edge;
		final int[] stack = this.stack;
		final int[] d = this.d;
		final IntArrayList visitStack = this.visitStack;
		
		// Stack initialization
		int v;
		visitStack.clear();
		visitStack.push( x );

		final int[] edge2Vertex0 = edge2Vertex[ 0 ];
		final int[] edge2Vertex1 = edge2Vertex[ 1 ];
		final int[] edge2Vertex2 = edge2Vertex[ 2 ];
		
		while ( ! visitStack.isEmpty() ) {
			v = visitStack.popInt();
			if ( d[ v ] == 1 ) {
				stack[ top++ ] = v;
				// System.err.println( "Stripping <" + v + ", " + vertex1[ v ] + ", " + vertex2[ v ] + ">" );
				final int e = edge[ v ];
				peeled[ e ] = true;
				xorEdge( e, v );
				if ( --d[ edge2Vertex0[ e ] ] == 1 ) visitStack.add( edge2Vertex0[ e ] );
				if ( --d[ edge2Vertex1[ e ] ] == 1 ) visitStack.add( edge2Vertex1[ e ] );
				if ( --d[ edge2Vertex2[ e ] ] == 1 ) visitStack.add( edge2Vertex2[ e ] );
			}
		}
	}
	
	private boolean solve( final LongBigList valueList ) {
		final boolean peelingCompleted = sort();
		solution = new long[ numVertices ];
		final long[] solution = this.solution;
		final int[] edge2Vertex0 = edge2Vertex[ 0 ], edge2Vertex1 = edge2Vertex[ 1 ], edge2Vertex2 = edge2Vertex[ 2 ], edge = this.edge, d = this.d;

		if ( valueList != null ) {

			if ( ! peelingCompleted ) {

				final int[][] vertex2Edge = new int[ d.length ][];
				for( int i = vertex2Edge.length; i-- != 0; ) vertex2Edge[ i ] = new int[ d[ i ] ];
				final int[] p = new int[ d.length ];
				final long[] c = new long[ d.length - top ];
				Arrays.fill( d, 0 );
				
				for ( int i = 0, j = 0; i < numEdges; i++ ) {
					if ( ! peeled[ i ] ) {
						final int v0 = edge2Vertex0[ i ];
						vertex2Edge[ v0 ][ p[ v0 ]++ ] = j;
						final int v1 = edge2Vertex1[ i ];
						vertex2Edge[ v1 ][ p[ v1 ]++ ] = j;
						final int v2 = edge2Vertex2[ i ];
						vertex2Edge[ v2 ][ p[ v2 ]++ ] = j;

						c[ j++ ] = valueList.getLong( i );
					}
				}
				
				if ( ! Modulo2System.structuredGaussianElimination( vertex2Edge, c, Util.identity( numVertices ), solution ) ) {
					unsolvable++;
					LOGGER.debug( "System is unsolvable" );
					return false;
				}
			}

			while( top > 0 ) {
				final int x = stack[ --top ];
				final int e = edge[ x ];
				solution[ x ] = valueList.getLong( e );
				if ( x != edge2Vertex0[ e ] ) solution[ x ] ^= solution[ edge2Vertex0[ e ] ];
				if ( x != edge2Vertex1[ e ] ) solution[ x ] ^= solution[ edge2Vertex1[ e ] ];
				if ( x != edge2Vertex2[ e ] ) solution[ x ] ^= solution[ edge2Vertex2[ e ] ];
				
				assert valueList.getLong( e ) == ( solution[ edge2Vertex0[ e ] ] ^ solution[ edge2Vertex1[ e ] ] ^ solution[ edge2Vertex2[ e ] ] ) :
					edge2String( e ) + ": " + valueList.getLong( e ) + " != " + ( solution[ edge2Vertex0[ e ] ] ^ solution[ edge2Vertex1[ e ] ] ^ solution[ edge2Vertex2[ e ] ] );
			}
				
			return true;
		}
		else {
			if ( ! peelingCompleted ) {
				final int nonPeeled = numEdges - top;
				final int[] remEdge2Vertex0 = new int[ nonPeeled ], remEdge2Vertex1 = new int[ nonPeeled ], remEdge2Vertex2 = new int[ nonPeeled ]; 
				final int[][] edges = new int[ d.length ][];
				final boolean[] peeled = this.peeled;
				for( int i = edges.length; i-- != 0; ) edges[ i ] = new int[ d[ i ] ];
				Arrays.fill( d, 0 );

				// Compress the edge representation eliminating peeled edges.
				for ( int i = 0, j = 0; i < numEdges; i++ ) {
					if ( ! peeled[ i ] ) {
						final int v0 = edge2Vertex0[ i ];
						remEdge2Vertex0[ j ] = v0;
						edges[ v0 ][ d[ v0 ]++ ] = j;
						final int v1 = edge2Vertex1[ i ];
						remEdge2Vertex1[ j ] = v1;
						edges[ v1 ][ d[ v1 ]++ ] = j;
						final int v2 = edge2Vertex2[ i ];
						remEdge2Vertex2[ j ] = v2;
						edges[ v2 ][ d[ v2 ]++ ] = j;

						j++;
					}
				}

				final int[] hinge = new int[ nonPeeled ];
				if ( ! directHyperedges( edges, d, remEdge2Vertex0, remEdge2Vertex1, remEdge2Vertex2, hinge, nonPeeled ) ) {
					LOGGER.debug( "Hypergraph cannot be directed" );
					undirectable++;
					return false;
				}
				
				if ( DEBUG ) for( int i = 0; i < nonPeeled; i++ ) System.err.println( "<" + remEdge2Vertex0[ i ] + "," + remEdge2Vertex1[ i ] + "," + remEdge2Vertex2[ i ] + "> => " + hinge[ i ] );

				final int[] c = new int[ nonPeeled ];
				for ( int i = 0; i < nonPeeled; i++ ) {
					final int h = hinge[ i ];
					c[ i ] = h == remEdge2Vertex0[ i ] ? 0 : h == remEdge2Vertex1[ i ] ? 1 : 2;
					assert c[ i ] != 0 || remEdge2Vertex0[ i ] == hinge[ i ];
					assert c[ i ] != 1 || remEdge2Vertex1[ i ] == hinge[ i ];
					assert c[ i ] != 2 || remEdge2Vertex2[ i ] == hinge[ i ];
				}
				
				if ( ! Modulo3System.structuredGaussianElimination( edges, c, hinge, solution ) ) {
					unsolvable++;
					LOGGER.debug( "System is unsolvable" );
					return false;
				}


				if ( ASSERTS ) {
					for( int i = 0; i < nonPeeled; i++ ) {
						final int k = hinge[ i ] == remEdge2Vertex0[ i ] ? 0 : hinge[ i ] == remEdge2Vertex1[ i ] ? 1 : 2;
						assert k != 0 || remEdge2Vertex0[ i ] == hinge[ i ];
						assert k != 1 || remEdge2Vertex1[ i ] == hinge[ i ];
						assert k != 2 || remEdge2Vertex2[ i ] == hinge[ i ];
						assert ( k == ( solution[ remEdge2Vertex0[ i ] ] + solution[ remEdge2Vertex1[ i ] ] + solution[ remEdge2Vertex2[ i ] ] ) % 3 ) :
							"<" + remEdge2Vertex0[ i ] + "," + remEdge2Vertex1[ i ] + "," + remEdge2Vertex2[ i ] + ">: " + k + " != " + ( solution[ remEdge2Vertex0[ i ] ] + solution[ remEdge2Vertex1[ i ] ] + solution[ remEdge2Vertex2[ i ] ] ) % 3;
					}
				}
				
				for( int i = 0; i < nonPeeled; i++ ) if ( solution[ hinge[ i ] ] == 0 ) solution[ hinge[ i ] ] = 3;
			}

			if ( DEBUG ) System.err.println( "Peeled (" + top + "): " );

			// Complete with peeled hyperedges
			while ( top > 0 ) {
				final int v = stack[ --top ];
				final int e = edge[ v ];
				int k;
				if ( v == edge2Vertex0[ e ] ) k = 0;
				else if ( v == edge2Vertex1[ e ] ) k = 1;
				else k = 2; 

				assert v == edge2Vertex0[ e ] || v == edge2Vertex1[ e ] || v == edge2Vertex2[ e ] : v + " not in " + edge2String( e );
				
				if ( DEBUG ) System.err.println( edge2String( e ) + " => @" + k );  

				assert solution[ v ] == 0;

				long s = 0;
				if ( v != edge2Vertex0[ e ] ) s += solution[ edge2Vertex0[ e ] ];
				if ( v != edge2Vertex1[ e ] ) s += solution[ edge2Vertex1[ e ] ];
				if ( v != edge2Vertex2[ e ] ) s += solution[ edge2Vertex2[ e ] ];

				s = ( k - s + 9 ) % 3;
				solution[ v ] = s == 0 ? 3 : s;

				assert k == ( solution[ edge2Vertex0[ e ] ] + solution[ edge2Vertex1[ e ] ] + solution[ edge2Vertex2[ e ] ] ) % 3 :
					edge2String( e ) + ": " + k + " != " + ( solution[ edge2Vertex0[ e ] ] + solution[ edge2Vertex1[ e ] ] + solution[ edge2Vertex2[ e ] ] ) % 3 ;
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

