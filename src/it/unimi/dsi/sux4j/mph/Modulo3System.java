package it.unimi.dsi.sux4j.mph;


import it.unimi.dsi.Util;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongBigList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Modulo3System {
	private final static boolean DEBUG = false;
	
	/** A modulo-3 equation. */
	public static class Modulo3Equation {
		/** The constant term. */
		protected int c;
		protected final LongArrayBitVector bv;
		private final LongBigList list;
		protected int coeff;

		/** Creates a new equation.
		 * 
		 * @param c the constant term.
		 */
		public Modulo3Equation( final int c, final int numVar ){
			this.c = c;
			this.bv = LongArrayBitVector.ofLength( numVar * 2 );
			this.list = bv.asLongBigList( 2 );
		}
		
		protected Modulo3Equation( final Modulo3Equation equation ){
			this.c = equation.c;
			this.bv = equation.bv.copy();
			this.list = this.bv.asLongBigList( 2 );
		}
		
		public Modulo3Equation add( final int index, final int value ) {
			list.set( index, value );
			return this;
		}
		
		public Modulo3Equation add( final int index ) {
			list.set( index, 1 );
			return this;
		}
		
		public int[] variables() {
			IntArrayList variables = new IntArrayList();
			for( int var = -1; ( var = nextVar( var + 1 ) ) != Integer.MAX_VALUE; )	variables.add( var );
			return variables.toIntArray();
		}

		public int[] coefficients() {
			IntArrayList coefficients = new IntArrayList();
			for( int var = -1; ( var = nextVar( var + 1 ) ) != Integer.MAX_VALUE; )	coefficients.add( coeff );
			return coefficients.toIntArray();
		}
		
		public Modulo3Equation eliminate( final Modulo3Equation equation, final int var ) {
			final int mul = this.list.get( var ) == equation.list.get( var ) ? 1 : 2;
			sub( equation, mul );
			return this;

			//System.err.println( this + " - " + mul + " * " + equation + "  =>  " + result );
			//assert result.sub( equation, 3 - mul ).equals( this ) : result.sub( equation, 3 - mul ) + " != " + this;
		}

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

		private final static void addMod3( final LongArrayBitVector x, final LongArrayBitVector y ) {
			final long[] bx = x.bits(), by = y.bits();
			for( int i = (int)( ( x.length() + 63 ) / 64 ); i-- != 0; ) bx[ i ] = addMod3( bx[ i ], by[ i ] ); 
		}
		
		private final static void subMod3( final LongArrayBitVector x, final LongArrayBitVector y ) {
			final long[] bx = x.bits(), by = y.bits();
			for( int i = (int)( ( x.length() + 63 ) / 64 ); i-- != 0; ) bx[ i ] = subMod3( bx[ i ], by[ i ] ); 
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
				subMod3( bv, equation.bv );
			}
			else {
				c = ( c + equation.c ) % 3;
				addMod3( bv, equation.bv );
			}
		}
		
		public int firstVar() {
			final int numWords = (int)( ( bv.length() + 63 ) / 64 );
			final long[] bits = bv.bits();
			for( int i = 0; i < numWords; i++ ) if ( bits[ i ] != 0 ) return Fast.select( bits[ i ], 0 ) / 2 + 32 * i;
			throw new IllegalArgumentException();
		}

		public int nextVar( final int i ) {
			final long[] bits = bv.bits();
			int word = ( i * 2 ) / 64;
			if ( word >= bits.length ) return Integer.MAX_VALUE;
			long w = bits[ word ] & ( -1L << ( i % 32 ) * 2 );
			while( w == 0 ) {
				if ( ++word * 64 >= bv.length() ) return Integer.MAX_VALUE;
				w = bits[ word ];
			}
			
			final int select = Fast.select( w, 0 );
			final int nextVar = word * 32 + select / 2;
			
			coeff = (int)( w >> ( select & ~1 ) & 3 );
			
			return nextVar;
		}
		
		public boolean noCoefficients() {
			final long[] bits = bv.bits();
			for( int i = (int)( ( bv.length() + 63 ) / 64 ); i-- != 0; ) if ( bits[ i ] != 0 ) return false;
			return true;
		}
		
		public boolean isUnsolvable() {
			return noCoefficients() && c != 0;
		}

		public boolean isIdentity() {
			return noCoefficients() && c == 0;
		}
		
		@Override
		public boolean equals( Object o ) {
			if ( ! ( o instanceof Modulo3Equation ) ) return false;
			final Modulo3Equation equation = (Modulo3Equation)o;
			return c == equation.c && bv.equals( equation.bv );
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

	private final ArrayList<Modulo3Equation> equations;
	private final int numVar;

	public Modulo3System( final int numVar ) {
		equations = new ArrayList<Modulo3Equation>();
		this.numVar = numVar;
	}

	protected Modulo3System( ArrayList<Modulo3Equation> system, final int numVar ) {
		this.equations = system;
		this.numVar = numVar;
	}
	
	public Modulo3System copy() {
		ArrayList<Modulo3Equation> list = new ArrayList<Modulo3System.Modulo3Equation>( equations.size() );
		for( Modulo3Equation equation: equations ) list.add( equation.copy() );
		return new Modulo3System( list, numVar );
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for ( int i = 0; i < equations.size(); i++ ) b.append( equations.get( i ) ).append( '\n' );
		return b.toString();
	}

	public void add( Modulo3Equation equation ) {
		if ( equation.list.size() != numVar ) throw new IllegalArgumentException( "The number of variables in the equation (" + equation.list.size() + ") does not match the number of variables of the system (" + numVar + ")" );
		equations.add( equation );
	}

	public boolean echelonForm() {
		main: for ( int i = 0; i < equations.size() - 1; i++ ) {
			assert ! equations.get( i ).noCoefficients();
			
			for ( int j = i + 1; j < equations.size(); j++ ) {
				// Note that because of exchanges we cannot extract the first assignment
				Modulo3Equation eqJ = equations.get( j );
				if ( eqJ.isIdentity() ) continue;
				Modulo3Equation eqI = equations.get( i );

				assert ! eqI.noCoefficients();
				assert ! eqJ.noCoefficients();

				final int firstVar = eqI.firstVar();

				if( firstVar == eqJ.firstVar() ) {
					eqI.eliminate( eqJ, firstVar );
					if ( eqI.isUnsolvable() ) return false;
					if ( eqI.isIdentity() ) continue main;
					assert firstVar != eqI.firstVar() : firstVar + " = " + eqI.firstVar();
				}

				if ( eqI.firstVar() > eqJ.firstVar() ) Collections.swap( equations, i, j );
			}
		}
		return true;
	}

	public boolean structuredGaussianElimination( final int[] solution ) {
		if ( DEBUG ) {
			System.err.println();
			System.err.println( "====================" );
			System.err.println();
			System.err.println( this );
		}
			
		final int numEquations = equations.size();
		/* The weight of each variable, that is, the opposite of number of equations still 
		 * in the queue in which the variable appears. We use negative weights to have
		 * variables of weight of maximum modulus at the top of the queue. */
		final int weight[] = new int[ numVar ];
		// For each variable, the equations still in the queue containing it.
		final IntOpenHashSet[] varEquation = new IntOpenHashSet[ numVar ];
		// The priority of each equation still in the queue (the number of light variables).
		final int[] priority = new int[ numEquations ];

		for( int i = 0; i < numEquations; i++ ) {
			final Modulo3Equation equation = equations.get( i );
			// Initially, all variables are light.
			for( int var = -1; ( var = equation.nextVar( var + 1 ) ) != Integer.MAX_VALUE; ) {  
				if ( varEquation[ var ] == null ) varEquation[ var ] = new IntOpenHashSet( 8, Hash.FAST_LOAD_FACTOR );
				weight[ var ]--;
				varEquation[ var ].add( i );
				priority[ i ]++;
			}
		}

		final boolean[] isHeavy = new boolean[ numVar ];		
		IntHeapIndirectPriorityQueue variableQueue = new IntHeapIndirectPriorityQueue( weight, Util.identity( numVar ) );
		
		// The equations that are neither dense, nor solved.
		IntHeapIndirectPriorityQueue equationQueue = new IntHeapIndirectPriorityQueue( priority, Util.identity( numEquations ) );
		
		ArrayList<Modulo3Equation> dense = new ArrayList<Modulo3Equation>();
		ArrayList<Modulo3Equation> solved = new ArrayList<Modulo3Equation>();
		IntArrayList pivots = new IntArrayList();
		
		while( ! equationQueue.isEmpty() ) {
			final int first = equationQueue.first(); // Index of the equation of minimum weight
			Modulo3Equation firstEquation = equations.get( first );
			if ( DEBUG ) System.err.println( "Looking at equation " + first + " of priority " + priority[ first ] + " : " + firstEquation );
			
			if ( priority[ first ] == 0 ) {
				equationQueue.dequeue();
				if ( firstEquation.isUnsolvable() ) return false;
				if ( firstEquation.isIdentity() ) continue;
				// This equation must be necessarily solved by standard Gaussian elimination.
				dense.add( firstEquation );
				// We remove references to this equations, as we have no longer to update its priority.
				for( int var = -1;; ) {
					var = firstEquation.nextVar( var + 1 );
					if ( var == Integer.MAX_VALUE ) break;
					varEquation[ var ].remove( first );
					if ( ! isHeavy[ var ] ) {
						weight[ var ]++;
						variableQueue.changed( var );
					}
				}
			}
			else if ( priority[ first ] == 1 ) {
				equationQueue.dequeue();
				// This is solved (in terms of the heavy variables). Let's find the light variable.
				int pivot = -1;
				for( int var = -1; ( var = firstEquation.nextVar( var + 1 ) ) != Integer.MAX_VALUE; ) {
					// We remove references to this equations, as we have no longer to update its priority.
					varEquation[ var ].remove( first );
					if ( ! isHeavy[ var ] ) {
						assert pivot == -1 : pivot;
						pivot = var;
					}
				}
				assert pivot != -1;
				// Record the light variable and the equation for computing it later.
				if ( DEBUG ) System.err.println( "Adding to solved variables x_" + pivot + " by equation " + firstEquation );
				pivots.add( pivot );
				solved.add( firstEquation );
				variableQueue.remove( pivot ); // Pivots cannot become heavy

				// Now we need to eliminate the variable from all other equations containing it.
				for( IntIterator iterator = varEquation[ pivot ].iterator(); iterator.hasNext(); ) {
					final int equationIndex = iterator.nextInt();
					assert equationIndex != first;
					priority[ equationIndex ]--;
					equationQueue.changed( equationIndex );
					final Modulo3Equation equation = equations.get( equationIndex ).copy();
					if ( DEBUG ) System.err.print( "Replacing equation (" + equationIndex + ") " + equation + " with " );
					Modulo3Equation result = equations.get( equationIndex ).eliminate( firstEquation, pivot );
					// We now update information about variables.
					
					int a = result.nextVar( 0 );
					int b = equation.nextVar( 0 );

					while( a != Integer.MAX_VALUE || b != Integer.MAX_VALUE ) {
						if ( a < b ) {
							assert a != pivot;
							varEquation[ a ].add( equationIndex );
							if ( ! isHeavy[ a ] ) {
								weight[ a ]--;
								variableQueue.changed( a );
							}
							a = result.nextVar( a + 1 );
						}
						else if ( b < a ) {
							if ( b != pivot ) { // We don't care, and we cannot update varEquation[ pivot ] while iterating.
								// System.err.println( "Removing equation " + equationIndex + " from the list of variable " + b );
								varEquation[ b ].remove( equationIndex );
								if ( ! isHeavy[ b ] ) {
									weight[ b ]++;
									variableQueue.changed( b );
								}
							}
							b = equation.nextVar( b + 1 );
						}
						else {
							a = result.nextVar( a + 1 );
							b = equation.nextVar( b + 1 );
						}
					}
					
					equations.set( equationIndex, result );
					if ( DEBUG ) System.err.println( result );
				}
			}
			else {
				// Make another variable heavy
				final int var = variableQueue.dequeue();
				isHeavy[ var ] = true;
				if ( DEBUG ) {
					int numHeavy = 0;
					for( boolean b: isHeavy ) if ( b ) numHeavy++;
					System.err.println( "Making variable " + var + " of weight " + ( - weight[ var ] ) + " heavy (" + numHeavy + " heavy variables, " + equationQueue.size() + " equations to go)" );
				}
				for( IntIterator iterator = varEquation[ var ].iterator(); iterator.hasNext(); ) {
					final int equationIndex = iterator.nextInt();
					priority[ equationIndex ]--;
					equationQueue.changed( equationIndex );
				}
			}
		}


		if ( DEBUG ) {
			int numHeavy = 0;
			for( boolean b: isHeavy ) if ( b ) numHeavy++;
			System.err.println( "Heavy variables: " + numHeavy + " (" + Util.format( numHeavy * 100 / numVar ) + "%)" );
			System.err.println( "Dense equations: " + dense );
			System.err.println( "Solved equations: " + solved );
			System.err.println( "Pivots: " + pivots );
		}

		Modulo3System denseSystem = new Modulo3System( dense, numVar );
		if ( ! denseSystem.gaussianElimination( solution ) ) return false;  // numVar >= denseSystem.numVar

		if ( DEBUG ) System.err.println( "Solution (dense): " + Arrays.toString( solution ) );

		for ( int i = solved.size(); i-- != 0; ) {
			final Modulo3Equation equation = solved.get( i );
			final int pivot = pivots.getInt( i );
			int pivotCoefficient = -1;

			int c = equation.c;
			
			for( int var = -1;; ) {
				var = equation.nextVar( var + 1 );
				if ( var == Integer.MAX_VALUE ) break;
				if ( var == pivot ) pivotCoefficient = equation.coeff;
				else c = ( 6 + c - ( equation.coeff * solution[ var ] ) ) % 3;
			}

			assert pivotCoefficient != -1;
			if ( c == 0 ) solution[ pivot ] = 0;
			else solution[ pivot ] = pivotCoefficient == c ? 1 : 2;
		}

		if ( DEBUG ) System.err.println( "Solution (all): " + Arrays.toString( solution ) );

		return true;
	}


	public boolean gaussianElimination( final int[] solution ) {
		if ( ! echelonForm() ) return false;
		for ( int i = equations.size(); i-- != 0; ) {
			final Modulo3Equation equation = equations.get( i );
			if ( equation.isIdentity() ) continue;

			int c = equation.c;
			// First variable
			final int firstVar = equation.nextVar( 0 );
			final int firstCoeff = equation.coeff;

			for( int var = firstVar; ( var = equation.nextVar( var + 1 ) ) != Integer.MAX_VALUE; ) 
				c = ( 6 + c - ( equation.coeff * solution[ var ] ) ) % 3;

			assert solution[ firstVar ] == 0 : firstVar;
			if ( c == 0 ) solution[ firstVar ] = 0;
			else solution[ firstVar ] = firstCoeff == c ? 1 : 2;
		}
		
		return true;
	}
	
	public int size() {
		return equations.size();
	}

	public boolean check( final int solution[] ) {
		for( Modulo3Equation equation: equations ) {
			int sum = 0;
			for( int var = -1;( var = equation.nextVar( var + 1 ) ) != Integer.MAX_VALUE; ) 
				sum = ( sum + solution[ var ] * equation.coeff ) % 3;
			if ( equation.c != sum ) {
				System.err.println( equation + " " + Arrays.toString( solution ) );
				return false;
			}
		}
		return true;
	}
}
