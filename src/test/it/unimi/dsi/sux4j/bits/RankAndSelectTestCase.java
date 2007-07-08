package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.RankAndSelect;
import junit.framework.TestCase;

public class RankAndSelectTestCase extends TestCase {
	public void assertRankAndSelect( RankAndSelect rs ) {
		final long length = rs.length();
		final LongArrayBitVector bits = LongArrayBitVector.wrap( rs.bits(), rs.length() );
		long lastOne = -1;
		
		for( int j = 0, i = 0; i < length; i++ ) {
			if ( bits.getBoolean( i ) ) {
				j++;
				lastOne = i;
				assertEquals( "Selecting " + j, i, rs.select( j ) );
			}
			assertEquals( "Ranking " + i, j, rs.rank( i ) );
			
		}
		
		assertEquals( lastOne, rs.lastOne() );
		assertEquals( -1, rs.select( 0 ) );
		assertEquals( 0, rs.rank( -1 ) );
	}
}
