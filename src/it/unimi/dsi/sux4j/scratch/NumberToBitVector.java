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
