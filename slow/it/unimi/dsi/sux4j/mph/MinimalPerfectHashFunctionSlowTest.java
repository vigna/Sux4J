package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertTrue;
import it.unimi.dsi.bits.TransformationStrategies;

import java.io.IOException;
import java.util.Iterator;

import org.junit.Test;

public class MinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		Iterable<Long> p = LargeLongCollection.getInstance();
		
		final MinimalPerfectHashFunction<Long> mph = new MinimalPerfectHashFunction<Long>( p, TransformationStrategies.fixedLong() );
				
		for( Iterator<Long> i = p.iterator(); i.hasNext(); ) {
			Long s = i.next();
			assertTrue( mph.getLong( s ) >= 0 );
		}
	}
}
