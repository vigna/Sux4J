package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.GRRRBalancedParentheses;

public class GRRRBalancedParenthesesTest extends BalancedParenthesesTestCase {
	
	public void testCountFarOpen() {
		
		assertEquals( 0, GRRRBalancedParentheses.countFarOpen( 0x5555555555555555L, Long.SIZE ) );
		assertEquals( 1, GRRRBalancedParentheses.countFarOpen( 0xAAAAAAAAAAAAAAAAL, Long.SIZE ) );
		assertEquals( 4, GRRRBalancedParentheses.countFarOpen( Long.parseLong( "110111", 2 ), 6 ) );
		assertEquals( 6, GRRRBalancedParentheses.countFarOpen( Long.parseLong( "111111", 2 ), 6 ) );
		assertEquals( 4, GRRRBalancedParentheses.countFarOpen( Long.parseLong( "101111", 2 ), 6 ) );
		assertEquals( 2, GRRRBalancedParentheses.countFarOpen( Long.parseLong( "001111", 2 ), 6 ) );
		
		assertEquals( 0, GRRRBalancedParentheses.countFarClose( 0x5555555555555555L, Long.SIZE ) );
		assertEquals( 1, GRRRBalancedParentheses.countFarClose( 0xAAAAAAAAAAAAAAAAL, Long.SIZE ) );
		assertEquals( 4, GRRRBalancedParentheses.countFarClose( ~Long.parseLong( "110111", 2 ), 6 ) );
		assertEquals( 6, GRRRBalancedParentheses.countFarClose( ~Long.parseLong( "111111", 2 ), 6 ) );
		assertEquals( 4, GRRRBalancedParentheses.countFarClose( ~Long.parseLong( "101111", 2 ), 6 ) );
		assertEquals( 4, GRRRBalancedParentheses.countFarClose( ~Long.parseLong( "001111", 2 ), 6 ) );
	}

	
	private void recTest( LongArrayBitVector bv, int e ) {
		if ( e == 0 ) {
			assertEquals( bv.toString(), bv.length() - 1, GRRRBalancedParentheses.findNearClose( bv.getLong( 0, bv.length() ) ) );
			return;
		}
		if ( bv.length() + e == Long.SIZE ) {
			assertEquals( 63, GRRRBalancedParentheses.findNearClose( bv.getLong( 0, bv.length() ) ) );
			return;
		}
		
		if ( e > 0 ) {
			bv.add( 0 );
			recTest( bv, e - 1 );
			bv.set( bv.length() - 1 );
			recTest( bv, e + 1 );
			bv.length( bv.length() - 1 );
		}
	}
	
	public void testFindNearCloseRec() {
		LongArrayBitVector bv = LongArrayBitVector.getInstance();
		bv.add( 1 );
		recTest( bv, 1 );
	}
	
	public void testFindNearClose() {
		assertEquals( 1, GRRRBalancedParentheses.findNearClose( parseSmall( "()" ) ) );

		assertEquals( 3, GRRRBalancedParentheses.findNearClose( parseSmall( "(())" ) ) );

		assertEquals( 5, GRRRBalancedParentheses.findNearClose( parseSmall( "(()())" ) ) );

		assertEquals( 7, GRRRBalancedParentheses.findNearClose( parseSmall( "(((())))" ) ) );
		assertEquals( 7, GRRRBalancedParentheses.findNearClose( parseSmall( "(()()())" ) ) );
		assertEquals( 7, GRRRBalancedParentheses.findNearClose( parseSmall( "(()(()))" ) ) );
		
		assertEquals( 9, GRRRBalancedParentheses.findNearClose( parseSmall( "((((()))))" ) ) );
		assertEquals( 11, GRRRBalancedParentheses.findNearClose( parseSmall( "(((((())))))" ) ) );
		assertEquals( 13, GRRRBalancedParentheses.findNearClose( parseSmall( "((((((()))))))" ) ) );
		assertEquals( 15, GRRRBalancedParentheses.findNearClose( parseSmall( "(((((((())))))))" ) ) );
		
		assertEquals( 9, GRRRBalancedParentheses.findNearClose( parseSmall(  "(((()())))" ) ) );
		assertEquals( 11, GRRRBalancedParentheses.findNearClose( parseSmall( "((((()()))))" ) ) );
		assertEquals( 13, GRRRBalancedParentheses.findNearClose( parseSmall( "(((((()())))))" ) ) );
		assertEquals( 15, GRRRBalancedParentheses.findNearClose( parseSmall( "((((((()()))))))" ) ) );

		assertEquals( 9, GRRRBalancedParentheses.findNearClose( parseSmall(  "(()()()())" ) ) );
		assertEquals( 11, GRRRBalancedParentheses.findNearClose( parseSmall( "(()()()()())" ) ) );
		assertEquals( 13, GRRRBalancedParentheses.findNearClose( parseSmall( "(()()()()()())" ) ) );
		assertEquals( 15, GRRRBalancedParentheses.findNearClose( parseSmall( "(()()()()()()())" ) ) );

		assertEquals( 17, GRRRBalancedParentheses.findNearClose( parseSmall( "((((((((()))))))))" ) ) );
		assertEquals( 19, GRRRBalancedParentheses.findNearClose( parseSmall( "(((((((((())))))))))" ) ) );
		assertEquals( 21, GRRRBalancedParentheses.findNearClose( parseSmall( "((((((((((()))))))))))" ) ) );
		assertEquals( 23, GRRRBalancedParentheses.findNearClose( parseSmall( "(((((((((((())))))))))))" ) ) );
		assertEquals( 25, GRRRBalancedParentheses.findNearClose( parseSmall( "((((((((((((()))))))))))))" ) ) );
		assertEquals( 27, GRRRBalancedParentheses.findNearClose( parseSmall( "(((((((((((((())))))))))))))" ) ) );
		assertEquals( 29, GRRRBalancedParentheses.findNearClose( parseSmall( "((((((((((((((()))))))))))))))" ) ) );
		assertEquals( 31, GRRRBalancedParentheses.findNearClose( parseSmall( "((((((((((((((()()))))))))))))))" ) ) );
	}
	
	public void testLong() {
		GRRRBalancedParentheses bp = new GRRRBalancedParentheses( new long[] { -1, -1, 0, 0 }, Long.SIZE * 4 );
		assertBalancedParentheses( bp );

		bp = new GRRRBalancedParentheses( new long[] { -1, 0xFFFFFFFFL, 0xFFFFFFFFL, 0 }, Long.SIZE * 4 );
		assertBalancedParentheses( bp );

		bp = new GRRRBalancedParentheses( new long[] { -1, 0xFFFFFFL, 0xFFFFFFFFFFL, 0 }, Long.SIZE * 4 );
		assertBalancedParentheses( bp );

		bp = new GRRRBalancedParentheses( new long[] { 0xFFFFFFFFFFFFFFL, 0xFFFFFFL, 0xFFFFFFFFFFL, 0xFF00L }, Long.SIZE * 4 );
		assertBalancedParentheses( bp );
	}
		
	public void notestSimple() {
		LongArrayBitVector bv = LongArrayBitVector.of( 1, 0 );
		GRRRBalancedParentheses bp = new GRRRBalancedParentheses( bv );
		assertEquals( 1, bp.findClose( 0 )  );
		assertEquals( 0, bp.findOpen( 1 )  );
		//assertEquals( 0, bp.enclose( 1 ) );
		
		bv = LongArrayBitVector.of( 1, 1, 0, 0 );
		bp = new GRRRBalancedParentheses( bv );
		assertEquals( 3, bp.findClose( 0 )  );
		//assertEquals( 0, bp.enclose( 1 ) );
		assertEquals( 2, bp.findClose( 1 )  );
		assertEquals( 1, bp.findOpen( 2 )  );
		//assertEquals( 1, bp.enclose( 2 ) );
		assertEquals( 0, bp.findOpen( 3 )  );
		//assertEquals( 1, bp.enclose( 3 ) );

		bv = LongArrayBitVector.of( 1, 1, 0, 1, 0, 0 );
		bp = new GRRRBalancedParentheses( bv );
		assertEquals( 5, bp.findClose( 0 )  );
		assertEquals( 2, bp.findClose( 1 )  );
		//assertEquals( 0, bp.enclose( 1 ) );
		assertEquals( 1, bp.findOpen( 2 )  );
		//assertEquals( 1, bp.enclose( 2 ) );
		assertEquals( 4, bp.findClose( 3 )  );
		//assertEquals( 1, bp.enclose( 3 ) );
		assertEquals( 3, bp.findOpen( 4 )  );
		//assertEquals( 3, bp.enclose( 4 ) );
		assertEquals( 0, bp.findOpen( 5 )  );
		//assertEquals( 3, bp.enclose( 5 ) );

	}
		
}
