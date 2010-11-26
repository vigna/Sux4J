package it.unimi.dsi.sux4j.scratch;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2010 Sebastiano Vigna 
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
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.longs.AbstractLongList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.sux4j.mph.AbstractHashFunction;
import it.unimi.dsi.sux4j.mph.HypergraphSorter;
import it.unimi.dsi.sux4j.mph.MWHCFunction;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;

/** A read-only function stored using the Majewski-Wormald-Havas-Czech {@linkplain HypergraphSorter 3-hypergraph technique}.
 * 
 * <p>After generating a random 3-hypergraph with suitably sorted edges,
 * we assign to each vertex a value in such a way that for each 3-hyperedge the 
 * xor of the three values associated to its vertices is the required value for the corresponding
 * element of the function domain.
 * 
 * @author Sebastiano Vigna
 * @since 0.2
 */

public class TwoSizesMWHCFunction<T> extends AbstractHashFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Util.getLogger( TwoSizesMWHCFunction.class );
		
	/** The number of elements. */
	final protected int n;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	final protected TransformationStrategy<? super T> transform;
	/** The first function. The special output value {@link #escape} denotes that {@link #secondFunction} (if not <code>null</code>) 
	 * should be queried instead. */
	final protected MWHCFunction<BitVector> firstFunction;
	/** The second function. If not <code>null</code>, all queries for which {@link #firstFunction} returns
	 * {@link #escape} will be rerouted here. */
	final protected MWHCFunction<BitVector> secondFunction;	
	/** The escape value returned by {@link #firstFunction} to suggest that {@link #secondFunction} should be queried instead. */
	protected final long escape;

	/** Creates a new function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @throws IOException 
	 */

	public TwoSizesMWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongList values ) throws IOException {
		this.transform = transform;

		final long[] count = new long[ Long.SIZE ];
		
		n = values.size();
		if ( n == 0 ) {
			escape = 0;
			firstFunction = secondFunction = null;
			return;
		}

		// Compute distribution of bit sizes.
		int w = 0;
		for( int i = 0; i < n; i++ ) {
			final long value = values.getLong( i );
			count[ Fast.length( value + 1 ) ]++;
			final int size = Fast.length( value );
			if ( size > w ) w = size;
		}

		// Analyze data and choose a threshold
		long post = n, cost, bestCost = Long.MAX_VALUE;
		int best = -1;
		
		for( int i = 0; i < count.length; i++ ) {
			/* This cost function is dependent on the implementation of MWHCFunction.
			 * 
			 * Note that for i = 0 we are actually computing the cost of a single function (the first one).
			 * For i = 1 no change is possible as count[ 0 ] == 0. */
			cost = (long)Math.min( HypergraphSorter.GAMMA * n * 1.126 + n * (long)i, HypergraphSorter.GAMMA * n * i ) +
					(long)Math.min( HypergraphSorter.GAMMA * post * 1.126 + post * w, HypergraphSorter.GAMMA * post * w );

			if ( cost < bestCost ) { 
				best = i;
				bestCost = cost;
			}
			
			post -= count[ i ];
		}
		
		final int threshold = best - 1;
		
		LOGGER.debug( "Threshold: " + threshold );
		escape = threshold == -1 ? Long.MAX_VALUE : ( 1 << threshold ) - 1; // Immaterial if threshold == -1.
		
		firstFunction = new MWHCFunction<BitVector>( TransformationStrategies.wrap( elements, transform ), TransformationStrategies.identity(), new AbstractLongList() {
			public long getLong( int index ) {
				long value = values.getLong( index );
				if ( value < escape || threshold == 0 ) return value; 
				return escape;
			}

			public int size() {
				return n;
			}
			
		}, threshold == -1 ? w : threshold );

		LOGGER.debug( "Actual bit cost per element of first function: " + (double)firstFunction.numBits() / n );

		if ( threshold == -1 ) secondFunction = null;
		else {
			LongList secondValues = new LongArrayList();
			ArrayList<LongArrayBitVector> secondKeys = new ArrayList<LongArrayBitVector>();
			Iterator<? extends T> iterator = elements.iterator();

			for( int i = 0; i < n; i++ ) {
				T key = iterator.next();
				final long value = values.getLong( i );
				if ( value >= escape ) {
					secondKeys.add( LongArrayBitVector.copy( transform.toBitVector( key ) ) );
					secondValues.add( value );
				}
			}

			secondFunction = new MWHCFunction<BitVector>( secondKeys, TransformationStrategies.identity(), secondValues, w );
			if ( secondFunction != null ) LOGGER.debug( "Actual bit cost per element of second function: " + (double)secondFunction.numBits() / n );
		}
		
		LOGGER.info( "Completed." );
		LOGGER.info( "Actual bit cost per element: " + (double)numBits() / n );
	}


	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final BitVector bitVector = transform.toBitVector( (T)o );
		long firstValue = firstFunction.getLong( bitVector );
		if ( firstValue != escape || secondFunction == null ) return firstValue;
		return secondFunction.getLong( bitVector );
	}
	
	public long getLongByTriple( final long[] triple ) {
		long firstValue = firstFunction.getLong( triple );
		if ( firstValue != escape || secondFunction == null ) return firstValue;
		return secondFunction.getLong( triple );
	}
	
	/** Returns the number of elements in the function domain.
	 *
	 * @return the number of the elements in the function domain.
	 */
	public int size() {
		return n;
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		return firstFunction.numBits() + ( secondFunction == null ? 0 : secondFunction.numBits() ) + transform.numBits();
	}

	/** Creates a new function by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param function the function to be copied.
	 */
	protected TwoSizesMWHCFunction( final TwoSizesMWHCFunction<T> function ) {
		this.n = function.n;
		this.firstFunction = function.firstFunction;
		this.secondFunction = function.secondFunction;
		this.transform = function.transform.copy();
		this.escape = function.escape;
	}
}
