package it.unimi.dsi.sux4j.test;

import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.compression.Decoder;
import it.unimi.dsi.compression.TreeDecoder;
import it.unimi.dsi.fastutil.booleans.BooleanIterator;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.sux4j.bits.JacobsonBalancedParentheses;

import java.io.IOException;
import java.io.Serializable;

public class SuccinctTreeDecoder implements Decoder, Serializable {
	private static final long serialVersionUID = 1L;

	private final JacobsonBalancedParentheses balParen;
	private final LongArrayBitVector bitVector;
	private final boolean returnZero;

	public SuccinctTreeDecoder(TreeDecoder treeDecoder) {
		bitVector = treeDecoder.succinctRepresentation();
		//System.err.println(bitVector);
		//System.err.println(Arrays.toString(treeDecoder.buildCodes()));
		returnZero = bitVector.length() <= 2;
		balParen = new JacobsonBalancedParentheses(bitVector, false, true, false);
	}

	@Override
	public int decode(BooleanIterator iterator) {
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
	public int decode(InputBitStream ibs) throws IOException {
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
