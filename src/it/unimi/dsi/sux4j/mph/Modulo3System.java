package it.unimi.dsi.sux4j.mph;


import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.Swapper;
import it.unimi.dsi.fastutil.ints.AbstractIntComparator;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntHeapIndirectPriorityQueue;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.apache.commons.lang.ArrayUtils;

public class Modulo3System {
	private final static boolean DEBUG = false;
	
	/** A modulo-3 equation. */
	public static class Modulo3Equation {

		public static final class Builder {
			private final int c;
			private final IntArrayList variables;
			private final IntArrayList coefficients;
			
			public Builder( final int c, final int capacity ) {
				this.c = c;
				this.variables = new IntArrayList( capacity );
				this.coefficients = new IntArrayList( capacity );
			}
			
			public Builder( final int c ) {
				this( c, 16 );
			}
			
			public Builder add( final int index, final int value ) {
				variables.add( index );
				coefficients.add( value );
				return this;
			}
			
			public Builder add( final int index ) {
				variables.add( index );
				coefficients.add( 1 );
				return this;
			}
			
			public Modulo3Equation build() {
				final int variable[] = variables.elements();
				final int coefficient[] = coefficients.elements();
				final int length = variables.size();
				if ( length > 1 ) {
					int i;
					for( i = length; i-- != 1; ) if ( variable[ i ] < variable[ i - 1 ] ) break;
					if ( i != 0 ) {
						it.unimi.dsi.fastutil.Arrays.quickSort( 0, length, new AbstractIntComparator() {
							@Override
							public int compare( int k1, int k2 ) {
								return Integer.compare( variable[ k1 ], variable[ k2 ] );
							}
						}, new Swapper() {
							@Override
							public void swap( int a, int b ) {
								final int v = variable[ a ];
								final int c = coefficient[ a ];
								variable[ a ] = variable[ b ];
								coefficient[ a ] = coefficient[ b ];
								variable[ b ] = v;
								coefficient[ b ] = c;
							}
						} );
					}
				}
				return new Modulo3Equation( c, variables, coefficients );
			}
		}
		
		/** The constant term. */
		public final int c;
		public final int[] variable;
		public final int[] coefficient;

		/** Creates a new equation.
		 * 
		 * @param c the constant term.
		 * @param variables the variables (sorted).
		 * @param coefficients the coefficients (parallel to {@code variable}).
		 */
		protected Modulo3Equation( final int c, final IntArrayList variables, final IntArrayList coefficients ){
			this.c = c;
			variable = variables.toIntArray();
			coefficient = coefficients.toIntArray();
		}
		
		protected Modulo3Equation( final Modulo3Equation equation ){
			this.c = equation.c;
			variable = equation.variable.clone();
			coefficient = equation.coefficient.clone();
		}
		
		public Modulo3Equation eliminate( final Modulo3Equation equation, final int var ) {
			final int i = ArrayUtils.indexOf( this.variable, var );
			final int j = ArrayUtils.indexOf( equation.variable, var );

			final int mul = coefficient[ i ] == equation.coefficient[ j ] ? 1 : 2;
			final Modulo3Equation result = sub( equation, mul );

			//System.err.println( this + " - " + mul + " * " + equation + "  =>  " + result );
			assert result.sub( equation, 3 - mul ).equals( this ) : result.sub( equation, 3 - mul ) + " != " + this;
			return result;
		}

		/** Subtract from this equation another equation multiplied by a provided constant.
		 * 
		 * @param equation the subtrahend.
		 * @param mul a multiplier that will be applied to the subtrahend.
		 */
		
		public Modulo3Equation sub( final Modulo3Equation equation, final int mul ) {
			// Explicit multiplication tables to avoid modulo operators.
			final int[] timeMinusMul = { 0, 3 - mul,  mul };
			final int[] timesMul = { 0, mul, 3 - mul };
			
			Builder builder = new Modulo3Equation.Builder( ( c + timeMinusMul[ equation.c ] ) % 3, variable.length + equation.variable.length );

			int i = 0, j = 0;
			int a = i < this.variable.length ? this.variable[ i ] : Integer.MAX_VALUE;
			int b = j < equation.variable.length ? equation.variable[ j ] : Integer.MAX_VALUE;

			while( a != Integer.MAX_VALUE || b != Integer.MAX_VALUE ) {
				if ( a < b ) {
					builder.add( a, this.coefficient[ i ] );
					a = ++i < this.variable.length ? this.variable[ i ] : Integer.MAX_VALUE;
				}
				else if ( a > b ) {
					builder.add( b, timeMinusMul[ equation.coefficient[ j ] ] );
					b = ++j < equation.variable.length ? equation.variable[ j ] : Integer.MAX_VALUE;
				}
				else {
					if ( this.coefficient[ i ] != timesMul[ equation.coefficient[ j ] ] ) builder.add( a, 3 - this.coefficient[ i ] );
					a = ++i < this.variable.length ? this.variable[ i ] : Integer.MAX_VALUE;
					b = ++j < equation.variable.length ? equation.variable[ j ] : Integer.MAX_VALUE;
				}
			}

			for ( ; i < this.variable.length; i++ ) builder.add( this.variable[ i ], this.coefficient[ i ] );
			for ( ; j < equation.variable.length; j++ ) builder.add( equation.variable[ j ], equation.coefficient[ j ] );
			
			return builder.build();
		}

		public boolean isUnsolvable() {
			return variable.length == 0 && c != 0;
		}

		public boolean isIdentity() {
			return variable.length == 0 && c == 0;
		}
		
		@Override
		public boolean equals( Object o ) {
			if ( ! ( o instanceof Modulo3Equation ) ) return false;
			final Modulo3Equation equation = (Modulo3Equation)o;
			return c == equation.c && Arrays.equals( variable, equation.variable );
		}

		public String toString() {
			StringBuilder b = new StringBuilder();
			for( int i = 0; i < variable.length; i++ ) {
				if ( i != 0 ) b.append( " + " );
				b.append( coefficient[ i ] == 1 ? "x" : "2x" ).append( '_' ).append( variable[ i ] );
	 		}
			if ( variable.length == 0 ) b.append( '0' );
			return b.append( " = " ).append( c ).toString();
		}
		
		public Modulo3Equation copy() {
			return new Modulo3Equation( this );
		}
	}

	private final ArrayList<Modulo3Equation> equations;
	private int numVar;

	public Modulo3System() {
		equations = new ArrayList<Modulo3Equation>();
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
		equations.add( equation );
		for( int i = 0; i < equation.variable.length; i++ ) numVar = Math.max( numVar, equation.variable[ equation.variable.length - 1 ] + 1 );
	}

	public boolean echelonForm() {
		main: for ( int i = 0; i < equations.size() - 1; i++ ) {
			assert equations.get( i ).variable.length > 0;
			
			for ( int j = i + 1; j < equations.size(); j++ ) {
				// Note that because of exchanges we cannot extract the first assignment
				if ( equations.get( j ).isIdentity() ) continue;
				assert equations.get( i ).variable.length > 0;
				assert equations.get( j ).variable.length > 0;

				if( equations.get( i ).variable[ 0 ] == equations.get( j ).variable[ 0 ] ) {
					equations.set( i, equations.get( i ).eliminate( equations.get( j ), equations.get( i ).variable[ 0 ] ) );
					if ( equations.get( i ).isUnsolvable() ) return false;
					if ( equations.get( i ).isIdentity() ) continue main;
				}

				if ( ( equations.get( i ).variable[ 0 ] > equations.get( j ).variable[ 0 ] ) ) Collections.swap( equations, i, j );
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
			// Initially, all variables are light.
			priority[ i ] = equations.get( i ).variable.length;
			for( int j = 0; j < equations.get( i ).variable.length; j++ ) {  
				final int var = equations.get( i ).variable[ j ];
				if ( varEquation[ var ] == null ) varEquation[ var ] = new IntOpenHashSet( 8, Hash.FAST_LOAD_FACTOR );
				weight[ var ]--;
				varEquation[ var ].add( i );
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
				for( int i = firstEquation.variable.length; i-- != 0; ) {
					final int var = firstEquation.variable[ i ];
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
				for( int i = firstEquation.variable.length; i-- != 0; ) {
					final int var = firstEquation.variable[ i ];
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
					final Modulo3Equation equation = equations.get( equationIndex );
					if ( DEBUG ) System.err.print( "Replacing equation (" + equationIndex + ") " + equation + " with " );
					Modulo3Equation result = equation.eliminate( firstEquation, pivot );
					// We now update information about variables.
					
					int i = 0, j = 0;
					int a = i < result.variable.length ? result.variable[ i ] : Integer.MAX_VALUE;
					int b = j < equation.variable.length ? equation.variable[ j ] : Integer.MAX_VALUE;

					while( a != Integer.MAX_VALUE || b != Integer.MAX_VALUE ) {
						if ( a < b ) {
							assert a != pivot;
							varEquation[ a ].add( equationIndex );
							if ( ! isHeavy[ a ] ) {
								weight[ a ]--;
								variableQueue.changed( a );
							}
							a = ++i < result.variable.length ? result.variable[ i ] : Integer.MAX_VALUE;
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
							b = ++j < equation.variable.length ? equation.variable[ j ] : Integer.MAX_VALUE;
						}
						else {
							a = ++i < result.variable.length ? result.variable[ i ] : Integer.MAX_VALUE;
							b = ++j < equation.variable.length ? equation.variable[ j ] : Integer.MAX_VALUE;
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
			
			for( int j = equation.variable.length; j-- != 0; ) {
				if ( equation.variable[ j ] == pivot ) pivotCoefficient = equation.coefficient[ j ];
				else c = ( 6 + c - ( equation.coefficient[ j ] * solution[ equation.variable[ j ] ] ) ) % 3;
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
			for( int j = equation.variable.length; j-- != 0; ) {
				if ( j == 0 ) {
					// First variable
					assert solution[ equation.variable[ j ] ] == 0;
					if ( c == 0 ) solution[ equation.variable[ j ] ] = 0;
					else solution[ equation.variable[ j ] ] = equation.coefficient[ j ] == c ? 1 : 2;
				}
				else c = ( 6 + c - ( equation.coefficient[ j ] * solution[ equation.variable[ j ] ] ) ) % 3;
			}
		}
		
		return true;
	}
	
	public int size() {
		return equations.size();
	}

	public boolean check( final int solution[] ) {
		for( Modulo3Equation equation: equations ) {
			int sum = 0;
			for( int i = 0; i < equation.variable.length; i++ ) {
				sum = ( sum + solution[ equation.variable[ i ] ] * equation.coefficient[ i ] ) % 3;
			}
			if ( equation.c != sum ) {
				System.err.println( equation + " " + Arrays.toString( solution ));
				return false;
			}
		}
		return true;
	}
}
