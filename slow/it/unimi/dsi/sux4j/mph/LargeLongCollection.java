package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

final class LargeLongCollection implements Iterable<Long> {
	
	public final static long SIZE = 3000000000L;
	private final static long INCREMENT = ( ( 1L << 62 ) / SIZE );

	private LargeLongCollection() {}
	private final static LargeLongCollection INSTANCE = new LargeLongCollection();
	
	public static LargeLongCollection getInstance() {
		return INSTANCE;
	}
	
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
}
