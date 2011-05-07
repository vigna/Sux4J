package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Test;

public class MWHCFunctionSlowTest {

	@Test
	public void testBig() throws IOException {
		Iterable<Long> p = new Iterable<Long>() {
			private final static long SIZE = 3000000000L;
			private final static long INCREMENT= ( ( 1L << 62 ) / SIZE );
			@Override
			public Iterator<Long> iterator() {
				return new AbstractObjectIterator<Long>() {
					long curr = 0;
					@Override
					public boolean hasNext() {
						return curr < SIZE;
					}

					@Override
					public Long next() {
						if ( ! hasNext() ) throw new NoSuchElementException();
						return Long.valueOf( curr++ * INCREMENT );
					}
				};
			}
		};
		
		final MWHCFunction<Long> f = new MWHCFunction<Long>( p, TransformationStrategies.fixedLong() );
				
		long j = 0;
		for( Iterator<Long> i = p.iterator(); i.hasNext(); ) {
			Long s = i.next();
			assertEquals( j++, f.getLong( s ) );
		}
	}
}
