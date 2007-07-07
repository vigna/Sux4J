package it.unimi.dsi.sux4j.bits;

/*		 
 * MG4J: Managing Gigabytes for Java
 *
 * Copyright (C) 2005-2007 Sebastiano Vigna 
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
import it.unimi.dsi.fastutil.ints.IntSet;

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
 * {@link #asIntSet()} method that exposes a {@link java.util.BitSet}-like view.
 * 
 * <P>Most, if not all, classical operations on bit vectors can be seen as standard
 * operations on these two views: for instance, the number of bits set to one is just
 * the number of elements of the set returned by {@link #asIntSet()} (albeit a direct {@link #count()} method
 * is provided, too). The standard {@link java.util.Collection#addAll(java.util.Collection)} method
 * can be used to concatenate bit vectors, and {@linkplain java.util.List#subList(int, int) sublist views}
 * make it easy performing any kind of logical operation on subvectors.
 * 
 * <P>The only <i>caveat</i> is that sometimes the standard interface naming clashes slightly
 * with standard usage: for instance, {@link #clear(int)} will <em>not</em> set to zero
 * all bits (use {@link #fill(int) fill(0)} for that purpose), but rather will set the
 * vector length to zero. Also, {@link #add(int, int)} will not add logically a value at
 * the specified index, but rather will insert a new bit with the specified value at the specified
 * position.
 * 
 * <P>The {@link AbstractBitVector} class provides a fairly complete
 * abstract implementation that provides all methods except for the most
 * basic {@link #getBoolean(int)}, {@link #set(int)} and {@link #clear(int)}
 * operations. Of course, the methods of {@link it.unimi.dsi.sux4j.bits.AbstractBitVector} are
 * very inefficient, but implementations such as {@link it.unimi.dsi.sux4j.bits.LongArrayBitVector}
 * have their own optimised implementations. 
 */
public interface BitVector extends RandomAccess, BooleanList {

	/** Sets a bit in this bit vector (optional operation). 
	 * @param index the index of a bit.
	 */
	public void set( int index );
	
	/** Clears a bit in this bit vector (optional operation). 
	 * @param index the index of a bit.
	 */
	public void clear( int index );

	/** Flips a bit in this bit vector (optional operation). 
	 * @param index the index of a bit.
	 */
	public void flip( int index );

	/** Fills a range of bits in this bit vector (optional operation). 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 * @param value the value (true or false).
	 */
	public void fill( int from, int to, boolean value );
	
	/** Clears a range of bits in this bit vector (optional operation). 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 * @param value the value (zero or nonzero).
	 */
	public void fill( int from, int to, int value );
	
	/** Sets all bits this bit vector to the given boolean value (optional operation). */
	public void fill( boolean value );
	
	/** Sets all bits this bit vector to the given integer value (optional operation). */
	public void fill( int value );
	
	/** Flips a range of bits in this bit vector (optional operation). 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 */
	public void flip( int from, int to );

	/** Flips all bits in this bit vector (optional operation). */
	public void flip();

	
	/** Returns a view on a subvector of this vector. 
	 * 
	 * @param from the first index (inclusive).
	 * @param to the last index (not inclusive).
	 */
	public BitVector subVector( int from, int to );

	/** Returns a view of this bit vector as a set of integers.
	 * 
	 * <P>More formally, this bit vector is infinitely extended to the
	 * left with zeros (e.g., all bits beyond {@link java.util.List#size()} are
	 * considered zeroes). The resulting infinite string is interpreted as the
	 * characteristic function of a set of integers.
	 * 
	 * <P>Note that, in particular, the resulting string representation is
	 * exactly that of a {@link java.util.BitSet}.
	 *
	 */
	public IntSet asIntSet();
	
	/** Returns the value of the specified bit as an integer.
	 * 
	 * <P>This method is a useful synonym for {@link BooleanList#getBoolean(int)}.
	 * 
	 * @param index the index of a bit.
	 * @return the value of the specified bit as an integer (0 or 1).
	 */
	public int getInt( final int index );
	
	/** Sets the value of the specified bit, specifying the value as an integer (optional operation).
	 * 
	 * <P>This method is a useful synonym for {@link BooleanList#set(int,boolean)}.
	 * 
	 * @param index the index of a bit.
	 * @param value the new value (any nonzero integer for setting the bit, zero for clearing the bit).
	 */
	public void set( final int index, final int value );
	
	/** Adds a bit with specified value at the specified index.
	 * 
	 * <P>This method is a useful synonym for {@link BooleanList#add(int,boolean)}.
	 * 
	 * @param index the index of a bit.
	 * @param value the new value (any nonzero integer for a true bit, zero for a false bit).
	 */
	public void add( final int index, final int value );
	
	/** Adds a bit with specified value at the end of this bit vector.
	 * 
	 * <P>This method is a useful synonym for {@link BooleanList#add(boolean)}.
	 * 
	 * @param value the new value (any nonzero integer for a true bit, zero for a false bit).
	 */
	public void add( final int value );

	/** Counts the number of bits set to true in this bit vector.
	 *
	 * @return the number of bits set to true in this bit vector. 
	 */
	public int count();
	
	/** Performs a logical and between this bit vector and another one, leaving the result in this vector.
	 * 
	 * @param v a bit vector.
	 */
	public void and( BitVector v );

	/** Performs a logical or between this bit vector and another one, leaving the result in this vector.
	 * 
	 * @param v a bit vector.
	 */
	public void or( BitVector v );

	/** Performs a logical xor between this bit vector and another one, leaving the result in this vector.
	 * 
	 * @param v a bit vector.
	 */
	public void xor( BitVector v );

	/** Returns the most significant bit of this vector.
	 *
	 * @return the most significant bit of this vector, or -1 for a vector of zeroes. 
	 */
	public int mostSignificantBit();

	/** Returns the least significant bit of this vector.
	 *
	 * @return the least significant bit of this vector, or -1 for a vector of zeroes. 
	 */
	public int leastSignificantBit();

	/** Returns the length of the greatest common prefix between this and the specified vector.
	 *
	 * @param v a bit vector.
	 * @return the length of the greatest common prefix.
	 */
	public int maximumCommonPrefixLength( BitVector v );

	/** Returns a copy of a part of this bit vector.
	 *
	 * @param from the starting bit, inclusive.
	 * @param to the ending bit, not inclusive.
	 * @return a copy of the part of this bit vector going from bit <code>from</code> (inclusive) to bit <code>to</code>
	 * (not inclusive)
	 */
	public BitVector copy( final int from, final int to );

	/** Returns a copy of a part of this bit vector.
	 *
	 * @return a copy of this bit vector. 
	 */
	public BitVector copy();
	
}
