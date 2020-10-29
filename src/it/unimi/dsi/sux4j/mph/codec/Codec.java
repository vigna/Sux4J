/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2017-2020 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.mph.codec;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.google.common.primitives.Longs;

import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntMaps;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.sux4j.mph.GV3CompressedFunction;

/** A class representing a specific instantaneous code for {@linkplain GV3CompressedFunction compressed functions}.
 * The logic of this code is quite tightly coupled with such functions, and it is unlikely to be resusable elsewhere easily.
 *
 * @author Sebastiano Vigna
 * @author Marco Genuzio
 */
public interface Codec {
	/** A coder: provides methods to turn symbols into codewords. */
	public interface Coder {
		/** Returns the codeword associated with a symbol, or &minus;1 if the provided symbol should be escaped.
		 *
		 * <p>If a symbol needs to be escaped, it must be encoded using
		 * the {@linkplain #escape() escape codeword} followed by
		 * the symbol written in a field of {@link #escapedSymbolLength()} bits.
		 *
		 * @param symbol a symbol.
		 * @return the associated codeword.
		 */
		long encode(long symbol);

		/** Returns the length of the codeword associated with the given symbol.
		 *
		 * <p>For escaped symbols, the returned values is the length of
		 * the escape codeword plus {@link #escapedSymbolLength()}.
		 *
		 * @param symbol a symbol provided at construction time.
		 * @return the length of the codeword associated with the given symbol.
		 */
		int codewordLength(long symbol);

		/** Returns the maximum length of a codeword (including escaped symbols).
		 *
		 * @return the maximum length of a codeword (including escaped symbols).
		 */
		int maxCodewordLength();

		/** Returns the length in bit of an escaped symbol, or zero if there are no escaped symbols.
		 *
		 * @return the length in bit of an escaped symbol, or zero if there are no escaped symbols.
		 */
		default int escapedSymbolLength() {
			return 0;
		}

		/** Returns the escape codeword, if it exists.
		 *
		 * <p>Note that the length of the escape codeword can be recovered
		 * by subtracting from the {@linkplain #codewordLength(long) length of the codeword of an escaped symbol}
		 * the {@linkplain #escapedSymbolLength() length of an escaped symbol}.
		 * @return the escape codeword, if it exists.
		 */
		default long escape() {
			return 0;
		}

		/** Return a decoder associated with this coder.
		 *
		 * @return a decoder associated with this coder.
		 */
		Decoder getDecoder();
	}

	/** A decoder: provides a {@linkplain #decode(long) method} to turn sequences of bits into symbols.
	 * <p>Note that a decoder can only built using {@link Coder#getDecoder()}.
	 */
	public interface Decoder extends Serializable {
		/** Decodes a sequence of bits.
		 *
		 * <p>If the first codeword appearing in the sequence is the
		 * {@linkplain Coder#escape() escape codeword}, this method returns &minus;1 and the actual
		 * symbol must be retrieved by reading {@link #escapedSymbolLength()}
		 * further bits.
		 *
		 * <p>This method assumes that the first
		 * bit of the code is the leftmost bit (i.e., the bit of index
		 * {@link Coder#maxCodewordLength()} &minus; 1).
		 *
		 * @param sequence a sequence of bits.
		 * @return the symbol associated with the first codeword appearing in the sequence,
		 * or &minus;1 if the codeword is an escape.
		 */
		long decode(long sequence);

		/** The number of bits used by this decoder.
		 *
		 * @return the number of bits used by this decoder.
		 */
		long numBits();

		/** Returns the length in bit of an escaped symbol, or zero if there are no escaped symbols.
		 *
		 * @return the length in bit of an escaped symbol, or zero if there are no escaped symbols.
		 */
		default int escapedSymbolLength() {
			return 0;
		}

		/** Returns the length of the escape codeword, if it exists, or zero.
		 *
		 * @return the escape codeword, if it exists, or zero.
		 */
		default int escapeLength() {
			return 0;
		}
	}

	/** Returns a coder for a specific map from symbols to frequencies.
	 *
	 * <p>Note that even instantaneous codes (such as {@link Unary}) need to know
	 * the set of symbols, as the returned coder needs
	 * to known the {@linkplain Coder#maxCodewordLength() maximum codeword length}.
	 *
	 * @param frequencies a map from symbols (longs) to frequencies (longs).
	 * @return a coder for the given map.
	 */
	Coder getCoder(Long2LongMap frequencies);

	/** A binary fixed-width codec. */
	public static class Binary implements Codec {

		protected static class Coder implements Codec.Coder {
			private final int codewordLength;

			protected final static class Decoder implements Codec.Decoder {
				private static final long serialVersionUID = 0L;
				@Override
				public long decode(final long value) {
					return value;
				}

				@Override
				public long numBits() {
					return 0;
				}
			}

			public Coder(final int codewordLength) {
				this.codewordLength = codewordLength;
			}

			@Override
			public long encode(final long next) {
				return Long.reverse(next) >>> 64 - codewordLength;
			}

			@Override
			public int codewordLength(final long symbol) {
				return codewordLength;
			}

			@Override
			public int maxCodewordLength() {
				return codewordLength;
			}

			@Override
			public Decoder getDecoder() {
				return new Decoder();
			}
		}

		@Override
		public Coder getCoder(final Long2LongMap frequencies) {
			assert Longs.min(frequencies.values().toLongArray()) > 0;
			return new Coder(Fast.length(Longs.max(frequencies.keySet().toLongArray())));
		}
	}

	/** A codec based on Elias's &gamma; code (starting at zero). */
	public static class Gamma implements Codec {
		protected static class Coder implements Codec.Coder {
			private final int maxCodewordLength;

			protected final static class Decoder implements Codec.Decoder {
				private static final long serialVersionUID = 0L;
				private final int maxCodewordLengthMinusOne;

				public Decoder(final int maxCodewordLength) {
					this.maxCodewordLengthMinusOne = maxCodewordLength - 1;
				}

				@Override
				public long decode(final long code) {
					final int length = maxCodewordLengthMinusOne - 63 + Long.numberOfLeadingZeros(code);
					return (code >>> maxCodewordLengthMinusOne - 2 * length) - 1;
				}

				@Override
				public long numBits() {
					return Integer.SIZE;
				}
			}

			public Coder(final int maxCodewordLength) {
				this.maxCodewordLength = maxCodewordLength;
			}

			@Override
			public long encode(long symbol) {
				symbol++;
				final int msb = Fast.mostSignificantBit(symbol);
				return Long.reverse(symbol) >>> 63 - 2 * msb;
			}

			@Override
			public int codewordLength(final long symbol) {
				return 2 * Fast.mostSignificantBit(symbol + 1) + 1;
			}

			@Override
			public int maxCodewordLength() {
				return maxCodewordLength;
			}

			@Override
			public Decoder getDecoder() {
				return new Decoder(maxCodewordLength);
			}
		}

		@Override
		public Coder getCoder(final Long2LongMap frequencies) {
			assert Longs.min(frequencies.values().toLongArray()) > 0;
			return new Coder(Fast.mostSignificantBit(Longs.max(frequencies.keySet().toLongArray()) + 1) * 2 + 1);
		}
	}

	/** A unary codec (starting at zero). */
	public static class Unary implements Codec {
		protected static class Coder implements Codec.Coder {
			private final int maxCodewordLength;

			protected final static class Decoder implements Codec.Decoder {
				private static final long serialVersionUID = 0L;
				private final int maxCodewordLengthMinus64;

				public Decoder(final int maxCodewordLength) {
					maxCodewordLengthMinus64 = maxCodewordLength - 64;
				}

				@Override
				public long decode(final long value) {
					return maxCodewordLengthMinus64 + Long.numberOfLeadingZeros(value);
				}

				@Override
				public long numBits() {
					return Integer.SIZE;
				}
			}

			public Coder(final int maxCodewordLength) {
				this.maxCodewordLength = maxCodewordLength;
			}

			@Override
			public long encode(final long symbol) {
				return 1L << symbol;
			}

			@Override
			public int codewordLength(final long symbol) {
				return (int) symbol + 1;
			}

			@Override
			public int maxCodewordLength() {
				return maxCodewordLength;
			}

			@Override
			public Decoder getDecoder() {
				return new Decoder(maxCodewordLength);
			}
		}

		@Override
		public Coder getCoder(final Long2LongMap frequencies) {
			assert Longs.min(frequencies.values().toLongArray()) > 0;
			return new Coder((int) Longs.max(frequencies.keySet().toLongArray()) + 1);
		}
	}

	/** A degenerate stateless codec (always returns zero). */
	public static class ZeroCodec implements Codec {
		private static final ZeroCodec INSTANCE = new ZeroCodec();

		private ZeroCodec() {}

		public static ZeroCodec getInstance() {
			return INSTANCE;
		}

		protected static class Coder implements Codec.Coder {
			private static Coder INSTANCE = new Coder();

			protected final static class Decoder implements Codec.Decoder {
				private static Decoder INSTANCE = new Decoder();
				private static final long serialVersionUID = 0L;

				@Override
				public long decode(final long value) {
					return 0;
				}

				@Override
				public long numBits() {
					return 0;
				}

				private Object readResolve()  {
				    return INSTANCE;
				}
			}

			@Override
			public long encode(final long symbol) {
				throw new AssertionError("The zero codec cannot encode symbols");
			}

			@Override
			public int codewordLength(final long symbol) {
				throw new AssertionError("The zero codec cannot encode symbols");
			}

			@Override
			public int maxCodewordLength() {
				return 0;
			}

			@Override
			public Decoder getDecoder() {
				return Decoder.INSTANCE;
			}
		}

		@Override
		public Coder getCoder(final Long2LongMap frequencies) {
			assert frequencies.isEmpty();
			return Coder.INSTANCE;
		}
	}


	/** A Huffman codec with length-limiting capabilities and a fast canonical decoder. */
	public static class Huffman implements Codec {
		/** Hard limit for the length of the decoding table. */
		private final int maxDecodingTableLength;
		/** The decoding table will be truncated if the accumulated entropy (starting from the most frequent symbols) exceeds this fraction of the overall entropy. */
		private final double entropyThreshold;

		/** Creates a new Huffman codec with specified limit and entropy threshold.
		 *
		 * @param maxDecodingTableLength a hard limit for the length of the decoding table.
		 * @param entropyThreshold the decoding table will be truncated if the accumulated entropy (starting from the most frequent symbols) exceeds this fraction of the overall entropy.
		 */
		public Huffman(final int maxDecodingTableLength, final double entropyThreshold) {
			this.maxDecodingTableLength = maxDecodingTableLength;
			this.entropyThreshold = entropyThreshold;
		}

		/** Creates a new Huffman codec with specified limit and entropy threshold equal to 0.999.
		 *
		 * @param maxDecodingTableLength a hard limit for the length of the decoding table.
		 */
		public Huffman(final int maxDecodingTableLength) {
			this(maxDecodingTableLength, 0.999);
		}

		/** Creates a new Huffman codec no length limitations. */
		public Huffman() {
			this(Integer.MAX_VALUE, 1);
		}

		public final static class Coder implements Codec.Coder {
			private final long[] codeword;
			private final int[] codewordLength;
			private final long[] symbol;
			private final Long2IntMap symbol2Rank;
			private final int escapedSymbolLength;
			private final int escapeLength;

			public final static class Decoder implements Codec.Decoder {
				private static final long serialVersionUID = 0L;

				private final int escapedSymbolLength;
				private final int escapeLength;
				private final long[] lastCodeWordPlusOne;
				private final int[] howManyUpToBlock;
				private final long[] symbol;
				private final byte[] shift;

				public Decoder(final long[] lastCodeWordPlusOne, final int[] howManyUpToBlock, final byte[] shift, final int escapeLength, final int escapedSymbolLength, final long[] symbol) {
					this.lastCodeWordPlusOne = lastCodeWordPlusOne;
					this.howManyUpToBlock = howManyUpToBlock;
					this.shift = shift;
					this.escapeLength = escapeLength;
					this.escapedSymbolLength = escapedSymbolLength;
					this.symbol = symbol;
				}

				@Override
				public int escapedSymbolLength() {
					return escapedSymbolLength;
				}

				@Override
				public int escapeLength() {
					return escapeLength;
				}

				@Override
				public long decode(final long value) {
					//System.err.println("value: " + StringUtils.leftPad(Long.toBinaryString(value), 64, '0'));
					final long[] lastCodeWordPlusOne = this.lastCodeWordPlusOne;
					for (int curr = 0;; curr++) {
						// System.err.println("Checking " + Long.toHexString(value) + " against " + Long.toHexString(lastCodeWordPlusOne[curr]));
						if (value < lastCodeWordPlusOne[curr]) {
							//System.err.println("LC:" + StringUtils.leftPad(Long.toBinaryString(lastCodeWordPlusOne[curr]), 64, '0'));
							//System.err.println("Diff: " + StringUtils.leftPad(Long.toBinaryString((lastCodeWordPlusOne[curr] >>> (shift[curr])) - (x >>> (shift[curr]))), 64, '0'));
							//System.err.println(howManyUpToBlock[curr]);
							//System.err.println(curr + " " + listLength);
							final int s = shift[curr];
							return symbol[(int)((value >>> s) - (lastCodeWordPlusOne[curr] >>> s)) + howManyUpToBlock[curr]];
						}
					}
				}

				@Override
				public long numBits() {
					return Integer.SIZE * shift.length + Integer.SIZE * howManyUpToBlock.length + Long.SIZE * lastCodeWordPlusOne.length + Long.SIZE * symbol.length;
				}

				public void dump(final ByteBuffer buffer) {
					buffer.putLong(escapedSymbolLength);
					buffer.putLong(escapeLength);
					buffer.putLong(lastCodeWordPlusOne.length);
					buffer.putLong(symbol.length);
					for(final long l : lastCodeWordPlusOne) buffer.putLong(l);
					for(final int i : howManyUpToBlock) buffer.putInt(i);
					for(final byte i : shift) buffer.put(i);
					for(final long l : symbol) buffer.putLong(l);
				}

			}

			public Coder(final long[] codeWord, final int[] codewordLength, final long[] symbol, final Long2IntMap symbol2Rank, final int escapedSymbolLength) {
				this.codeword = codeWord;
				this.codewordLength = codewordLength;
				this.symbol = symbol;
				this.symbol2Rank = symbol2Rank;
				this.escapedSymbolLength = escapedSymbolLength;
				this.escapeLength = codewordLength[codewordLength.length - 1];
			}

			@Override
			public long encode(final long symbol) {
				final int rank = symbol2Rank.get(symbol);
				return rank == -1 ? -1 : codeword[rank];
			}

			@Override
			public int codewordLength(final long symbol) {
				final int rank = symbol2Rank.get(symbol);
				return rank == -1 ? escapeLength + escapedSymbolLength : codewordLength[rank];
			}

			@Override
			public int maxCodewordLength() {
				if (codewordLength.length == 0) return 0;
				return codewordLength[codewordLength.length - 1] + escapedSymbolLength;
			}

			@Override
			public long escape() {
				return codeword[codeword.length - 1];
			}

			@Override
			public int escapedSymbolLength() {
				return escapedSymbolLength;
			}

			@Override
			public Decoder getDecoder() {
				final int size = codeword.length;
				final int w = maxCodewordLength();
				if (w > 62) throw new IllegalArgumentException("Codeword length must not exceed 62");
				//System.err.println("w: " + w);

				// We compute how many different codeword lengths are present. We
				// check also for excessive or nondecreasing length.
				int decodingTableLength = size < 2 ? 0 : 1;
				if (size > 1) for (int i = size - 1; i-- != 0;) {
					assert codewordLength[i] <= codewordLength[i + 1];
					if (codewordLength[i] != codewordLength[i + 1]) decodingTableLength++;
				}

				decodingTableLength++; // For the escape codeword

				final byte[] shift = new byte[decodingTableLength];
				final int[] howManyUpToBlock = new int[decodingTableLength];
				final long[] lastCodeWordPlusOne = new long[decodingTableLength];

				int p = -1, l = -1, prevL = 0;
				long word = 0;

				//System.err.println("Codewords: " + Arrays.toString(codeWord));
				//System.err.println("Codeword length: " + Arrays.toString(codeWordLength));
				for (int i = 0; i < size; i++) {
					//System.err.println("Codeword: " + codeWord[i]);
					l = codewordLength[i];
					if (l != prevL || i == size - 1) {
						if (i != 0) {
							lastCodeWordPlusOne[p] = word << w - prevL;
							// System.err.println("lastCodeWordPlusPone[p] : " +StringUtils.leftPad(Long.toBinaryString(lastCodeWordPlusOne[p]), 64, '0') + " l: "+ l	);
							howManyUpToBlock[p] = i;
						}
						shift[++p] = (byte) (w - l);
						word <<= l - prevL;
						prevL = l;
					}
					word++;
					//System.err.println("word*: " + StringUtils.leftPad(Long.toBinaryString(word), 64, '0'));
				}

				lastCodeWordPlusOne[p] = -1L >>> 1; // Escape, if necessary
				shift[p] = 63;
				if (symbol.length != 0) symbol[size - 1] = -1;
				howManyUpToBlock[p] = size - 1;

				//System.err.println("Symbol: " + Arrays.toString(symbol));
				//System.err.println("Last code word plus one: " + Arrays.toString(LongArrayList.wrap(lastCodeWordPlusOne).stream().map(x -> StringUtils.leftPad(Long.toBinaryString(x), 64, '0')).toArray(String[]::new)));

				return new Decoder(lastCodeWordPlusOne, howManyUpToBlock, shift, l, escapedSymbolLength, symbol);
			}
		}

		@Override
		public Coder getCoder(final Long2LongMap frequencies) {
			assert frequencies.isEmpty() || Longs.min(frequencies.values().toLongArray()) > 0;
			final int size = frequencies.size();
			if (size == 0) return new Coder(new long[1], new int[1], new long[0], Long2IntMaps.EMPTY_MAP, 0);
			final long[] symbol = new long[size];
			frequencies.keySet().toArray(symbol);
			// Sort symbols by frequency
			LongArrays.quickSort(symbol, (x,y) -> Long.compare(frequencies.get(y), frequencies.get(x)));

			//System.err.println("a:" + Arrays.toString(a));

			final long[] a = new long[size];
			for (int i = 0; i < size; i++) a[size - 1 - i] = frequencies.get(symbol[i]);

			//System.err.println("a: " + Arrays.toString(a));

			// The following lines are from Moffat & Katajainen sample code.
			// Please refer to their paper.
			long overallLength = 0;
			if (size > 1) {
				// First pass, left to right, setting parent pointers.
				a[0] += a[1];
				int root = 0;
				int leaf = 2;
				for (int next = 1; next < size - 1; next++) {
					// Select first item for a pairing.
					if (leaf >= size || a[root] < a[leaf]) {
						a[next] = a[root];
						a[root++] = next;
					} else a[next] = a[leaf++];

					// Add on the second item.
					if (leaf >= size || (root < next && a[root] < a[leaf])) {
						a[next] += a[root];
						a[root++] = next;
					} else a[next] += a[leaf++];
				}

				// Second pass, right to left, setting internal depths.
				a[size - 2] = 0;
				for (int next = size - 3; next >= 0; next--)
					a[next] = a[(int) a[next]] + 1;

				// Third pass, right to left, setting leaf depths.
				int available = 1, used = 0, depth = 0;
				root = size - 2;
				int next = size - 1;

				// We now compute depth and the overall length of all symbols
				while (available > 0) {
					while (root >= 0 && a[root] == depth) {
						used++;
						root--;
					}
					while (available > used) {
						overallLength += depth * frequencies.get(symbol[size - next - 1]);
						//System.err.println(depth + " => " + frequencies.get(symbol[size - next - 1]));
						a[next--] = depth;
						available--;
					}
					available = 2 * used;
					depth++;
					used = 0;
				}
			}
			else a[0] = 1;

			// Reverse the order of symbol lengths, and store them into an int array.
			final int[] length = new int[size + 1];
			for (int i = 0; i < size; i++) length[size - 1 - i] = (int) a[i];

			/* We now progress through the symbols, from more frequent
			 * to less frequent, computing for each prefix of symbols
			 * the length of the decoding table and the cumulative
			 * length of such symbols. If we pass the table length limit
			 * or the empirical entropy threshold be break the loop. */
			long accumulatedOverallLength = 0;
			int currentLength = length[0], d = 1;
			int cutpoint;
			for (cutpoint = 0; cutpoint < size; cutpoint++) {
				if (currentLength != length[cutpoint]) {
					if (++d >= maxDecodingTableLength) break;
					if (accumulatedOverallLength / (double)overallLength > entropyThreshold) break;
					currentLength = length[cutpoint];
				}
				accumulatedOverallLength += length[cutpoint] * frequencies.get(symbol[cutpoint]);
			}

			//System.err.println("Coded length : " + overallLength);


			// System.err.println("Length: " + length.length + " Cutpoint: " + cutpoint + " Depth: " + d + " Codeword length: " + length[cutpoint - 1] + " Accumulated length: " + accumulatedOverallLength + " (" + Util.format(100. * accumulatedOverallLength / overallLength) + "%)");
			// We leave space for the escape codeword, if necessary
			final long[] codeword = new long[cutpoint + 1];

			int s = 0;
			long value = 0;
			currentLength = length[0];
			codeword[0] = 0;

			for (int i = 1; i < cutpoint; i++) {
				s = i;
				if (length[i] == currentLength) value++;
				else {
					value++;
					value <<= length[i] - currentLength;
					currentLength = length[i];
				}
				codeword[s] = Long.reverse(value) >>> 64 - currentLength;
			}

			// Escape keyword (even if not necessary)
			codeword[cutpoint] = -1 >>> 64 - currentLength;
			length[cutpoint] = currentLength;

			int maxLengthEscaped = 0;
			for(int i = cutpoint; i < size; i++) maxLengthEscaped = Math.max(maxLengthEscaped, Fast.length(symbol[i]));
			final Long2IntOpenHashMap symbol2Rank = new Long2IntOpenHashMap();
			for (int i = 0; i < cutpoint; i++) symbol2Rank.put(symbol[i], i);
			symbol2Rank.defaultReturnValue(-1);

			return new Coder(codeword, Arrays.copyOf(length, cutpoint + 1), Arrays.copyOf(symbol, cutpoint + 1), symbol2Rank, maxLengthEscaped);
		}
	}
}
