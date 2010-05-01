package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.TrivialBalancedParentheses;

public class TrivialBalancedParenthesesTest extends BalancedParenthesesTestCase {
	
	public void testSimple() {
		LongArrayBitVector bv = LongArrayBitVector.of( 1, 0 );
		TrivialBalancedParentheses bp = new TrivialBalancedParentheses( bv );
		assertBalancedParentheses( bp );
		assertEquals( 1, bp.findClose( 0 )  );
		assertEquals( 0, bp.findOpen( 1 )  );
		//assertEquals( 0, bp.enclose( 1 ) );
		
		bv = LongArrayBitVector.of( 1, 1, 0, 0 );
		bp = new TrivialBalancedParentheses( bv );
		assertBalancedParentheses( bp );
		assertEquals( 3, bp.findClose( 0 )  );
		//assertEquals( 0, bp.enclose( 1 ) );
		assertEquals( 2, bp.findClose( 1 )  );
		assertEquals( 1, bp.findOpen( 2 )  );
		//assertEquals( 1, bp.enclose( 2 ) );
		assertEquals( 0, bp.findOpen( 3 )  );
		//assertEquals( 1, bp.enclose( 3 ) );

		bv = LongArrayBitVector.of( 1, 1, 0, 1, 0, 0 );
		bp = new TrivialBalancedParentheses( bv );
		assertBalancedParentheses( bp );
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
