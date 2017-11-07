package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.sux4j.scratch.EliasFanoMonotoneLongBigListTables;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

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
		final LongBigArrayBigList l = new LongBigArrayBigList();
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom( 0 );
		for( long i = 10000000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros( random.nextLong() );
			l.add( c );
		}
		assertEquals( l, new EliasFanoMonotoneLongBigList( l ) );
	}

	@Test
	public void testBulk() {
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom();
		for( final int base: new int[] { 0, 1, 10 } ) {
			for( final int jump : new int[] { 1, 10, 100 } ) {
				final long[] s = new long[ 100000 ];
				for( int i = 1; i < s.length; i++ ) s[ i ] = s[ i - 1 ] + random.nextInt( jump ) + base;
				final EliasFanoMonotoneLongBigList ef = new EliasFanoMonotoneLongBigList( LongArrayList.wrap( s ) );
				for( int i = 0; i < 1000; i++ ) {
					final int from = random.nextInt( s.length - 100 );
					final int to = from + random.nextInt( 100 );
					final long[] dest = ef.get( from, new long[ to - from ] );
					for( int j = from; j < to; j++ ) assertEquals( s[ j ], dest[ j - from ] );
				}

				for( int i = 0; i < 1000; i++ ) {
					final int from = random.nextInt( s.length - 100 );
					final int to = from + random.nextInt( 100 );
					final int offset = random.nextInt( 10 );
					final long[] dest = ef.get( from, new long[ to - from + offset + random.nextInt( 10 ) ], offset, to - from );
					for( int j = from; j < to; j++ ) assertEquals( "From: " + from + " to: " + to + " j: " + j, s[ j ], dest[ offset + j - from ] );
				}
			}
		}
	}
}