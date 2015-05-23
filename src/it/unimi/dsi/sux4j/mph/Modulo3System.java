package it.unimi.dsi.sux4j.mph;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2015 Sebastiano Vigna 
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
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongBigList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Modulo3System {
	private final static boolean DEBUG = false;
	
	/** A modulo-3 equation. */
	public static class Modulo3Equation {
		/** The vector representing the coefficients (two bits for each variable). */
		protected final LongArrayBitVector bitVector;
		/** The {@link LongArrayBitVector#bits() bv.bits()}, cached. */
		protected final long[] bits;
		/** A {@linkplain LongArrayBitVector#asLongBigList(int) 2-bit list view} of {@link #bitVector}, cached. */
		protected final LongBigList list;
		/** The constant term. */
		protected int c;
		/** The first variable. This field must be updated by {@link #updateFirstVar()} to be meaningful. */
		protected int firstVar;
		/** The first coefficient. This field must be updated by {@link #updateFirstVar()} to be meaningful. */
		protected int firstCoeff;
		/** The variables added to this equation. They might not be the variables in {@link #bitVector} if {@link #sub(Modulo3Equation, int)} has been invoked. */
		protected IntArrayList addedVars;
		/** Whether any variable appears on the left side of the equation. */
		private boolean isEmpty;

		/** Creates a new equation.
		 * 
		 * @param c the constant term.
		 * @param numVars the number of variables.
		 */
		public Modulo3Equation( final int c, final int numVars ){
			this.c = c;
			this.bitVector = LongArrayBitVector.ofLength( numVars * 2 );
			this.bits = bitVector.bits();
			this.list = bitVector.asLongBigList( 2 );
			this.firstVar = Integer.MAX_VALUE;
			this.addedVars = new IntArrayList();
			this.isEmpty = true;
		}
		
		protected Modulo3Equation( final Modulo3Equation equation ){
			this.c = equation.c;
			this.bitVector = equation.bitVector.copy();
			this.bits = this.bitVector.bits();
			this.list = this.bitVector.asLongBigList( 2 );
			this.firstVar = equation.firstVar;
			this.firstCoeff = equation.firstCoeff;
			this.addedVars = equation.addedVars;
			this.isEmpty = equation.isEmpty;
		}
		
		/** Adds a new variable with given coefficient.
		 * 
		 * @param variable a variable.
		 * @param coefficient its coefficient.
		 * @return this equation.
		 */
		public Modulo3Equation add( final int variable, final int coefficient ) {
			if ( list.set( variable, coefficient ) != 0 ) throw new IllegalArgumentException();
			addedVars.add( variable );
			if ( variable < firstVar ) {
				firstVar = variable;
				firstCoeff = coefficient;
			}
			isEmpty = false;
			return this;
		}
		
		/** Adds a new variable with coefficient equal to one.
		 * 
		 * @param variable a variable.
		 * @return this equation.
		 */
		public Modulo3Equation add( final int variable ) {
			return add( variable, 1 );
		}
		
		/** Returns an array containing the variables in increasing order.
		 * 
		 * <p>Mainly for debugging purposes.
		 * 
		 * @return an array containing the variables in increasing order.
		 * @see #coefficients()
		 */
		public int[] variables() {
			IntArrayList variables = new IntArrayList();
			for( int i = 0; i < list.size(); i++ ) if ( list.getLong( i ) != 0 ) variables.add( i );
			return variables.toIntArray();
		}

		/** Returns an array containing the coeffcient in variable increasing order.
		 * 
		 * <p>Mainly for debugging purposes.
		 * 
		 * @return an array containing the coeffcient in variable increasing order.
		 * @see #variables()
		 */
		public int[] coefficients() {
			IntArrayList coefficients = new IntArrayList();
			for( int i = 0, c; i < list.size(); i++ ) if ( ( c = (int)list.getLong( i ) ) != 0 ) coefficients.add( c );
			return coefficients.toIntArray();
		}
		
		/** Eliminates the given variable from this equation, using the provided equation, but subtracting it multiplied by a suitable constant.
		 * 
		 * @param var a variable.
		 * @param equation an equation in which {@code var} appears. 
		 * 
		 * @return this equation.
		 */
		public Modulo3Equation eliminate( final int var , final Modulo3Equation equation  ) {
			assert this.list.getLong( var ) != 0;
			assert equation.list.getLong( var ) != 0;
			final int mul = this.list.getLong( var ) == equation.list.getLong( var ) ? 1 : 2;
			sub( equation, mul );
			return this;
		}

		/** Subtract from this equation another equation multiplied by a provided constant.
		 * 
		 * @param equation the subtrahend.
		 * @param mul a multiplier that will be applied to the subtrahend.
		 */
		public void sub( final Modulo3Equation equation, final int mul ) {
			// Explicit multiplication tables to avoid modulo operators.
			if ( mul == 1 ) {
				c = ( c + 2 * equation.c ) % 3;
				subMod3( equation.bits );
			}
			else {
				c = ( c + equation.c ) % 3;
				addMod3( equation.bits );
			}
		}

		/** Adds two 64-bit words made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 * 
		 * @param x a 64-bit word made of modulo-3 2-bit fields.
		 * @param y a 64-bit word made of modulo-3 2-bit fields.
		 * @return the field-by-field mod 3 sum of {@code x} and {@code y}. 
		 */
		protected final static long addMod3( final long x, final long y ) {
	        // mask the high bit of each pair set iff the result of the
	        // sum in that position is >= 3
	        // check if x is 2 and y is nonzero ...
	        long mask = x & (y | (y << 1));
	        // ... or x is 1 and y is 2
	        mask |= (x << 1) & y;
	        // clear the low bits
	        mask &= 0x5555555555555555L << 1;
	        // and turn 2s into 3s
	        mask |= mask >> 1;
	        return (x + y) - mask;
	    }

		/** Subtracts two 64-bit words made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 * 
		 * @param x a 64-bit word made of modulo-3 2-bit fields.
		 * @param y a 64-bit word made of modulo-3 2-bit fields.
		 * @return the field-by-field mod 3 difference of {@code x} and {@code y}. 
		 */
		protected final static long subMod3( final long x, long y ) {
	        // Change of sign
	        y = ( y & 0x5555555555555555L ) << 1 | ( y & 0xAAAAAAAAAAAAAAAAL ) >>> 1;
	        // mask the high bit of each pair set iff the result of the
	        // sum in that position is >= 3
	        // check if x is 2 and y is nonzero ...
			long mask = x & ( y | (y << 1) );
	        // ... or x is 1 and y is 2
	        mask |= (x << 1) & y;
	        // clear the low bits
	        mask &= 0x5555555555555555L << 1;
	        // and turn 2s into 3s
	        mask |= mask >> 1;
	        return (x + y) - mask;
	    }

		/** Adds to the left side of this equation a bit vectors made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 * 
		 * @param y a bit vector made of modulo-3 2-bit fields.
		 */
		private final void addMod3( final long[] y ) {
			final long[] x = this.bits;
			long isNotEmpty = 0;
			for( int i = x.length; i-- != 0; ) 
				isNotEmpty |= ( x[ i ] = addMod3( x[ i ], y[ i ] ) );
			isEmpty = isNotEmpty == 0;
		}
		
		/** Subtracts from the left side of this equation a bit vectors made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 * 
		 * @param y a bit vector made of modulo-3 2-bit fields.
		 */
		private final void subMod3( final long[] y ) {
			final long[] x = this.bits;
			long isNotEmpty = 0;
			for( int i = x.length; i-- != 0; ) 
				isNotEmpty |= ( x[ i ] = subMod3( x[ i ], y[ i ] ) ); 
			isEmpty = isNotEmpty == 0;
		}
		
		/** Updates the information contained in {@link #firstVar} and {@link #firstCoeff}.
		 * 
		 * <p>This method does not check whether {@link #firstVar} is actually updated.
		 */
		public void updateFirstVar() {
			if ( isEmpty ) firstVar = Integer.MAX_VALUE;
			else {
				int i = -1;
				while( bits[ ++i ] == 0 );
				final int lsb = Long.numberOfTrailingZeros( bits[ i ] ) / 2;
				firstVar = lsb + 32 * i;
				firstCoeff = (int)( bits[ i ] >> lsb * 2 & 3 );
			}
		}

		public boolean isUnsolvable() {
			return isEmpty && c != 0;
		}

		public boolean isIdentity() {
			return isEmpty && c == 0;
		}
		
		@Override
		public boolean equals( Object o ) {
			if ( ! ( o instanceof Modulo3Equation ) ) return false;
			final Modulo3Equation equation = (Modulo3Equation)o;
			return c == equation.c && bitVector.equals( equation.bitVector );
		}

		/** Writes in the provided array a normalized (all coefficients turned into ones) version of the {@linkplain #bits bit vector representing the equation}.
		 * 
		 * @param result an array where the result will be stored; must be long at least as {@link #bits}.
		 */
		public void normalized( final long[] result ) {
			final long[] bits = this.bits;
			// Drop coefficients
			for( int i = bits.length; i-- != 0; ) result[ i ] = ( bits[ i ] & 0x5555555555555555L ) | ( bits[ i ] & 0xAAAAAAAAAAAAAAAAL ) >>> 1;
		}

		/** Returns the modulo-3 scalar product of the two provided bit vectors.
		 * 
		 * @param x a bit vector represented as an array of longs.
		 * @param y a bit vector represented as an array of longs.
		 * @return the modulo-3 scalar product of {@code x} and {code y}.
		 */
		public static int scalarProduct( final long[] x, final long[] y ) {
			int sum = 0;

			for( int i = y.length; i-- != 0; ) {
				final long high = x[ i ] & 0xAAAAAAAAAAAAAAAAL;
				final long low = x[ i ] & 0x5555555555555555L;
				final long highShift = high >>> 1; // Make every 10 into a 11 and zero everything else
				final long t = ( y[ i ] ^ ( high | highShift ) ) & ( x[ i ] | highShift | low << 1 ); // Exchange ones with twos, and make 00 into 11

				sum += Long.bitCount( t & 0xAAAAAAAAAAAAAAAAL ) * 2 + Long.bitCount( t & 0x5555555555555555L );
			}
			return sum;
		}

		public String toString() {
			StringBuilder b = new StringBuilder();
			boolean someNonZero = false;
			for( int i = 0; i < list.size(); i++ ) {
				if ( list.getLong( i ) != 0 ) {
					if ( someNonZero ) b.append( " + " );
					someNonZero = true;
					b.append( list.getLong( i ) == 1 ? "x" : "2x" ).append( '_' ).append( i );
				}
	 		}
			if ( ! someNonZero ) b.append( '0' );
			return b.append( " = " ).append( c ).toString();
		}
		
		public Modulo3Equation copy() {
			return new Modulo3Equation( this );
		}
	}

	/** The number of variables. */
	private final int numVars;
	/** The equations. */
	private final ArrayList<Modulo3Equation> equations;
	/** The number of heavy variables after a call to {@link #structuredGaussianElimination(LongArrayBitVector)}. */
	private int numHeavy;

	public Modulo3System( final int numVar ) {
		equations = new ArrayList<Modulo3Equation>();
		this.numVars = numVar;
	}

	protected Modulo3System( final int numVar , ArrayList<Modulo3Equation> system  ) {
		this.equations = system;
		this.numVars = numVar;
	}
	
	public Modulo3System copy() {
		ArrayList<Modulo3Equation> list = new ArrayList<Modulo3System.Modulo3Equation>( equations.size() );
		for( Modulo3Equation equation: equations ) list.add( equation.copy() );
		return new Modulo3System( numVars, list );
	}

	/** Adds a new equation to the system.
	 * 
	 * @param equation an equation with the same number of variables of the system.
	 */
	public void add( Modulo3Equation equation ) {
		if ( equation.list.size() != numVars ) throw new IllegalArgumentException( "The number of variables in the equation (" + equation.list.size() + ") does not match the number of variables of the system (" + numVars + ")" );
		equations.add( equation );
	}

	public int size() {
		return equations.size();
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for ( int i = 0; i < equations.size(); i++ ) b.append( equations.get( i ) ).append( '\n' );
		return b.toString();
	}


	public boolean check( final int solution[] ) {
		assert solution.length == numVars;
		final LongArrayBitVector solutions = LongArrayBitVector.ofLength( numVars * 2 );
		final LongBigList list = solutions.asLongBigList( 2 );
		for( int i = solution.length; i-- != 0; ) list.set( i, solution[ i ] );
		return check( solutions );
	}


	public boolean check( final LongArrayBitVector solution ) {
		assert solution.length() == numVars * 2;
		final long[] solutionBits = solution.bits();

		for( Modulo3Equation equation: equations ) 
			if ( equation.c != Modulo3Equation.scalarProduct( equation.bits,  solutionBits ) % 3 ) return false;
		return true;
	}


	private boolean echelonForm() {
		main: for ( int i = 0; i < equations.size() - 1; i++ ) {
			assert equations.get( i ).firstVar != Integer.MAX_VALUE;
			
			for ( int j = i + 1; j < equations.size(); j++ ) {
				// Note that because of exchanges we cannot extract the first assignment
				Modulo3Equation eqJ = equations.get( j );
				Modulo3Equation eqI = equations.get( i );

				assert eqI.firstVar != Integer.MAX_VALUE;
				assert eqJ.firstVar != Integer.MAX_VALUE;

				final int firstVar = eqI.firstVar;

				if( firstVar == eqJ.firstVar ) {
					eqI.eliminate( firstVar, eqJ );
					if ( eqI.isUnsolvable() ) return false;
					if ( eqI.isIdentity() ) continue main;
					eqI.updateFirstVar();
				}

				if ( eqI.firstVar > eqJ.firstVar ) Collections.swap( equations, i, j );
			}
		}
		return true;
	}


	public boolean gaussianElimination( final int[] solution ) {
		assert solution.length == numVars;
		LongArrayBitVector solutions = LongArrayBitVector.ofLength( numVars * 2 );
		if ( ! gaussianElimination( solutions ) ) return false;
		final LongBigList list = solutions.asLongBigList( 2 );
		for( int i = solution.length; i-- != 0; ) solution[ i ] = (int)list.getLong( i );
		return true;
	}


	public boolean gaussianElimination( final LongArrayBitVector solution ) {
		assert solution.length() == numVars * 2;
		for ( Modulo3Equation equation: equations ) equation.updateFirstVar();

		if ( ! echelonForm() ) return false;

		final long[] solutionBits = solution.bits();
		final LongBigList solutionList = solution.asLongBigList( 2 );

		for ( int i = equations.size(); i-- != 0; ) {
			final Modulo3Equation equation = equations.get( i );
			if ( equation.isIdentity() ) continue;

			assert solutionList.getLong( equation.firstVar ) == 0 : equation.firstVar;

			int sum = ( equation.c - Modulo3Equation.scalarProduct( equation.bits, solutionBits ) ) % 3;
			if ( sum < 0 ) sum += 3;

			solutionList.set( equation.firstVar,  sum == 0 ? 0 : equation.firstCoeff == sum ? 1 : 2 );
		}

		return true;
	}


	public boolean structuredGaussianElimination( final int[] solution ) {
		assert solution.length == numVars;
		LongArrayBitVector solutions = LongArrayBitVector.ofLength( numVars * 2 );
		if ( ! structuredGaussianElimination( solutions ) ) return false;
		final LongBigList list = solutions.asLongBigList( 2 );
		for( int i = solution.length; i-- != 0; ) solution[ i ] = (int)list.getLong( i );
		return true;
	}

	public boolean structuredGaussianElimination( final LongArrayBitVector solution ) {
		assert solution.length() == numVars * 2;

		if ( DEBUG ) {
			System.err.println();
			System.err.println( "====================" );
			System.err.println();
			System.err.println( this );
		}
			
		final int numEquations = equations.size();
		if ( numEquations == 0 ) return true;
		/* The weight of each variable, that is, the number of equations still 
		 * in the queue in which the variable appears. We use zero to 
		 * denote pivots of solved equations. */
		final int weight[] = new int[ numVars ];
		// For each variable, the equations containing it.
		final IntArrayList[] varEquation = new IntArrayList[ numVars ];
		// The priority of each equation still to be examined (the number of light variables).
		final int[] priority = new int[ numEquations ];

		for( int i = 0; i < numEquations; i++ ) {
			final Modulo3Equation equation = equations.get( i );
			// Initially, all variables are light.
			final int[] elements = equation.addedVars.elements();
			for( int j = equation.addedVars.size(); j-- != 0; ) {
				final int var = elements[ j ];
				if ( varEquation[ var ] == null ) varEquation[ var ] = new IntArrayList( 8 );
				weight[ var ]++;
				varEquation[ var ].add( i );
				priority[ i ]++;
			}
		}

		// All variables in a stack returning heavier variables first.
		final IntArrayList variables;
		{
			final int[] t = Util.identity( numVars );
			final int[] u = new int[ t.length ];
			final int[] count = new int[ numEquations + 1 ]; // CountSort
			for( int i = t.length; i-- != 0; ) count[ weight[ t[ i ] ] ]++;
			for( int i = 1; i < count.length; i++ ) count[ i ] += count[ i - 1 ];
			for( int i = t.length; i-- != 0; ) u[ --count[ weight[ t[ i ] ] ] ] = t[ i ];
			variables = IntArrayList.wrap( u );
		}
		
		// The equations that are neither dense, nor solved, and have weight <= 1.
		final IntArrayList equationList = new IntArrayList();
		for( int i = priority.length; i-- != 0; ) if ( priority[ i ] <= 1 ) equationList.add( i );
		// The equations that are part of the dense system (entirely made of heavy variables).
		ArrayList<Modulo3Equation> dense = new ArrayList<Modulo3Equation>();
		// The equations that define a light variable in term of heavy variables.
		ArrayList<Modulo3Equation> solved = new ArrayList<Modulo3Equation>();
		// The light variable corresponding to each solved equation.
		IntArrayList pivots = new IntArrayList();

		final long[] normalized = new long[ equations.get( 0 ).bits.length ];
		// A bit vector made of 2-bit blocks containing a 01 in correspondence of each light variable.
		final long[] lightNormalized = new long[ normalized.length ];
		Arrays.fill( lightNormalized, 0x5555555555555555L );

		numHeavy = 0;

		for( int remaining = equations.size(); remaining != 0; ) {
			if ( equationList.isEmpty() ) {
				// Make another variable heavy
				int var;
				do var = variables.popInt(); while( weight[ var ] == 0 );
				numHeavy++;
				lightNormalized[ var / 32 ] ^= 1L << ( var % 32 ) * 2;
				if ( DEBUG ) System.err.println( "Making variable " + var + " of weight " + ( - weight[ var ] ) + " heavy (" + remaining + " equations to go)" );
				final int[] elements = varEquation[ var ].elements();
				for( int i = varEquation[ var ].size(); i-- != 0; ) {
					final int equationIndex = elements[ i ];
					if ( --priority[ equationIndex ] == 1 ) equationList.push( equationIndex );
				}
			}
			else {
				remaining--;
				final int first = equationList.popInt(); // An equation of weight 0 or 1.
				final Modulo3Equation equation = equations.get( first );
				if ( DEBUG ) System.err.println( "Looking at equation " + first + " of priority " + priority[ first ] + " : " + equation );

				if ( priority[ first ] == 0 ) {
					if ( equation.isUnsolvable() ) return false;
					if ( equation.isIdentity() ) continue;
					/* This equation must be necessarily solved by standard Gaussian elimination. No updated
					 * is needed, as all its variables are heavy. */
					dense.add( equation );
				}
				else if ( priority[ first ] == 1 ) {
					/* This is solved (in terms of the heavy variables). Let's find the pivot, that is, 
					 * the only light variable. Note that we do not need to update varEquation[] of any variable, as they
					 * are all either heavy (the non-pivot), or appearing only in this equation (the pivot). */
					equation.normalized( normalized );
					int wordIndex = 0;
					while( ( normalized[ wordIndex ] & lightNormalized[ wordIndex ] ) == 0 ) wordIndex++;
					final int pivot = wordIndex * 32 + Long.numberOfTrailingZeros( normalized[ wordIndex ] & lightNormalized[ wordIndex ] ) / 2; 

					// Record the light variable and the equation for computing it later.
					if ( DEBUG ) System.err.println( "Adding to solved variables x_" + pivot + " by equation " + equation );
					pivots.add( pivot );
					solved.add( equation );
					// This forces to skip the pivot when looking for a new variable to be made heavy.
					weight[ pivot ] = 0;

					// Now we need to eliminate the variable from all other equations containing it.
					final int[] elements = varEquation[ pivot ].elements();
					for( int i = varEquation[ pivot ].size(); i-- != 0; ) {
						final int equationIndex = elements[ i ];
						if ( equationIndex == first ) continue;
						if ( --priority[ equationIndex ] == 1 ) equationList.add( equationIndex );
						final Modulo3Equation temp = equations.get( equationIndex );
						if ( DEBUG ) System.err.print( "Replacing equation (" + equationIndex + ") " + temp + " with " );
						temp.eliminate( pivot, equation );
						if ( DEBUG ) System.err.println( temp );
					}
				}
			}
		}


		if ( DEBUG ) {
			System.err.println( "Heavy variables: " + numHeavy + " (" + Util.format( numHeavy * 100 / numVars ) + "%)" );
			System.err.println( "Dense equations: " + dense );
			System.err.println( "Solved equations: " + solved );
			System.err.println( "Pivots: " + pivots );
		}

		Modulo3System denseSystem = new Modulo3System( numVars, dense );
		if ( ! denseSystem.gaussianElimination( solution ) ) return false;  // numVars >= denseSystem.numVars

		final long[] solutionBits = solution.bits();
		final LongBigList solutionList = solution.asLongBigList( 2 );

		if ( DEBUG ) System.err.println( "Solution (dense): " + solutionList );

		for ( int i = solved.size(); i-- != 0; ) {
			final Modulo3Equation equation = solved.get( i );
			final int pivot = pivots.getInt( i );
			assert solutionList.getLong( pivot ) == 0 : pivot;

			final int pivotCoefficient = (int)equation.list.getLong( pivot );

			int sum = ( equation.c - Modulo3Equation.scalarProduct( equation.bits, solutionBits ) ) % 3;
			if ( sum < 0 ) sum += 3;

			assert pivotCoefficient != -1;
			solutionList.set( pivot,  sum == 0 ? 0 : pivotCoefficient == sum ? 1 : 2 );
		}

		if ( DEBUG ) System.err.println( "Solution (all): " + solutionList );

		return true;
	}
}
