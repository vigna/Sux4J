package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertFalse;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;

import java.io.IOException;
import java.util.Iterator;

import org.junit.Test;

public class MinimalPerfectHashFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		Iterable<Long> p = LargeLongCollection.getInstance();

		final LongArrayBitVector b = LongArrayBitVector.ofLength(LargeLongCollection.SIZE);
		final GOVMinimalPerfectHashFunction<Long> mph = new GOVMinimalPerfectHashFunction.Builder<Long>().keys(p).transform(TransformationStrategies.fixedLong()).build();

		for(Iterator<Long> i = p.iterator(); i.hasNext();) {
			final long pos = mph.getLong(i.next());
			assertFalse(b.getBoolean(pos));
			b.set(pos);
		}
	}
}
