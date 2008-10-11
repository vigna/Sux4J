package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
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
import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;

import java.util.Iterator;

import org.apache.log4j.Logger;

import cern.colt.Sorting;
import cern.colt.function.IntComparator;

/** A class implementing the 3-hypergraph edge sorting procedure that is necessary for the
 * Majewski-Wormald-Havas-Czech technique.
 * 
 * <p>Bohdan S. Majewski, Nicholas C. Wormald, George Havas, and Zbigniew J. Czech have
 * described in &ldquo;A family of perfect hashing methods&rdquo;, 
 * <i>Comput. J.</i>, 39(6):547&minus;554, 1996,
 * a 3-hypergraph based technique to store functions
 * (actually, the paper uses the technique just to store a permutation of the key set, but
 * it is clear it can be used to store any function). More generally, the procedure 
 * first generates a random 3-hypergraph whose edges correspond to elements of the function domain.
 * The, it sorts the edges of the random 3-hypergraph so that for each edge at least one vertex, the <em>hinge</em>, never
 * appeared before in the sorted edge list (this happens with positive probability if the number of vertices is at
 * least {@linkplain #GAMMA &gamma;} times the number of edges).
 * 
 * <p>Instances of this class contain the data necessary to generate the random hypergraph
 * and apply the sorting procedure. At construction time, you provide just the desired number
 * of edges; then, each call to {@link #generateAndSort(Iterator, TransformationStrategy, long) generateAndSort()}
 * will generate a new 3-hypergraph using a 64-bit seed, an iterator returning the key set,
 * and a corresponding {@link TransformationStrategy}. If the method returns true, the sorting was
 * successful and in the public field {@link #stack} you can retrieve the <em>opposite</em>
 * of the desired order (so enumerating edges starting from the last in {@link #stack} you
 * are guaranteed to find each time a vertex that never appeared before). The public fields
 * {@link #edge}, {@link #numEdges} and {@link #numVertices} expose the structure of the generated
 * 3-hypergraph.
 * 
 * <p>To guarantee the same results when reading a Majewski-Wormald-Havas-Czech-like structure,
 * the method {@link #bitVectorToEdge(BitVector, long, int, int[]) bitVectorToEdge()} can be used to retrieve, starting from
 * a bit vector, the corresponding edge. While having a function returning the edge starting
 * from a key would be more object-oriented and avoid hidden dependencies, it would also require
 * storing the transformation provided at construction time, which would make this class non-thread-safe.
 * Just be careful to transform the keys into bit vectors using
 * the same {@link TransformationStrategy} used to generate the random 3-hypergraph.
 * 
 * <h2>Support for preprocessed keys</h2>
 * 
 * <p>This class provides two special access points for classes that have pre-digested their keys. The methods
 * {@link #generateAndSort(Iterator, long)} and {@link #bitVectorToEdge(long[], long, int, int[])} use
 * fixed-length 192-bit keys under the form of triples of longs. The intended usage is that of 
 * turning the keys into such a triple using {@linkplain Hashes#jenkins(BitVector) Jenkins's hash} and
 * then operating directly on the hash codes. This is particularly useful in bucketed constructions, where
 * the keys are replaced by their 192-bit hashes in the first place. Note that the hashes are actually
 * rehashed using {@link Hashes#jenkins(long[], long, long[])}&mdash;this is necessary to vary the associated edges whenever
 * the generated 3-hypergraph is not acyclic.
 * 
 * <h2>Implementation details</h2>
 * 
 * <p>We use {@linkplain Hashes#jenkins(BitVector, long, long[]) Jenkin's hash} in its 64-bit incarnation: beside
 * providing an excellent hash function, it actually computes <em>three</em> 64-bit hash values,
 * which is exactly what we need.
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
 * class, this class will log at {@link org.apache.log4j.Level#INFO INFO} level
 * what's happening.
 *
 * <P>Note that if during the generation process the log warns more than once about duplicate edges, you should
 * suspect that there are duplicates in the string list, as duplicate edges are <em>extremely</em> unlikely.
 *
 * @author Sebastiano Vigna
 */


public class HypergraphSorter<T> {

	public static enum Result { OK, DUPLICATE, CYCLIC }; 
	
	/** The mythical threshold (or better, a reasonable upper bound of): random 3-hypergraphs
	 * are acyclic with positive probability if the ratio edges/vertices exceeds this constant. */
	public static final double GAMMA = 1.23;

	/** The internal state of a visit. */
	private final static Logger LOGGER = Util.getLogger( HypergraphSorter.class );

	/** The number of vertices in the hypergraph ( &lceil; {@link #GAMMA} * {@link #numEdges} &rceil; + 1 ). */
	final public int numVertices;
	/** The number of edges in the hypergraph. */
	final public int numEdges;
	/** An 3&times;n array recording the triple of vertices involved in each edge. It is *reversed*
		   w.r.t. what you would expect to reduce object creation. */
	final public int[][] edge;
	/** Whether a edge has been already removed. */
	final private boolean[] removed;
	/** For each vertex of the intermediate hypergraph, the vector of incident edges. */
	final private int[] inc;
	/** The next position to fill in the respective incidence vector. 
	 * Used also to store the edge permutation and speed up permute(). */
	final private int[] last; 
	/** For each vertex of the intermediate hypergraph, the offset into 
	 * the vector of incident edges. Used also to speed up permute(). */
	final private int[] incOffset;
	/** The edge stack. Used also to invert the edge permutation. */
	final public int[] stack;
	/** The degree of each vertex of the intermediate hypergraph. */
	final private int[] d;
	/** Initial top of the edge stack. */
	int top;
	/** The stack for i. */
	final private int[] recStackI;
	/** The stack for k. */
	final private int[] recStackK;

	/** Creates a hypergraph sorter for a given number of edges.
	 * 
	 * @param numEdges the number of edges of this hypergraph sorter.
	 */
	public HypergraphSorter( final int numEdges ) {
		this.numEdges = numEdges;
		numVertices = numEdges == 0 ? 0 : (int)Math.ceil( GAMMA * numEdges ) + 1;
		edge = new int[ 3 ][ numEdges ];
		last = new int[ numVertices ];
		inc = new int[ numVertices * 3 ];
		incOffset = new int[ numVertices ];
		stack = new int[ numEdges ];
		d = new int[ numVertices ];
		removed = new boolean[ numEdges ];
		recStackI = new int[ numEdges ];
		recStackK = new int[ numEdges ];
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
	 */
	public static void bitVectorToEdge( final BitVector bv, final long seed, final int numVertices, final int e[] ) {
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] h = new long[ 3 ];
		Hashes.jenkins( bv, seed, h );
		e[ 0 ] = (int)( ( h[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
		e[ 1 ] = (int)( e[ 0 ] + ( h[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % ( numVertices - 1 ) + 1 );
		e[ 2 ] = (int)( e[ 0 ] + ( h[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % ( numVertices - 2 ) + 1 );
		if ( e[ 2 ] >= e[ 1 ] ) e[ 2 ]++;
		e[ 1 ] %= numVertices;
		e[ 2 ] %= numVertices;
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
	 */
	public static void bitVectorToEdge( final long[] triple, final long seed, final int numVertices, final int e[] ) {
		if ( numVertices == 0 ) {
			e[ 0 ] = e[ 1 ] = e[ 2 ] = -1;
			return;
		}
		final long[] hash = new long[ 3 ];
		Hashes.jenkins( triple, seed, hash );
		e[ 0 ] = (int)( ( hash[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
		e[ 1 ] = (int)( e[ 0 ] + ( hash[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % ( numVertices - 1 ) + 1 );
		e[ 2 ] = (int)( e[ 0 ] + ( hash[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % ( numVertices - 2 ) + 1 );
		if ( e[ 2 ] >= e[ 1 ] ) e[ 2 ]++;
		e[ 1 ] %= numVertices;
		e[ 2 ] %= numVertices;
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
		final int[] edge0 = edge[ 0 ], edge1 = edge[ 1 ], edge2 = edge[ 2 ];
		final int[] e = new int[ 3 ];

		/* We build the edge list. */
		for( int k = 0; k < numEdges; k++ ) {
			bitVectorToEdge( transform.toBitVector( iterator.next() ), seed, numVertices, e );
			edge0[ k ] = e[ 0 ];
			edge1[ k ] = e[ 1 ];
			edge2[ k ] = e[ 2 ];
		}

		if ( iterator.hasNext() ) throw new IllegalStateException( "This " + HypergraphSorter.class.getSimpleName() + " has " + numEdges + " edges, but the provided iterator returns more" );

		return generateAndSort();
	}

	/** Generates a random 3-hypergraph and tries to sort its edges.
	 * 
	 * @param iterator an iterator returning {@link #numEdges} triples of longs.
	 * @param seed a 64-bit random seed.
	 * @return true if the sorting procedure succeeded.
	 */
	public Result generateAndSort( final Iterator<long[]> iterator, final long seed ) {
		// We cache all variables for faster access
		final int[] edge0 = edge[ 0 ], edge1 = edge[ 1 ], edge2 = edge[ 2 ];
		final int[] e = new int[ 3 ];

		/* We build the edge list. */
		for( int k = 0; k < numEdges; k++ ) {
			bitVectorToEdge( iterator.next(), seed, numVertices, e );
			edge0[ k ] = e[ 0 ];
			edge1[ k ] = e[ 1 ];
			edge2[ k ] = e[ 2 ];
		}

		if ( iterator.hasNext() ) throw new IllegalStateException( "This " + HypergraphSorter.class.getSimpleName() + " has " + numEdges + " edges, but the provided iterator returns more" );

		return generateAndSort();
	}

	/** Generates a random 3-hypergraph and tries to sort its edges.
	 * 
	 * @return true if the sorting procedure succeeded.
	 */
	private Result generateAndSort() {
		// We cache all variables for faster access
		final int[][] edge = this.edge;
		final int[] edge0 = edge[ 0 ], edge1 = edge[ 1 ], edge2 = edge[ 2 ];
		final int[] last = this.last;
		final int[] inc = this.inc;
		final int[] incOffset = this.incOffset;
		final int[] d = this.d;
		BooleanArrays.fill( removed, false );
		
		
		/* We compute the degree of each vertex. */
		IntArrays.fill( d, 0 );
		
		for( int j = 0; j < 3; j++ ) 
			for( int i = numEdges; i-- != 0; ) 
				d[ edge[ j ][ i ] ]++; 


		if ( numEdges > 1 ) {
			LOGGER.debug( "Checking for duplicate edges..." );

			/* Now we quicksort edges lexicographically, keeping into last their permutation. */
			for( int i = numEdges; i-- != 0; ) last[ i ] = i;

			Sorting.quickSort( last, 0, numEdges, new IntComparator() {
				public int compare( final int x, final int y ) {
					int r;
					if ( ( r = edge0[ x ] - edge0[ y ] ) != 0 ) return r;
					if ( ( r = edge1[ x ] - edge1[ y ] ) != 0 ) return r;
					return edge2[ x ] - edge2[ y ];
				}
			} );

			for( int i = numEdges - 1, prev = last[ numEdges - 1 ], next; i-- != 0; ) {
				next = last[ i ];
				if ( edge0[ next ] == edge0[ prev ] && edge1[ next ] == edge1[ prev ] && edge2[ next ] == edge2[ prev ] ) {
					LOGGER.info( "Found double edge for elements " + next + " and " + prev + "." );
					return Result.DUPLICATE;
				}
				prev = next;
			}
		}

		LOGGER.debug( "Visiting hypergraph..." );

		/* We set up the offset of each vertex in the incidence
				   vector. This is necessary to avoid creating m incidence vectors at
				   each round. */
		IntArrays.fill( last, 0 );

		if ( numVertices > 0 ) {
			incOffset[ 0 ] = 0;
			for( int i = 1; i < numVertices; i++ ) incOffset[ i ] = incOffset[ i - 1 ] + d[ i - 1 ];
		}
		
		/* We fill the vector. */
		for( int i = 0; i < numEdges; i++ ) 
			for( int j = 0; j < 3; j++ ) {
				final int v = edge[ j ][ i ];
				inc[ incOffset[ v ] + last[ v ]++ ] = i;
			}

		/* We visit the hypergraph. */
		BooleanArrays.fill( removed, false );

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
		// We cache all variables for faster access
		final int[] recStackI = this.recStackI;
		final int[] recStackK = this.recStackK;
		final int[][] edge = this.edge;
		final int[] last = this.last;
		final int[] inc = this.inc;
		final int[] incOffset = this.incOffset;
		final int[] stack = this.stack;
		final int[] d = this.d;
		final boolean[] removed = this.removed;			

		int i, k = -1;
		boolean inside;

		// Stack initialization
		int recTop = 0; // Initial top of the recursion stack.
		inside = false;

		while ( true ) {
			if ( ! inside ) {
				for ( i = 0; i < last[ x ]; i++ ) 
					if ( !removed[ k = inc[ incOffset[ x ] + i ] ] ) break; // The only edge incident on x in the current configuration.

				// TODO: k could be wrong if the graph is regular and cyclic.
				stack[ top++ ] = k;

				/* We update the degrees and the incidence lists. */
				removed[ k ] = true;
				for ( i = 0; i < 3; i++ ) d[ edge[ i ][ k ] ]--;
			}

			/* We follow recursively the other vertices of the edge, if they have degree one in the current configuration. */
			for( i = 0; i < 3; i++ ) 
				if ( edge[ i ][ k ] != x && d[ edge[ i ][ k ] ] == 1 ) {
					recStackI[ recTop ] = i + 1;
					recStackK[ recTop ] = k;
					recTop++;
					x = edge[ i ][ k ];
					inside = false;
					break;
				}
			if ( i < 3 ) continue;

			if ( --recTop < 0 ) return;
			i = recStackI[ recTop ];
			k = recStackK[ recTop ];
			inside = true;
		}
	}
}

