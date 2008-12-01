package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.sux4j.bits.BalancedParentheses;
import junit.framework.TestCase;

public abstract class BalancedParenthesesTestCase extends TestCase {
	
	public static LongArrayBitVector parse( String s ) {
		int e = 0;
		LongArrayBitVector bv = LongArrayBitVector.getInstance();
		for( int i = 0; i < s.length(); i++ ) {
			if ( s.charAt( i ) == '(' ) {
				bv.add( 1 );
				e++;
			}
			else {
				if ( e == 0 ) throw new IllegalArgumentException();
				bv.add( 0 );
				e--;
			}
		}

		if ( e != 0 ) throw new IllegalArgumentException();
		
		return bv;
	}

	
	public static long parseSmall( String s ) {
		if ( s.length() > Long.SIZE ) throw new IllegalArgumentException();
		LongArrayBitVector bv = parse( s );
		return bv.getLong( 0, s.length() );
	}

	public void assertBalancedParentheses( BalancedParentheses balancedParentheses ) {
		final long length = balancedParentheses.bitVector().length();
		final BitVector bits = balancedParentheses.bitVector();
		
		// Build matching
		
		IntArrayList stack = new IntArrayList();
		IntArrayList matches  = new IntArrayList();
		matches.size( (int)length );
		
		for( int i = 0; i < length; i++ ) {
			if ( bits.getBoolean( i ) ) stack.push( i );
			else {
				if ( stack.isEmpty() ) throw new AssertionError( "The bit vector does not represent a correctly parenthesised string");
				final int pos = stack.popInt(); 
				matches.set( pos, i );
				matches.set( i, pos );
			}
		}
		
		if ( ! stack.isEmpty() ) throw new AssertionError( "The bit vector does not represent a correctly parenthesised string");
		
		for( int i = 0; i < length; i++ ) {
			//System.err.println( i);
			if ( bits.getBoolean( i ) ) assertEquals( "Finding closing for position " + i, matches.getInt( i ), balancedParentheses.findClose( i ) );
			//else assertEquals( "Finding opening for position " + i, matches.getInt( i ), balancedParentheses.findOpen( i ) );
		}
	}

}
