package it.unimi.dsi.sux4j.mph.codec;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongMaps;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.sux4j.mph.codec.Codec.Binary;
import it.unimi.dsi.sux4j.mph.codec.Codec.Coder;
import it.unimi.dsi.sux4j.mph.codec.Codec.Decoder;
import it.unimi.dsi.sux4j.mph.codec.Codec.Huffman;

public class CodecTest {

	@Test
	public void testGamma() {
		final Codec.Gamma gamma = new Codec.Gamma();
		final Long2LongOpenHashMap frequencies = new Long2LongOpenHashMap(new long[] { 6, 9, 1, 2, 4, 5, 3, 4, 7, 10000000 }, new long[] { 64, 32, 16, 1, 8, 4, 20, 2, 1, 10 });
		final Coder coder = gamma.getCoder(frequencies);
		final Decoder decoder = coder.getDecoder();
		for (int i = 0; i < 10000000; i++) {
			final long encoded = coder.encode(i);
			final long longEncoded = Long.reverse(encoded) >>> 64 - coder.maxCodewordLength();
			final long decoded = decoder.decode(longEncoded);
			assertEquals(i, decoded);
		}
	}

	@Test
	public void testHuffman() {
		final Long2LongOpenHashMap frequencies = new Long2LongOpenHashMap(new long[] { 6, 9, 1, 2, 4, 5, 3, 4, 7, 1000 }, new long[] { 64, 32, 16, 1, 8, 4, 20, 2, 1, 10 });
		final Huffman huffman = new Codec.Huffman();
		final Coder coder = huffman.getCoder(frequencies);
		final Decoder decoder = coder.getDecoder();
		for (final long l: frequencies.keySet()) {
			final long encoded = coder.encode(l);
			final long longEncoded = Long.reverse(encoded) >>> 64 - coder.maxCodewordLength();
			final long decoded = decoder.decode(longEncoded);
			assertEquals(l, decoded);
		}
	}

	@Test
	public void testLengthLimitedLengthHuffman() {
		final Long2LongOpenHashMap frequencies = new Long2LongOpenHashMap(new long[] { 6, 9, 1, 2, 4, 5, 3, 4, 7, 1000 }, new long[] { 64, 32, 16, 1, 8, 4, 20, 2, 1, 10 });
		final Huffman huffman = new Codec.Huffman(3);
		final Coder coder = huffman.getCoder(frequencies);
		final Decoder decoder = coder.getDecoder();
		int maxLengthEscaped = 0;
		for (final long l: frequencies.keySet()) {
			final long encoded = coder.encode(l);
			if (encoded == -1) maxLengthEscaped = Math.max(maxLengthEscaped, Fast.length(l));
			else {
				final long longEncoded = Long.reverse(encoded) >>> 64 - coder.maxCodewordLength();
				final long decoded = decoder.decode(longEncoded);
				assertEquals(l, decoded);
			}
		}

		assertEquals(maxLengthEscaped, coder.escapedSymbolLength());
	}

	@Test
	public void testLengthLimitedHuffManGeom() {
		final int size = 20;
		final long[] symbols = new long[size];
		final long[] frequency = new long[size];
		for (int i = 0; i < size; i++) {
			symbols[i] = i;
			frequency[i] = 1 << i;
		}

		for(int t = 0; t < 5; t++) {
			final Huffman huffman = new Codec.Huffman(t);
			final Long2LongOpenHashMap frequencies = new Long2LongOpenHashMap(symbols, frequency);
			final Coder coder = huffman.getCoder(frequencies);
			final Decoder decoder = coder.getDecoder();
			int maxLengthEscaped = 0;
			for (final long l: frequencies.keySet()) {
				final long encoded = coder.encode(l);
				if (encoded == -1) maxLengthEscaped = Math.max(maxLengthEscaped, Fast.length(l));
				else {
					final long longEncoded = Long.reverse(encoded) >>> 64 - coder.maxCodewordLength();
			final long decoded = decoder.decode(longEncoded);
			assertEquals(l, decoded);
				}
			}

			assertEquals(maxLengthEscaped, coder.escapedSymbolLength());
		}
	}

	@Test
	public void testUnary() {
		final Codec.Unary unary = new Codec.Unary();
		final Long2LongOpenHashMap frequencies = new Long2LongOpenHashMap(new long[] { 6, 9, 1, 2, 4, 5, 3, 4, 7, 61 }, new long[] { 64, 32, 16, 1, 8, 4, 10, 2, 1, 1 });
		final Coder coder = unary.getCoder(frequencies);
		final Decoder decoder = coder.getDecoder();
		for (int i = 0; i < 62; i++) {
			final long encoded = coder.encode(i);
			final long longEncoded = Long.reverse(encoded) >>> 64 - coder.maxCodewordLength();
			final long decoded = decoder.decode(longEncoded);
			assertEquals(i, decoded);
		}
	}

	@Test
	public void testBinary() {
		final Binary binary = new Codec.Binary();
		final Long2LongOpenHashMap frequencies = new Long2LongOpenHashMap(new long[] { 6, 9, 1, 2, 4, 5, 3, 4, 7, 1000 }, new long[] { 64, 32, 16, 1, 8, 4, 10, 2, 1, 1 });
		final Coder coder = binary.getCoder(frequencies);
		final Decoder decoder = coder.getDecoder();
		for (int i = 0; i <= 1000; i++) {
			final long encoded = coder.encode(i);
			final long decoded = decoder.decode(Long.reverse(encoded) >>> 64 - coder.maxCodewordLength());
			assertEquals(i, decoded);
		}
	}

	@Test
	public void testEmpty() {
		Codec.Huffman huffman = new Codec.Huffman();
		final Long2LongMap frequencies = Long2LongMaps.EMPTY_MAP;
		Coder coder = huffman.getCoder(frequencies);
		Decoder decoder = coder.getDecoder();
		assertEquals(0, decoder.escapeLength());

		huffman = new Codec.Huffman(1);
		coder = huffman.getCoder(frequencies);
		decoder = coder.getDecoder();
		assertEquals(0, decoder.escapeLength());
	}

	@Test
	public void testSingleton() {
		Codec.Huffman huffman = new Codec.Huffman();
		final Long2LongMap frequencies = new Long2LongOpenHashMap(new long[] { 0 }, new long[] { 1 });
		Coder coder = huffman.getCoder(frequencies);
		Decoder decoder = coder.getDecoder();
		assertEquals(1, coder.codewordLength(0));
		assertEquals(1, decoder.escapeLength());

		huffman = new Codec.Huffman(0);
		coder = huffman.getCoder(frequencies);
		decoder = coder.getDecoder();
		assertEquals(1, coder.codewordLength(0));
		assertEquals(1, decoder.escapeLength());
	}
}

