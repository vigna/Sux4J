package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.objects.AbstractObjectIterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

final class LargeLongCollection implements Iterable<Long>, Size64 {
	
	public final static long SIZE = 3000000005L; // An odd number is essential to catch problems in the computation of the last bucket.
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

	@Override
	public int size() {
		throw new UnsupportedOperationException( "You should invoke size64(), only." );
	}

	@Override
	public long size64() {
		return SIZE;
	}
}
