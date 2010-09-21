package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.lang.MutableString;
import junit.framework.TestCase;

public abstract class BalancedParenthesesTestCase extends TestCase {

	public static String binary( long l, boolean reverse ) {
		if ( reverse ) l = Long.reverse( l );
		MutableString s = new MutableString().append( "0000000000000000000000000000000000000000000000000000000000000000000000000" ).append( Long.toBinaryString( l ) );
		s.delete( 0, s.length() - 64 );
		s.insert( 0, '\n' );
		s.append( '\n' );
		for( int i = 0; i < 32; i++ ) s.append( " " ).append( Long.toHexString( ( l >>> ( 31 - i ) * 2 ) & 0x3 ) );
		s.append( '\n' );
		for( int i = 0; i < 16; i++ ) s.append( "   " ).append( Long.toHexString( ( l >>> ( 15 - i ) * 4 ) & 0xF ) );
		s.append( '\n' );
		return s.toString();
	}


	public static LongArrayBitVector parse( String s, boolean check ) {
		int e = 0;
		LongArrayBitVector bv = LongArrayBitVector.getInstance();
		for( int i = 0; i < s.length(); i++ ) {
			if ( s.charAt( i ) == '(' ) {
				bv.add( 1 );
				e++;
			}
			else {
				if ( check && e == 0 ) throw new IllegalArgumentException();
				bv.add( 0 );
				e--;
			}
		}

		if ( check && e != 0 ) throw new IllegalArgumentException();
		
		return bv;
	}

	
	public static long parseSmall( String s, boolean check ) {
		if ( s.length() > Long.SIZE ) throw new IllegalArgumentException();
		LongArrayBitVector bv = parse( s, check );
		return bv.getLong( 0, s.length() );
	}

	public static long parseSmall( String s ) {
		return parseSmall( s, true );
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
