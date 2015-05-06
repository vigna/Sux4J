package it.unimi.dsi.sux4j.mph;


import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Modulo3System {

	/** A modulo-3 equation. */
	public static class Modulo3Equation {
		/** The variables. */
		private final Int2IntRBTreeMap variables;
		/** The constant term. */
		private int c;

		/** Creates a new equation.
		 * 
		 * @param c the constant term.
		 * @param variables the variables (with coefficent one).
		 */
		public Modulo3Equation( final int c, int... variables ){
			this.variables = new Int2IntRBTreeMap();
			for ( int v: variables ) this.variables.put( v, 1 );
			this.c = c;
		}
		
		protected Modulo3Equation( Modulo3Equation equation ) {
			this.variables = equation.variables.clone();
			this.c = equation.c;
		}

		public void eliminate( final Modulo3Equation equation ) {
			assert this.variables.firstIntKey() == equation.variables.firstIntKey() : this.variables.firstIntKey() + " != " + equation.variables.firstIntKey(); 
			sub( equation, this.variables.get( this.variables.firstIntKey() )
					== equation.variables.get( equation.variables.firstIntKey() ) ? 1 : 2 );
		}

		/** Subtracts the provided equation from this equation.
		 * 
		 * @param equation the subtrahend.
		 * @param mul a multiplier that will be applied to the subtrahend.
		 */
		
		public void sub( final Modulo3Equation equation, final int mul ) {
			ObjectArrayList<Entry> supp = new ObjectArrayList<Entry>();
			ObjectBidirectionalIterator<Entry> iteratorThis = this.variables.int2IntEntrySet().iterator();
			ObjectBidirectionalIterator<Entry> iteratorOther = equation.variables.int2IntEntrySet().iterator();
			
			Entry a = iteratorThis.next(), b = iteratorOther.next();
			for ( ;; ) {
				if ( a.getIntKey() < b.getIntKey() ) {
					if ( !iteratorThis.hasNext() ) {
						supp.add( b );
						break;
					}
					a = iteratorThis.next();
				}
				else if ( a.getIntKey() > b.getIntKey() ) {
					supp.add( b );
					if ( !iteratorOther.hasNext() ) break;
					b = iteratorOther.next();
				}
				else {
					if ( a.getIntValue() == ( b.getIntValue() * mul ) % 3 ) iteratorThis.remove();
					else a.setValue( 3 - a.getIntValue() );
					if ( !iteratorThis.hasNext() || !iteratorOther.hasNext() ) break;
					a = iteratorThis.next();
					b = iteratorOther.next();
				}
			}

			// Subtracting something multiplied by mul is like adding that something multiplied by -mul = 3 - mul (mod 3)
			while ( iteratorOther.hasNext() ) {
				Entry v = iteratorOther.next();
				variables.put( v.getIntKey(), ( v.getIntValue() * ( 3 - mul ) ) % 3 );
			}
			
			for( Entry e: supp ) variables.put( e.getIntKey(), ( e.getIntValue() * ( 3 - mul ) ) % 3 );

			c -= equation.c * mul;
			c = ( c + 6 ) % 3;
		}

		public boolean isUnsolvable() {
			return variables.isEmpty() && c != 0;
		}

		public boolean isIdentity() {
			return variables.isEmpty() && c == 0;
		}

		public Modulo3Equation copy() {
			return new Modulo3Equation( this );
		}
		
		public String toString() {
			return variables.toString() + " = " + c;
		}
	}

	private final ArrayList<Modulo3Equation> equations;

	public Modulo3System() {
		equations = new ArrayList<Modulo3Equation>();
	}

	protected Modulo3System( ArrayList<Modulo3Equation> system ) {
		this.equations = system;
	}

	public void print() {
		for ( int i = 0; i < equations.size(); i++ ) System.out.println( equations.get( i ) );
	}

	public void add( Modulo3Equation equation ) {
		equations.add( equation );
	}

	public boolean echelonForm() {
		for ( int i = 0; i < equations.size() - 1; i++ ) {
			assert ! equations.get( i ).variables.isEmpty();
			
			main: for ( int j = i + 1; j < equations.size(); j++ ) {
				// Note that because of exchanges we cannot extract the first assignment
				final Modulo3Equation equation0 = equations.get( i );
				final Modulo3Equation equation1 = equations.get( j );
				assert ! equation1.variables.isEmpty();

				if( equation0.variables.firstIntKey() == equation1.variables.firstIntKey() ) {
					equation0.eliminate( equation1 );
					if ( equation0.isUnsolvable() ) return false;
					if ( equation0.isIdentity() ) continue main;
				}

				if ( ( equation0.variables.firstIntKey() > equation1.variables.firstIntKey() ) ) Collections.swap( equations, i, j );
			}
		}
		return true;
	}


	public boolean solve( final int[] solution ) {
		if ( ! echelonForm() ) return false;
		for ( int i = equations.size(); i-- != 0; ) {
			Modulo3Equation equation = equations.get( i );
			if ( equation.variables.isEmpty() ) continue;

			int count = equation.variables.size();
			int c = equation.c;
			ObjectBidirectionalIterator<Entry> iterator = equation.variables.int2IntEntrySet().iterator( equation.variables.int2IntEntrySet().last() );
			while( iterator.hasPrevious() ) {
				final Entry e = iterator.previous();
				if ( count == 1 ) {
					assert solution[ e.getIntKey() ] == 0;
					if ( c == 0 ) solution[ e.getIntKey() ] = 0;
					else solution[ e.getIntKey() ] = e.getIntValue() == c ? 1 : 2;
				}
				else c = ( 6 + c - ( e.getIntValue() * solution[ e.getIntKey() ] ) ) % 3;
				count--;
			}
		}
		
		return true;
	}


	public int size() {
		return equations.size();
	}

	public Modulo3System copy() {
		Modulo3System s = new Modulo3System();
		for ( Modulo3Equation e: equations ) s.add( e.copy() );
		return s;
	}
	
	public boolean check( final int solution[] ) {
		for( Modulo3Equation equation: equations ) {
			int sum = 0;
			for( ObjectBidirectionalIterator<Entry> i = equation.variables.int2IntEntrySet().iterator(); i.hasNext(); ) {
				final Entry e = i.next();
				sum = ( sum + solution[ e.getIntKey() ] * e.getIntValue() ) % 3;
			}
			if ( equation.c != sum ) {
				System.err.println( equation + " " + Arrays.toString( solution ));
				return false;
			}
		}
		return true;
	}
}
