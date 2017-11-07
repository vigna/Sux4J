package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Iterator;

import org.junit.Ignore;
import org.junit.Test;

import it.unimi.dsi.bits.TransformationStrategies;

public class HollowTrieDistributorMonotoneMinimalPerfectHashFunctionSlowTest {

	@Ignore("Takes a couple of days")
	@Test
	public void testBig() throws IOException {
		final Iterable<Long> p = LargeLongCollection.getInstance();
		final HollowTrieDistributorMonotoneMinimalPerfectHashFunction<Long> f = new HollowTrieDistributorMonotoneMinimalPerfectHashFunction<>( p, TransformationStrategies.fixedLong() );

		long j = 0;
		for( final Iterator<Long> i = p.iterator(); i.hasNext(); ) {
			final Long s = i.next();
			assertEquals( j++, f.getLong( s ) );
		}
	}
}
