package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;

import org.junit.Test;

public class EliasFanoPrefixSumLongBigListTest {

	@Test
	public void testSmall() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList( new long[][] { { 0, 0, 0 } } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );

		l = new LongBigArrayBigList( new long[][] { { 0, 1, 0 } } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );

		l = new LongBigArrayBigList( new long[][] { { 1, 1, 1 } } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );

		l = new LongBigArrayBigList( new long[][] { { 4, 3, 2 } } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );

		l = new LongBigArrayBigList( new long[][] { { 128, 2000, 50000000, 200, 10 } } );
		assertEquals( l, new EliasFanoPrefixSumLongBigList( l ) );
	}
}
