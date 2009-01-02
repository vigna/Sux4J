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

	
	private void recFindNearCloseTest( LongArrayBitVector bv, int e ) {
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
			bv.add( 0 );
			recFindNearCloseTest( bv, e - 2 );
			bv.set( bv.length() - 1 );
			bv.set( bv.length() - 2 );
			recFindNearCloseTest( bv, e + 2 );
			bv.length( bv.length() - 2 );
		}
	}
	
	public void testFindNearCloseRec() {
		LongArrayBitVector bv = LongArrayBitVector.getInstance();
		bv.add( 1 );
		bv.add( 1 );
		recFindNearCloseTest( bv, 2 );
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

		assertEquals( 63, GRRRBalancedParentheses.findNearClose( parseSmall( "(((((((((((((((((((((((((((((((())))))))))))))))))))))))))))))))" ) ) );
}

	private void recFindFarCloseTest( LongArrayBitVector bv ) {
		if ( bv.length() == Long.SIZE ) {
			final long word = bv.getLong( 0, Long.SIZE );
			for( int i = 0; ; i++ ) {
				final int result = GRRRBalancedParentheses.findFarClose( word, Long.SIZE, i );
				if ( result != -1 ) assertEquals( result, GRRRBalancedParentheses.findFarClose( word, i ) );
				else {
					assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( word, i ) );
					break;
				}
			}
			return;
		}

		bv.add( 0 );
		bv.add( 0 );
		recFindFarCloseTest( bv );
		bv.set( bv.length() - 1 );
		bv.set( bv.length() - 2 );
		recFindFarCloseTest( bv );
		bv.length( bv.length() - 2 );
	}
	
	public void testFindFarCloseRec() {
		LongArrayBitVector bv = LongArrayBitVector.getInstance();
		recFindFarCloseTest( bv );
	}
	

	public void testFindFarClose() {
		assertEquals( 2, GRRRBalancedParentheses.findFarClose( parseSmall( "()))(()(", false ), 0 ) );
		assertEquals( 31, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false ), 31 ) );
		assertEquals( 0, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false ), 0 ) );
		assertEquals( 15, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false ), 15 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false ), 32 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false ), 33 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))((((((((((((((((((((((((((((((((", false ), 40 ) );
		assertEquals( 0, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false ), 0 ) );
		assertEquals( 63, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false ), 31 ) );
		assertEquals( 15, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false ), 15 ) );
		assertEquals( 48, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))(((((((((((((((())))))))))))))))))))))))))))))))", false ), 16 ) );

		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "()((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 0 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "()((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 1 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "()((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 2 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 0 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 1 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 2 ) );
		assertEquals( 0, GRRRBalancedParentheses.findFarClose( parseSmall( ")(((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 0 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( ")(((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 1 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( ")(((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 2 ) );
		assertEquals( 0, GRRRBalancedParentheses.findFarClose( parseSmall( "))((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 0 ) );
		assertEquals( 1, GRRRBalancedParentheses.findFarClose( parseSmall( "))((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 1 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((", false ), 2 ) );

		assertEquals( 62, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 61 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 62 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 63 ) );
		assertEquals( 0, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 0 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 1 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 2 ) );
		assertEquals( 0, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 0 ) );
		assertEquals( 1, GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 1 ) );
		assertTrue( Long.SIZE <= GRRRBalancedParentheses.findFarClose( parseSmall( "))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))()", false ), 2 ) );
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
