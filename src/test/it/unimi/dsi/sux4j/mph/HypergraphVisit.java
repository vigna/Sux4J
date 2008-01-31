package test.it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.booleans.BooleanArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.AbstractObject2IntFunction;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.Fast;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;

import java.util.Iterator;

import org.apache.log4j.Logger;

import cern.colt.GenericPermuting;
import cern.colt.GenericSorting;
import cern.colt.Swapper;
import cern.colt.function.IntComparator;

public class HypergraphVisit<T> {
	/** The mythical threshold (or better, a reasonable upper bound of): random 3-hypergraphs
	 * are acyclic with positive probability if the ratio hyperedges/vertices exceeds this constant. */
	private static final double GAMMA = 1.23;

	/** The internal state of a visit. */
	private final static Logger LOGGER = Fast.getLogger( HypergraphVisit.class );

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
	final int[] recStackI;
	/** The stack for k. */
	final int[] recStackK;

	public int numberOfVertices;
	private int n;


	public HypergraphVisit( final int n ) {
		this.n = n;
		numberOfVertices = (int)Math.ceil( GAMMA * n );
		edge = new int[ 3 ][ n ];
		last = new int[ numberOfVertices ];
		inc = new int[ numberOfVertices * 3 ];
		incOffset = new int[ numberOfVertices ];
		stack = new int[ n ];
		d = new int[ numberOfVertices ];
		removed = new boolean[ n ];
		recStackI = new int[ n ];
		recStackK = new int[ n ];
	}
		
	public boolean visit( final Iterable<? extends T> terms, Object2ObjectFunction<? extends T,int[]> hyperedge ) {
		// We cache all variables for faster access
		final int[][] edge = this.edge;
		final int[] last = this.last;
		final int[] inc = this.inc;
		final int[] incOffset = this.incOffset;
		final int[] stack = this.stack;
		final int[] d = this.d;
		BooleanArrays.fill( removed, false );
		
		int i, j, v = -1;
		int[] h;

		/* We build the hyperedge list, checking that we do not create a degenerate hyperedge. */
		i = 0;
		T bv = null;
		IntArrays.fill( d, 0 );

		for( Iterator<? extends T> w = terms.iterator(); w.hasNext(); ) {
			bv = w.next();
			h = hyperedge.get( bv );
			if ( h[ 0 ] == h[ 1 ] || h[ 1 ] == h[ 2 ] || h[ 2 ] == h[ 0 ] ) break;

			edge[ 0 ][ i ] = h[ 0 ];
			edge[ 1 ][ i ] = h[ 1 ];
			edge[ 2 ][ i ] = h[ 2 ];

			i++;
		}

		if ( i < n ) {
			LOGGER.info( "Hypergraph generation interrupted by degenerate hyperedge " + i + " (string: \"" + bv + "\")." );
			return false;
		} 

		/* We compute the degree of each vertex. */
		for( j = 0; j < 3; j++ ) {
			i = n;
			while( i-- != 0 ) d[ edge[ j ][ i ] ]++; 
		}

		LOGGER.info( "Checking for duplicate hyperedges..." );

		/* Now we quicksort hyperedges lexicographically, keeping into last their permutation. */
		i = n;
		while( i-- != 0 ) last[ i ] = i;

		GenericSorting.quickSort( 0, n, new IntComparator() {
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

		i = n - 1;
		while( i-- != 0 ) if ( edge[ 0 ][ i + 1 ] == edge[ 0 ][ i ] && edge[ 1 ][ i + 1 ] == edge[ 1 ][ i ] && edge[ 2 ][ i + 1 ] == edge[ 2 ][ i ]) break;

		if ( i != -1 ) {
			LOGGER.info( "Found double hyperedge for terms " + last[ i + 1 ] + " and " + last[ i ] + "." );
			return false;
		}

		/* We now invert last and permute all hyperedges back into their place. Note that
		 * we use last and incOffset to speed up the process. */
		i = n;
		while( i-- != 0 ) stack[ last[ i ] ] = i;

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

		for( i = 1; i < numberOfVertices; i++ ) incOffset[ i ] = incOffset[ i - 1 ] + d[ i - 1 ];

		/* We fill the vector. */
		for( i = 0; i < n; i++ ) 
			for( j = 0; j < 3; j++ ) {
				v = edge[ j ][ i ];
				inc[ incOffset[ v ] + last[ v ]++ ] = i;
			}

		/* We visit the hypergraph. */
		BooleanArrays.fill( removed, false );

		top = 0;
		for( i = 0; i < numberOfVertices; i++ ) if ( d[ i ] == 1 ) visit( i );

		if ( top == n ) LOGGER.info( "Visit completed." );
		else LOGGER.info( "Visit failed: stripped " + top + " hyperedges out of " + n + "." );

		if ( top != n ) return false;

			
		LOGGER.info( "Assigning values..." );

		/* We assign values. */
		/** Whether a specific node has already been used as perfect hash value for an item. */
		final BitVector used = LongArrayBitVector.getInstance( numberOfVertices );
		used.length( numberOfVertices );
		int value;

		/*while( top > 0 ) {
			k = stack[ --top ];

			s = 0;

			for ( j = 0; j < 3; j++ ) {
				if ( ! used.getBoolean( edge[ j ][ k ] ) ) v = j;
				else s += bits.getLong( edge[ j ][ k ] );
				used.set( edge[ j ][ k ] );
			}

			value = ( v - s + 9 ) % 3;
			bits.set( edge[ v ][ k ], value == 0 ? 3 : value );
		}*/

		LOGGER.info( "Completed." );
		
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

