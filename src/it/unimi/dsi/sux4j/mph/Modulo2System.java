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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Modulo2System {
	private final static boolean DEBUG = false;
	
	/** A modulo-2 equation. */
	public static class Modulo2Equation {
		/** The vector representing the coefficients (one bit for each variable). */
		protected final LongArrayBitVector bitVector;
		/** The {@link LongArrayBitVector#bits() bv.bits()}, cached. */
		protected final long[] bits;
		/** The constant term. */
		protected long c;
		/** The first variable. This field must be updated by {@link #updateFirstVar()} to be meaningful. */
		protected int firstVar;
		/** The variables added to this equation. They might not be the variables in {@link #bitVector} if {@link #add(Modulo2Equation)} has been invoked. */
		protected IntArrayList addedVars;
		/** Whether any variable appears on the left side of the equation. */
		private boolean isEmpty;

		/** Creates a new equation.
		 * 
		 * @param c the constant term.
		 * @param numVars the number of variables.
		 */
		public Modulo2Equation( final long c, final int numVars ){
			this.c = c;
			this.bitVector = LongArrayBitVector.ofLength( numVars );
			this.bits = bitVector.bits();
			this.firstVar = Integer.MAX_VALUE;
			this.addedVars = new IntArrayList();
			this.isEmpty = true;
		}
		
		protected Modulo2Equation( final Modulo2Equation equation ){
			this.c = equation.c;
			this.bitVector = equation.bitVector.copy();
			this.bits = this.bitVector.bits();
			this.firstVar = equation.firstVar;
			this.addedVars = equation.addedVars;
			this.isEmpty = equation.isEmpty;
		}
		
		/** Adds a new variable with given coefficient.
		 * 
		 * @param variable a variable.
		 * @return this equation.
		 */
		public Modulo2Equation add( final int variable ) {
			assert ! bitVector.getBoolean( variable );
			bitVector.set( variable );
			addedVars.add( variable );
			if ( variable < firstVar ) firstVar = variable;
			isEmpty = false;
			return this;
		}
		
		
		/** Returns an array containing the variables in increasing order.
		 * 
		 * <p>Mainly for debugging purposes.
		 * 
		 * @return an array containing the variables in increasing order.
		 */
		public int[] variables() {
			IntArrayList variables = new IntArrayList();
			for( int i = 0; i < bitVector.length(); i++ ) if ( bitVector.getBoolean( i ) ) variables.add( i );
			return variables.toIntArray();
		}

		/** Subtract from this equation another equation multiplied by a provided constant.
		 * 
		 * @param equation an equation.
		 */
		public void add( final Modulo2Equation equation ) {
			this.c ^= equation.c;
			// Explicit multiplication tables to avoid modulo operators.
			final long[] x = this.bits, y = equation.bits;
			long isNotEmpty = 0;
			for( int i = x.length; i-- != 0; ) isNotEmpty |= ( x[ i ] ^= y[ i ] );
			isEmpty = isNotEmpty == 0;
		}
		
		/** Updates the information contained in {@link #firstVar}.
		 * 
		 * <p>This method does not check whether {@link #firstVar} is actually updated.
		 */
		public void updateFirstVar() {
			if ( isEmpty ) firstVar = Integer.MAX_VALUE;
			else {
				int i = -1;
				while( bits[ ++i ] == 0 );
				firstVar = i * 64 + Long.numberOfTrailingZeros( bits[ i ] );
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
			if ( ! ( o instanceof Modulo2Equation ) ) return false;
			final Modulo2Equation equation = (Modulo2Equation)o;
			return c == equation.c && bitVector.equals( equation.bitVector );
		}

		/** Returns the modulo-2 scalar product of the two provided bit vectors.
		 * 
		 * @param bits a bit vector represented as an array of longs.
		 * @param values an array of long representing the 64-bit values associated with each variable.
		 * @return the modulo-2 scalar product of {@code x} and {code y}.
		 */
		public static long scalarProduct( final long[] bits, final long[] values ) {
			long sum = 0;

			for( int i = bits.length; i-- != 0; ) {
				final int offset = i * 64;
				long word = bits[ i ];
				while( word != 0 ) {
					final int lsb = Long.numberOfTrailingZeros( word );
					sum ^= values[ offset + lsb ];
					word &= word - 1;
				}
			}
			
			return sum;
		}

		public String toString() {
			StringBuilder b = new StringBuilder();
			boolean someNonZero = false;
			for( int i = 0; i < bitVector.length(); i++ ) {
				if ( bitVector.getBoolean( i ) ) {
					if ( someNonZero ) b.append( " + " );
					someNonZero = true;
					b.append( "x_" ).append( i );
				}
	 		}
			if ( ! someNonZero ) b.append( '0' );
			return b.append( " = " ).append( c ).toString();
		}
		
		public Modulo2Equation copy() {
			return new Modulo2Equation( this );
		}
	}

	/** The number of variables. */
	private final int numVars;
	/** The equations. */
	private final ArrayList<Modulo2Equation> equations;
	/** The number of heavy variables after a call to {@link #structuredGaussianElimination(LongArrayBitVector)}. */
	private int numHeavy;

	public Modulo2System( final int numVar ) {
		equations = new ArrayList<Modulo2Equation>();
		this.numVars = numVar;
	}

	protected Modulo2System( final int numVar , ArrayList<Modulo2Equation> system  ) {
		this.equations = system;
		this.numVars = numVar;
	}
	
	public Modulo2System copy() {
		ArrayList<Modulo2Equation> list = new ArrayList<Modulo2System.Modulo2Equation>( equations.size() );
		for( Modulo2Equation equation: equations ) list.add( equation.copy() );
		return new Modulo2System( numVars, list );
	}

	/** Adds a new equation to the system.
	 * 
	 * @param equation an equation with the same number of variables of the system.
	 */
	public void add( Modulo2Equation equation ) {
		if ( equation.bitVector.length() != numVars ) throw new IllegalArgumentException( "The number of variables in the equation (" + equation.bitVector.length() + ") does not match the number of variables of the system (" + numVars + ")" );
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


	public boolean check( final long[] solution ) {
		assert solution.length == numVars;

		for( Modulo2Equation equation: equations ) 
			if ( equation.c != Modulo2Equation.scalarProduct( equation.bits, solution ) ) return false;
		return true;
	}


	private boolean echelonForm() {
		main: for ( int i = 0; i < equations.size() - 1; i++ ) {
			assert equations.get( i ).firstVar != Integer.MAX_VALUE;
			
			for ( int j = i + 1; j < equations.size(); j++ ) {
				// Note that because of exchanges we cannot extract the first assignment
				Modulo2Equation eqJ = equations.get( j );
				Modulo2Equation eqI = equations.get( i );

				assert eqI.firstVar != Integer.MAX_VALUE;
				assert eqJ.firstVar != Integer.MAX_VALUE;

				final int firstVar = eqI.firstVar;

				if( firstVar == eqJ.firstVar ) {
					eqI.add( eqJ );
					if ( eqI.isUnsolvable() ) return false;
					if ( eqI.isIdentity() ) continue main;
					eqI.updateFirstVar();
				}

				if ( eqI.firstVar > eqJ.firstVar ) Collections.swap( equations, i, j );
			}
		}
		return true;
	}

	public boolean gaussianElimination( final long[] solution ) {
		assert solution.length == numVars;
		for ( Modulo2Equation equation: equations ) equation.updateFirstVar();

		if ( ! echelonForm() ) return false;

		for ( int i = equations.size(); i-- != 0; ) {
			final Modulo2Equation equation = equations.get( i );
			if ( equation.isIdentity() ) continue;

			assert solution[ equation.firstVar ] == 0 : equation.firstVar;

			solution[ equation.firstVar ] = equation.c ^ Modulo2Equation.scalarProduct( equation.bits, solution );
		}

		return true;
	}

	public boolean structuredGaussianElimination( final long[] solution ) {
		final int[][] var2Eq = new int[ numVars ][];
		final int[] d = new int[ numVars ];
		for( final Modulo2Equation equation: equations ) 
			for( int v = (int)equation.bitVector.length(); v-- != 0; ) 
				if ( equation.bitVector.getBoolean( v ) ) d[ v ]++; 
		
		for( int v = numVars; v-- != 0; ) var2Eq[ v ] = new int[ d[ v ] ];
		Arrays.fill( d, 0 );
		final long[] c = new long[ equations.size() ];
		for( int e = equations.size(); e-- != 0; ) {
			c[ e ] = equations.get( e ).c;
			LongArrayBitVector bitVector = equations.get( e ).bitVector;
			for( int v = (int)bitVector.length(); v-- != 0; ) 
				if ( bitVector.getBoolean( v ) ) var2Eq[ v ][ d[ v ]++ ] = e;
		}
		
		return structuredGaussianElimination( this, var2Eq, c, Util.identity( numVars ), solution );
	}

	public static boolean structuredGaussianElimination( final int var2Eq[][], final long[] c, final int[] variable, final long[] solution ) {
		return structuredGaussianElimination( null, var2Eq, c, variable, solution );
	}
	
	public static boolean structuredGaussianElimination( Modulo2System system, final int var2Eq[][], final long[] c, final int[] variable, final long[] solution ) {
		final int numEquations = c.length;
		if ( numEquations == 0 ) return true;

		final int numVars = var2Eq.length;
		assert solution.length == numVars;

		final boolean buildSystem = system == null;

		if ( buildSystem ) {
			system = new Modulo2System( numVars );
			for( int i = 0; i < c.length; i++ ) system.add( new Modulo2Equation( c[ i ], numVars ) );
		}
				
		/* The weight of each variable, that is, the number of equations still 
		 * in the queue in which the variable appears. We use zero to 
		 * denote pivots of solved equations. */
		final int weight[] = new int[ numVars ];

		// The priority of each equation still to be examined (the number of light variables).
		final int[] priority = new int[ numEquations ];

		for( final int v : variable ) {
			final int[] eq = var2Eq[ v ];
			if ( eq.length == 0 ) continue;
			
			int currEq = eq[ 0 ];
			boolean currCoeff = true;
			int j = 0;

			for( int i = 1; i < eq.length; i++ ) {
				if ( eq[ i ] != currEq ) {
					assert eq[ i ] > currEq;
					if ( currCoeff ) {
						if ( buildSystem ) system.equations.get( currEq ).add( v );
						weight[ v ]++;
						priority[ currEq ]++;
						eq[ j++ ] = currEq;
					}
					currEq = eq[ i ];
					currCoeff = true;
				}
				else currCoeff = ! currCoeff;
			}
		
			if ( currCoeff ) {
				if ( buildSystem ) system.equations.get( currEq ).add( v );
				weight[ v ]++;
				priority[ currEq ]++;
				eq[ j++ ] = currEq;
			}
			
			// In case we found duplicates, we replace the array with a uniquified one.
			if ( j != eq.length ) var2Eq[ v ] = Arrays.copyOf( var2Eq[ v ], j );
		}		
		
		if ( DEBUG ) {
			System.err.println();
			System.err.println( "===== Going to solve... ======" );
			System.err.println();
			System.err.println( system );
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
		ArrayList<Modulo2Equation> dense = new ArrayList<Modulo2Equation>();
		// The equations that define a light variable in term of heavy variables.
		ArrayList<Modulo2Equation> solved = new ArrayList<Modulo2Equation>();
		// The light variable corresponding to each solved equation.
		IntArrayList pivots = new IntArrayList();

		final ArrayList<Modulo2Equation> equations = system.equations;
		// A bit vector containing a 1 in correspondence of each light variable.
		final long[] lightNormalized = new long[ equations.get( 0 ).bits.length ];
		Arrays.fill( lightNormalized, -1 );

		int numHeavy = 0;

		for( int remaining = equations.size(); remaining != 0; ) {
			if ( equationList.isEmpty() ) {
				// Make another variable heavy
				int var;
				do var = variables.popInt(); while( weight[ var ] == 0 );
				numHeavy++;
				lightNormalized[ var / 64 ] ^= 1L << ( var % 64 );
				if ( DEBUG ) System.err.println( "Making variable " + var + " of weight " + weight[ var ] + " heavy (" + remaining + " equations to go)" );
				for( final int equationIndex: var2Eq[ var ] )
					if ( --priority[ equationIndex ] == 1 ) equationList.push( equationIndex );
			}
			else {
				remaining--;
				final int first = equationList.popInt(); // An equation of weight 0 or 1.
				final Modulo2Equation equation = equations.get( first );
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
					int wordIndex = 0;
					while( ( equation.bits[ wordIndex ] & lightNormalized[ wordIndex ] ) == 0 ) wordIndex++;
					final int pivot = wordIndex * 64 + Long.numberOfTrailingZeros( equation.bits[ wordIndex ] & lightNormalized[ wordIndex ] ); 

					// Record the light variable and the equation for computing it later.
					if ( DEBUG ) System.err.println( "Adding to solved variables x_" + pivot + " by equation " + equation );
					pivots.add( pivot );
					solved.add( equation );
					// This forces to skip the pivot when looking for a new variable to be made heavy.
					weight[ pivot ] = 0;

					// Now we need to eliminate the variable from all other equations containing it.
					for( final int equationIndex: var2Eq[ pivot ] ) {
						if ( equationIndex == first ) continue;
						if ( --priority[ equationIndex ] == 1 ) equationList.add( equationIndex );
						if ( DEBUG ) System.err.print( "Replacing equation (" + equationIndex + ") " + equations.get( equationIndex ) + " with " );
						equations.get( equationIndex ).add( equation );
						if ( DEBUG ) System.err.println( equations.get( equationIndex ) );
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

		Modulo2System denseSystem = new Modulo2System( numVars, dense );
		if ( ! denseSystem.gaussianElimination( solution ) ) return false;  // numVars >= denseSystem.numVars

		if ( DEBUG ) System.err.println( "Solution (dense): " + Arrays.toString( solution ) );

		for ( int i = solved.size(); i-- != 0; ) {
			final Modulo2Equation equation = solved.get( i );
			final int pivot = pivots.getInt( i );
			assert solution[ pivot ] == 0 : pivot;
			solution[ pivot ] = equation.c ^ Modulo2Equation.scalarProduct( equation.bits, solution );
		}

		if ( DEBUG ) System.err.println( "Solution (all): " + Arrays.toString( solution ) );

		return true;
	}
}
