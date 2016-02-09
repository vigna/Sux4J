package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.longs.AbstractLongBigList;

import java.io.IOException;
import java.util.Iterator;

import org.junit.Test;

public class GOV3FunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		Iterable<Long> p = LargeLongCollection.getInstance();		
		final GOV3Function<Long> f = new GOV3Function.Builder<Long>().keys( p ).transform( TransformationStrategies.fixedLong() ).values( new AbstractLongBigList() {

			@Override
			public long getLong( long index ) {
				return index % 7;
			}

			@Override
			public long size64() {
				return LargeLongCollection.SIZE;
			}
		}, 3 ).build();
				
		long j = 0;
		for( Iterator<Long> i = p.iterator(); i.hasNext(); ) {
			Long s = i.next();
			assertEquals( j++ % 7, f.getLong( s ) );
		}
	}
}
