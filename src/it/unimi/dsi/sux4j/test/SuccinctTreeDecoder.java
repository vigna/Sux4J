/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2020 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.test;

import java.io.IOException;
import java.io.Serializable;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.compression.Decoder;
import it.unimi.dsi.compression.TreeDecoder;
import it.unimi.dsi.fastutil.booleans.BooleanIterator;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.sux4j.bits.JacobsonBalancedParentheses;

public class SuccinctTreeDecoder implements Decoder, Serializable {
	private static final long serialVersionUID = 1L;

	private final JacobsonBalancedParentheses balParen;
	private final LongArrayBitVector bitVector;
	private final boolean returnZero;

	public SuccinctTreeDecoder(final TreeDecoder treeDecoder) {
		bitVector = treeDecoder.succinctRepresentation();
		//System.err.println(bitVector);
		//System.err.println(Arrays.toString(treeDecoder.buildCodes()));
		returnZero = bitVector.length() <= 2;
		balParen = new JacobsonBalancedParentheses(bitVector, false, true, false);
	}

	@Override
	public int decode(final BooleanIterator iterator) {
		if (returnZero) return 0;
		int p = 1, index = 0;

		for(;;) {
			if (iterator.nextBoolean()) {
				final int q = (int)(balParen.findClose(p) + 1);
				index += (q - p) / 2;
				if (! bitVector.getBoolean(q)) return index;
				p = q;
			}
			else if (! bitVector.getBoolean(++p)) return index;
		}
	}

	@Override
	public int decode(final InputBitStream ibs) throws IOException {
		if (returnZero) return 0;
		int p = 1, index = 0;

		for(;;) {
			if (ibs.readBit() != 0) {
				final int q = (int)(balParen.findClose(p) + 1);
				index += (q - p) / 2;
				if (! bitVector.getBoolean(q)) return index;
				p = q;
			}
			else if (! bitVector.getBoolean(++p)) return index;
		}
	}
}
