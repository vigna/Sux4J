package test.it.unimi.dsi.sux4j.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.util.EliasFanoPrefixSumLongBigList;
import junit.framework.TestCase;

public class EliasFanoPrefixSumLongBigListTest extends TestCase {

	public void testSmall() {
		LongArrayList l;
		l = new LongArrayList( new long[] { 0, 0, 0 } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );
		
		l = new LongArrayList( new long[] { 0, 1, 0 } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );

		l = new LongArrayList( new long[] { 1, 1, 1 } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );
		
		l = new LongArrayList( new long[] { 4, 3, 2 } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );

		l = new LongArrayList( new long[] { 128, 2000, 50000000, 200, 10 } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );
	}
}
