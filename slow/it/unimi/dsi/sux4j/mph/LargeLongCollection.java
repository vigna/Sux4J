/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

package it.unimi.dsi.sux4j.mph;

import java.util.Iterator;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

final class LargeLongCollection implements Iterable<Long>, Size64 {

	public final static long SIZE = 3000000005L; // An odd number is essential to catch problems in the computation of the last bucket.
	private final static long INCREMENT = ((1L << 62) / SIZE);

	private LargeLongCollection() {}
	private final static LargeLongCollection INSTANCE = new LargeLongCollection();

	public static LargeLongCollection getInstance() {
		return INSTANCE;
	}

	@Override
	public Iterator<Long> iterator() {
		return new ObjectIterator<Long>() {
			long curr = 0;
			@Override
			public boolean hasNext() {
				return curr < SIZE;
			}

			@Override
			public Long next() {
				if (! hasNext()) throw new NoSuchElementException();
				return Long.valueOf(curr++ * INCREMENT);
			}
		};
	}

	@Override
	@Deprecated
	public int size() {
		throw new UnsupportedOperationException("You should invoke size64(), only.");
	}

	@Override
	public long size64() {
		return SIZE;
	}
}
