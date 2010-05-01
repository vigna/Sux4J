package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.sux4j.bits.Rank;
import it.unimi.dsi.sux4j.bits.Select;
import it.unimi.dsi.sux4j.bits.SelectZero;
import junit.framework.TestCase;

public abstract class RankSelectTestCase extends TestCase {
	public void assertRankAndSelect( Rank rank, Select select ) {
		final long length = rank.bitVector().length();
		final BitVector bits = rank.bitVector();

		for( int j = 0, i = 0; i < length; i++ ) {
			assertEquals( "Ranking " + i, j, rank.rank( i ) );
			if ( bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, select.select( j ) );
				j++;
			}
			
		}
	}

	public void assertSelect( Select s ) {
		final BitVector bits = s.bitVector();
		final long length = bits.length();
		
		for( int j = 0, i = 0; i < length; i++ ) {
			if ( bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, s.select( j ) );
				j++;
			}
			
		}
	}

	public void assertSelectZero( SelectZero s ) {
		final BitVector bits = s.bitVector();
		final long length = bits.length();
		
		for( int j = 0, i = 0; i < length; i++ ) {
			if ( ! bits.getBoolean( i ) ) {
				assertEquals( "Selecting " + j, i, s.selectZero( j ) );
				j++;
			}
			
		}
	}

	public void assertRank( Rank rank ) {
		final long length = rank.bitVector().length();
		final BitVector bits = rank.bitVector();
		
		for( int j = 0, i = 0; i < length; i++ ) {
			assertEquals( "Ranking " + i, j, rank.rank( i ) );
			if ( bits.getBoolean( i ) ) j++;
		}
	}

}
