package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.sux4j.scratch.EliasFanoMonotoneLongBigListTables;
import it.unimi.dsi.util.XorShift1024StarRandomGenerator;

import org.junit.Test;

public class EliasFanoMonotoneLongBigListTest {

	@Test
	public void testSmall() {
		LongBigArrayBigList l;

		l = new LongBigArrayBigList( new long[][] { { 0, 1, 2 } } );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );

		l = new LongBigArrayBigList( new long[][] { { 0, 10, 20 } } );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );
	}

	@Test
	public void testMedium() {
		// No skip tables involved
		LongBigArrayBigList l;

		l = new LongBigArrayBigList( Util.identity( 1L << ( EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM ) ) );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );

		for( int i = (int)l.size64(); i-- != 0; ) l.set( i, l.getLong( i ) * 1000 );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );

		l = new LongBigArrayBigList( Util.identity( ( 1L << ( EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM ) ) + 5 ) );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );

		for( int i = (int)l.size64(); i-- != 0; ) l.set( i, l.getLong( i ) * 1000 );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );
	}

	@Test
	public void testLarge() {
		// No skip tables involved
		LongBigArrayBigList l;

		l = new LongBigArrayBigList( Util.identity( 2 * ( 1L << ( EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM ) ) ) );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );

		for( int i = (int)l.size64(); i-- != 0; ) l.set( i, l.getLong( i ) * 1000 );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );

		l = new LongBigArrayBigList( Util.identity( 2 * ( 1L << ( EliasFanoMonotoneLongBigListTables.LOG_2_QUANTUM ) ) + 5 ) );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );

		for( int i = (int)l.size64(); i-- != 0; ) l.set( i, l.getLong( i ) * 1000 );
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );
	}

	@Test
	public void testRandom() {
		// Weird skips
		LongBigArrayBigList l = new LongBigArrayBigList();
		XorShift1024StarRandomGenerator random = new XorShift1024StarRandomGenerator( 0 );
		for( long i = 10000000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros( random.nextLong() );
			l.add( c );
		}
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );
	}
}
