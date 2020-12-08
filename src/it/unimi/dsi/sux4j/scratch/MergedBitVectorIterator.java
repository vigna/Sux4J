/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2020 Sebastiano Vigna
 *
 * This program and the accompanying materials are made available under the
 * terms of the GNU Lesser General Public License v2.1 or later,
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html,
 * or the Apache Software License 2.0, which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later OR Apache-2.0
 */

/** An iterator returning the union of the bit vectors returned by two iterators.
 *  The two iterators must return bit vectors in an increasing fashion; the resulting
 *  {@link MergedBitVectorIterator} will do the same. Duplicates will be eliminated.
 */

package it.unimi.dsi.sux4j.scratch;

import java.util.Iterator;
import java.util.NoSuchElementException;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

public class MergedBitVectorIterator implements ObjectIterator<BitVector> {
	/** The first component iterator. */
	private final Iterator<? extends BitVector> it0;
	/** The second component iterator. */
	private final Iterator<? extends BitVector> it1;
	/** The last bit vector returned by {@link #it0}. */
	private BitVector curr0;
	/** The last bit vector returned by {@link #it1}. */
	private BitVector curr1;
	/** The result. */
	private final LongArrayBitVector result;

	/** Creates a new merged iterator by merging two given iterators.
	 *
	 * @param it0 the first (monotonically nondecreasing) component iterator.
	 * @param it1 the second (monotonically nondecreasing) component iterator.
	 */
	public MergedBitVectorIterator(final Iterator<? extends BitVector> it0, final Iterator<? extends BitVector> it1) {
		this.it0 = it0;
		this.it1 = it1;
		result = LongArrayBitVector.getInstance();
		if (it0.hasNext()) curr0 = it0.next();
		if (it1.hasNext()) curr1 = it1.next();
	}

	@Override
	public boolean hasNext() {
		return curr0 != null || curr1 != null;
	}

	@Override
	public BitVector next() {
		if (! hasNext()) throw new NoSuchElementException();

		final int cmp;

		if (curr0 == null) {
			result.replace(curr1);
			curr1 = it1.hasNext() ? it1.next() : null;
		}
		else if (curr1 == null) {
			result.replace(curr0);
			curr0 = it0.hasNext() ? it0.next() : null;
		}
		else if ((cmp = curr0.compareTo(curr1)) < 0) {
			result.replace(curr0);
			curr0 = it0.hasNext() ? it0.next() : null;
		}
		else if (cmp > 0) {
			result.replace(curr1);
			curr1 = it1.hasNext() ? it1.next() : null;
		}
		else {
			result.replace(curr1);
			curr0 = it0.hasNext() ? it0.next() : null;
			curr1 = it1.hasNext() ? it1.next() : null;
		}

		return result;
	}
}
