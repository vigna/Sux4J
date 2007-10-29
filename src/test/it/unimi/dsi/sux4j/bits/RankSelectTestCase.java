package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.RankSelect;
import junit.framework.TestCase;

public abstract class RankSelectTestCase extends TestCase {
	public void assertRankAndSelect( RankSelect rs ) {
		final long length = rs.length();
		final LongArrayBitVector bits = LongArrayBitVector.wrap( rs.bits(), rs.length() );
		long lastOne = -1;
		
		for( int j = 0, i = 0; i < length; i++ ) {
			assertEquals( "Ranking " + i, j, rs.rank( i ) );
			if ( bits.getBoolean( i ) ) {
				lastOne = i;
				assertEquals( "Selecting " + j, i, rs.select( j ) );
				j++;
			}
			
		}
		
		assertEquals( lastOne, rs.lastOne() );
		//assertEquals( lastOne, rs.select( rs.rank( rs.length() ) ) );
		//assertEquals( 0, rs.rank( rs.length() ) );
	}
}
