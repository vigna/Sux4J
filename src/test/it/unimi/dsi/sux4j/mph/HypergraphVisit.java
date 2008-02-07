package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.Fast;
import it.unimi.dsi.sux4j.bits.BitVector.TransformationStrategy;
import it.unimi.dsi.sux4j.mph.Hashes;

import java.util.Iterator;

import org.apache.log4j.Logger;

import cern.colt.GenericPermuting;
import cern.colt.GenericSorting;
import cern.colt.Swapper;
import cern.colt.function.IntComparator;

/** A class implementing the 3-hypergraph edge sorting procedure that is necessary for the
 * Majewski-Wormald-Havas-Czech technique.
 * 
 * <p>Bohdan S. Majewski, Nicholas C. Wormald, George Havas, and Zbigniew J. Czech. have
 * described in &ldquo;A family of perfect hashing methods&rdquo;, 
 * <i>Comput. J.</i>, 39(6):547&minus;554, 1996,
 * a 3-hypergraph based technique to store functions
 * (actually, the paper uses the technique just to store a permutation of the key set, but
 * it is clear it can be used to store any function). More generally, the procedure sorts
 * the hyperedges of a random 3-hypergraph so that for each edge at least one vertex never
 * appeared before (this happens with positive probability if the number of vertices is at
 * least {@link #GAMMA} times the number of 3-hyperedges).
 * 
 * <p>Instances of this class contain the data necessary to generate the random hypergraph
 * and apply the sorting procedure. At construction time, you provide just the desired number
 * of edges; then, each call to {@link #visit(Iterator, TransformationStrategy, long) visit()}
 * will generate a new 3-hypergraph using a 64-bit seed, an iterator returning the key set,
 * and a corresponding {@link TransformationStrategy}. If the method returns true, the visit was
 * successful and in the public field {@link #stack} you can retrieve the <em>opposite</em>
 * of the desired order (so enumerating edges starting from the last in {@link #stack} you
 * are guaranteed to find each time a vertex that never appeared before). The public fields
 * {@link #edge}, {@link #numEdges} and {@link #numVertices} expose the structure of the generated
 * 3-hypergraph.
 * 
 * @author Sebastiano Vigna
 */


public class HypergraphVisit<T> {
	/** The mythical threshold (or better, a reasonable upper bound of): random 3-hypergraphs
	 * are acyclic with positive probability if the ratio hyperedges/vertices exceeds this constant. */
	public static final double GAMMA = 1.23;

	/** The internal state of a visit. */
	private final static Logger LOGGER = Fast.getLogger( HypergraphVisit.class );

	/** The number of vertices in the hypergraph ( &lceil; {@link #GAMMA} * {@link #numEdges} &rceil; + 1 ). */
	final public int numVertices;
	/** The number of edges in the hypergraph. */
	final public int numEdges;
	/** An 3&times;n array recording the triple of vertices involved in each hyperedge. It is *reversed*
		   w.r.t. what you would expect to reduce object creation. */
	final public int[][] edge;
	/** Whether a hyperedge has been already removed. */
	final private boolean[] removed;
	/** For each vertex of the intermediate hypergraph, the vector of incident hyperedges. */
	final private int[] inc;
	/** The next position to fill in the respective incidence vector. 
	 * Used also to store the hyperedge permutation and speed up permute(). */
	final private int[] last; 
	/** For each vertex of the intermediate hypergraph, the offset into 
	 * the vector of incident hyperedges. Used also to speed up permute(). */
	final private int[] incOffset;
	/** The hyperedge stack. Used also to invert the hyperedge permutation. */
	final public int[] stack;
	/** The degree of each vertex of the intermediate hypergraph. */
	final private int[] d;
	/** Initial top of the hyperedge stack. */
	int top;
	/** The stack for i. */
	final private int[] recStackI;
	/** The stack for k. */
	final private int[] recStackK;


	public HypergraphVisit( final int numEdges ) {
		this.numEdges = numEdges;
		numVertices = (int)Math.ceil( GAMMA * numEdges ) + 1;
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

	/** Turns a triple of long hash values into a 3-hyperedge, guaranteeing not to return a degenerate edge.
	 * 
	 * @param h a triple of hashes
	 * @param e an array to store the resulting hyperedge.
	 * @param numVertices the number of vertices in the underlying hypergraph.
	 */
	public static void hashesToEdge( final long h[], final int e[], final int numVertices ) {
		e[ 0 ] = (int)( ( h[ 0 ] & 0x7FFFFFFFFFFFFFFFL ) % numVertices );
		e[ 1 ] = (int)( e[ 0 ] + ( h[ 1 ] & 0x7FFFFFFFFFFFFFFFL ) % ( numVertices - 1 ) + 1 );
		e[ 2 ] = (int)( e[ 0 ] + ( h[ 2 ] & 0x7FFFFFFFFFFFFFFFL ) % ( numVertices - 2 ) + 1 );
		if ( e[ 2 ] >= e[ 1 ] ) e[ 2 ]++;
		e[ 1 ] %= numVertices;
		e[ 2 ] %= numVertices;
	}
	
	public boolean visit( final Iterator<? extends T> iterator, final TransformationStrategy<? super T> transform, final long seed ) {
		// We cache all variables for faster access
		final int[][] edge = this.edge;
		final int[] last = this.last;
		final int[] inc = this.inc;
		final int[] incOffset = this.incOffset;
		final int[] stack = this.stack;
		final int[] d = this.d;
		BooleanArrays.fill( removed, false );
		
		final long[] h = new long[ 3 ];
		final int[] e = new int[ 3 ];

		/* We build the hyperedge list. */
		int k = 0;
		BitVector bv = null;
		IntArrays.fill( d, 0 );
		
		while( iterator.hasNext() ) {
			bv = transform.toBitVector( iterator.next() );
			Hashes.jenkins( bv, seed, h );
			hashesToEdge( h, e, numVertices );
			edge[ 0 ][ k ] = e[ 0 ];
			edge[ 1 ][ k ] = e[ 1 ];
			edge[ 2 ][ k ] = e[ 2 ];

			k++;
		}

		/* We compute the degree of each vertex. */
		for( int j = 0; j < 3; j++ ) 
			for( int i = numEdges; i-- != 0; ) 
				d[ edge[ j ][ i ] ]++; 


		LOGGER.info( "Checking for duplicate hyperedges..." );

		/* Now we quicksort hyperedges lexicographically, keeping into last their permutation. */
		for( int i = numEdges; i-- != 0; ) last[ i ] = i;

		GenericSorting.quickSort( 0, numEdges, new IntComparator() {
			public int compare( final int x, final int y ) {
				int r;
				if ( ( r = edge[ 0 ][ x ] - edge[ 0 ][ y ] ) != 0 ) return r;
				if ( ( r = edge[ 1 ][ x ] - edge[ 1 ][ y ] ) != 0 ) return r;
				return edge[ 2 ][ x ] - edge[ 2 ][ y ];
			}
		},
		new Swapper() {
			public void swap( final int x, final int y ) {
				int e0 = edge[ 0 ][ x ], e1 = edge[ 1 ][ x ], e2 = edge[ 2 ][ x ], p = last[ x ];
				edge[ 0 ][ x ] = edge[ 0 ][ y ];
				edge[ 1 ][ x ] = edge[ 1 ][ y ];
				edge[ 2 ][ x ] = edge[ 2 ][ y ];
				edge[ 0 ][ y ] = e0;
				edge[ 1 ][ y ] = e1;
				edge[ 2 ][ y ] = e2;
				last[ x ] = last[ y ];
				last[ y ] = p;
			}
		}
		);

		//for( int i = 0; i < numEdges; i++ ){ for( int j = 0; j < 3; j++ ) System.err.print( edge[ j ][i ]+ " "); System.err.print( tmp[last[i ]] );
		//Hashes.jenkins( tmp[last[i ] ], init, h ); System.err.print( Arrays.toString(h));
		//System.err.println(); }
		
		for( int i = numEdges - 1; i-- != 0; ) 
			if ( edge[ 0 ][ i + 1 ] == edge[ 0 ][ i ] && edge[ 1 ][ i + 1 ] == edge[ 1 ][ i ] && edge[ 2 ][ i + 1 ] == edge[ 2 ][ i ] ) {
				LOGGER.info( "Found double hyperedge for elements " + last[ i ] + " and " + last[ i + 1 ] + "." );
				return false;
			}

		/* We now invert last and permute all hyperedges back into their place. Note that
		 * we use last and incOffset to speed up the process. */
		for( int i = numEdges; i-- != 0; ) stack[ last[ i ] ] = i;

		GenericPermuting.permute( stack, new Swapper() {
			public void swap( final int x, final int y ) {
				int e0 = edge[ 0 ][ x ], e1 = edge[ 1 ][ x ], e2 = edge[ 2 ][ x ];
				edge[ 0 ][ x ] = edge[ 0 ][ y ];
				edge[ 1 ][ x ] = edge[ 1 ][ y ];
				edge[ 2 ][ x ] = edge[ 2 ][ y ];
				edge[ 0 ][ y ] = e0;
				edge[ 1 ][ y ] = e1;
				edge[ 2 ][ y ] = e2;
			}
		}, last, incOffset
		);

		LOGGER.info( "Visiting hypergraph..." );

		/* We set up the offset of each vertex in the incidence
				   vector. This is necessary to avoid creating m incidence vectors at
				   each round. */
		IntArrays.fill( last, 0 );
		incOffset[ 0 ] = 0;

		for( int i = 1; i < numVertices; i++ ) incOffset[ i ] = incOffset[ i - 1 ] + d[ i - 1 ];

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

		if ( top == numEdges ) LOGGER.info( "Visit completed." );
		else {
			LOGGER.info( "Visit failed: stripped " + top + " hyperedges out of " + numEdges + "." );
			return false;
		}

		return true;
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
					if ( !removed[ k = inc[ incOffset[ x ] + i ] ] ) break; // The only hyperedge incident on x in the current configuration.

				// TODO: k could be wrong if the graph is regular and cyclic.
				stack[ top++ ] = k;

				/* We update the degrees and the incidence lists. */
				removed[ k ] = true;
				for ( i = 0; i < 3; i++ ) d[ edge[ i ][ k ] ]--;
			}

			/* We follow recursively the other vertices of the hyperedge, if they have degree one in the current configuration. */
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

