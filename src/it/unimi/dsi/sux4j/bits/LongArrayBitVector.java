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

import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.sux4j.util.LongBigList;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/** A bit vector implementation based on arrays of longs.
 * 
 * <P>The main goal of this class is to be fast and flexible. It implements a lightweight, 
 * fast, open, optimized, reuse-oriented version of bit vectors. Instances of this class
 * represent a bit vector an array of longs that is enlarged as needed when new entries
 * are created (by dividing the current length by the golden ratio), but is
 * <em>never</em> made smaller (even on a {@link #clear()}). Use 
 * {@link #trim()} for that purpose.
 * 
 * <p>Besides usual methods for setting and getting bits, this class provides <em>views</em>
 * that make it possible to access comfortably the bit vector in different ways: for instance,
 * {@link #asLongBigList(int)} provide access as a list of longs, whereas
 * {@link #asLongSet()} provides access in setwise form.
 * 
 * <p><strong>Warning</strong>: A few optional methods have still to be implemented (e.g.,
 * adding an element at an arbitrary position using the list view).
 *
 * <P>Bit numbering follows the right-to-left convention: bit <var>k</var> (counted from the
 * right) of word <var>w</var> is bit 64<var>w</var> + <var>k</var> of the overall bit vector.
 *
 * <P>If {@link #CHECKS} is true at compile time, boundary checks for all bit operations
 * will be compiled in. For maximum speed, you may want to recompile this class with {@link #CHECKS}
 *  set to false. {@link #CHECKS} is public, so you can check from your code whether you're
 * being provided a version with checks or not.
 */

// TODO: take care that unused bits are zeroes

// TODO: implement subVector properly

public class LongArrayBitVector extends AbstractBitVector implements Cloneable, Serializable {
	private static final long serialVersionUID = 1L;
	public final static int LOG2_BITS_PER_WORD = 6;
	public final static int BITS_PER_WORD = 1 << LOG2_BITS_PER_WORD;
	public final static int WORD_MASK = BITS_PER_WORD - 1;
	public final static int LAST_BIT = BITS_PER_WORD - 1;
	public final static long ALL_ONES = 0xFFFFFFFFFFFFFFFFL;
	public final static long LAST_BIT_MASK = 1L << LAST_BIT;
	
	/** Whether this class has been compiled with index checks or not. */
	public  final static boolean CHECKS = true;
	
	/** The number of bits in this vector. */
	private long length;
	/** The backing array of this vector. Bit 0 of the first element contains bit 0 of the bit vector, 
	 * bit 1 of the second element contains bit {@link #BITS_PER_WORD} of the bit vector and so on. */
	public transient long[] bits;

	/** Returns the number of words that are necessary to hold the given number of bits.
	 * 
	 * @param size a number of bits.
	 * @return the number of words that are necessary to hold the given number of bits.
	 */
	
	private static int numWords( final long size ) {
		if ( CHECKS && ( size + WORD_MASK ) >>> LOG2_BITS_PER_WORD > Integer.MAX_VALUE ) throw new IllegalArgumentException();
		return (int)( ( size + WORD_MASK ) >>> LOG2_BITS_PER_WORD );
	}

	/** Return the index of the word that holds a bit of specified index.
	 * 
	 * @param index the index of a bit.
	 * @return the index of the word that holds the bit of given index.
	 */
	private static int word( final long index ) {
		if ( CHECKS && index >>> LOG2_BITS_PER_WORD > Integer.MAX_VALUE ) throw new IllegalArgumentException();
		return (int)( index >>> LOG2_BITS_PER_WORD );
	}

	/** Returns the inside-word index of the bit that would hold the bit of specified index.
	 * 
	 * <P>Note that bit 0 is positioned in word 0, index 0, bit 1 in word 0, index 1, &hellip;,
	 * bit {@link #BITS_PER_WORD} in word 0, index 0, bit {@link #BITS_PER_WORD} + 1 in word 1, index 1,
	 * and so on.
	 * 
	 * @param index the index of a bit.
	 * @return the inside-word index of the bit that would hold the bit of specified index.
	 */
	private static int bit( final long index ) {
		return (int)( index & WORD_MASK );
	}

	/** Returns a mask having a 1 exactly at the bit {@link #bit(int) bit(index)}.
	 * 
	 * @param index the index of a bit
	 * @return a mask having a 1 exactly at the bit {@link #bit(int) bit(index)}.
	 */
	
	private static long mask( final long index ) {
		return 1L << ( index & WORD_MASK );
	}

	protected LongArrayBitVector( final long capacity ) {
		this.bits = capacity > 0 ? new long[ numWords( capacity ) ] : LongArrays.EMPTY_ARRAY;
	}

	/** Creates a new empty bit vector of given capacity. The 
	 * resulting vector will be able to contain <code>capacity</code>
	 * bits without reallocations of the backing array.
	 * 
	 * <P>Note that this constructor creates an <em>empty</em> bit vector.
	 * If you want a cleared bit vector of a specified size, use
	 * {@link #size(int)} after creation.
	 * 
	 * @param capacity the size (number of bits) of the new bit vector.
	 */
	public static LongArrayBitVector getInstance( final long capacity ) {
		return new LongArrayBitVector( capacity );
	}

	/** Creates a new empty bit vector. */
	public static LongArrayBitVector getInstance() {
		return new LongArrayBitVector( 0 );
	}
	
	/** Creates a new bit vector with given bits. */
	public static LongArrayBitVector of( final int... bit ) {
		final LongArrayBitVector bitVector = new LongArrayBitVector( bit.length );
		for( int b : bit ) bitVector.add( b );
		return bitVector;
	}
	
	public long[] bits() {
		return bits;
	}
	
	public long length() {
		return length;
	}
	
	public LongArrayBitVector length( final long newLength ) {
		bits = LongArrays.grow( bits, numWords( newLength ), numWords( length ) );
		final long oldLength = length;
		length = newLength;
		if ( newLength > oldLength ) fill( oldLength, newLength, false );
		return this;
	}


	/** Reduces as must as possible the size of the backing array.
	 * 
	 * @return true if some trimming was actually necessary.
	 */
	
	public boolean trim() {
		if ( bits.length == numWords( length ) ) return false;
		bits = LongArrays.setLength( bits, numWords( length ) );
		return true;
	}

	/** Sets the size of this bit vector to 0.
	 * <P>Note that this method does not try to reallocate that backing array.
	 * If you want to force that behaviour, call {@link #trim()} afterwards.
	 */
	public void clear() {
		length = 0;
		LongArrays.fill( bits, 0 );
	}

	public BitVector copy( final long from, final long to ) {
		BitVectors.ensureFromTo( length, from, to );

		final LongArrayBitVector copy = new LongArrayBitVector( to - from );
		if ( ( copy.length = to - from ) == 0 ) return copy;
		
		final int numWords = numWords( to - from );
		final int startWord = word( from );
		final int startBit = bit( from );
		final int endBit = bit( to );

		// If we're copying from the first bit, we just copy the array. 
		if ( startBit == 0 ) {
			System.arraycopy( bits, startWord, copy.bits, 0, numWords );
			if ( endBit > 0 ) copy.bits[ numWords - 1 ] &= ( 1L << endBit ) - 1;
		}
		else if ( startWord == word( to - 1 ) ) {
			// Same word
			copy.bits[ 0 ] = bits[ startWord ] >>> startBit & ( ( 1L << to - from ) - 1 );
		}
		else {
			copy.bits[ 0 ] = bits[ startWord ] >>> startBit;
			for( int word = 1; word < numWords; word++ ) {
				copy.bits[ word - 1 ] |= bits[ word + startWord ] << BITS_PER_WORD - startBit;
				copy.bits[ word ] = bits[ word + startWord ] >>> startBit;
			}
			if ( endBit > 0 ) copy.bits[ numWords - 1 ] &= ( 1L << endBit ) - 1;
		}
	
		return copy;
	}

	public LongArrayBitVector copy() {
		LongArrayBitVector copy = new LongArrayBitVector( length );
		copy.length = length;
		System.arraycopy( bits, 0, copy.bits, 0, numWords( length ) );
		return copy;
	}

	/** Returns a copy of the given bit vector.
	 * 
	 * <p>This method uses {@link BitVector#getLong(long, long)} on {@link Long#SIZE} boundaries to copy at high speed.
	 * 
	 * @param bv a bit vector.
	 * @return an instance of this class containing a copy of the given vector.
	 */
	public static LongArrayBitVector copy( final BitVector bv ) {
		final LongArrayBitVector copy = new LongArrayBitVector( bv.length() ).length( bv.length() );
		final long fullBits = bv.length() - bv.length() % Long.SIZE;
		for( long i = 0; i < fullBits; i += Long.SIZE ) copy.bits[ (int)( i / Long.SIZE ) ] = bv.getLong( i, i + Long.SIZE );
		if ( bv.length() % Long.SIZE != 0 ) copy.bits[ (int)( fullBits / Long.SIZE ) ] = bv.getLong( fullBits, bv.length() );
		return copy;
	}
	
	public boolean getBoolean( final long index ) {
		if ( CHECKS ) ensureRestrictedIndex( index );
		return ( bits[ word( index ) ] & mask( index ) ) != 0;  
	}

	public boolean set( final long index, final boolean value ) {
		if ( CHECKS ) ensureRestrictedIndex( index );
		final int word = word( index );
		final long mask = mask( index );
		final boolean oldValue = ( bits[ word ] & mask ) != 0;
		if ( value ) bits[ word ] |= mask;
		else bits[ word ] &= ~mask;
		return oldValue != value;
	}

	public void set( final long index ) {
		if ( CHECKS ) ensureRestrictedIndex( index );
		bits[ word( index ) ] |= mask( index ); 
	}

	public void clear( final long index ) {
		if ( CHECKS ) ensureRestrictedIndex( index );
		bits[ word( index ) ] &= ~mask( index ); 
	}
	
	public void add( final long index, final boolean value ) {
		if ( CHECKS ) ensureIndex( index );
		if ( length == bits.length << LOG2_BITS_PER_WORD ) bits = LongArrays.grow( bits, numWords( length + 1 ) );
		
		length++;

		if ( index == length - 1 ) set( index, value );
		else {
			final int word = word( index );
			final int bit = bit( index );
			boolean carry = ( bits[ word ] & LAST_BIT_MASK ) != 0, nextCarry;
			long t = bits[ word ];
			if ( bit == LAST_BIT ) t &= ~LAST_BIT_MASK;
			else t = ( t & - ( 1L << bit ) ) << 1 | t & ( 1L << bit ) - 1;
			if ( value ) t |= 1L << bit;
			bits[ word ] = t;
			final int numWords = numWords( length );
			for( int i = word + 1; i < numWords; i++ ) {
				nextCarry = ( bits[ i ] & LAST_BIT_MASK ) != 0;
				bits[ i ] <<= 1;
				if ( carry ) bits[ i ] |= 1;
				carry = nextCarry;
			}
		}
			
		return;
	}
	
	public boolean removeBoolean( final long index ) {
		if ( CHECKS ) ensureRestrictedIndex( index );
		final boolean oldValue = getBoolean( index );
		final long[] bits = this.bits;

		final int word = word( index );
		final int bit = bit( index );
		bits[ word ] = ( bits[ word ] & - ( 1L << bit ) << 1 ) >>> 1 | bits[ word ] & ( 1L << bit ) - 1;
		final int numWords = numWords( length-- );
		for( int i = word + 1; i < numWords; i++ ) {
			if ( ( bits[ i ] & 1 ) != 0 ) bits[ i - 1 ] |= LAST_BIT_MASK;
			bits[ i ] >>>= 1;
		}

		return oldValue;
	}

	public LongArrayBitVector append( long value, int width ) {
		if ( width == 0 ) return this;
		if ( CHECKS && width < Long.SIZE && ( value & 1L << width ) != 0 ) throw new IllegalArgumentException( "The specified value (" + value + ") is larger than the maximum value for the given width (" + width + ")" );
		final long length = length();
		final long start = length;
		final long end = length + width - 1;
		final int startWord = word( start );
		final int endWord = word( end );
		
		length( length + width );

		if ( startWord == endWord ) bits[ startWord ] |= value << bit( start );
		else {
			final int startBit = bit( start );
			bits[ startWord ] |= value << startBit;
			bits[ endWord ] = value >>> BITS_PER_WORD - startBit;
		}
		return this;
	}

	public long getLong( long from, long to ) {
		if ( CHECKS && to > length ) throw new IllegalArgumentException();
		if ( from % Long.SIZE == 0 && to == from + Long.SIZE ) return bits[ (int)( from / Long.SIZE ) ];
		if ( from == to ) return 0;
		to--;
		final int startWord = word( from );
		final int endWord = word( to );
		final int startBit = bit( from );
		long mask = 1L << to - from;
		mask |= mask - 1;

		if ( startWord == endWord ) return bits[ startWord ] >>> startBit & mask;
		return bits[ startWord ] >>> startBit | bits[ endWord ] << ( BITS_PER_WORD - startBit ) & mask; 
	}

	public long count() {
		long c = 0;
		for( int i = numWords( length ); i-- != 0; ) c += Fast.count( bits[ i ] );
		return c;
	}

	public long nextOne( final long index ) {
		if ( index >= length ) return -1; 
		final long[] bits = this.bits;
		final long words = numWords( length );
		final int from = word( index );
		final long maskedFirstWord = bits[ from ] & -( 1L << bit( index ) );
		if ( maskedFirstWord != 0 ) return from * BITS_PER_WORD + Fast.leastSignificantBit( maskedFirstWord );

		for ( int i = from + 1; i < words; i++ ) 
			if ( bits[ i ] != 0 ) return i * BITS_PER_WORD + Fast.leastSignificantBit( bits[ i ] );
		return -1;
	}
	
	public long previousOne( final long index ) {
		if ( index == 0 ) return -1;
		final long[] bits = this.bits;
		final int from = word( index - 1 );
		final long mask = 1L << bit( index - 1 );
		final long maskedFirstWord = bits[ from ] & ( mask | mask - 1 );
		if ( maskedFirstWord != 0 ) return from * BITS_PER_WORD + Fast.mostSignificantBit( maskedFirstWord );

		for ( int i = from; i-- != 0; ) 
			if ( bits[ i ] != 0 ) return i * BITS_PER_WORD + Fast.mostSignificantBit( bits[ i ] );
		return -1;
	}
	
	public long maximumCommonPrefixLength( final BitVector v ) {
		if ( v instanceof LongArrayBitVector ) return maximumCommonPrefixLength( (LongArrayBitVector)v );
		return super.maximumCommonPrefixLength( v );
	}

	public long maximumCommonPrefixLength( final LongArrayBitVector v ) {
		final long words = Math.min( bits.length, v.bits.length );
		final long[] bits = this.bits;
		final long[] vBits = v.bits;
		for ( int i = 0; i < words; i++ ) 
			if ( bits[ i ] != vBits[ i ] ) 
				return i * BITS_PER_WORD + Fast.leastSignificantBit( bits[ i ] ^ vBits[ i ] );
		return Math.min( v.length(), length() );
	}

	public BitVector and( final BitVector v ) {
		if ( v instanceof LongArrayBitVector ) {
			LongArrayBitVector l = (LongArrayBitVector)v;
			int words = Math.min( numWords( length() ), numWords( l.length() ) );
			while( words-- != 0 ) bits[ words ] &= l.bits[ words ];
		}
		else super.and( v );
		return this;
	}
	
	public BitVector or( final BitVector v ) {
		if ( v instanceof LongArrayBitVector ) {
			LongArrayBitVector l = (LongArrayBitVector)v;
			int words = Math.min( numWords( length() ), numWords( l.length() ) );
			while( words-- != 0 ) bits[ words ] |= l.bits[ words ];
		}
		else super.or( v );
		return this;
	}

	public BitVector xor( final BitVector v ) {
		if ( v instanceof LongArrayBitVector ) {
			LongArrayBitVector l = (LongArrayBitVector)v;
			int words = Math.min( numWords( length() ), numWords( l.length() ) );
			while( words-- != 0 ) bits[ words ] ^= l.bits[ words ];
		}
		else super.xor( v );
		return this;
	}


	
	
	
	/** Wraps the given array of longs in a bit vector for the given number of bits.
	 * 
	 * <p>Note that all bits in <code>array</code> beyond that of index
	 * <code>size</code> must be unset, or an exception will be thrown.
	 * 
	 * @param array an array of longs.
	 * @param size the number of bits of the newly created bit vector.
	 * @return a bit vector of size <code>size</code> using <code>array</code> as backing array.
	 */
	public static LongArrayBitVector wrap( final long[] array, final long size ) {
		if ( size > array.length << LOG2_BITS_PER_WORD ) throw new IllegalArgumentException( "The provided array is too short (" + array.length + " elements) for the given size (" + size + ")" );
		final LongArrayBitVector result = new LongArrayBitVector( 0 );
		result.length = size;
		result.bits = array;
		
		final int arrayLength = array.length;
		final int lastWord = (int)( size / Long.SIZE ); 
		if ( lastWord < arrayLength && ( array[ lastWord ] & ~ ( ( 1L << size % Long.SIZE ) - 1 ) ) != 0 )  throw new IllegalArgumentException( "Garbage beyond size in bit array" );
		for( int i = lastWord + 1; i < arrayLength; i++ ) if ( array[ i ] != 0 ) throw new IllegalArgumentException( "Garbage beyond size in bit array" );
		return result;
	}

	/** Wraps the given array of longs in a bit vector.
	 * 
	 * @param array an array of longs.
	 * @return a bit vector of size <code>array.length * Long.SIZE</code> using <code>array</code> as backing array.
	 */
	public static LongArrayBitVector wrap( final long[] array ) {
		return wrap( array, array.length * Long.SIZE );
	}

	/** Returns a cloned copy of this bit vector.
	 * 
	 * <P>This method is functionally equivalent to {@link #copy()},
	 * except that {@link #copy()} trims the backing array.
	 * 
	 * @return a copy of this bit vector.
	 */
	public LongArrayBitVector clone() throws CloneNotSupportedException {
		LongArrayBitVector copy = (LongArrayBitVector)super.clone();
		copy.bits = bits.clone();
		return copy;
	}

	public boolean equals( final Object o ) {
		if ( o instanceof LongArrayBitVector ) return equals( (LongArrayBitVector) o );
		return super.equals( o );
	}

	public boolean equals( final LongArrayBitVector v ) {
		if ( length != v.length() ) return false;
		int i = numWords( length );
		while( i-- != 0 ) if ( bits[ i ] != v.bits[ i ] ) return false;
		return true;
	}

	public int hashCode() {
		long h = 1234;
		for( int i = numWords( length ); i-- != 0; )
			h ^= bits[ i ] * ( i + 1 );

		return (int)( ( h >> 32 ) ^ h );
	}
	
	/** A list-of-integers view of a bit vector. 
	 * 
	 * <P>This class implements in the obvious way a view
	 * of a bit vector as a list of integers of given width. The vector is enlarged as needed (i.e., when
	 * adding new elements), but it is never shrunk.
	 */
	
	protected static class LongBigListView extends AbstractBitVector.LongBigListView {
		private static final long serialVersionUID = 1L;
		private static final boolean ASSERTS = false;
		@SuppressWarnings("hiding")
		final private LongArrayBitVector bitVector;
		
		public LongBigListView( final LongArrayBitVector bitVector, final int width ) {
			super( bitVector, width );
			this.bitVector = bitVector;
		}
		
		@Override
		public boolean add( long value ) {
			bitVector.append( value, width );
			return true;
		}

		@Override
		public long getLong( long index ) {
			final long start = index * width;
			return bitVector.getLong( start, start + width );
		}

		@Override
		public void clear() {
			bitVector.clear();
		}
		
		@Override
		public long set( long index, long value ) {
			if ( value > maxValue ) throw new IllegalArgumentException( "Value too large:" + value );
			final long bits[] = bitVector.bits;
			final long start = index * width;
			final int startWord = word( start );
			final int endWord = word( start + width - 1 );
			final int startBit = bit( start );
			final long oldValue;

			if ( startWord == endWord ) {
				oldValue = bits[ startWord ] >>> startBit & maxValue;
				bits[ startWord ] &= ~ ( maxValue << startBit );
				bits[ startWord ] |= value << startBit;

				if ( ASSERTS ) assert value == ( bits[ startWord ] >>> startBit & maxValue );
			}
			else {
				// Here startBit > 0.
				oldValue = bits[ startWord ] >>> startBit | bits[ endWord ] << ( BITS_PER_WORD - startBit ) & maxValue;	
				bits[ startWord ] &= ( 1L << startBit ) - 1;
				bits[ startWord ] |= value << startBit;
				bits[ endWord ] &=  - ( 1L << width - BITS_PER_WORD + startBit );
				bits[ endWord ] |= value >>> BITS_PER_WORD - startBit;
				
				if ( ASSERTS ) assert value == ( bits[ startWord ] >>> startBit | bits[ endWord ] << ( BITS_PER_WORD - startBit ) & maxValue );
			}
			return oldValue;
		}
	}
	
	@Override
	public LongBigList asLongBigList( final int width ) {
		return new LongBigListView( this, width );
	}	
	
	private void writeObject( final ObjectOutputStream s ) throws IOException {
		s.defaultWriteObject();
		final int numWords = numWords( length );
		for( int i = 0; i < numWords; i++ ) s.writeLong( bits[ i ] );
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		final int numWords = numWords( length );
		bits = new long[ numWords ];
		for( int i = 0; i < numWords; i++ ) bits[ i ] = s.readLong();
	}

}
