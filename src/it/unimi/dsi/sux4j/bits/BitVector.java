package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2007-2008 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 2.1 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

import it.unimi.dsi.fastutil.booleans.BooleanList;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import it.unimi.dsi.sux4j.util.LongBigList;

import java.io.Serializable;
import java.util.List;
import java.util.RandomAccess;

/** A vector of bits, a&#46;k&#46;a&#46; bit sequence, bit string, binary word, etc.
 * 
 * <P>This interface define several operations on finite sequences of bits.
 * Efficient implementations, such as {@link it.unimi.dsi.sux4j.bits.LongArrayBitVector},
 * use approximately one bit of memory for each bit in the vector, but this is not enforced.
 * 
 * <P>Operation of a bit vector are partially of boolean nature 
 * (e.g., logical operations between vectors),
 * partially of language-theoretical nature (e.g., concatenation), and 
 * partially of set-theoretical nature (e.g., asking which bits are set to one). 
 * To accomodate all these points of view, this interface extends  
 * {@link it.unimi.dsi.fastutil.booleans.BooleanList}, but also provides an
 * {@link #asLongSet()} method that exposes a {@link java.util.BitSet}-like view
 * and a {@link #asLongBigList(int)} method that provides integer-like access to
 * blocks of bits of given width.
 * 
 * <P>Most, if not all, classical operations on bit vectors can be seen as standard
 * operations on these two views: for instance, the number of bits set to one is just
 * the number of elements of the set returned by {@link #asLongSet()} (albeit a direct {@link #count()} method
 * is provided, too). The standard {@link java.util.Collection#addAll(java.util.Collection)} method
 * can be used to concatenate bit vectors, and {@linkplain java.util.List#subList(int, int) sublist views}
 * make it easy performing any kind of logical operation on subvectors.
 * 
 * <P>The only <i>caveat</i> is that sometimes the standard interface naming clashes slightly
 * with standard usage: for instance, {@link #clear(long)} will <em>not</em> set to zero
 * all bits (use {@link #fill(int) fill(0)} for that purpose), but rather will set the
 * vector length to zero. Also, {@link #add(long, int)} will not add logically a value at
 * the specified index, but rather will insert a new bit with the specified value at the specified
 * position.
 * 
 * <P>The {@link AbstractBitVector} class provides a fairly complete
 * abstract implementation that provides all methods except for the most
 * basic operations. Of course, the methods of {@link it.unimi.dsi.sux4j.bits.AbstractBitVector} are
 * very inefficient, but implementations such as {@link it.unimi.dsi.sux4j.bits.LongArrayBitVector}
 * have their own optimised implementations. 
 */
public interface BitVector extends RandomAccess, BooleanList {

	/** A generic transformation from objects of a given type to bit vector. Most useful
	 * when adding strings, etc. to a trie.
	 */
	
	public interface TransformationStrategy<T> extends Serializable {
		/** Returns a bit vector representation of the given object.
		 * 
		 * <p><strong>Warning</strong>: string representations must be prefix-free. This
		 * is essential to ensure that tries to work.
		 * 
		 * @param object the object to be turned into bit representation.
		 * @return a bit vector representation of <code>object</code>.
		 */
		public BitVector toBitVector( T object );
		
		/** The (approximate) number of bits occupied by this transformation.
		 * 
		 * @return the (approximate) number of bits occupied by this transformation.
		 */
		public long numBits();
		
		/** Returns a copy of this transformation strategy.
		 * 
		 * @return a copy of this transformation strategy.
		 */
		public TransformationStrategy<T> copy();
	}
	
	
	/** Sets a bit in this bit vector (optional operation). 
	 * @param index the index of a bit.
	 */
	public void set( long index );
	
	/** Clears a bit in this bit vector (optional operation). 
	 * @param index the index of a bit.
	 */
	public void clear( long index );

	/** Flips a bit in this bit vector (optional operation). 
	 * @param index the index of a bit.
	 */
	public void flip( long index );

	/** Fills a range of bits in this bit vector (optional operation). 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 * @param value the value (true or false).
	 */
	public void fill( long from, long to, boolean value );
	
	/** Clears a range of bits in this bit vector (optional operation). 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 * @param value the value (zero or nonzero).
	 */
	public void fill( long from, long to, int value );
	
	/** Sets all bits this bit vector to the given boolean value (optional operation). */
	public void fill( boolean value );
	
	/** Sets all bits this bit vector to the given integer value (optional operation). */
	public void fill( int value );
	
	/** Flips a range of bits in this bit vector (optional operation). 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 */
	public void flip( long from, long to );

	/** Flips all bits in this bit vector (optional operation). */
	public void flip();

	/** Replaces the content of this bit vector with another bit vector.
	 * 
	 * @param bitVector a bit vector.
	 * @return this bit vector.
	 */
	public BitVector replace( final BitVector bitVector );
	
	/** Returns a view on a subvector of this vector. 
	 * 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 */
	public BitVector subVector( long from, long to );

	/** Returns a view of this bit vector as a sorted set of long integers.
	 * 
	 * <P>More formally, this bit vector is infinitely extended to the
	 * left with zeros (e.g., all bits beyond {@link #length(long)} are
	 * considered zeroes). The resulting infinite string is interpreted as the
	 * characteristic function of a set of integers.
	 * 
	 * <P>Note that, in particular, the resulting string representation is
	 * exactly that of a {@link java.util.BitSet}.
	 *
	 */
	public LongSortedSet asLongSet();
	
	/** Returns a view of this bit vector as a list of nonnegative integers of specified width.
	 * 
	 * <P>More formally, {@link LongBigList#getLong(long) getLong(p)} will return
	 * the nonnegative integer defined by the bits starting at <code>p * width</code> (bit 0, inclusive)
	 * and ending at <code>(p + 1) * width</code> (bit <code>width</code> &minus; 1, exclusive). 
	 */
	public LongBigList asLongBigList( int width );
	
	/** Returns the value of the specified bit.
	 * 
	 * <P>This method is semantically equivalent to {@link BooleanList#getBoolean(int)},
	 * but it gives access to long indices.
	 * 
	 * @param index the index of a bit.
	 * @return the value of the specified bit.
	 */
	public boolean getBoolean( final long index );

	/** Returns the value of the specified bit as an integer.
	 * 
	 * <P>This method is a useful synonym for {@link #getBoolean(long)}.
	 * 
	 * @param index the index of a bit.
	 * @return the value of the specified bit as an integer (0 or 1).
	 */
	public int getInt( final long index );

	/** Returns the specified bit range as a long.
	 * 
	 * <P>Note that bit 0 of the returned long will be bit <code>from</code>
	 * of this bit vector.
	 * 
	 * <P>Implementations are invited to provide high-speed implementations for
	 * the case in which <code>from</code> is a multiple of {@link Long#SIZE}
	 * and <code>to</code> is <code>from</code> + {@link Long#SIZE} (or less,
	 * in case the vector length is exceeded). This behaviour make it possible to
	 * implement high-speed hashing, copies, etc.
	 * 
	 * @param from the starting bit (inclusive).
	 * @param to the ending bit (exclusive).
	 * @return the long value contained in the specified bits.
	 */
	public long getLong( final long from, final long to );

	/** Sets the value of the specified bit (optional operation).
	 * 
	 * <P>This method is semantically equivalent to {@link BooleanList#set(int,boolean)},
	 * but it gives access to long indices.
	 * 
	 * @param index the index of a bit.
	 * @param value the new value.
	 */
	public boolean set( final long index, final boolean value );
	
	/** Sets the value of the specified bit as an integer (optional operation).
	 * 
	 * <P>This method is a useful synonym for {@link #set(long, boolean)}.
	 * 
	 * @param index the index of a bit.
	 * @param value the new value (any nonzero integer for setting the bit, zero for clearing the bit).
	 */
	public void set( final long index, final int value );
	
	/** Adds a bit with specified value at the specified index (optional operation).
	 * 
	 * <P>This method is semantically equivalent to {@link BooleanList#add(int,boolean)},
	 * but it gives access to long indices.
	 * 
	 * @param index the index of a bit.
	 * @param value the value that will be inserted at position <code>index</code>.
	 */
	public void add( final long index, final boolean value );
	
	/** Removes a bit with specified index (optional operation).
	 * 
	 * <P>This method is semantically equivalent to {@link BooleanList#removeBoolean(int)},
	 * but it gives access to long indices.
	 * 
	 * @param index the index of a bit.
	 * @return the previous value of the bit.
	 */
	public boolean removeBoolean( final long index );
	
	/** Adds a bit with specified integer value at the specified index (optional operation).
	 * 
	 * <P>This method is a useful synonym for {@link #add(long, boolean)}.
	 * 
	 * @param index the index of a bit.
	 * @param value the value that will be inserted at position <code>index</code> (any nonzero integer for a true bit, zero for a false bit).
	 */
	public void add( final long index, final int value );
	
	/** Adds a bit with specified value at the end of this bit vector.
	 * 
	 * <P>This method is a useful synonym for {@link BooleanList#add(boolean)}.
	 * 
	 * @param value the new value (any nonzero integer for a true bit, zero for a false bit).
	 */

	public void add( final int value );

	/** Adds the less significant bits of a long integer to this bit vector.
	 * 
	 * @param value a value to be appended
	 * @param k the number of less significant bits to be added to this bit vector.
	 * @return this bit vector.
	 */

	public BitVector append( final long value, final int k );

	/** Returns the number of bits in this bit vector.
	 *
	 * <p>If the number of bits in this vector is smaller than or equal to {@link Integer#MAX_VALUE}, this
	 * method is semantically equivalent to {@link List#size()}. 
	 *
	 * @return the number of bits in this bit vector. 
	 */
	public long length();
	
	/** Sets the number of bits in this bit vector.
	 *
	 * <p>It is expected that this method will try to allocate exactly
	 * the necessary space. 
	 *
	 * <p>If the number of bits in this vector is smaller than 
	 * or equal to {@link Integer#MAX_VALUE}, this
	 * method is semantically equivalent to {@link BooleanList#size(int)}.
	 *  
	 * @return this bit vector.
	 */
	public BitVector length( long newLength );
	
	/** Counts the number of bits set to true in this bit vector.
	 *
	 * @return the number of bits set to true in this bit vector. 
	 */
	public long count();
	
	/** Performs a logical and between this bit vector and another one, leaving the result in this vector.
	 * 
	 * @param v a bit vector.
	 * @return this bit vector.
	 */
	public BitVector and( BitVector v );

	/** Performs a logical or between this bit vector and another one, leaving the result in this vector.
	 * 
	 * @param v a bit vector.
	 * @return this bit vector.
	 */
	public BitVector or( BitVector v );

	/** Performs a logical xor between this bit vector and another one, leaving the result in this vector.
	 * 
	 * @param v a bit vector.
	 * @return this bit vector.
	 */
	public BitVector xor( BitVector v );

	/** Returns the position of the first bit set in this vector.
	 *
	 * @return the first bit set, or -1 for a vector of zeroes. 
	 */
	public long firstOne();

	/** Returns the position of the last bit set in this vector.
	 *
	 * @return the last bit set, or -1 for a vector of zeroes. 
	 */
	public long lastOne();

	/** Returns the position of the first bit set after the given position.
	 *
	 * @return the first bit set after position <code>index</code> (inclusive), or -1 if no such bit exists. 
	 */
	public long nextOne( final long index );

	/** Returns the position of the first bit set before or at the given position.
	 *
	 * @return the first bit set before or at the given position, or -1 if no such bit exists. 
	 */
	public long previousOne( final long index );

	/** Returns the length of the greatest common prefix between this and the specified vector.
	 *
	 * @param v a bit vector.
	 * @return the length of the greatest common prefix.
	 */
	public long maximumCommonPrefixLength( BitVector v );

	/** Returns a copy of a part of this bit vector.
	 *
	 * @param from the starting bit, inclusive.
	 * @param to the ending bit, not inclusive.
	 * @return a copy of the part of this bit vector going from bit <code>from</code> (inclusive) to bit <code>to</code>
	 * (not inclusive)
	 */
	public BitVector copy( final long from, final long to );

	/** Returns a copy of this bit vector.
	 *
	 * @return a copy of this bit vector. 
	 */
	public BitVector copy();
	
	/** Returns the bits in this bit vector as an array of longs, not to be modified.
	 * 
	 * @return an array of longs whose first {@link #length()} bits contain the bits of
	 * this bit vector. The array cannot be modified.
	 */
	public long[] bits();
}
