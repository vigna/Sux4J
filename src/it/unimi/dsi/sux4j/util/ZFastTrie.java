/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2022 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.GZIPInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.booleans.BooleanArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectSet;
import it.unimi.dsi.fastutil.objects.AbstractObjectSortedSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.Hashes;

/** A z-fast trie, that is, a predecessor/successor data structure using low linear (in the number of keys) additional space and
 * answering to the query string
 * <var>x</var> in time |<var>x</var>|/<var>w</var> + log(max{|<var>x</var>|, |<var>x</var><sup>-</sup>|, |<var>x</var><sup>+</sup>|}) with high probability,
 * where <var>w</var> is the machine word size, and <var>x</var><sup>-</sup>/<var>x</var><sup>+</sup> are the predecessor/successor of <var>x</var> in the currently stored set, respectively.
 *
 * <p>In rough terms, the z-fast trie uses time |<var>x</var>|/<var>w</var> (which is optimal) to actually look at the string content,
 * and log(max{|<var>x</var>|, |<var>x</var><sup>-</sup>|, |<var>x</var><sup>+</sup>|}) to perform the search. This is known to be (essentially) optimal.
 * String lengths are up to {@link Integer#MAX_VALUE}, and not limited to be a constant multiple of <var>w</var> for the bounds to hold.
 *
 * <p>The linear overhead of a z-fast trie is very low. For <var>n</var> keys we allocate 2<var>n</var> &minus; 1 nodes containing six references and
 * two longs, plus a dictionary containing <var>n</var> &minus; 1 nodes (thus using around 2<var>n</var> references and 2<var>n</var> longs).
 *
 */

@SuppressWarnings({"rawtypes"})
public class ZFastTrie<T> extends AbstractObjectSortedSet<T> implements Serializable {
    public static final long serialVersionUID = 2L;
	private static final Logger LOGGER = LoggerFactory.getLogger(ZFastTrie.class);
	private static final boolean ASSERTS = false;
	private static final boolean DDDEBUG = false;
	private static final boolean DDEBUG = false || DDDEBUG;
	private static final boolean DEBUG = false || DDEBUG;
	/** The mask used to extract the actual signature (the high bit marks duplicates). */
	private static final long SIGNATURE_MASK = 0x7FFFFFFFFFFFFFFFL;
	// private static final long SIGNATURE_MASK = 0x1L;
	/** The mask for the high bit (which marks duplicates). */
	private static final long DUPLICATE_MASK = 0x8000000000000000L;

	/** The number of elements in the trie. */
	private int size;
	/** The root node. */
	private transient Node<T> root;
	/** The transformation strategy. */
	private final TransformationStrategy<? super T> transform;
	/** A dictionary mapping handles to the corresponding internal nodes. */
	public transient Handle2NodeMap<T> handle2Node;
	/** The head of the doubly linked list of leaves. */
	private transient Leaf<T> head;
	/** The tail of the doubly linked list of leaves. */
	private transient Leaf<T> tail;

	/** A linear-probing hash map that compares keys using signatures as a first try. */
	protected final static class Handle2NodeMap<U> {
		private static final int INITIAL_LENGTH = 64;

		/** The transformation strategy. */
		protected final TransformationStrategy<? super U> transform;
		/** The node table. */
		protected InternalNode<U>[] node;
		/** The signature of the handle of the corresponding entry {@link #node}. */
		protected long[] signature;
        /** The number of elements in the table. */
		protected int size;
		/** The number of slots in the table (always a power of two). */
		protected int length;
		/** {@link #length} &minus; 1. */
		protected int mask;

		protected void assertTable() {
			int c = 0;
			for(int i = signature.length; i-- != 0;) {
				if (node[i] != null) {
					assert get(node[i].handle(transform)) == node[i] : node[i] + " != " + get(node[i].handle(transform));
					c++;
				}
			}

			assert c == size() : c + " != " + size();
			if (size == 0) return;
			final IntOpenHashSet overallHashes = new IntOpenHashSet();
			int start = 0;
			int first = -1;
			while(node[start] != null) start = (start + 1) & mask;
			// We are on an empty entry
			for(;;) {
				while(node[start] == null) start = (start + 1) & mask;
				// We are on a nonempty entry
				if (first == -1) first = start;
				else if (first == start) break;
				int end = start;
				while(node[end] != null) end = (end + 1) & mask;
				// [start..end) is a maximal nonempty subsequence
				final IntOpenHashSet hashesSeen = new IntOpenHashSet();
				final LongOpenHashSet signaturesSeen = new LongOpenHashSet();

				for(int pos = end; pos != start;) {
					pos = (pos - 1) & mask;
					final boolean newSignature = signaturesSeen.add(signature[pos] & SIGNATURE_MASK);
					assert newSignature != ((signature[pos] & DUPLICATE_MASK) != 0) : newSignature + " == " + ((signature[pos] & DUPLICATE_MASK) != 0);
					hashesSeen.add(hash(signature[pos] & SIGNATURE_MASK));
				}

				// Hashes in each maximal nonempty subsequence must be disjoint
				for(final IntIterator iterator = hashesSeen.iterator(); iterator.hasNext();) assert overallHashes.add(iterator.nextInt());

				start = end;
			}
		}

		/** Creates a new handle-to-node map using a given transformation strategy and expected number of elements.
		 *
		 * @param size the expected number of elements.
		 * @param transform the transformation strategy used for this map.
		 */
		@SuppressWarnings("unchecked")
		public Handle2NodeMap(final int size, final TransformationStrategy<? super U> transform) {
			this.transform = transform;
			length = Math.max(INITIAL_LENGTH, 1 << Fast.ceilLog2(1 + (3L * size / 2)));
			mask = length - 1;
			signature = new long[length];
			node = new InternalNode[length];
		}

		/** Creates a new handle-to-node map using a given transformation strategy.
		 *
		 * @param transform the transformation strategy used for this map.
		 */
		@SuppressWarnings("unchecked")
		public Handle2NodeMap(final TransformationStrategy<? super U> transform) {
			this.transform = transform;
			length = INITIAL_LENGTH;
			mask = length - 1;
			signature = new long[length];
			node = new InternalNode[length];
		}

		/** Generates a hash table position starting from a signature.
		 *
		 * @param s a signature.
		 * @return the hash table position of {@code s}.
		 */
		protected int hash(final long s) {
			return (int)(s ^ s >>> 32) & mask;
		}

		/**
		 * Find a node with given handle using signatures.
		 *
		 * <p>
		 * Note that this function just compares signatures (except for duplicates, which are checked
		 * explicitly). Thus, it might return false positives when queried with keys that are not handles.
		 * Nonetheless, it will always return a correct result on a handle.
		 *
		 * @param v a bit vector.
		 * @param handleLength the length of the prefix of {@code v} that will be used as a handle.
		 * @param state the hash state of {@code v} precomputed by
		 *            {@link Hashes#preprocessMurmur(BitVector, long)}.
		 *
		 * @return a node with the specified handle signature, or {@code null}.
		 */
		protected InternalNode<U> find(final BitVector v, final long handleLength, final long[] state) {
			final long s = Hashes.murmur(v, handleLength, state) & SIGNATURE_MASK;
			return find(v, handleLength, s);
		}

		/**
		 * Find a node with given handle using signatures.
		 *
		 * <p>
		 * Note that this function just compares signatures and handle lengths (except for duplicates, which are checked
		 * explicitly). Thus, it might return false positives when queried with keys that are not handles.
		 * Nonetheless, it will always return a correct result on a handle.
		 *
		 * @param v a bit vector.
		 * @param handleLength the length of the prefix of {@code v} that will be used as a handle.
		 * @param s the signature of the handle.
		 *
		 * @return a node with the specified handle signature, or {@code null}.
		 */
		private InternalNode<U> find(final BitVector v, final long handleLength, final long s) {
			int pos = hash(s);

			final InternalNode<U>[] node = this.node;
			final long[] signature = this.signature;

			while (node[pos] != null) { // Position is not empty
				final long sig = signature[pos];

				if ((sig & SIGNATURE_MASK) == s) { // Same signature
					// It's not a duplicate: if the extent length is compatible, we return the node, otherwise null
					if ((sig & DUPLICATE_MASK) == 0) return node[pos].handleLength() == handleLength ? node[pos] : null;
					// It's a duplicate: we have to check explicitly
					if (handleLength == node[pos].handleLength() && v.equals(node[pos].reference.key(transform), 0, handleLength)) return node[pos];
				}
				pos = (pos + 1) & mask;
			}
			return null;
		}


		/**
		 * Find a node with given handle using handles.
		 *
		 * <p>
		 * Note that this function compares handles. Thus, it always returns a correct value.
		 *
		 * @param v a bit vector.
		 * @param handleLength the length of the prefix of {@code v} that will be used as a handle.
		 * @param state the hash state of {@code v} precomputed by
		 *            {@link Hashes#preprocessMurmur(BitVector, long)}.
		 *
		 * @return a node with the specified handle, or {@code null}.
		 */
		protected InternalNode<U> findExact(final BitVector v, final long handleLength, final long[] state) {
			final long s = Hashes.murmur(v, handleLength, state) & SIGNATURE_MASK;
			return findExact(v, handleLength, s);
		}

		/**
		 * Find a node with given handle using handles.
		 *
		 * <p>
		 * Note that this function compares handles. Thus, it always returns a correct value.
		 *
		 * @param v a bit vector.
		 * @param handleLength the length of the prefix of {@code v} that will be used as a handle.
		 * @param s the signature of the handle.
		 *
		 * @return a node with the specified handle, or {@code null}.
		 */
		private InternalNode<U> findExact(final BitVector v, final long handleLength, final long s) {
			int pos = hash(s);

			final InternalNode<U>[] node = this.node;
			final long[] signature = this.signature;

			while(node[pos] != null) { // Position is not empty
				if ((signature[pos] & SIGNATURE_MASK) == s && // Same signature
							handleLength == node[pos].handleLength() && // Same handle length
								v.equals(node[pos].reference.key(transform), 0, handleLength)) // Same handle
					return node[pos];
				pos = (pos + 1) & mask;
			}
			return null;
		}

		@SuppressWarnings("unchecked")
		public void clear() {
			length = INITIAL_LENGTH;
			mask = length - 1;
			size = 0;
			signature = new long[length];
			node = new InternalNode[length];
		}

		public ObjectSet<LongArrayBitVector> keySet() {
			return new AbstractObjectSet<>() {

				@Override
				public ObjectIterator<LongArrayBitVector> iterator() {
					return new ObjectIterator<>() {
						private int i = 0;
						private int pos = -1;

						@Override
						public boolean hasNext() {
							return i < size;
						}

						@Override
						public LongArrayBitVector next() {
							if (! hasNext()) throw new NoSuchElementException();
							while(node[++pos] == null);
							i++;
							return LongArrayBitVector.copy(node[pos].handle(transform));
						}
					};
				}

				@Override
				public boolean contains(final Object o) {
					final BitVector v = (BitVector)o;
					return get(v) != null;
				}

				@Override
				public int size() {
					return size;
				}

			};
		}

		public ObjectSet<Node<U>> values() {
			return new AbstractObjectSet<>() {

				@Override
				public ObjectIterator<Node<U>> iterator() {
					return new ObjectIterator<>() {
						private int i = 0;
						private int pos = -1;

						@Override
						public boolean hasNext() {
							return i < size;
						}

						@Override
						public Node<U> next() {
							if (! hasNext()) throw new NoSuchElementException();
							while(node[++pos] == null);
							i++;
							return node[pos];
						}
					};
				}

				@Override
				public boolean contains(final Object o) {
					@SuppressWarnings("unchecked")
					final Node<U> node = (Node<U>)o;
					return get(node.handle(transform)) != null;
				}

				@Override
				public int size() {
					return size;
				}
			};
		}

		/** Replaces an entry with a given node.
		 *
		 * @param oldNode a node appearing in the table.
		 * @param newNode a node with the same handle as {@code oldNode}.
		 * @param s the signature of the handle of {@code oldNode} and {@code newNode}.
		 */
		public void replaceExisting(final InternalNode<U> oldNode, final InternalNode<U> newNode, final long s) {
			if (DEBUG) System.err.println("Map.replaceExisting(" + oldNode + ", " + newNode + ", " + s + ")");
			int pos = hash(s);
			while(node[pos] != oldNode) pos = (pos + 1) & mask;
			assert node[pos].handle(transform).equals(newNode.handle(transform)) : node[pos].handle(transform) + " != " + newNode.handle(transform);
			node[pos] = newNode;
			if (ASSERTS) assertTable();
		}

		/** Removes an existing entry from the table.
		 *
		 * @param n the node to be removed.
		 * @param s the signature of the handle of {@code n}.
		 *
		 * @throws IllegalStateException if {@code n} is not in the table.
		 */
		public void removeExisting(final InternalNode<U> n, final long s) {
			if (DEBUG) System.err.println("Map.removeExisting(" + n + ", " + s + ")");

			int pos = hash(s);
			int lastDup = -1; // Keeps track of the last duplicate entry with the same signature.

			while (node[pos] != n) {
				if ((signature[pos] & SIGNATURE_MASK) == s) lastDup = pos;
				pos = (pos + 1) & mask;
			}

			if (node[pos] == null) throw new IllegalStateException();
			if ((signature[pos] & DUPLICATE_MASK) == 0 && lastDup != -1) signature[lastDup] &= SIGNATURE_MASK;  // We are removing the only non-duplicate entry.

			// Move entries, compatibly with their hash code, to fill the hole.
		    int candidateHole, h;
		    do {
				candidateHole = pos;
		    	// Find candidate for a move (possibly empty).
				do {
					pos = (pos + 1) & mask;
					if (node[pos] == null) break;
					h = hash(signature[pos] & SIGNATURE_MASK);
					/* The hash h must lie cyclically between candidateHole and pos: more precisely, h must be after candidateHole
					 * but before the first free entry in the table (which is equivalent to the previous statement). */
				} while(candidateHole <= pos ? candidateHole < h && h <= pos : candidateHole < h || h <= pos);

				node[candidateHole] = node[pos];
				signature[candidateHole] = signature[pos];
		    } while(node[pos] != null);

			size--;
			if (ASSERTS) assertTable();
		}

		/** Adds a new entry to the table.
		 *
		 * @param v a node.
		 *
		 * @see #addNew(ZFastTrie.InternalNode, long)
		 */
		public void addNew(final InternalNode<U> v) {
			addNew(v, v.handleHash(transform));
		}

		/** Adds a new entry to the table.
		 *
		 * <p>Note that as long as the handle of the given node is not in the
		 * table this function will always perform correctly. Otherwise,
		 * the table will end up containing two copies of the same key (i.e., handle).
		 *
		 * @param n a node.
		 * @param s the signature of the handle of {@code n}.
		 */
		public void addNew(final InternalNode<U> n, long s) {
			if (DEBUG) System.err.println("Map.addNew(" + n + ", " + s + ")");
			int pos = hash(s);

			/* Finds a free position, marking the only non-duplicate key (if any) with
			 * the same signature along the search path as a duplicate. */
			while(node[pos] != null) {
				if (signature[pos] == s) signature[pos] |= DUPLICATE_MASK;
				pos = (pos + 1) & mask;
			}

			size++;
			signature[pos] = s;
			node[pos] = n;

			if (2L * length < 3L * size) {
				length *= 2;
				mask = length - 1;
				final long newKey[] = new long[length];
				@SuppressWarnings("unchecked")
				final InternalNode<U>[] newValue = new InternalNode[length];
				final long[] key = this.signature;
				final InternalNode<U>[] value = this.node;

				for(int i = key.length; i-- != 0;) {
					if (value[i] != null) {
						s = key[i] & SIGNATURE_MASK;
						pos = hash(s);
						while(newValue[pos] != null) {
							if ((newKey[pos] & SIGNATURE_MASK) == s) newKey[pos] |= DUPLICATE_MASK;
							pos = (pos + 1) & mask;
						}
						newKey[pos] = s;
						newValue[pos] = value[i];
					}
				}

				this.signature = newKey;
				this.node = newValue;
			}

			if (ASSERTS) assertTable();
		}

		public int size() {
			return size;
		}

		/**
		 * Retrieves a node given its handle.
		 *
		 * @param handle a handle.
		 * @param exact whether the search should be exact; if false, and the given handle does not appear
		 *            in the table, it is possible that an unpredictable internal node is returned.
		 *
		 * @return the node with given handle, or {@code null} if there is no such node (if {@code exact} is
		 *         false, a false positive might be returned).
		 *
		 * @see #get(BitVector, long, long, boolean)
		 */
		public InternalNode<U> get(final BitVector handle) {
			return findExact(handle, handle.length(), Hashes.murmur(handle, 42) & SIGNATURE_MASK);
		}

		@Override
		public String toString() {
			final StringBuilder s = new StringBuilder();
			s.append('{');
			for (final LongArrayBitVector v : keySet()) s.append(v).append(" => ").append(get(v)).append(", ");
			if (s.length() > 1) s.setLength(s.length() - 2);
			s.append('}');
			return s.toString();
		}
	}

	/** A node of the trie. */
	protected abstract static class Node<U> {
		/** The length of the name of the node. */
		protected long nameLength;

		public boolean isLeaf() {
			return this instanceof Leaf;
		}

		public boolean isInternal() {
			return this instanceof InternalNode;
		}

		public long handleLength(final TransformationStrategy<? super U> transform) {
			return twoFattest(nameLength - 1, extentLength(transform));
		}

		public abstract BitVector key(TransformationStrategy<? super U> transform);
		public abstract BitVector handle(TransformationStrategy<? super U> transform);
		public abstract long extentLength(TransformationStrategy<? super U> transform);
		public abstract BitVector extent(TransformationStrategy<? super U> transform);
		public abstract boolean intercepts(final long h);

		public long handleHash(final TransformationStrategy<? super U> transform) {
			return Hashes.murmur(handle(transform), 42) & SIGNATURE_MASK;
		}

		/** Returns true if this node is the exit node of a string.
		 *
		 * @param v the string.
		 * @param transform the transformation strategy used to build the trie this node belongs to.
		 * @return true if the string exits at this node.
		 */
		public boolean isExitNodeOf(final LongArrayBitVector v, final TransformationStrategy<? super U> transform) {
			return isExitNodeOf(v.length(), v.longestCommonPrefixLength(extent(transform)), transform);
		}

		/** Returns true if this node is the exit node of a string given its length and the length of the longest
		 * common prefix with the node extent.
		 *
		 * @param length the length of a string.
		 * @param lcpLength the length of the longest common prefix between the string and the extent of this node.
		 * @param transform the transformation strategy used to build the trie this node belongs to.
		 * @return true if the string exits at this node.
		 */
		public boolean isExitNodeOf(final long length, final long lcpLength, final TransformationStrategy<? super U> transform) {
			return (nameLength <= lcpLength) && (lcpLength < extentLength(transform) || lcpLength == length);
		}


		public Leaf<U> leftLeaf() {
			Node<U> node = this;
			while(node.isInternal()) node = ((InternalNode<U>)node).jumpLeft;
			return (Leaf<U>)node;
		}

		public Leaf<U> rightLeaf() {
			Node<U> node = this;
			while(node.isInternal()) node = ((InternalNode<U>)node).jumpRight;
			return ((Leaf<U>)node);
		}

		@Override
		@SuppressWarnings({"unchecked"})
		public String toString() {
			final Object key = isInternal() ? ((InternalNode<U>)this).reference.key : ((Leaf<U>)this).key;
			final TransformationStrategy transform = key instanceof CharSequence ? TransformationStrategies.prefixFreeIso() : key instanceof Long ? TransformationStrategies.fixedLong() : TransformationStrategies.identity();
			final long extentLength = extentLength(transform);
			return (isLeaf() ? "[" : "(") + Integer.toHexString(hashCode() & 0xFFFF) +
				(key(transform) == null ? "" :
					" " + (extentLength > 16 ? key(transform).subVector(0, 8) + "..." + key(transform).subVector(extentLength - 8, extentLength): key(transform).subVector(0, extentLength))) +
					" [" + nameLength + ".." + extentLength + "], " + (isInternal() ? ((InternalNode<U>)this).handleLength() + "->" + ((InternalNode<U>)this).jumpLength() : "") +
				(isLeaf() ? "]" : ")");
		}

		public String toString(final TransformationStrategy<? super U> transform) {
			final long extentLength = extentLength(transform);
			final Object key = isInternal() ? ((InternalNode<U>)this).reference.key : ((Leaf<U>)this).key;
			return (isLeaf() ? "[" + key + " " : "(") + Integer.toHexString(hashCode() & 0xFFFF) + (key(transform) == null ? "" : " " + (extentLength > 16 ? key(transform).subVector(0, 8) + "..." + key(transform).subVector(extentLength - 8, extentLength) : key(transform).subVector(0, extentLength))) + " [" + nameLength + ".." + extentLength + "], " + (isInternal() ? ((InternalNode<U>)this).handleLength() + "->" + ((InternalNode<U>)this).jumpLength() : "") + (isLeaf() ? "]" : ")");
		}
	}

	/** A internal node. */
	protected final static class InternalNode<U> extends Node<U> {
		/** The length of the extent (for leaves, this is equal to the length of the transformed {@link #key}, which
		 * is returned by {@link #extentLength(TransformationStrategy)}). */
		protected long extentLength;
		/** The left subtrie. */
		protected Node<U> left;
		/** The right subtrie. */
		protected Node<U> right;
		/** The left jump pointer. */
		protected Node<U> jumpLeft;
		/** The right jump pointer. */
		protected Node<U> jumpRight;
		/** The leaf whose key this node refers to. */
		protected Leaf<U> reference;

		public long handleLength() {
			return twoFattest(nameLength - 1, extentLength);
		}

		public long jumpLength() {
			final long handleLength = handleLength();
			if (handleLength == 0) return Long.MAX_VALUE; // This only happens on a root node with empty extent.
			return handleLength + (handleLength & -handleLength);
		}

		@Override
		public boolean isLeaf() {
			return false;
		}

		@Override
		public boolean isInternal() {
			return true;
		}

		@Override
		public boolean intercepts(final long h) {
			return h >= nameLength && h <= extentLength;
		}

		@Override
		public BitVector extent(final TransformationStrategy<? super U> transform) {
			return reference.key(transform).subVector(0, extentLength);
		}

		@Override
		public long extentLength(final TransformationStrategy<? super U> transform) {
			return extentLength;
		}

		@Override
		public BitVector key(final TransformationStrategy<? super U> transform) {
			return reference.key(transform);
		}

		@Override
		public BitVector handle(final TransformationStrategy<? super U> transform) {
			return reference.key(transform).subVector(0, handleLength());
		}
	}

	/** An external node, a&#46;k&#46;a&#46; leaf. */
	protected final static class Leaf<U> extends Node<U> {
		/** The previous leaf. */
		protected Leaf<U> prev;
		/** The next leaf. */
		protected Leaf<U> next;
		/** The key associated to this leaf. */
		@SuppressWarnings("null")
		protected U key;
		/** The internal node that refers to the key of this leaf, if any. It will be {@code null} for exactly one leaf. */
		protected InternalNode<U> reference;

		@Override
		public BitVector handle(final TransformationStrategy<? super U> transform) {
			return reference.key(transform).subVector(0, handleLength(transform));
		}

		@Override
		public boolean isLeaf() {
			return true;
		}

		@Override
		public boolean isInternal() {
			return false;
		}

		@Override
		public boolean intercepts(final long h) {
			return h >= nameLength;
		}

		@Override
		public BitVector extent(final TransformationStrategy<? super U> transform) {
			return key(transform);
		}

		@Override
		public long extentLength(final TransformationStrategy<? super U> transform) {
			return transform.length(key);
		}

		@Override
		public BitVector key(final TransformationStrategy<? super U> transform) {
			return transform.toBitVector(key);
		}
	}

	/** Creates a new z-fast trie using the given transformation strategy.
	 *
	 * @param transform a transformation strategy that must turn distinct elements into distinct, prefix-free bit vectors.
	 */
	public ZFastTrie(final TransformationStrategy<? super T> transform) {
		this.transform = transform;
		this.handle2Node = new Handle2NodeMap<>(transform);
		initHeadTail();
	}

	private void initHeadTail() {
		head = new Leaf<>();
		tail = new Leaf<>();
		head.next = tail;
		tail.prev = head;
	}

	/** Creates a new z-fast trie using the given elements and transformation strategy.
	 *
	 * @param elements an iterator returning the elements to be inserted in the trie.
	 * @param transform a transformation strategy that must turn distinct elements into distinct, prefix-free bit vectors.
	 */
	public ZFastTrie(final Iterator<? extends T> elements, final TransformationStrategy<? super T> transform) {
		this(transform);
		while(elements.hasNext()) add(elements.next());
	}

	/** Creates a new z-fast trie using the given elements and transformation strategy.
	 *
	 * @param elements an iterator returning the elements to be inserted in the trie.
	 * @param transform a transformation strategy that must turn distinct elements into distinct, prefix-free bit vectors.
	 */
	public ZFastTrie(final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform) {
		this(elements.iterator(), transform);
	}

	@Override
	public int size() {
		return size > Integer.MAX_VALUE ? -1 : size;
	}

	/**
	 * Returns the 2-fattest number in an interval.
	 *
	 * <p>
	 * Note that to get the length of the handle of a node you must call this function passing the
	 * length of the extent of the parent (one less than the node name) and the length of the extent of
	 * the node.
	 *
	 * @param a left extreme, &ge;-1 (excluded).
	 * @param b right extreme, &ge; 0 (included).
	 * @return the 2-fattest number in ({@code a}..{@code b}].
	 */
	public final static long twoFattest(final long a, final long b) {
		return (-1L << Fast.mostSignificantBit(a ^ b) & b);
	}

	/**
	 * Returns the mask used for check for 2-fattest numbers when the left extreme of the interval is
	 * not -1.
	 *
	 * <p>
	 * Note that to get the length of the handle of a node you must call this function passing the
	 * length of the extent of the parent (one less than the node name) and the length of the extent of
	 * the node.
	 *
	 * @param a left extreme, &ge;-1 (excluded).
	 * @param b right extreme, &ge; 0 (included).
	 * @return &minus;1 &ll; &lambda;({@code a} &oplus; {@code b}), the initial mask for fat binary
	 *         search in {@code (a..b]}.
	 */
	public final static long checkMask(final long a, final long b) {
		return -1L << 63 - Long.numberOfLeadingZeros(a ^ b);
	}

	/**
	 * Returns the mask used for check for 2-fattest numbers when the left extreme of the interval is
	 * &minus;1.
	 *
	 * @param b right extreme, &ge; 0 (included).
	 * @return &minus;1 &ll; &lambda;{@code b} + 1, the initial mask for fat binary search in
	 *         {@code (-1..b]}.
	 */
	public final static long checkMask(final long b) {
		return -1L << 64 - Long.numberOfLeadingZeros(b);
	}

	private static <U> void removeLeaf(final Leaf<U> node) {
		node.next.prev = node.prev;
		node.prev.next = node.next;
	}

	private static <U> void addAfter(final Leaf<U> pred, final Leaf<U> node) {
		node.next = pred.next;
		node.prev = pred;
		pred.next.prev = node;
		pred.next = node;
	}

	private static <U> void addBefore(final Leaf<U> succ, final Leaf<U> node) {
		node.prev = succ.prev;
		node.next = succ;
		succ.prev.next = node;
		succ.prev = node;
	}

	private void assertTrie() {
		assert root == null || root.nameLength == 0 : root.nameLength + " != " + 0;
		/* Shortest key */
		LongArrayBitVector root = null;
		/* Keeps track of which nodes in map are reachable using the handle-2-node
		 * map first, and then left/right pointer from the root. */
		final ObjectOpenHashSet<Node<T>> nodes = new ObjectOpenHashSet<>();
		/* Keeps track of leaves. */
		final ObjectOpenHashSet<Leaf<T>> leaves = new ObjectOpenHashSet<>();
		/* Keeps track of reference to leaf keys in internal nodes. */
		final ObjectOpenHashSet<T> references = new ObjectOpenHashSet<>();

		assert size == 0 && handle2Node.size() == 0 || size == handle2Node.size() + 1;

		/* Search for the root (shortest handle) and check that nodes and handles do match. */
		for(final LongArrayBitVector v : handle2Node.keySet()) {
			final long vHandleLength = handle2Node.get(v).handleLength();
			if (root == null || handle2Node.get(root).handleLength() > vHandleLength) root = v;
			final InternalNode<T> node = handle2Node.get(v);
			nodes.add(node);
			assert node.reference.reference == node : node + " -> " + node.reference + " -> " + node.reference.reference;
		}

		assert nodes.size() == handle2Node.size() : nodes.size() + " != " + handle2Node.size();
		assert size < 2 || this.root == handle2Node.get(root);

		if (size > 1) {
			/* Verify doubly linked list of leaves. */
			Leaf<T> toRight = head.next, toLeft = tail.prev;
			if (head.next.key instanceof CharSequence) {
				for(int i = 1; i < size; i++) {
					assert new MutableString((CharSequence)toRight.key).compareTo((CharSequence)toRight.next.key) < 0 : toRight.key + " >= " + toRight.next.key + " " + toRight + " " + toRight.next;
					assert new MutableString((CharSequence)toLeft.key).compareTo((CharSequence)toLeft.prev.key) > 0 : toLeft.key + " >= " + toLeft.prev.key + " " + toLeft + " " + toLeft.prev;
					toRight = toRight.next;
					toLeft = toLeft.prev;
				}
			}

			final int numNodes = visit(handle2Node.get(root), null, 0, 0, nodes, leaves, references);
			assert numNodes == 2 * size - 1 : numNodes + " != " + (2 * size - 1);
			assert leaves.size() == size;
			int c = 0;

			for(final Leaf<T> leaf: leaves) if (references.contains(leaf.key)) c++;

			assert c == size - 1 : c + " != " + (size - 1);
		}
		else if (size == 1) {
			assert head.next == this.root;
			assert tail.prev == this.root;
		}
		assert nodes.isEmpty();
	}

	private int visit(final Node<T> n, final Node<T> parent, final long nameLength, final int depth, final ObjectOpenHashSet<Node<T>> nodes, final ObjectOpenHashSet<Leaf<T>> leaves, final ObjectOpenHashSet<T> references) {
		if (n == null) return 0;
		if (DEBUG) {
			for(int i = depth; i-- != 0;) System.err.print('\t');
			System.err.println("Node " + n + " (name length: " + nameLength + ")" + (n.isInternal() ? " Jump left: " + ((InternalNode<T>)n).jumpLeft + " Jump right: " + ((InternalNode<T>)n).jumpRight : ""));
		}

		assert parent == null || parent.extent(transform).equals(n.extent(transform).subVector(0, ((InternalNode<T>)parent).extentLength));
		assert nameLength <= n.extentLength(transform);
		assert n.nameLength == nameLength : n.nameLength + " != " + nameLength + " " + n;

		if (n.isInternal()) {
			assert references.add(((InternalNode<T>)n).reference.key);
			assert nodes.remove(n) : n;
			assert handle2Node.keySet().contains(n.handle(transform)) : n;

			/* Check that jumps are correct. */
			final long jumpLength = ((InternalNode<T>)n).jumpLength();
			Node<T> jumpLeft = ((InternalNode<T>)n).left;
			while(jumpLeft.isInternal() && jumpLength > ((InternalNode<T>)jumpLeft).extentLength) jumpLeft = ((InternalNode<T>)jumpLeft).left;
			assert jumpLeft == ((InternalNode<T>)n).jumpLeft : jumpLeft + " != " + ((InternalNode<T>)n).jumpLeft + " (node: " + n + ")";

			Node<T> jumpRight = ((InternalNode<T>)n).right;
			while(jumpRight.isInternal() && jumpLength > ((InternalNode<T>)jumpRight).extentLength) jumpRight = ((InternalNode<T>)jumpRight).right;
			assert jumpRight == ((InternalNode<T>)n).jumpRight : jumpRight + " != " + ((InternalNode<T>)n).jumpRight + " (node: " + n + ")";
			return 1 + visit(((InternalNode<T>)n).left, n, ((InternalNode<T>)n).extentLength + 1, depth + 1, nodes, leaves, references) + visit(((InternalNode<T>)n).right, n, n.extentLength(transform) + 1, depth + 1, nodes, leaves, references);
		}
		else {
			assert leaves.add((Leaf<T>)n);
			assert n.extentLength(transform) == n.key(transform).length();
			return 1;
		}
	}

	/**
	 * Sets the jump pointers of a node by searching exhaustively for handles that are jumps of the node
	 * handle length.
	 *
	 * @param node the node whose jump pointers must be set.
	 */
	private static <U> void setJumps(final InternalNode<U> node) {
		if (DEBUG) System.err.println("setJumps(" + node + ")");
		final long jumpLength = node.jumpLength();
		Node<U> jump;

		for (jump = node.right; jump.isInternal() && jumpLength > ((InternalNode<U>)jump).extentLength;) jump = ((InternalNode<U>)jump).jumpRight;
		assert jump.intercepts(jumpLength) : jumpLength + " not in " + "[" + jump.nameLength + ".." + ((InternalNode<U>)jump).extentLength + "] " + jump;
		node.jumpRight = jump;

		for (jump = node.left; jump.isInternal() && jumpLength > ((InternalNode<U>)jump).extentLength;) jump = ((InternalNode<U>)jump).jumpLeft;
		assert jump.intercepts(jumpLength) : jumpLength + " not in " + "[" + jump.nameLength + ".." + ((InternalNode<U>)jump).extentLength + "] " + jump;
		node.jumpLeft = jump;
	}

	/**
	 * Fixes the left jumps of the ancestors of a node after an insertion when the exit node used to be
	 * right child.
	 *
	 * @param internal the new internal node.
	 * @param exitNode the exit node.
	 * @param keyExitsRight whether the new leaf is on the right.
	 * @param leaf the new leaf.
	 * @param stack a stack containing the 2-fat ancestors of the parent of the exit node.
	 */
	private static <U> void fixJumpsForRightExitNode(final InternalNode<U> internal, final Node<U> exitNode, final boolean keyExitsRight, final Leaf<U> leaf, final ObjectArrayList<InternalNode<U>> stack) {
		if (DEBUG) System.err.println("fixJumpsAfterRightInsertion(" + internal + ", " + exitNode + ", " + keyExitsRight + ", " + leaf + ", " + stack);
		final long leafNameLength = leaf.nameLength;
		InternalNode<U> toBeFixed;
		long jumpLength;

		/* In the first phase, we look for jump pointers that were pointing
		 * to the exit node but now must point to the new internal node. */
		while (!stack.isEmpty()) {
			toBeFixed = stack.top();
			jumpLength = toBeFixed.jumpLength();
			if (toBeFixed.jumpRight != exitNode || jumpLength >= leafNameLength) break;
			toBeFixed.jumpRight = internal;
			stack.pop();
		}

		if (keyExitsRight) {
			/* If the key exits on the right and the exit node is a right child,
			 * all remaining pointers pointing to a left descendant of the exit node
			 * must point to the new leaf. We follow the jump left pointer of the
			 * exit node, popping in parallel elements from the stack, and update. */
			Node<U> exitNodeRightDesc = exitNode;
			while (!stack.isEmpty()) {
				toBeFixed = stack.pop();
				while (exitNodeRightDesc.isInternal() && toBeFixed.jumpRight != exitNodeRightDesc) exitNodeRightDesc = ((InternalNode<U>)exitNodeRightDesc).jumpRight;
				if (toBeFixed.jumpRight != exitNodeRightDesc) return; // ! exitnodeRightDesc.isInternal()
				toBeFixed.jumpRight = leaf;
			}
		}
	}

	/**
	 * Fixes the right jumps of the ancestors of a node after an insertion when the exit node used to be
	 * left child.
	 *
	 * @param internal the new internal node.
	 * @param exitNode the exit node.
	 * @param keyExitsRight whether the new leaf is on the right.
	 * @param leaf the new leaf.
	 * @param stack a stack containing the 2-fat ancestors of the parent of the exit node.
	 */
	private static <U> void fixJumpsForLeftExitNode(final InternalNode<U> internal, final Node<U> exitNode, final boolean keyExitsRight, final Leaf<U> leaf, final ObjectArrayList<InternalNode<U>> stack) {
		if (DEBUG) System.err.println("fixJumpsAfterRightInsertion(" + internal + ", " + exitNode + ", " + keyExitsRight + ", " + leaf + ", " + stack);
		final long leafNameLength = leaf.nameLength;
		InternalNode<U> toBeFixed;
		long jumpLength;

		/* In the first phase, we look for jump pointers that were pointing
		 * to the exit node but now must point to the new internal node. */
		while (!stack.isEmpty()) {
			toBeFixed = stack.top();
			jumpLength = toBeFixed.jumpLength();
			if (toBeFixed.jumpLeft != exitNode || jumpLength >= leafNameLength) break;
			toBeFixed.jumpLeft = internal;
			stack.pop();
		}

		if (!keyExitsRight) {
			/* If the key exits on the left and the exit node is a left child,
			 * all remaining pointers pointing to a left descendant of the exit node
			 * must point to the new leaf. We follow the jump left pointer of the
			 * exit node, popping in parallel elements from the stack, and update. */
			Node<U> exitNodeLeftDesc = exitNode;
			while (!stack.isEmpty()) {
				toBeFixed = stack.pop();
				while (exitNodeLeftDesc.isInternal() && toBeFixed.jumpLeft != exitNodeLeftDesc) exitNodeLeftDesc = ((InternalNode<U>)exitNodeLeftDesc).jumpLeft;
				if (toBeFixed.jumpLeft != exitNodeLeftDesc) return; // ! exitnodeLeftDesc.isInternal()
				toBeFixed.jumpLeft = leaf;
			}
		}
	}

	/**
	 * Fixes the right jumps of the ancestors of a node after a deletion when the parex node is a right
	 * child.
	 *
	 * @param parentExitNode the parent of the exit node.
	 * @param exitNode the exit node.
	 * @param otherNode the other child of the parent of the exit node.
	 * @param exitNodeIsRightChild whether the exit node is a right child.
	 * @param stack a stack containing the 2-fat ancestors of the grandparent of the exit node.
	 */
	private static <U> void fixJumpsForRightParexNode(final InternalNode<U> parentExitNode, final Leaf<U> exitNode, Node<U> otherNode, final boolean exitNodeIsRightChild, final ObjectArrayList<InternalNode<U>> stack) {
		if (DEBUG) System.err.println("fixJumpForRightParexNode(" + parentExitNode + ", " + exitNode + ", " + otherNode + ", " + exitNodeIsRightChild + ", " + stack);
		InternalNode<U> toBeFixed;
		long jumpLength;

		/* In the first phase, we look for jump pointers that were pointing
		 * to the parent of the exit node but now must point the other node. */
		while (!stack.isEmpty()) {
			toBeFixed = stack.top();
			jumpLength = toBeFixed.jumpLength();
			if (toBeFixed.jumpRight != parentExitNode) break;
			toBeFixed.jumpRight = otherNode;
			stack.pop();
		}

		if (exitNodeIsRightChild) {
			/* If the parex node is a right child and the exit node is a right child
			 * all remaining pointers pointing to the exit node must point to
			 * the correct right descendant of the other node. We follow the right
			 * jump pointer of the other node, popping in parallel elements
			 * from the stack, and update. */
			while (!stack.isEmpty()) {
				toBeFixed = stack.pop();
				if (toBeFixed.jumpRight != exitNode) break;
				jumpLength = toBeFixed.jumpLength();
				while (!otherNode.intercepts(jumpLength)) otherNode = ((InternalNode<U>)otherNode).jumpRight;
				toBeFixed.jumpRight = otherNode;
			}
		}
	}

	/**
	 * Fixes the left jumps of the ancestors of a node after a deletion when the parex node is a left
	 * child.
	 *
	 * @param parentExitNode the parent of the exit node.
	 * @param exitNode the exit node.
	 * @param otherNode the other child of the parent of the exit node.
	 * @param exitNodeIsRightChild whether the exit node is a right child.
	 * @param stack a stack containing the 2-fat ancestors of the grandparent of the exit node.
	 */
	private static <U> void fixJumpsForLeftParexNode(final InternalNode<U> parentExitNode, final Leaf<U> exitNode, Node<U> otherNode, final boolean exitNodeIsRightChild, final ObjectArrayList<InternalNode<U>> stack) {
		if (DEBUG) System.err.println("fixLeftJumpsAfterDeletion(" + parentExitNode + ", " + exitNode + ", " + otherNode + ", " + exitNodeIsRightChild + ", " + stack);
		InternalNode<U> toBeFixed;
		long jumpLength;

		/* In the first phase, we look for jump pointers that were pointing
		 * to the parent of the exit node but now must point the other node. */
		while (!stack.isEmpty()) {
			toBeFixed = stack.top();
			jumpLength = toBeFixed.jumpLength();
			if (toBeFixed.jumpLeft != parentExitNode) break;
			toBeFixed.jumpLeft = otherNode;
			stack.pop();
		}

		if (!exitNodeIsRightChild) {
			/* If the parex node is a left child and the exit node is a left child
			 * all remaining pointers pointing to the exit node must point to
			 * the correct left descendant of the other node. We follow the right
			 * jump pointer of the other node, popping in parallel elements
			 * from the stack, and update. */
			while(! stack.isEmpty()) {
				toBeFixed = stack.pop();
				if (toBeFixed.jumpLeft != exitNode) break;
				jumpLength = toBeFixed.jumpLength();
				while(! otherNode.intercepts(jumpLength)) otherNode = ((InternalNode<U>)otherNode).jumpLeft;
				toBeFixed.jumpLeft = otherNode;
			}
		}
	}

	@Override
	public boolean add(final T k) {
		if (DEBUG) System.err.println("add(" + k + ")");
		final LongArrayBitVector v = LongArrayBitVector.copy(transform.toBitVector(k));
		if (DEBUG) System.err.println("add(" + v + ")");

		if (size == 0) {
			final Leaf<T> leaf = new Leaf<>();
			leaf.key = k;
			leaf.nameLength = 0;
			leaf.reference = null;
			addAfter(head, leaf);
			root = leaf;
			size++;
			if (ASSERTS) {
				assertTrie();
				assert contains(k) : k;
			}
			return true;
		}

		final ObjectArrayList<InternalNode<T>> stack = new ObjectArrayList<>(64);

		final long[] state = Hashes.preprocessMurmur(v, 42);
		final ParexData<T> parexData = getParentExitNode(v, state, stack);
		if (ASSERTS) assertParent(v, parexData, stack);

		final InternalNode<T> parentExitNode = parexData.parexNode;
		final Node<T> exitNode = parexData.exitNode;
		final long lcp = parexData.lcp;
		final boolean exitNodeIsRightChild = parentExitNode != null && parentExitNode.right == exitNode;

		if (DDEBUG) System.err.println("Parex node: " + parentExitNode + " Exit node: " + exitNode  + " LCP: " + lcp);

		if (exitNode.isLeaf()) {
			// We check whether k is already in the trie, or if it is a proper
			// extension of a bit vector in the trie.
			final long exitNodeExtentLength = transform.length(((Leaf<T>)exitNode).key);
			if (exitNodeExtentLength == parexData.lcp) { // k is an extension of exitNode.key
				if (v.length() == exitNodeExtentLength) return false; // Already there
				assert v.length() > exitNodeExtentLength;
				throw new IllegalArgumentException("Cannot add key " + k + " as it is a proper extension of " + exitNode.key(transform));
			}
		}

		// k is a proper prefix of the extent of the exit node, and thus of a key in the trie
		if (parexData.lcp == v.length()) throw new IllegalArgumentException("Cannot add key " + k + " as it is a proper prefix of " + exitNode.key(transform));

		final boolean exitDirection = v.getBoolean(lcp);
		final long exitNodeHandleLength = exitNode.handleLength(transform);
		final boolean cutLow = lcp >= exitNodeHandleLength;

		final Leaf<T> leaf = new Leaf<>();
		final InternalNode<T> internal = new InternalNode<>();

		final boolean exitNodeIsInternal = exitNode.isInternal();

		leaf.key = k;
		leaf.nameLength = lcp + 1;
		leaf.reference = internal;

		internal.reference = leaf;
		internal.nameLength = exitNode.nameLength;
		internal.extentLength = lcp;

		if (exitDirection) {
			internal.jumpRight = internal.right = leaf;
			internal.left = exitNode;
			internal.jumpLeft = cutLow && exitNodeIsInternal ? ((InternalNode<T>)exitNode).jumpLeft : exitNode;
		}
		else {
			internal.jumpLeft = internal.left = leaf;
			internal.right = exitNode;
			internal.jumpRight = cutLow && exitNodeIsInternal ? ((InternalNode<T>)exitNode).jumpRight : exitNode;
		}

		if (exitNode == root) root = internal; // Update root
		else {
			if (exitNodeIsRightChild) parentExitNode.right = internal;
			else parentExitNode.left = internal;
		}

		if (DDEBUG) System.err.println("Cut " + (cutLow ? "low" : "high") + "; exit to the " + (exitDirection ? "right" : "left"));

		if (exitNodeIsRightChild) fixJumpsForRightExitNode(internal, exitNode, exitDirection, leaf, stack);
		else fixJumpsForLeftExitNode(internal, exitNode, exitDirection, leaf, stack);

		if (cutLow && exitNodeIsInternal) {
			handle2Node.replaceExisting((InternalNode<T>)exitNode, internal, Hashes.murmur(v, exitNodeHandleLength, state) & SIGNATURE_MASK);
			exitNode.nameLength = lcp + 1;
			handle2Node.addNew((InternalNode<T>)exitNode, Hashes.murmur(exitNode.key(transform), exitNode.handleLength(transform), state, lcp) & SIGNATURE_MASK);
			setJumps((InternalNode<T>)exitNode);
		}
		else {
			exitNode.nameLength = lcp + 1;
			handle2Node.addNew(internal, Hashes.murmur(v, internal.handleLength(), state) & SIGNATURE_MASK);
		}


		if (DEBUG) System.err.println("After insertion, map: " + handle2Node + " root: " + root);

		size++;

		/* We find a predecessor or successor to insert the new leaf in the doubly linked list. */
		if (exitDirection) addAfter(exitNode.rightLeaf(), leaf);
		else addBefore(exitNode.leftLeaf(), leaf);

		if (ASSERTS) {
			assertTrie();
			assert contains(k) : k;
		}

		return true;
	}

	@Override
	@SuppressWarnings({ "unchecked", "null" })
	public boolean remove(final Object k) {
		if (DEBUG) System.err.println("remove(" + k + ")");
		if (size == 0) return false;

		final LongArrayBitVector v = LongArrayBitVector.copy(transform.toBitVector((T)k));

		if (size == 1) {
			if (! ((Leaf<T>)root).key.equals(k)) return false;
			removeLeaf((Leaf<T>)root);
			root = null;
			size = 0;
			if (ASSERTS) {
				assertTrie();
				assert ! contains(k) : k;
			}
			return true;
		}

		final ObjectArrayList<InternalNode<T>> stack = new ObjectArrayList<>(64);

		final long[] state = Hashes.preprocessMurmur(v, 42);

		final ParexData<T> parexData = getParentExitNode(v, state, stack);
		if (ASSERTS) assertParent(v, parexData, stack);

		final InternalNode<T> parentExitNode = parexData.parexNode;
		final Node<T> exitNode = parexData.exitNode;
		final long lcp = parexData.lcp;

		if (DDEBUG) System.err.println("Parex node: " + parentExitNode + " Exit node: " + exitNode  + " LCP: " + lcp);

		// Not found
		if (!(exitNode.isLeaf() && transform.length(((Leaf<T>)exitNode).key) == parexData.lcp && v.length() == parexData.lcp)) return false;

		// size > 1 && exitNode().isLeaf() => parentExitNode != null
		final boolean exitNodeIsRightChild = parentExitNode.right == exitNode;
		final Node<T> otherNode = exitNodeIsRightChild ? parentExitNode.left : parentExitNode.right;
		final boolean otherNodeIsInternal = otherNode.isInternal();

		boolean parexIsRightChild = false;

		if (parentExitNode == root) root = otherNode;
		else {
			// Let us fix grandpa's child pointer and update the stack.
			getGrandParentExitNode(v, state, stack);
			final InternalNode<T> grandParentExitNode = stack.top();
			if (DDEBUG) System.err.println("Grandparex node: " + grandParentExitNode);
			assert grandParentExitNode.left == parentExitNode || grandParentExitNode.right == parentExitNode;
			if (parexIsRightChild = (grandParentExitNode.right == parentExitNode)) grandParentExitNode.right = otherNode;
			else grandParentExitNode.left = otherNode;
		}

		final long parentExitNodehandleLength = parentExitNode.handleLength();
		final long otherNodeHandleLength = otherNode.handleLength(transform);
		final long t = parentExitNodehandleLength | otherNodeHandleLength;
		// parentExitNodehandleLength is 2-fatter than otherNodeHandleLength
		final boolean cutLow = (t & -t & otherNodeHandleLength) != 0;


		// Fix leaf reference if not null
		final InternalNode<T> refersToExitNode = ((Leaf<T>)exitNode).reference;
		if (refersToExitNode == null) parentExitNode.reference.reference = null;
		else {
			refersToExitNode.reference = parentExitNode.reference;
			refersToExitNode.reference.reference = refersToExitNode;
		}

		// Fix doubly-linked list
		removeLeaf((Leaf<T>)exitNode);

		if (DDEBUG) System.err.println("Cut " + (cutLow ? "low" : "high") + "; leaf on the " + (exitNodeIsRightChild ? "right" : "left") + "; other node is " + (otherNodeIsInternal ? "internal" : "a leaf"));

		if (cutLow && otherNodeIsInternal) {
			handle2Node.removeExisting((InternalNode<T>)otherNode, Hashes.murmur(otherNode.key(transform), otherNodeHandleLength, state, parentExitNode.extentLength) & SIGNATURE_MASK);
			otherNode.nameLength = parentExitNode.nameLength;
			handle2Node.replaceExisting(parentExitNode, (InternalNode<T>)otherNode, Hashes.murmur(v, parentExitNodehandleLength, state) & SIGNATURE_MASK);
			// We just need to fix the jump on the side of the exit node
			setJumps((InternalNode<T>)otherNode);
		}
		else {
			otherNode.nameLength = parentExitNode.nameLength;
			handle2Node.removeExisting(parentExitNode, Hashes.murmur(v, parentExitNodehandleLength, state) & SIGNATURE_MASK);
		}

		assert parentExitNode != root || stack.isEmpty();

		if (parexIsRightChild) fixJumpsForRightParexNode(parentExitNode, (Leaf<T>)exitNode, otherNode, exitNodeIsRightChild, stack);
		else fixJumpsForLeftParexNode(parentExitNode, (Leaf<T>)exitNode, otherNode, exitNodeIsRightChild, stack);

		size--;

		if (ASSERTS) {
			assertTrie();
			assert ! contains(k) : k;
		}
		return true;
	}


	protected final static class ExitData<U> {
		protected final long lcp;
		protected final Node<U> exitNode;

		protected ExitData(final Node<U> exitNode, final long lcp) {
			this.lcp = lcp;
			this.exitNode = exitNode;
		}
	}

	/** Returns the exit node of a given bit vector.
	 *
	 * @param v a bit vector.
	 * @param state the hash state of {@code v} precomputed by {@link Hashes#preprocessMurmur(BitVector, long)}.
	 * @return the exit node of {@code v}.
	 */
	private ExitData<T> getExitNode(final LongArrayBitVector v, final long[] state) {
		if (DDEBUG) System.err.println("getExitNode(" + v + ")");
		final long length = v.length();

		// This can be the exit node of v, the parex node of v, or something completely wrong.
		InternalNode<T> parexOrExitNode = fatBinarySearch(v, state, length);

		// In this case, necessarily the search was successful and we have the parex.
		if (parexOrExitNode == null) return new ExitData<>(root, v.longestCommonPrefixLength(root.extent(transform)));
		long lcpLength = v.longestCommonPrefixLength(parexOrExitNode.extent(transform));

		// If the search is not correct, we redo it exactly
		if (lcpLength < parexOrExitNode.handleLength()) {
			parexOrExitNode = fatBinarySearchExact(v, state, length);
			lcpLength = v.longestCommonPrefixLength(parexOrExitNode.extent(transform));
		}

		// From here the search is correct (the second test catches keys equal to an extent).
		if (lcpLength >= parexOrExitNode.extentLength && length != parexOrExitNode.extentLength) {
			// We actually got the parex
			final Node<T> exitNode = v.getBoolean(parexOrExitNode.extentLength) ? parexOrExitNode.right : parexOrExitNode.left;
			return new ExitData<>(exitNode, v.longestCommonPrefixLength(exitNode.extent(transform)));
		}

		// We actually got the exit node
		return new ExitData<>(parexOrExitNode, lcpLength);
	}

	protected final static class ParexData<U> {
		protected final long lcp;
		protected final InternalNode<U> parexNode;
		protected final Node<U> exitNode;

		protected ParexData(final InternalNode<U> parexNode, final Node<U> exitNode, final long lcp) {
			this.lcp = lcp;
			this.parexNode = parexNode;
			this.exitNode = exitNode;
		}
	}

	/**
	 * Returns the parent of the exit node of a given bit vector.
	 *
	 * @param v a bit vector.
	 * @param state the hash state of {@code v} precomputed by
	 *            {@link Hashes#preprocessMurmur(BitVector, long)}.
	 * @param stack a stack that will be filled with the 2-fat ancestors.
	 * @return the parent of the exit node of {@code v}, or {@code null} if the exit node is the root.
	 */
	public ParexData<T> getParentExitNode(final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode<T>> stack) {
		if (DDEBUG) System.err.println("getParentExitNode(" + v + ")");
		final long length = v.length();

		// This can be the exit node of v, the parex node of v, or something completely wrong.
		fatBinarySearchStack(v, state, stack, length - 1);

		// In this case, necessarily the search was successful and we have the parex.
		if (stack.isEmpty()) return new ParexData<>(null, root, v.longestCommonPrefixLength(root.extent(transform)));

		final InternalNode<T> parexOrExitNode = stack.top();
		final long lcpLength = v.longestCommonPrefixLength(parexOrExitNode.extent(transform));

		// If the search is not correct, we redo it exactly
		if (lcpLength < parexOrExitNode.handleLength()) {
			stack.clear();
			fatBinarySearchStackExact(v, state, stack, length - 1);

			if (stack.isEmpty()) return new ParexData<>(null, root, v.longestCommonPrefixLength(root.extent(transform)));

			final InternalNode<T> parex = stack.top();
			final Node<T> exitNode = v.getBoolean(parex.extentLength) ? parex.right : parex.left;
			return new ParexData<>(parex, exitNode, v.longestCommonPrefixLength(exitNode.extent(transform)));
		}

		// From here the search is correct, but we might have got the parex or the exit node
		if (lcpLength >= parexOrExitNode.extentLength) {
			// We actually got the parex
			final Node<T> exitNode = v.getBoolean(parexOrExitNode.extentLength) ? parexOrExitNode.right : parexOrExitNode.left;
			return new ParexData<>(parexOrExitNode, exitNode, v.longestCommonPrefixLength(exitNode.extent(transform)));
		}

		// In this case the fat binary search gave us the correct *exit* node. We must pop it from the stack
		// and maybe restart the search.
		stack.pop();
		if (stack.isEmpty()) return new ParexData<>(null, parexOrExitNode, lcpLength);

		final long startingPoint = stack.top().extentLength;
		// We're lucky: the second element on the stack is the parex node.
		if (startingPoint == parexOrExitNode.nameLength - 1) return new ParexData<>(stack.top(), parexOrExitNode, lcpLength);

		// We have to complete the stack.
		completeFatBinarySearchStack(v, state, stack, startingPoint, parexOrExitNode.nameLength - 1);
		final InternalNode<T> parex = stack.top();

		final Node<T> exitNode = v.getBoolean(parex.extentLength) ? parex.right : parex.left;
		return new ParexData<>(parex, exitNode, v.longestCommonPrefixLength(exitNode.extent(transform)));
	}

	private void assertParent(final LongArrayBitVector v, final ParexData<T> parexData, final ObjectArrayList<InternalNode<T>> stack) {
		assert (parexData.parexNode == null) == stack.isEmpty() : (parexData.parexNode == null) + " != " + stack.isEmpty();
		assert parexData.parexNode != null || parexData.exitNode == root;
		assert parexData.parexNode == null || parexData.parexNode.left == parexData.exitNode || parexData.parexNode.right == parexData.exitNode;
		for(final InternalNode<T> node : stack) {
			assert v.equals(node.extent(transform), Math.max(0, node.nameLength - 1), node.extentLength);
		}
	}



	/**
	 * Returns the grandparent of the exit node of a given bit vector.
	 *
	 * @param v a bit vector.
	 * @param state the hash state of {@code v} precomputed by
	 *            {@link Hashes#preprocessMurmur(BitVector, long)}.
	 * @param stack a nonempty stack as filled by
	 *            {@link #getParentExitNode(LongArrayBitVector, long[], ObjectArrayList)}; the top of
	 *            the stack must not be the root.
	 * @return the grandparent of the exit node of {@code v}, or {@code null} if there is no
	 *         grandparent.
	 */
	public void getGrandParentExitNode(final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode<T>> stack) {
		if (DDEBUG) System.err.println("getParentGrandExitNode(" + v + ", " + stack + ")");
		assert !stack.isEmpty();
		assert stack.top() != root;
		final InternalNode<T> parentExitNode = stack.pop();

		final long startingPoint = stack.top().extentLength;
		// We're lucky: the second element on the stack is the grandparent of the exit node.
		if (startingPoint == parentExitNode.nameLength - 1) return;

		// We have to complete the stack.
		completeFatBinarySearchStack(v, state, stack, startingPoint, parentExitNode.nameLength - 1);
		final InternalNode<T> grandParentExitNode = stack.top();																																// check
		assert grandParentExitNode.left == parentExitNode || grandParentExitNode.right == parentExitNode;
	}


	/**
	 * Performs a non-exact fat binary search with stack.
	 *
	 * @param v the bit vector on which to perform the search.
	 * @param state {@linkplain Hashes#preprocessMurmur(BitVector, long) preprocessed MurmurHash state}
	 *            for {@code v}.
	 * @param stack a stack where the results of the search will be cumulated.
	 * @param b the right extreme of the search interval, &ge; &minus;1 (included).
	 */

	protected void fatBinarySearchStack(final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode<T>> stack, long b) {
		if (DDDEBUG) System.err.println("fatBinarySearchStack(" + v + ", " + stack + ", [0.." + b + "])");
		assert b >= -1 : b + " < " + -1;

		long a = -1;
		final long length = v.length();
		long checkMask = checkMask(b);

		while (a < b) {
			assert checkMask != 0;
			if (DDDEBUG) System.err.println("[" + (a + 1) + ".." + b + "]");

			final long f = b & checkMask;
			if ((a & checkMask) != f) {
				if (DDDEBUG) System.err.println("Inquiring with key " + v.subVector(0, f) + " (" + f + ")");

				final InternalNode<T> n = handle2Node.find(v, f, state);

				if (n != null && n.extentLength < length) {
					if (DDDEBUG) System.err.println("Found extent of length " + n.extentLength);
					a = n.extentLength;
					stack.push(n);
				}
				else {
					if (DDDEBUG) System.err.println("Missing");
					b = f - 1;
				}
			}

			checkMask >>= 1;
		}

		if (DDDEBUG) System.err.println("Final interval: [" + (a + 1) + ".." + b + "]; stack: " + stack);
	}

	/**
	 * Performs an exact fat binary search with stack.
	 *
	 * @param v the bit vector on which to perform the search.
	 * @param state {@linkplain Hashes#preprocessMurmur(BitVector, long) preprocessed MurmurHash state}
	 *            for {@code v}.
	 * @param stack a stack where the results of the search will be cumulated.
	 * @param b the right extreme of the search interval, &ge; &minus;1 (included).
	 */

	protected void fatBinarySearchStackExact(final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode<T>> stack, long b) {
		if (DDDEBUG) System.err.println("fatBinarySearchStackExact(" + v + ", " + stack + ", [0.." + b + "])");
		assert b >= -1 : b + " < " + -1;

		long a = -1;
		final long length = v.length();
		long checkMask = checkMask(b);

		while (a < b) {
			assert checkMask != 0;
			if (DDDEBUG) System.err.println("[" + (a + 1) + ".." + b + "]");

			final long f = b & checkMask;
			if ((a & checkMask) != f) {
				if (DDDEBUG) System.err.println("Inquiring with key " + v.subVector(0, f) + " (" + f + ")");

				final InternalNode<T> n = handle2Node.findExact(v, f, state);

				if (n != null && n.extentLength < length && n.extent(transform).isPrefix(v)) {
					if (DDDEBUG) System.err.println("Found extent of length " + n.extentLength);
					a = n.extentLength;
					stack.push(n);
				} else {
					if (DDDEBUG) System.err.println("Missing");
					b = f - 1;
				}
			}

			checkMask >>= 1;
		}

		if (DDDEBUG) System.err.println("Final interval: [" + (a + 1) + ".." + b + "]; stack: " + stack);
	}

	/**
	 * Completes the stack of a previous successful fat binary search.
	 *
	 * @param v the bit vector on which to perform the search.
	 * @param state {@linkplain Hashes#preprocessMurmur(BitVector, long) preprocessed MurmurHash state}
	 *            for {@code v}.
	 * @param stack a stack where the results of the completion will be cumulated.
	 * @param a the left extreme of the completion interval, &ge; &minus;1 (excluded)
	 * @param b the right extreme of the completion interval, &ge; {@code a} (included).
	 */

	protected void completeFatBinarySearchStack(final LongArrayBitVector v, final long[] state, final ObjectArrayList<InternalNode<T>> stack, long a, final long b) {
		if (DDDEBUG) System.err.println("completeFatBinarySearchStack(" + v + ", " + stack + ", [" + (a + 1) + ".." + b + "])");
		assert a > -1;
		assert a <= b : a + " >= " + b;
		long checkMask = checkMask(a, b);

		while (a < b) {
			assert checkMask != 0;
			if (DDDEBUG) System.err.println("[" + (a + 1) + ".." + b + "]");

			final long f = b & checkMask;
			if ((a & checkMask) != f) {
				final InternalNode<T> n = handle2Node.find(v, f, state);
				a = n.extentLength;
				stack.push(n);
			}

			checkMask >>= 1;
		}
	}

	/**
	 * Performs a non-exact fat binary search.
	 *
	 * @param v the bit vector on which to perform the search.
	 * @param state {@linkplain Hashes#preprocessMurmur(BitVector, long) preprocessed MurmurHash state}
	 *            for {@code v}.
	 * @param b the right extreme of the search interval, &ge; &minus;1 (included).
	 * @param stack a stack where the results of the search will be cumulated.
	 * @return the parent of the exit node or the exit node, in case of success; an arbitrary node
	 *         otherwise.
	 */

	protected InternalNode<T> fatBinarySearch(final LongArrayBitVector v, final long[] state, long b) {
		if (DDDEBUG) System.err.println("fatBinarySearch(" + v + ", [0.." + b + "])");
		assert b >= -1 : b + " < " + -1;

		long a = -1;
		long checkMask = checkMask(b);
		InternalNode<T> top = null;

		while (a < b) {
			assert checkMask != 0;
			if (DDDEBUG) System.err.println("[" + (a + 1) + ".." + b + "]");

			final long f = b & checkMask;
			if ((a & checkMask) != f) {
				if (DDDEBUG) System.err.println("Inquiring with key " + v.subVector(0, f) + " (" + f + ")");

				final InternalNode<T> n = handle2Node.find(v, f, state);

				if (n != null) {
					if (DDDEBUG) System.err.println("Found extent of length " + n.extentLength);
					a = n.extentLength;
					top = n;
				} else {
					if (DDDEBUG) System.err.println("Missing");
					b = f - 1;
				}
			}

			checkMask >>= 1;
		}

		if (DDDEBUG) System.err.println("Final interval: [" + (a + 1) + ".." + b + "]; top: " + top);
		return top;
	}

	/**
	 * Performs an exact fat binary search.
	 *
	 * @param v the bit vector on which to perform the search.
	 * @param state {@linkplain Hashes#preprocessMurmur(BitVector, long) preprocessed MurmurHash state}
	 *            for {@code v}.
	 * @param b the right extreme of the search interval, &ge; &minus;1 (included).
	 * @return the parent of the exit node.
	 */

	protected InternalNode<T> fatBinarySearchExact(final LongArrayBitVector v, final long[] state, long b) {
		if (DDDEBUG) System.err.println("fatBinarySearchExact(" + v + ", [0.." + b + "])");
		assert b >= -1 : b + " < " + -1;

		long a = -1;
		long checkMask = checkMask(b);
		InternalNode<T> top = null;

		while (a < b) {
			assert checkMask != 0;
			if (DDDEBUG) System.err.println("[" + (a + 1) + ".." + b + "]");

			final long f = b & checkMask;
			if ((a & checkMask) != f) {
				if (DDDEBUG) System.err.println("Inquiring with key " + v.subVector(0, f) + " (" + f + ")");

				final InternalNode<T> n = handle2Node.findExact(v, f, state);

				if (n != null) {
					if (DDDEBUG) System.err.println("Found extent of length " + n.extentLength);
					a = n.extentLength;
					top = n;
				} else {
					if (DDDEBUG) System.err.println("Missing");
					b = f - 1;
				}
			}

			checkMask >>= 1;
		}

		if (DDDEBUG) System.err.println("Final interval: [" + (a + 1) + ".." + b + "]; top: " + top);
		return top;
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean contains(final Object o) {
		if (DEBUG) System.err.println("contains(" + o + ")");
		if (DDEBUG) System.err.println("Map: " + handle2Node + " root: " + root);
		if (size == 0) return false;
		final LongArrayBitVector v = LongArrayBitVector.copy(transform.toBitVector((T)o));
		final long[] state = Hashes.preprocessMurmur(v, 42);
		final ExitData<T> exitData = getExitNode(v, state);
		return exitData.exitNode.isLeaf() && exitData.lcp == transform.length(((Leaf<T>)exitData.exitNode).key) && exitData.lcp == v.length();
	}

	/**
	 * Returns the first node in the trie whose key is greater than or equal to the provided bound.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the first node in the trie whose key is greater than or equal to {@code lowerBound}, or
	 *         {@code null} if no such element exists.
	 */
	private Leaf<T> successorNode(final T lowerBound) {
		final LongArrayBitVector v = LongArrayBitVector.copy(transform.toBitVector(lowerBound));
		final long[] state = Hashes.preprocessMurmur(v, 42);
		final ExitData<T> exitData = getExitNode(v, state);
		if (v.length() == exitData.lcp || !v.getBoolean(exitData.lcp)) return exitData.exitNode.leftLeaf();
		else return exitData.exitNode.rightLeaf().next;
	}

	/**
	 * Returns the first element in the trie that is greater than or equal to the provided bound.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the first element in the trie that is greater than or equal to {@code lowerBound}, or
	 *         {@code null} if no such element exists.
	 */
	@SuppressWarnings("unchecked")
	public T successor(final Object lowerBound) {
		if (size == 0) return null;
		return successorNode((T)lowerBound).key;
	}

	/**
	 * Returns the first element in the trie that is greater than or equal to the provided bound.
	 *
	 * @implSpec This method just delegates to {@link #successor(Object)}.
	 *
	 * @param lowerBound a lower bound on the returned value.
	 * @return the first element in the trie that is greater than or equal to {@code lowerBound}, or
	 *         {@code null} if no such element exists.
	 */
	public T ceiling(final Object lowerBound) {
		return successor(lowerBound);
	}

	/**
	 * Returns the first node in the trie whose key is greater than the provided bound.
	 *
	 * @param lowerBound a strict lower bound on the returned value.
	 * @return the first node in the trie whose key is greater than {@code lowerBound}, or {@link #tail}
	 *         if no such element exists.
	 */
	private Leaf<T> strictSuccessorNode(final T lowerBound) {
		final LongArrayBitVector v = LongArrayBitVector.copy(transform.toBitVector(lowerBound));
		final long[] state = Hashes.preprocessMurmur(v, 42);
		final ExitData<T> exitData = getExitNode(v, state);
		if (v.length() > exitData.lcp && !v.getBoolean(exitData.lcp)) return exitData.exitNode.leftLeaf();
		else return exitData.exitNode.rightLeaf().next;
	}

	/**
	 * Returns the first element in the trie that is greater than the provided bound.
	 *
	 * @param lowerBound a strict lower bound on the returned value.
	 * @return the first element in the trie that is greater than {@code lowerBound}, or {@link #tail}
	 *         if no such element exists.
	 */
	@SuppressWarnings("unchecked")
	public T strictSuccessor(final Object lowerBound) {
		if (size == 0) return null;
		return strictSuccessorNode((T)lowerBound).key;
	}

	/**
	 * Returns the first element in the trie that is greater than the provided bound.
	 *
	 * @implSpec This method just delegates to {@link #strictSuccessor(Object)}.
	 *
	 * @param lowerBound a strict lower bound on the returned value.
	 * @return the first element in the trie that is greater than {@code lowerBound}, or {@link #tail}
	 *         if no such element exists.
	 */
	public T higher(final Object lowerBound) {
		return strictSuccessor(lowerBound);
	}

	/**
	 * Returns the first node in the trie whose key is smaller than the provided bound.
	 *
	 * @param upperBound a strict upper bound on the returned value.
	 * @return the first node in the trie whose key is smaller than {@code upperBound}, or {@link #head}
	 *         if no such element exists.
	 */
	private Leaf<T> predecessorNode(final T upperBound) {
		final LongArrayBitVector v = LongArrayBitVector.copy(transform.toBitVector(upperBound));
		final long[] state = Hashes.preprocessMurmur(v, 42);
		final ExitData<T> exitData = getExitNode(v, state);
		if (v.length() > exitData.lcp && v.getBoolean(exitData.lcp)) return exitData.exitNode.rightLeaf();
		else return exitData.exitNode.leftLeaf().prev;
	}

	/**
	 * Returns the first element in the trie that is smaller than the provided bound.
	 *
	 * @param upperBound a strict upper bound on the returned value.
	 * @return the first element in the trie that is smaller than {@code upperBound}, or {@link #head}
	 *         if no such element exists.
	 */
	@SuppressWarnings("unchecked")
	public T predecessor(final Object upperBound) {
		if (size == 0) return null;
		return predecessorNode((T)upperBound).key;
	}

	/**
	 * Returns the first element in the trie that is smaller than the provided bound.
	 *
	 * @implSpec This method just delegates to {@link #predecessor(Object)}.
	 *
	 * @param upperBound a strict upper bound on the returned value.
	 * @return the first element in the trie that is smaller than {@code upperBound}, or {@link #head}
	 *         if no such element exists.
	 */
	public T lower(final Object upperBound) {
		return predecessor(upperBound);
	}

	/**
	 * Returns the first node in the trie whose key is smaller than or equal to the provided bound.
	 *
	 * @param upperBound an upper bound on the returned value.
	 * @return the first node in the trie whose key is smaller than or equal to {@code upperBound}, or
	 *         {@link #head} if no such element exists.
	 */
	private Leaf<T> weakPredecessorNode(final T k) {
		final LongArrayBitVector v = LongArrayBitVector.copy(transform.toBitVector(k));
		final long[] state = Hashes.preprocessMurmur(v, 42);
		final ExitData<T> exitData = getExitNode(v, state);
		if (v.length() == exitData.lcp || v.getBoolean(exitData.lcp)) return exitData.exitNode.rightLeaf();
		else return exitData.exitNode.leftLeaf().prev;
	}

	/**
	 * Returns the first element in the trie that is smaller than or equal to the provided bound.
	 *
	 * @param upperBound an upper bound on the returned value.
	 * @return the first element in the trie that is smaller than or equal to {@code upperBound}, or
	 *         {@link #head} if no such element exists.
	 */
	@SuppressWarnings("unchecked")
	public T weakPredecessor(final Object upperBound) {
		if (size == 0) return null;
		return weakPredecessorNode((T)upperBound).key;
	}

	/**
	 * Returns the first element in the trie that is smaller than or equal to the provided bound.
	 *
	 * @implSpec This method just delegates to {@link #weakPredecessor(Object)}.
	 *
	 * @param upperBound an upper bound on the returned value.
	 * @return the first element in the trie that is smaller than or equal to {@code upperBound}, or
	 *         {@link #head} if no such element exists.
	 */
	public T floor(final Object upperBound) {
		return weakPredecessor(upperBound);
	}


	/**
	 * Returns whether there is an element between the given bounds.
	 *
	 * @param lowerBound a lower bound.
	 * @param upperBound an upper bound.
	 * @return true if there is an element in the interval {@code [lowerBound...upperBound)}.
	 */
	@SuppressWarnings("unchecked")
	public boolean nonemptyRange(final Object lowerBound, final Object upperBound) {
		final LongArrayBitVector l = LongArrayBitVector.copy(transform.toBitVector((T)lowerBound));
		final LongArrayBitVector u = LongArrayBitVector.copy(transform.toBitVector((T)upperBound));

		final long[] lowerState = Hashes.preprocessMurmur(l, 42);
		final Node<T> lowerExitNode = getExitNode(l, lowerState).exitNode;
		if (l.compareTo(lowerExitNode.extent(transform)) <= 0) {
			Node<T> node = lowerExitNode;
			while (node.isInternal() && node.extentLength(transform) < u.length()) node = ((InternalNode<T>)node).jumpLeft;
			return node.extent(transform).compareTo(u) < 0;
		}

		final long[] upperState = Hashes.preprocessMurmur(u, 42);
		final Node<T> upperExitNode = getExitNode(u, upperState).exitNode;
		if (u.compareTo(upperExitNode.extent(transform)) > 0) {
			Node<T> node = upperExitNode;
			while (node.isInternal() && node.extent(transform).length() < l.length()) node = ((InternalNode<T>)node).jumpRight;
			return l.compareTo(node.extent(transform)) <= 0;
		}

		final LongArrayBitVector lcp = LongArrayBitVector.copy(l.subVector(0, l.longestCommonPrefixLength(u)));
		// This is necessarily internal, and we can use the preprocessed state of either bound
		final InternalNode<T> lcpExitNode = (InternalNode<T>)getExitNode(lcp, lowerState).exitNode;

		Node<T> node = lcpExitNode.left;
		while (node.isInternal() && node.extentLength(transform) < l.length()) node = ((InternalNode<T>)node).jumpRight;
		if (l.compareTo(node.extent(transform)) <= 0) return true;

		node = lcpExitNode.right;
		while (node.isInternal() && node.extentLength(transform) < u.length()) node = ((InternalNode<T>)node).jumpLeft;
		return node.extent(transform).compareTo(u) < 0;
	}


	@Override
	public ObjectBidirectionalIterator<T> iterator() {
		return iteratorFromLeaf(head.next);
	}

	@Override
	public ObjectBidirectionalIterator<T> iterator(final T from) {
		return size == 0 ? iteratorFromLeaf(tail) : iteratorFromLeaf(successorNode(from));
	}

	private ObjectBidirectionalIterator<T> iteratorFromLeaf(final Leaf<T> from) {
		return new ObjectBidirectionalIterator<>() {
			private Leaf<T> curr = from;

			@Override
			public boolean hasNext() {
				return curr != tail;
			}

			@Override
			public T next() {
				if (! hasNext()) throw new NoSuchElementException();
				final T result = curr.key;
				curr = curr.next;
				return result;
			}

			@Override
			public boolean hasPrevious() {
				return curr.prev != head;
			}

			@Override
			public T previous() {
				if (! hasPrevious()) throw new NoSuchElementException();
				curr = curr.prev;
				return curr.key;
			}

		};
	}

	@Override
	public Comparator<? super T> comparator() {
		return null;
	}

	@Override
	public T first() {
		return head.next.key;
	}

	@Override
	public T last() {
		return tail.prev.key;
	}

	@Override
	public ObjectSortedSet<T> headSet(final T arg0) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ObjectSortedSet<T> subSet(final T arg0, final T arg1) {
		throw new UnsupportedOperationException();
	}

	@Override
	public ObjectSortedSet<T> tailSet(final T arg0) {
		throw new UnsupportedOperationException();
	}

	private void writeObject(final ObjectOutputStream s) throws IOException {
		s.defaultWriteObject();
		if (size > 0) writeNode(root, transform, s);
	}

	private static <U >void writeNode(final Node<U> node, final TransformationStrategy<? super U> transform, final ObjectOutputStream s) throws IOException {
		s.writeBoolean(node.isInternal());
		if (node.isInternal()) {
			final InternalNode<U> internalNode = (InternalNode<U>)node;
			s.writeLong(internalNode.extentLength - internalNode.nameLength);
			writeNode(internalNode.left, transform, s);
			writeNode(internalNode.right, transform, s);
		}
		else s.writeObject(((Leaf<U>)node).key);
	}

	private void readObject(final ObjectInputStream s) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		initHeadTail();
		handle2Node = new Handle2NodeMap<>(size, transform);
		if (size > 0) root = readNode(s, 0, 0, handle2Node, new ObjectArrayList<Leaf<T>>(), new ObjectArrayList<InternalNode<T>>(), new IntArrayList(), new IntArrayList(), new BooleanArrayList());
		if (ASSERTS) assertTrie();
	}

	/** Reads recursively a node of the trie.
	 *
	 * @param s the object input stream.
	 * @param depth the depth of the node to be read.
	 * @param parentExtentLength the length of the extent of the parent node.
	 * @param map the map representing the trie.
	 * @param leafStack a stack that cumulates leaves as they are found: internal nodes extract references from this stack when their visit is completed.
	 * @param jumpStack a stack that cumulates nodes that need jump pointer fixes.
	 * @param depthStack a stack parallel to {@code jumpStack}, providing the depth of the corresponding node.
	 * @param segmentStack a stack of integers representing the length of maximal constant subsequences of the string of directions taken up to the current node; for instance, if we reached the current node by 1/1/0/0/0/1/0/0, the stack will contain 2,3,1,2.
	 * @param dirStack a stack parallel to {@code segmentStack}: for each element, whether it counts left or right turns.
	 * @return the subtree rooted at the next node in the stream.
	 */
	@SuppressWarnings("unchecked")
	private Node<T> readNode(final ObjectInputStream s, final int depth, final long nameLength, final Handle2NodeMap<T> map, final ObjectArrayList<Leaf<T>> leafStack, final ObjectArrayList<InternalNode<T>> jumpStack, final IntArrayList depthStack, final IntArrayList segmentStack, final BooleanArrayList dirStack) throws IOException, ClassNotFoundException {
		final boolean isInternal = s.readBoolean();
		// The two following variables are identical when non-null.
		final InternalNode<T> internalNode = isInternal ? new InternalNode<>() : null;
		final Node<T> node = isInternal ? internalNode : new Leaf<>();
		node.nameLength = nameLength;
		if (isInternal) internalNode.extentLength = nameLength + s.readLong();

		if (! dirStack.isEmpty()) {
			/* We cannot fix the jumps of nodes that are more than this number of levels up in the tree. */
			final int maxDepthDelta = segmentStack.topInt();
			final boolean dir = dirStack.topBoolean();
			InternalNode<T> anc;
			int d;
			long jumpLength;
			do {
				jumpLength = (anc = jumpStack.top()).jumpLength();
				d = depthStack.topInt();
				/* To be fixable, a node must be within the depth limit, and we must intercept its jump length (note that
				 * we cannot use .intercept() as the state of node is not yet consistent). If a node cannot be fixed, no
				 * node higher in the stack can. */
				if (depth - d <= maxDepthDelta && jumpLength >= nameLength && (! isInternal || jumpLength <= internalNode.extentLength)) {
					//if (DDEBUG) System.err.println("Setting " + (dir ? "right" : "left") + " jump pointer of " + anc + " to " + node);
					if (dir) anc.jumpRight = node;
					else anc.jumpLeft = node;
					jumpStack.pop();
					depthStack.popInt();
				}
				else break;
			} while(! jumpStack.isEmpty());
		}

		if (isInternal) {
			if (dirStack.isEmpty() || dirStack.topBoolean() != false) {
				segmentStack.push(1);
				dirStack.push(false);
			}
			else segmentStack.push(segmentStack.popInt() + 1);
			jumpStack.push(internalNode);
			depthStack.push(depth);

			if (DEBUG) System.err.println("Recursing into left node... ");
			internalNode.left = readNode(s, depth + 1, internalNode.extentLength + 1, map, leafStack, jumpStack, depthStack, segmentStack, dirStack);

			int top = segmentStack.popInt();
			if (top != 1) segmentStack.push(top - 1);
			else dirStack.popBoolean();

			if (dirStack.isEmpty() || dirStack.topBoolean() != true) {
				segmentStack.push(1);
				dirStack.push(true);
			}
			else segmentStack.push(segmentStack.popInt() + 1);
			jumpStack.push(internalNode);
			depthStack.push(depth);

			if (DEBUG) System.err.println("Recursing into right node... ");
			internalNode.right = readNode(s, depth + 1, internalNode.extentLength + 1, map, leafStack, jumpStack, depthStack, segmentStack, dirStack);

			top = segmentStack.popInt();
			if (top != 1) segmentStack.push(top - 1);
			else dirStack.popBoolean();

			/* We assign the reference leaf, and store the associated key. */
			final Leaf<T> referenceLeaf = leafStack.pop();
			internalNode.reference = referenceLeaf;
			referenceLeaf.reference = internalNode;

			map.addNew(internalNode);

			if (ASSERTS) { // Check jump pointers.
				Node<T> t;
				t = internalNode.left;
				while(t.isInternal() && ! t.intercepts(internalNode.jumpLength())) t = ((InternalNode<T>)t).left;
				assert internalNode.jumpLeft == t : internalNode.jumpLeft + " != " + t + " (" + node + ")";
				t = internalNode.right;
				while(t.isInternal() && ! t.intercepts(internalNode.jumpLength())) t = ((InternalNode<T>)t).right;
				assert internalNode.jumpRight == t : internalNode.jumpRight + " != " + t + " (" + node + ")";
			}
		}
		else {
			final Leaf<T> leaf = (Leaf<T>)node;
			leaf.key = (T)s.readObject();
			leafStack.push(leaf);
			addBefore(tail, leaf);
		}

		return node;
	}

	public static void main(final String[] arg) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP(ZFastTrie.class.getName(), "Builds an PaCo trie-based monotone minimal perfect hash function reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption("encoding", ForNameStringParser.getParser(Charset.class), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding."),
			new Switch("loadAll", 'l', "load-all", "Load all strings into memory before building the trie."),
			new Switch("iso", 'i', "iso", "Use ISO-8859-1 coding internally (i.e., just use the lower eight bits of each character)."),
			new Switch("utf32", JSAP.NO_SHORTFLAG, "utf-32", "Use UTF-32 internally (handles surrogate pairs)."),
			new Switch("bitVector", 'b', "bit-vector", "Build a trie of bit vectors, rather than a trie of strings."),
			new Switch("zipped", 'z', "zipped", "The string list is compressed in gzip format."),
			new UnflaggedOption("trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised z-fast trie."),
			new UnflaggedOption("stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input."),
		});

		final JSAPResult jsapResult = jsap.parse(arg);
		if (jsap.messagePrinted()) return;

		final String functionName = jsapResult.getString("trie");
		final String stringFile = jsapResult.getString("stringFile");
		final Charset encoding = (Charset)jsapResult.getObject("encoding");
		final boolean zipped = jsapResult.getBoolean("zipped");
		final boolean iso = jsapResult.getBoolean("iso");
		final boolean utf32 = jsapResult.getBoolean("utf32");
		final boolean bitVector = jsapResult.getBoolean("bitVector");

		final InputStream inputStream = "-".equals(stringFile) ? System.in : new FileInputStream(stringFile);

		Iterator<MutableString> lineIterator = new LineIterator(new FastBufferedReader(new InputStreamReader(zipped ? new GZIPInputStream(inputStream) : inputStream, encoding)));
		if (jsapResult.userSpecified("loadAll")) lineIterator = ((LineIterator)lineIterator).allLines().iterator();

		final TransformationStrategy<CharSequence> transformationStrategy = iso
					? TransformationStrategies.prefixFreeIso()
					: utf32
						? TransformationStrategies.prefixFreeUtf32()
						: TransformationStrategies.prefixFreeUtf16();

		final ProgressLogger pl = new ProgressLogger();
		pl.displayLocalSpeed = true;
		pl.displayFreeMemory = true;
		pl.itemsName = "keys";
		pl.start("Adding keys...");

		if (bitVector) {
			final ZFastTrie<LongArrayBitVector> zFastTrie = new ZFastTrie<>(TransformationStrategies.identity());
			while(lineIterator.hasNext()) {
				zFastTrie.add(LongArrayBitVector.copy(transformationStrategy.toBitVector(lineIterator.next().copy())));
				pl.lightUpdate();
			}
			pl.done();
			BinIO.storeObject(zFastTrie, functionName);
		}
		else {
			final ZFastTrie<CharSequence> zFastTrie = new ZFastTrie<>(transformationStrategy);
			while(lineIterator.hasNext()) {
				zFastTrie.add(lineIterator.next().copy());
				pl.lightUpdate();
			}
			pl.done();
			BinIO.storeObject(zFastTrie, functionName);
		}

		inputStream.close();
		LOGGER.info("Completed.");
	}
}
