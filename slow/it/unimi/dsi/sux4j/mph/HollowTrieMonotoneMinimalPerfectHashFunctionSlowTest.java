package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;

public class HollowTrieMonotoneMinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() {
		final Iterable<Long> p = LargeLongCollection.getInstance();
		final HollowTrieMonotoneMinimalPerfectHashFunction<Long> f = new HollowTrieMonotoneMinimalPerfectHashFunction<>( p, TransformationStrategies.fixedLong() );

		long j = 0;
		for( final Iterator<Long> i = p.iterator(); i.hasNext(); ) {
			final Long s = i.next();
			assertEquals( j++, f.getLong( s ) );
		}
	}
}
