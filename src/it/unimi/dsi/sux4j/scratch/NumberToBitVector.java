/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2022 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.scratch;

import java.math.BigInteger;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategy;

/** A transformation strategy that converts strings representing integers between 0 (inclusive)
 *  and 2<sup><var>k</var></sup> (exclusive)) into fixed-length binary vectors (most-significant
 *  bit is the 0-th).
 */
public class NumberToBitVector implements TransformationStrategy<BigInteger> {
	private static final long serialVersionUID = 1L;
	/** Number of binary digits to be used. */
	private final int width;

	/** Creates a transformation strategy with given number of binary digits.
	 *
	 * @param width number of binary digits;
	 */
	public NumberToBitVector(final int width) {
		this.width = width;
	}

	@Override
	public TransformationStrategy<BigInteger> copy() {
		return new NumberToBitVector(width);
	}

	@Override
	public long numBits() {
		return 0;
	}

	@Override
	public long length(final BigInteger x) {
		return width;
	}

	@Override
	public BitVector toBitVector(final BigInteger x) {
		final LongArrayBitVector res = LongArrayBitVector.getInstance(width);
		for (int i = 0; i < width; i++)
			res.add(x.testBit(width - i - 1));
		return res;
	}

	public static void main(final String arg[]) {
		final NumberToBitVector ntbv = new NumberToBitVector(15);
		System.out.println(ntbv.toBitVector(new BigInteger("567")));
	}

}
