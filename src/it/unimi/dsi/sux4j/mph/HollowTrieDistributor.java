package it.unimi.dsi.sux4j.mph;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008-2019 Sebastiano Vigna
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

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongBigList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.BalancedParentheses;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;

/** A distributor based on a hollow trie.
 *
 * <h2>Implementation details</h2>
 *
 * <p>This class implements a distributor on top of a hollow trie. First, a compacted trie is built from the delimiter set.
 * Then, for each key we compute the node of the trie in which the bucket of the key is established. This gives us,
 * for each node of the trie, a set of paths to which we must associate an action (exit on the left,
 * go through, exit on the right). Overall, the number of such paths is equal to the number of keys plus the number of delimiters, so
 * the mapping from each pair node/path to the respective action takes linear space. Now, from the compacted trie we just
 * retain a hollow trie, as the path-length information is sufficient to rebuild the keys of the above mapping.
 * By sizing the bucket size around the logarithm of the average length, we obtain a distributor that occupies linear space.
 */

public class HollowTrieDistributor<T> extends AbstractObject2LongFunction<T> implements Size64 {
	private final static Logger LOGGER = LoggerFactory.getLogger(HollowTrieDistributor.class);
	private static final long serialVersionUID = 4L;
	private static final boolean DEBUG = false;
	private static final boolean ASSERTS = false;

	/** An integer representing the exit-on-the-left behaviour. */
	private final static int LEFT = 0;
	/** An integer representing the exit-on-the-right behaviour. */
	private final static int RIGHT = 1;
	/** An integer representing the follow-the-try behaviour. */
	private final static int FOLLOW = 2;

	/** The transformation used to map object to bit vectors. */
	private final TransformationStrategy<? super T> transformationStrategy;
	/** The bitstream representing the hollow trie. */
	private final LongArrayBitVector trie;
	/** The list of skips, indexed by the internal nodes (we do not need skips on the leaves). */
	private final EliasFanoLongBigList skips;
	/** For each external node and each possible path, the related behaviour. */
	private final GOV3Function<BitVector> externalBehaviour;
	/** The number of (internal and external) nodes of the trie. */
	private final long size;
	/** The balanced parentheses structure used to represent the trie. */
	private final BalancedParentheses balParen;
	/** Records the keys which are false follows. */
	private final GOV3Function<BitVector> falseFollowsDetector;
	/** The average skip length in bits (actually, the average length in bits of a skip length increased by one). */
	protected double meanSkipLength;

	/** A debug function used to store explicitly the internal behaviour. */
	Object2LongFunction<BitVector> externalTestFunction;
	/** A debug function used to store explicitly the false follow detector. */
	Object2LongFunction<BitVector> falseFollows;

	/** Creates a partial compacted trie using given elements, bucket size and transformation strategy.
	 *
	 * @param elements the elements among which the trie must be able to rank.
	 * @param log2BucketSize the logarithm of the size of a bucket.
	 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, lexicographically increasing (in iteration order) bit vectors.
	 */
	public HollowTrieDistributor(final Iterable<? extends T> elements, final int log2BucketSize, final TransformationStrategy<? super T> transformationStrategy) throws IOException {
		this(elements, log2BucketSize, transformationStrategy, null);
	}

	/** Creates a hollow trie distributor.
	 *
	 * @param elements the elements among which the trie must be able to rank.
	 * @param log2BucketSize the logarithm of the size of a bucket.
	 * @param transformationStrategy a transformation strategy that must turn the elements in <code>elements</code> into a list of
	 * distinct, lexicographically increasing (in iteration order) bit vectors.
	 * @param tempDir the directory where temporary files will be created, or <code>for the default directory</code>.
	 */
	@SuppressWarnings("resource")
	public HollowTrieDistributor(final Iterable<? extends T> elements, final int log2BucketSize, final TransformationStrategy<? super T> transformationStrategy, final File tempDir) throws IOException {
		this.transformationStrategy = transformationStrategy;
		final int bucketSize = 1 << log2BucketSize;

		/** The file containing the external keys (pairs node/path). */
		final File externalKeysFile;
		/** The values associated to the keys in {@link #externalKeysFile}. */
		LongBigList externalValues;
		/** The file containing the keys (pairs node/path) that are (either true or false) follows. */
		final File falseFollowsKeyFile;
		/** The values (true/false) associated to the keys in {@link #falseFollows}. */
		LongBigList falseFollowsValues;

		if (DEBUG) System.err.println("Bucket size: " + bucketSize);
		final long[] count = new long[1];

		final HollowTrieMonotoneMinimalPerfectHashFunction<T> intermediateTrie = new HollowTrieMonotoneMinimalPerfectHashFunction<>(new ObjectIterator<T>() {
			final Iterator<? extends T> iterator = elements.iterator();
			boolean toAdvance = true;
			private T curr;

			@Override
			public boolean hasNext() {
				if (toAdvance) {
					toAdvance = false;
					int i;
					for(i = 0; i < bucketSize && iterator.hasNext(); i++) {
						curr = iterator.next();
						count[0]++;
					}
					if (i != bucketSize) curr = null;
				}
				return curr != null;
			}

			@Override
			public T next() {
				if (! hasNext()) throw new NoSuchElementException();
				toAdvance = true;
				return curr;
			}
		}, transformationStrategy);

		size = count[0];

		if (ASSERTS) {
			externalTestFunction = new Object2LongOpenHashMap<>();
			externalTestFunction.defaultReturnValue(-1);
			falseFollows = new Object2LongOpenHashMap<>();
			falseFollows.defaultReturnValue(-1);
		}

		externalKeysFile = File.createTempFile(HollowTrieDistributor.class.getName(), "ext", tempDir);
		externalKeysFile.deleteOnExit();
		falseFollowsKeyFile = File.createTempFile(HollowTrieDistributor.class.getName(), "false", tempDir);
		falseFollowsKeyFile.deleteOnExit();

		externalValues = LongArrayBitVector.getInstance().asLongBigList(1);
		falseFollowsValues = LongArrayBitVector.getInstance().asLongBigList(1);
		long sumOfSkipLengths = 0;

		if (intermediateTrie.size64() > 0) {
			final OutputBitStream externalKeys = new OutputBitStream(externalKeysFile);
			final OutputBitStream falseFollowsKeys = new OutputBitStream(falseFollowsKeyFile);

			final Iterator<? extends T> iterator = elements.iterator();
			final LongArrayBitVector bucketKey[] = new LongArrayBitVector[bucketSize];

			LongArrayBitVector leftDelimiter, rightDelimiter = null;
			long delimiterLcp = -1;

			trie = intermediateTrie.trie;
			skips = intermediateTrie.skips;
			balParen = intermediateTrie.balParen;
			final LongArrayBitVector emitted = LongArrayBitVector.ofLength(intermediateTrie.size64());

			final ProgressLogger pl = new ProgressLogger(LOGGER);
			pl.displayLocalSpeed = true;
			pl.displayFreeMemory = true;
			pl.expectedUpdates = size;
			pl.start("Computing function keys...");

			while(iterator.hasNext()) {

				int realBucketSize;
				for(realBucketSize = 0; realBucketSize < bucketSize && iterator.hasNext(); realBucketSize++) {
					bucketKey[realBucketSize] = LongArrayBitVector.copy(transformationStrategy.toBitVector(iterator.next()));
					pl.lightUpdate();
				}

				// The next delimiter
				leftDelimiter = rightDelimiter;
				rightDelimiter = realBucketSize == bucketSize ? bucketKey[bucketSize - 1] : null;
				delimiterLcp = (rightDelimiter != null && leftDelimiter != null) ? rightDelimiter.longestCommonPrefixLength(leftDelimiter) : -1;

				long stackP[] = new long[1024];
				long stackR[] = new long[1024];
				int stackS[] = new int[1024];
				long stackIndex[] = new long[1024];
				stackP[0] = 1;
				int depth = 0;

				int pathLength;
				long lastNode = -1;
				BitVector lastPath = null;
				LongArrayBitVector curr = null, prev;

				for(int j = 0; j < realBucketSize; j++) {
					prev = curr;
					curr = bucketKey[j];
					if (DEBUG) System.err.println(curr);

					long p = 1;
					final long length = curr.length();
					long index = 0, r = 0;
					int s = 0, skip = 0;
					boolean isInternal;
					int startPath = -1, endPath = -1;
					boolean exitLeft = false;
					if (DEBUG) System.err.println("Distributing " + curr + "\ntrie:" + trie);
					long maxDescentLength;

					if (prev != null)  {
						final int prefix = (int)curr.longestCommonPrefixLength(prev);
						if (prefix == prev.length() && prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not distinct");
						if (prefix == prev.length() || prefix == curr.length()) throw new IllegalArgumentException("The input bit vectors are not prefix-free");
						if (prev.getBoolean(prefix)) throw new IllegalArgumentException("The input bit vectors are not lexicographically sorted");
						// Adjust stack using lcp between present string and previous one
						while(depth > 0 && stackS[depth] > prefix) depth--;
					}

					p = stackP[depth];
					r = stackR[depth];
					s = stackS[depth];
					index = stackIndex[depth];

					if (leftDelimiter == null) {
						maxDescentLength = rightDelimiter.longestCommonPrefixLength(curr) + 1;
						exitLeft = true;
					}
					else if (rightDelimiter == null) {
						maxDescentLength = leftDelimiter.longestCommonPrefixLength(curr) + 1;
						exitLeft = false;
					}
					else maxDescentLength = (exitLeft = curr.getBoolean(delimiterLcp)) ? rightDelimiter.longestCommonPrefixLength(curr) + 1 : leftDelimiter.longestCommonPrefixLength(curr) + 1;

					for(;;) {
						isInternal = trie.getBoolean(p);
						if (isInternal) skip = (int)skips.getLong(r);
						if (DEBUG) {
							final int usedLength = (int)(isInternal ? Math.min(length, s + skip) : length - s);
							final BitVector usedPath = curr.subVector(s, s + usedLength);
							System.err.println("Interrogating" + (isInternal ? "" : " leaf") + " <" + (p - 1) + ", [" + usedLength + ", " + Integer.toHexString(usedPath.hashCode()) + "] " + usedPath + "> " + (isInternal ? "" : "(skip: " + skip + ")"));
						}

						if (isInternal && s + skip < maxDescentLength && ! emitted.getBoolean(r)) {
							sumOfSkipLengths += Fast.length(skip + 1);
							emitted.set(r, true);
							falseFollowsValues.add(0);
							falseFollowsKeys.writeLong(p - 1, Long.SIZE);
							falseFollowsKeys.writeDelta(skip);
							for(int i = 0; i < skip; i += Long.SIZE)
								falseFollowsKeys.writeLong(curr.getLong(s + i, Math.min(s + i + Long.SIZE, s + skip)), Math.min(Long.SIZE, skip - i));

							if (DEBUG) System.err.println("Adding true follow at " + (p - 1) + " [" + skip + ", " + Integer.toHexString(curr.subVector(s, s + skip).hashCode()) + "] " + curr.subVector(s, s + skip));

							if (ASSERTS) {
								final long key[] = new long[(skip + Long.SIZE - 1) / Long.SIZE + 1];
								key[0] = p - 1;
								for(int i = 0; i < skip; i += Long.SIZE) key[i / Long.SIZE + 1] = curr.getLong(s + i, Math.min(s + i + Long.SIZE, s + skip));
								final long result = falseFollows.put(LongArrayBitVector.wrap(key, skip + Long.SIZE), 0);
								assert result == -1 : result + " != " + -1;
								if (DEBUG) System.err.println(falseFollows);
							}
						}

						if (! isInternal || (s += skip) >= maxDescentLength) break;

						if (DEBUG) System.err.println("Turning " + (curr.getBoolean(s) ? "right" : "left") + " at bit " + s + "... ");

						if (curr.getBoolean(s)) {
							final long q = balParen.findClose(p) + 1;
							index += (q - p) / 2;
							r += (q - p) / 2;
							//System.err.println("Increasing index by " + (q - p + 1) / 2 + " to " + index + "...");
							p = q;
						}
						else {
							p++;
							r++;
						}
						if (ASSERTS) assert p < trie.length();

						s++;

						if (++depth >= stackP.length) {
							stackP = LongArrays.grow(stackP, depth + 1);
							stackR = LongArrays.grow(stackR, depth + 1);
							stackS = IntArrays.grow(stackS, depth + 1);
							stackIndex = LongArrays.grow(stackIndex, depth + 1);
						}

						stackP[depth] = p;
						stackR[depth] = r;
						stackS[depth] = s;
						stackIndex[depth] = index;
					}


					if (isInternal) {
						startPath = s - skip;
						endPath = (int)Math.min(length, s);
					}
					else {
						startPath = s;
						endPath = (int)length;
					}

					// If we exit on a leaf, we invalidate last node/path data
					if (! isInternal) lastNode = -1;

					if (lastNode != p - 1 || ! curr.subVector(startPath, endPath).equals(lastPath)) {

						externalValues.add(exitLeft ? LEFT : RIGHT);
						pathLength = endPath - startPath;

						externalKeys.writeLong(p - 1, Long.SIZE);
						externalKeys.writeDelta(pathLength);
						for(int i = 0; i < pathLength; i += Long.SIZE)
							externalKeys.writeLong(curr.getLong(startPath + i, Math.min(startPath + i + Long.SIZE, endPath)), Math.min(Long.SIZE, endPath - i - startPath));

						if (DEBUG) System.err.println("Computed " + (isInternal ? "" : "leaf ") + "mapping <" + (p - 1) + ", [" + pathLength + ", " + Integer.toHexString(curr.subVector(startPath, endPath).hashCode()) + "] " + curr.subVector(startPath, endPath) + "> -> " + (exitLeft ? "left" : "right"));

						if (ASSERTS) {
							final long key[] = new long[(pathLength + Long.SIZE - 1) / Long.SIZE + 1];
							key[0] = p - 1;
							for(int i = 0; i < pathLength; i += Long.SIZE) key[i / Long.SIZE + 1] = curr.getLong(startPath + i, Math.min(startPath + i + Long.SIZE, endPath));
							assert externalTestFunction.put(LongArrayBitVector.wrap(key, pathLength + Long.SIZE), exitLeft ? LEFT : RIGHT) == -1;
							if (DEBUG) System.err.println(externalTestFunction);
						}

						if (isInternal) {
							if (DEBUG) System.err.println("Adding false follow [" + pathLength + ", " + Integer.toHexString(curr.subVector(startPath, endPath).hashCode()) + "] " + curr.subVector(startPath, endPath));

							lastPath = curr.subVector(startPath, endPath);
							lastNode = p - 1;
							falseFollowsValues.add(1);
							falseFollowsKeys.writeLong(p - 1, Long.SIZE);
							falseFollowsKeys.writeDelta(pathLength);
							for(int i = 0; i < pathLength; i += Long.SIZE)
								falseFollowsKeys.writeLong(curr.getLong(startPath + i, Math.min(startPath + i + Long.SIZE, endPath)), Math.min(Long.SIZE, endPath - i - startPath));

							if (ASSERTS) {
								final long key[] = new long[(pathLength + Long.SIZE - 1) / Long.SIZE + 1];
								key[0] = p - 1;
								for(int i = 0; i < pathLength; i += Long.SIZE) key[i / Long.SIZE + 1] = curr.getLong(startPath + i, Math.min(startPath + i + Long.SIZE, endPath));
								assert falseFollows.put(LongArrayBitVector.wrap(key, pathLength + Long.SIZE), 1) == -1;
								if (DEBUG) System.err.println(falseFollows);
							}
						}

					}
				}
			}

			meanSkipLength = (double)sumOfSkipLengths / size;
			pl.done();
			externalKeys.close();
			falseFollowsKeys.close();
		}
		else {
			trie = null;
			balParen = null;
			skips = null;
			falseFollowsDetector = null;
			externalBehaviour = null;
			return;
		}

		/** A class iterating over the temporary files produced by the intermediate trie. */
		class IterableStream implements Iterable<BitVector> {
			private final InputBitStream ibs;
			private final long n;
			private final Object2LongFunction<BitVector> test;
			private final LongBigList values;

			public IterableStream(final InputBitStream ibs, final Object2LongFunction<BitVector> testFunction, final LongBigList testValues) {
				this.ibs = ibs;
				this.n = testValues.size64();
				this.test = testFunction;
				this.values = testValues;
			}

			@Override
			public Iterator<BitVector> iterator() {
				try {
					ibs.position(0);
					return new ObjectIterator<BitVector>() {
						private long pos = 0;

						@Override
						public boolean hasNext() {
							return pos < n;
						}

						@Override
						public BitVector next() {
							if (! hasNext()) throw new NoSuchElementException();
							try {
								final long index = ibs.readLong(64);
								assert index >= 0;
								final int pathLength = ibs.readDelta();
								final long key[] = new long[((pathLength + Long.SIZE - 1) / Long.SIZE + 1)];
								key[0] = index;
								for(int i = 0; i < (pathLength + Long.SIZE - 1) / Long.SIZE; i++) key[i + 1] = ibs.readLong(Math.min(Long.SIZE, pathLength - i * Long.SIZE));

								if (DEBUG) {
									System.err.println("Adding mapping <" + index + ", " +  LongArrayBitVector.wrap(key, pathLength + Long.SIZE).subVector(Long.SIZE) + "> -> " + values.getLong(pos));
									System.err.println(LongArrayBitVector.wrap(key, pathLength + Long.SIZE));
								}

								if (ASSERTS) if (test != null) assert test.getLong(LongArrayBitVector.wrap(key, pathLength + Long.SIZE)) == values.getLong(pos) : test.getLong(LongArrayBitVector.wrap(key, pathLength + Long.SIZE)) + " != " + values.getLong(pos) ;

								pos++;
								return LongArrayBitVector.wrap(key, pathLength + Long.SIZE);
							}
							catch (final IOException e) {
								throw new RuntimeException(e);
							}
						}
					};
				}
				catch (final IOException e) {
					throw new RuntimeException(e);
				}
			}
		};

		externalBehaviour = new GOV3Function.Builder<BitVector>().keys(new IterableStream(new InputBitStream(externalKeysFile), externalTestFunction, externalValues)).transform(TransformationStrategies.identity()).values(externalValues, 1).build();
		falseFollowsDetector = new GOV3Function.Builder<BitVector>().keys(new IterableStream(new InputBitStream(falseFollowsKeyFile), falseFollows, falseFollowsValues)).transform(TransformationStrategies.identity()).values(falseFollowsValues, 1).build();

		if (ASSERTS) {
			assert externalBehaviour.size64() == externalTestFunction.size();
			assert falseFollowsDetector.size64() == falseFollows.size();
		}

		LOGGER.debug("False positives: " + (falseFollowsDetector.size64() - intermediateTrie.size64()));

		externalKeysFile.delete();
		falseFollowsKeyFile.delete();
	}


	@Override
	@SuppressWarnings("unchecked")
	public long getLong(final Object o) {
		if (size == 0) return 0;
		final BitVector bitVector = transformationStrategy.toBitVector((T)o).fast();
		final LongArrayBitVector key = LongArrayBitVector.getInstance();
		BitVector fragment = null;
		long p = 1;
		final long length = bitVector.length();
		long index = 0, r = 0;
		int s = 0, skip = 0, behaviour;
		long lastLeftTurn = 0;
		long lastLeftTurnIndex = 0;
		boolean isInternal;

		if (DEBUG) System.err.println("Distributing " + bitVector + "\ntrie:" + trie);

		for(;;) {
			isInternal = trie.getBoolean(p);
			if (isInternal) skip = (int)skips.getLong(r);
			if (DEBUG) {
				final int usedLength = (int)(isInternal ? Math.min(length, s + skip) : length - s);
				final BitVector usedPath = bitVector.subVector(s, s + usedLength);
				System.err.println("Interrogating" + (isInternal ? "" : " leaf") + " <" + (p - 1) +
					", [" + Math.min(length, s + skip) + ", "
					+ Integer.toHexString(usedPath.hashCode()) + "] " +
					usedPath + "> " + (isInternal ? "" : "(skip: " + skip + ")"));
			}

			if (isInternal && falseFollowsDetector.getLong(key.length(0).append(p - 1, Long.SIZE).append(fragment = bitVector.subVector(s, Math.min(length, s + skip)))) == 0) behaviour = FOLLOW;
			else behaviour = (int)externalBehaviour.getLong(key.length(0).append(p - 1, Long.SIZE).append(isInternal ? fragment : bitVector.subVector(s, length)));

			if (ASSERTS) {
				assert ! isInternal || falseFollows.getLong(key.length(0).append(p - 1, Long.SIZE).append(fragment)) != -1;
				if (behaviour != FOLLOW) {
					assert ! isInternal || falseFollows.getLong(key.length(0).append(p - 1, Long.SIZE).append(fragment)) == 1;
					final long result;
					result = externalTestFunction.getLong(key.length(0).append(p - 1, Long.SIZE).append(isInternal ? fragment : bitVector.subVector(s, length)));
					assert result != -1 : isInternal ? "Missing internal node" : "Missing leaf"; // Only if you don't test with non-keys
					if (result != -1) assert result == behaviour : result + " != " + behaviour;
				}
				else {
					assert s + skip <= length;
					assert falseFollows.getLong(key.length(0).append(p - 1, Long.SIZE).append(fragment)) == 0;
				}
			}

			if (DEBUG) System.err.println("Exit behaviour: " + behaviour);

			if (behaviour != FOLLOW || ! isInternal || (s += skip) >= length) break;

			if (DEBUG) System.err.println("Turning " + (bitVector.getBoolean(s) ? "right" : "left") + " at bit " + s + "... ");

			if (bitVector.getBoolean(s)) {
				final long q = balParen.findClose(p) + 1;
				index += (q - p) / 2;
				r += (q - p) / 2;
				//System.err.println("Increasing index by " + (q - p + 1) / 2 + " to " + index + "...");
				p = q;
			}
			else {
				lastLeftTurn = p;
				lastLeftTurnIndex = index;
				p++;
				r++;
			}

			if (ASSERTS) assert p < trie.length();

			s++;
		}

		if (behaviour == LEFT) {
			if (DEBUG) System.err.println("Returning (on the left) " + index);
			return index;
		}
		else {
			if (isInternal) {
				final long q = balParen.findClose(lastLeftTurn);
				//System.err.println(p + ", " + q + " ," + lastLeftTurn + ", " +lastLeftTurnIndex);;
				index = (q - lastLeftTurn + 1) / 2 + lastLeftTurnIndex;
				if (DEBUG) System.err.println("Returning (on the right, internal) " + index);
			}
			else {
				index++;
				if (DEBUG) System.err.println("Returning (on the right, external) " + index);
			}
			return index;
		}

	}

	public long numBits() {
		return (trie == null ? 0 : trie.length() + skips.numBits() + falseFollowsDetector.numBits() + balParen.numBits() + externalBehaviour.numBits()) + transformationStrategy.numBits();
	}

	@Override
	public boolean containsKey(Object o) {
		return true;
	}

	@Override
	public long size64() {
		return size;
	}

	@Override
	@Deprecated
	public int size() {
		return (int)Math.min(size, Integer.MAX_VALUE);
	}

	public double bitsPerSkip() {
		return (double)skips.numBits() / skips.size64();
	}
}
