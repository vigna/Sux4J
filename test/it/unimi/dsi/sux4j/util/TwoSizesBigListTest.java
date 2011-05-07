package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.longs.LongBigList;

import org.junit.Test;

public class TwoSizesBigListTest {
	@Test
	public void testConstruction() {
		LongBigList l = LongArrayBitVector.getInstance().asLongBigList( 10 );
		for ( int i = 0; i < 1024; i++ )
			l.add( i );
		TwoSizesLongBigList ts = new TwoSizesLongBigList( l );
		assertEquals( ts, l );

		l.clear();
		for ( int i = 0; i < 512; i++ )
			l.add( 2 );
		for ( int i = 0; i < 512; i++ )
			l.add( i );
		ts = new TwoSizesLongBigList( l );
		assertEquals( ts, l );
	}
}
