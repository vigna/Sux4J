package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.RankSelect;
import it.unimi.dsi.sux4j.bits.Select;
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
	}

	public void assertSelect( Select s ) {
		final long length = s.length();
		final LongArrayBitVector bits = LongArrayBitVector.wrap( s.bits(), s.length() );
		
		for( int j = 0, i = 0; i < length; i++ ) {
			if ( bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, s.select( j ) );
				j++;
			}
			
		}
	}
}
