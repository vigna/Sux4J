package it.unimi.dsi.sux4j.util;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.util.TwoSizesLongBigList;
import it.unimi.dsi.util.LongBigList;
import junit.framework.TestCase;

public class TwoSizesBigListTest extends TestCase {
	public void testConstruction() {
		LongBigList l = LongArrayBitVector.getInstance().asLongBigList( 10 );
		for( int i = 0; i < 1024; i++ ) l.add( i );
		TwoSizesLongBigList ts = new TwoSizesLongBigList( l );
		assertEquals( ts, l );

		l.clear();
		for( int i = 0; i < 512; i++ ) l.add( 2 );
		for( int i = 0; i < 512; i++ ) l.add( i );
		ts = new TwoSizesLongBigList( l );
		assertEquals( ts, l );
	}
}
