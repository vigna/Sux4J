package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.Rank;
import it.unimi.dsi.sux4j.bits.Select;
import junit.framework.TestCase;

public abstract class RankSelectTestCase extends TestCase {
	public void assertRankAndSelect( Rank rank, Select select ) {
		final long length = rank.length();
		final LongArrayBitVector bits = LongArrayBitVector.wrap( rank.bits(), rank.length() );
		
		for( int j = 0, i = 0; i < length; i++ ) {
			assertEquals( "Ranking " + i, j, rank.rank( i ) );
			if ( bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, select.select( j ) );
				j++;
			}
			
		}
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
