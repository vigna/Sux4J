package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.TransformationStrategies;

import java.io.IOException;
import java.util.Iterator;

import org.junit.Test;

public class LcpMonotoneMinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		Iterable<Long> p = LargeLongCollection.getInstance();
		final LcpMonotoneMinimalPerfectHashFunction<Long> f = new LcpMonotoneMinimalPerfectHashFunction.Builder<Long>().keys(p).transform(TransformationStrategies.fixedLong()).build();

		long j = 0;
		for(Iterator<Long> i = p.iterator(); i.hasNext();) {
			Long s = i.next();
			assertEquals(j++, f.getLong(s));
		}
	}
}
