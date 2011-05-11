package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2011 Sebastiano Vigna 
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
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;
import it.unimi.dsi.fastutil.longs.AbstractLongComparator;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.io.ChunkedHashStore;

import java.io.IOException;
import java.io.Serializable;
import java.util.Random;

import org.apache.commons.collections.Predicate;
import org.apache.log4j.Logger;


/** A read-only function stored using two {@linkplain MWHCFunction Majewski-Wormald-Havas-Czech functions}&mdash;one for
 * frequent values, and one for infrequent values.
 * 
 * <p>The constructor of this class performs a pre-scan of the values to be assigned. If possible, it finds the best possible
 * <var>r</var> such that the 2<sup><var>r</var></sup> &minus; 1 most frequent values can be stored in a {@link MWHCFunction}
 * and suitably remapped when read. The function uses 2<sup><var>r</var></sup> &minus; 1 as an escape symbol for all other
 * values, which are stored in a separate function.
 * 
 * @author Sebastiano Vigna
 * @since 1.0.2
 */

public class TwoStepsMWHCFunction<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 3L;
    private static final Logger LOGGER = Util.getLogger( TwoStepsMWHCFunction.class );
		
    private final static boolean ASSERTS = false;
    
	/** The number of elements. */
	final protected long n;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	final protected TransformationStrategy<? super T> transform;
	/** The first function, or <code>null</code>. The special output value {@link #escape} denotes that {@link #secondFunction} 
	 * should be queried instead. */
	final protected MWHCFunction<T> firstFunction;
	/** The second function. All queries for which {@link #firstFunction} returns
	 * {@link #escape} (or simply all queries, if {@link #firstFunction} is <code>null</code>) will be rerouted here. */
	final protected MWHCFunction<T> secondFunction;	
	/** A mapping from values of the first function to actual values, provided that there is a {@linkplain #firstFunction first function}. */
	final protected long[] remap;
	/** The escape value returned by {@link #firstFunction} to suggest that {@link #secondFunction} should be queried instead, provided that there is a {@linkplain #firstFunction first function}. */
	protected final int escape;
	private long seed;
	/** The mean of the rank distribution. */
	public final double rankMean;
	/** The width of the output of this function, in bits. */
	public final int width;

	/** Creates a new two-step function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 */

	public TwoStepsMWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongBigList values ) throws IOException {
		this( elements, transform, values, null );
	}
		
	/** Creates a new two-step function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 */

	public TwoStepsMWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongBigList values, ChunkedHashStore<T> chunkedHashStore ) throws IOException {
		this.transform = transform;
		final ProgressLogger pl = new ProgressLogger( LOGGER );
		pl.displayFreeMemory = true;
		final Random random = new Random();
		pl.itemsName = "keys";

		if ( chunkedHashStore == null ) {
			chunkedHashStore = new ChunkedHashStore<T>( transform, pl );
			chunkedHashStore.reset( random.nextLong() );
			chunkedHashStore.addAll( elements.iterator() );
		}
		n = chunkedHashStore.size();
		defRetValue = -1; // For the very few cases in which we can decide
		
		if ( n == 0 ) {
			rankMean = escape = width = 0;
			firstFunction = secondFunction = null;
			remap = null;
			return;
		}

		// Compute distribution of values and maximum number of bits.
		int w = 0, size;
		long v;
		final Long2LongOpenHashMap counts = new Long2LongOpenHashMap();
		counts.defaultReturnValue( -1 );
		for( LongIterator i = values.iterator(); i.hasNext(); ) {
			v = i.nextLong();
			counts.put( v, counts.get( v ) + 1 );
			size = Fast.length( v );
			if ( size > w ) w = size;
		}
		
		this.width = w;
		final int m = counts.size();
		
		LOGGER.debug( "Generating two-steps MWHC function with " + w + " output bits..." );

		// Sort keys by reverse frequency
		final long[] keys = counts.keySet().toLongArray( new long[ m ] );
		LongArrays.quickSort( keys, 0, keys.length, new AbstractLongComparator() {
			public int compare( final long a, final long b ) {
				return Long.signum( counts.get( b ) - counts.get( a ) );
			}
		});

		long mean = 0;
		for( int i = 0; i < keys.length; i++ ) mean += i * counts.get( keys[ i ] );
		rankMean = (double)mean / n;
		
		// Analyze data and choose a threshold
		long post = n, pre = 0, bestCost = Long.MAX_VALUE;
		int pos = 0, best = -1;
		
		// Examine every possible choice for r. Note that r = 0 implies one function, so we do not need to test the case r == w.
		for( int r = 0; r < w && pos < m; r++ ) {

			/* This cost function is dependent on the implementation of MWHCFunction. 
			 * Note that for r = 0 we are actually computing the cost of a single function (the first one). */
			final long cost = (long)Math.min( HypergraphSorter.GAMMA * n * 1.126 + n * r, HypergraphSorter.GAMMA * n * r ) +
					(long)Math.min( HypergraphSorter.GAMMA * post * 1.126 + post * w, HypergraphSorter.GAMMA * post * w ) +
					pos * Long.SIZE;

			if ( cost < bestCost ) { 
				best = r;
				bestCost = cost;
			}

			/* We add to pre and subtract from post the counts of elements from position (1<<r)-1 to position (1<<r+1)-1. */
			for( int j = 0; j < ( 1 << r ) && pos < m; j++ ) {
				final long c = counts.get( keys[ pos++ ] ); 
				pre += c;
				post -= c;
			}	
		}

		if ( ASSERTS ) assert pos == m;
		
		counts.clear();
		counts.trim();
		
		// We must keep the remap array small.
		if ( best >= Integer.SIZE ) best = Integer.SIZE - 1;
		
		LOGGER.debug( "Best threshold: " + best );
		escape = ( 1 << best ) - 1;
		System.arraycopy( keys, 0, remap = new long[ escape ], 0, remap.length );
		final Long2LongOpenHashMap map = new Long2LongOpenHashMap();
		map.defaultReturnValue( -1 );
		for( int i = 0; i < escape; i++ ) map.put( remap[ i ], i );

		if ( best != 0 ) {
			firstFunction = new MWHCFunction<T>( elements, transform, chunkedHashStore, new AbstractLongBigList() {
				public long getLong( long index ) {
					long value = map.get( values.getLong( index ) );
					if ( value != -1 ) return value;
					return escape;
				}

				public long size64() {
					return n;
				}

			}, best );

			LOGGER.debug( "Actual bit cost per element of first function: " + (double)firstFunction.numBits() / n );
		}
		else firstFunction = null;

		chunkedHashStore.filter( new Predicate() {
			public boolean evaluate( Object triple ) {
				return firstFunction == null || firstFunction.getLongByTriple( (long[])triple ) == escape;
			}
		});
		
		secondFunction = new MWHCFunction<T>( elements, transform, chunkedHashStore, values, w );
		
		this.seed = chunkedHashStore.seed();
		
		LOGGER.debug( "Actual bit cost per element of second function: " + (double)secondFunction.numBits() / n );

		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );
		LOGGER.info( "Completed." );
	}


	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final long[] triple = new long[ 3 ];
		Hashes.jenkins( transform.toBitVector( (T)o ), seed, triple );
		if ( firstFunction != null ) {
			final int firstValue = (int)firstFunction.getLongByTriple( triple );
			if ( firstValue == -1 ) return defRetValue;
			if ( firstValue != escape ) return remap[ firstValue ];
		}
		return secondFunction.getLongByTriple( triple );
	}
	
	public long getLongByTriple( final long[] triple ) {
		if ( firstFunction != null ) {
			final int firstValue = (int)firstFunction.getLongByTriple( triple );
			if ( firstValue == -1 ) return defRetValue;
			if ( firstValue != escape ) return remap[ firstValue ];
		}
		return secondFunction.getLongByTriple( triple );
	}
	
	public long size64() {
		return n;
	}
	
	public int size() {
		return n > Integer.MAX_VALUE ? -1 : (int)n;
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return ( firstFunction != null ? firstFunction.numBits() : 0 ) + secondFunction.numBits() + transform.numBits() + remap.length * Long.SIZE;
	}

	/** Creates a new function by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param function the function to be copied.
	 */
	protected TwoStepsMWHCFunction( final TwoStepsMWHCFunction<T> function ) {
		this.n = function.n;
		this.width = function.width;
		this.rankMean = function.rankMean;
		this.remap = function.remap;
		this.firstFunction = function.firstFunction;
		this.secondFunction = function.secondFunction;
		this.transform = function.transform.copy();
		this.escape = function.escape;
	}
}
