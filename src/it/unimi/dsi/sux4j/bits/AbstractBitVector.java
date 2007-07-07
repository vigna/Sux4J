package it.unimi.dsi.sux4j.bits;

/*		 
 * Sux4J: Succinct data structures for Java
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

import it.unimi.dsi.fastutil.booleans.AbstractBooleanList;
import it.unimi.dsi.fastutil.longs.AbstractLongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.AbstractLongSortedSet;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import java.util.NoSuchElementException;

/** An abstract implementation of a {@link it.unimi.dsi.sux4j.bits.BitVector}.
 * 
 * <P>This abstract implementation provides almost all methods: you have to provide just
 * {@link it.unimi.dsi.fastutil.booleans.BooleanList#getBoolean(int)} and
 * {@link java.util.List#size()}. No attributes are defined.
 * 
 * <P>Note that the integer-set view provided by {@link #asLongSet()} is not cached: if you
 * want to cache the result of the first call, you must do your own caching.
 */
public abstract class AbstractBitVector extends AbstractBooleanList implements BitVector {
	
    protected void ensureRestrictedIndex( final long index ) {
        if ( index < 0 )  throw new IndexOutOfBoundsException( "Index (" + index + ") is negative" );
        if ( index >= length() ) throw new IndexOutOfBoundsException( "Index (" + index + ") is greater than or equal to length (" + ( length() ) + ")" );
    }
	
    protected void ensureIndex( final long index ) {
        if ( index < 0 )  throw new IndexOutOfBoundsException( "Index (" + index + ") is negative" );
        if ( index > length() ) throw new IndexOutOfBoundsException( "Index (" + index + ") is greater than length (" + ( length() ) + ")" );
    }

    public void set( final int index ) { set( index, true ); }
	public void clear( final int index ) { set( index, false ); }
	public void flip( final int index ) { set( index, ! getBoolean( index ) ); }

	public void set( final long index ) { set( index, true ); }
	public void clear( final long index ) { set( index, false ); }
	public void flip( final long index ) { set( index, ! getBoolean( index ) ); }
	
	public void fill( final boolean value ) { for( long i = length(); i-- != 0; ) set( i, value ); }
	public void fill( final int value ) { fill( value != 0 ); }
	public void flip() { for( long i = length(); i-- != 0; ) flip( i ); }

	public void fill( final long from, final long to, final boolean value ) { BitVectors.ensureFromTo( length(), from, to ); for( long i = to; i-- != from; ) set( i, value ); }
	public void fill( final long from, final long to, final int value ) { fill( from, to, value != 0 ); }
	public void flip( final long from, final long to ) { BitVectors.ensureFromTo( length(), from, to ); for( long i = to; i-- != from; ) flip( i ); }

	public int getInt( final long index ) { return getBoolean( index ) ? 1 : 0; }
	public boolean getBoolean( final int index ) { return getBoolean( (long)index ); }

	public boolean removeBoolean( final int index ) { return removeBoolean( (long)index ); }
	public boolean set( final int index, final boolean value ) { return set( (long)index, value ); }
	public void add( final int index, final boolean value ) { add( (long)index, value ); }

	public boolean removeBoolean( final long index ) { throw new UnsupportedOperationException(); }
	public boolean set( final long index, final boolean value ) { throw new UnsupportedOperationException(); }
	public void add( final long index, final boolean value ) { throw new UnsupportedOperationException(); }
	
	public void set( final long index, final int value ) { set( index, value != 0 ); }
	public void add( final long index, final int value ) { add( index, value != 0 ); }
	public boolean add( final boolean value ) { add( length(), value ); return true; }
	public void add( final int value ) { add( value != 0 ); }

	public BitVector copy() { return copy( 0, size() ); }

	public long count() {
		long c = 0;
		for( long i = length(); i-- != 0; ) c += getInt( i );
		return c;
	}
	
	public long mostSignificantBit() {
		for ( long i = length(); i-- != 0; ) if ( getBoolean( i ) ) return i;
		return -1;
	}
	
	public long leastSignificantBit() {
		final long length = length();
		for( int i = 0; i < length; i++ ) if ( getBoolean( i ) ) return i;
		return -1;
	}
	
	public long maximumCommonPrefixLength( final BitVector v ) {
		final long minLength = Math.min( length(), v.length() );
		for( long i = 0; i < minLength; i++ ) if ( getBoolean( i ) != v.getBoolean( i ) ) return i;
		return minLength;
	}

	public void and( final BitVector v ) {
		for( int i = Math.min( size(), v.size() ); i-- != 0; ) if ( ! v.getBoolean( i ) ) clear( i );
	}
	
	public void or( final BitVector v ) {
		for( int i = Math.min( size(), v.size() ); i-- != 0; ) if ( v.getBoolean( i ) ) set( i );
	}

	public void xor( final BitVector v ) {
		for( int i = Math.min( size(), v.size() ); i-- != 0; ) if ( v.getBoolean( i ) ) flip( i );
	}

	public int size() {
		final long length = length();
		if ( length > Integer.MAX_VALUE ) throw new IllegalStateException( "The number of bits of this bit vector (" + length + ") exceeds Integer.MAX_INT" );
		return (int)length;
	}
	
	public void size( final int newSize ) {
		length( newSize );
	}
	
	public void clear() {
		length( 0 );
	}

	public boolean equals( final Object o ) {
		if ( ! ( o instanceof BitVector ) ) return false;
		BitVector v = (BitVector)o;
		long length = length();
		if ( length != v.length() ) return false;
		while( length-- != 0 ) if ( getBoolean( length ) != v.getBoolean( length ) ) return false;
		return true;
	}

	
	/** An integer sorted set view of a bit vector. 
	 * 
	 * <P>This class implements in the obvious way an integer set view
	 * of a bit vector. The vector is enlarged as needed (i.e., when
	 * a one beyond the current size is set), but it is never shrunk.
	 */
	
	public static class LongSetView extends AbstractLongSortedSet implements LongSet {
		private final BitVector bitVector;
		private final long from;
		private final long to;
		private final boolean top;
		
		public LongSetView( final BitVector bitVector, final long from, final long to, final boolean top ) {
			if ( from > to ) throw new IllegalArgumentException( "Start index (" + from + ") is greater than end index (" + to + ")" );
			this.bitVector = bitVector;
			this.from = from;
			this.to = to;
			this.top = top;
		}

		
		public boolean contains( final long index ) {
			if ( index < 0 ) throw new IllegalArgumentException( "The provided index (" + index + ") is negative" );
			if ( index < from || !top && index > to ) return false;
			return index < bitVector.size() && bitVector.getBoolean( index );
		}
		
		public boolean add( final long index ) {
			if ( index < 0 ) throw new IllegalArgumentException( "The provided index (" + index + ") is negative" );
			if ( index < from || !top && index > to ) return false;

			final int size = bitVector.size();
			if ( index >= size ) bitVector.length( index + 1 );
			final boolean oldValue = bitVector.getBoolean( index );
			bitVector.set( index );
			return ! oldValue;
		}

		public boolean remove( final long index ) {
			final int size = bitVector.size();
			if ( index >= size ) return false;
			final boolean oldValue = bitVector.getBoolean( index );
			bitVector.clear( index );
			return oldValue;
		}
		
		public int size() {
			if ( bitVector.count() > Integer.MAX_VALUE ) throw new IllegalStateException( "Set is too large to return an integer size" );
			return (int)bitVector.count();
		}
		
		public LongBidirectionalIterator iterator( final long from ) {
			// TODO: implement 
			throw new UnsupportedOperationException();
		}
			
		public LongBidirectionalIterator iterator() {
			// TODO: implement bidirectionality
			return new AbstractLongBidirectionalIterator() {
				long pos = -1;
				boolean toAdvance = true;
				
				public boolean hasNext() {
					final long length = bitVector.length();
					if ( ! toAdvance ) return pos < length;
					toAdvance = false;
					while( ++pos < length && ! bitVector.getBoolean( pos ) );
					return pos < length;
				}
				
				public long nextLong() {
					if ( ! hasNext() ) throw new NoSuchElementException();
					toAdvance = true;
					return pos;
				}
				
				public long previousLong() {
					throw new UnsupportedOperationException();
				}

				public boolean hasPrevious() {
					throw new UnsupportedOperationException();
				}

				public void remove() {
					if ( pos == -1 ) throw new IllegalStateException();
					bitVector.clear( pos );
				}
			};
		}
		
		public long firstLong() {
			final int size = bitVector.size();
			for( int i = 0; i < size; i++ ) if ( bitVector.getBoolean( i ) ) return i;
			throw new NoSuchElementException();
		}

		public long lastLong() {
			for( int i = bitVector.size(); i-- != 0; ) if ( bitVector.getBoolean( i ) ) return i;
			throw new NoSuchElementException();
		}

		public LongComparator comparator() {
			return null;
		}
		
		public LongSortedSet headSet( final long to ) {
			if ( top ) return new LongSetView( bitVector, from, to, false );
			return to < this.to ? new LongSetView( bitVector, from, to, false ) : this;
		}

		public LongSortedSet tailSet( final long from ) {
			return from > this.from ? new LongSetView( bitVector, from, to, top ) : this;
		}

		public LongSortedSet subSet( long from, long to ) {
			if ( ! top ) to = to < this.to ? to : this.to;
			from = from > this.from ? from : this.from;
			if ( ! top && from == this.from && to == this.to ) return this;
			return new LongSetView( bitVector, from, to, false );
		}		
	}
	
	public void length( long newLength ) {
		final long length = length();
		if ( length < newLength ) for( long i = newLength - length; i-- != 0; ) add( false );
		else for( long i = length - newLength; i-- != 0; ) removeBoolean( length );
	}

	public LongSet asLongSet() {
		return new LongSetView( this, 0, 0, true );
	}	
	
	public SubBitVector subList( final int from, final int to ) {
		return new SubBitVector( this, from, to );
	}

	public BitVector subVector( final long from, final long to ) {
		return new SubBitVector( this, from, to );
	}

	/** Returns a string representation of this vector.
	 * 
	 * <P>Note that this string representation shows the least significant bit on the right.
	 * @return a string representation of this vector, with the most significant bit on the left.
	 */
	
	public String toString() {
		final StringBuffer s = new StringBuffer();
		final int size = size();
		for( int i = 0; i < size; i++ ) s.append( getInt( i ) );
		return s.toString();
	}

	/** A subvector of a given bit vector, specified by an initial and a final bit. */
	
	public static class SubBitVector extends AbstractBitVector implements BitVector {
		private static final long serialVersionUID = 1L;
		final protected BitVector bitVector;		
		protected long from;
		protected long to;
		
		public SubBitVector( final BitVector l, final long from, final long to ) {
			this.from = from;
			this.to = to;
			bitVector = l;
		}
		
		public boolean getBoolean( final long index ) { return bitVector.getBoolean( from + index ); }
		public int getInt( final long index ) { return getBoolean( index ) ? 1 : 0; }
		public boolean set( final long index, final boolean value ) { return bitVector.set( from + index, value ); }
		public void set( final long index, final int value ) { set( index, value != 0 ); }
		public void add( final long index, final boolean value ) { bitVector.add( from + index, value ); }
		public void add( final long index, final int value ) { add( index, value ); }
		public void add( final int value ) { bitVector.add( to, value ); }
		public boolean removeBoolean( final long index ) { return bitVector.removeBoolean( from + index ); }
		
		public BitVector copy() { return copy( 0, length() ); }

		public BitVector copy( final long from, final long to ) {
			BitVectors.ensureFromTo( length(), from, to );
			return bitVector.copy( this.from + from, this.from + to );
		}
		
		public BitVector subVector( final long from, final long to ) {
			BitVectors.ensureFromTo( length(), from, to );
			return new SubBitVector( bitVector, this.from + from, this.from + to );
		}

		public long count() {
			int c = 0;
			for( long i = length(); i-- != 0; ) c += getInt( i );
			return c;
		}
		
		public long mostSignificantBit() {
			for ( long i = length(); i-- != 0; ) if ( getBoolean( i ) ) return i;
			return -1;
		}
		
		public long leastSignificantBit() {
			final long length = length();
			for( long i = 0; i < length; i++ ) if ( getBoolean( i ) ) return i;
			return -1;
		}
		
		public long maximumCommonPrefixLength( final BitVector v ) {
			final long minlength = Math.min( length(), v.length() );
			for( int i = 0; i < minlength; i++ ) if ( getBoolean( i ) != v.getBoolean( i ) ) return i;
			return minlength;
		}

		public void and( final BitVector v ) {
			for( int i = Math.min( size(), v.size() ); i-- != 0; ) if ( ! v.getBoolean( i ) ) clear( i );
		}
		
		public void or( final BitVector v ) {
			for( int i = Math.min( size(), v.size() ); i-- != 0; ) if ( v.getBoolean( i ) ) set( i );
		}

		public void xor( final BitVector v ) {
			for( int i = Math.min( size(), v.size() ); i-- != 0; ) if ( v.getBoolean( i ) ) flip( i );
		}

		public LongSet asLongSet() {
			return new LongSetView( this, 0, 0, true );
		}

		public long length() {
			return to - from;
		}

		public void length( long newLength ) {
			final long length = length();
			if ( length < newLength ) for( long i = newLength - length; i-- != 0; ) bitVector.add( to++, false );
			else for( long i = length - newLength; i-- != 0; ) bitVector.removeBoolean( --to );
		}
	}
}
