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

import it.unimi.dsi.fastutil.Arrays;
import it.unimi.dsi.fastutil.booleans.AbstractBooleanList;
import it.unimi.dsi.fastutil.ints.AbstractIntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.AbstractIntSortedSet;
import it.unimi.dsi.fastutil.ints.IntBidirectionalIterator;
import it.unimi.dsi.fastutil.ints.IntComparator;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;

import java.util.NoSuchElementException;

/** An abstract implementation of a {@link it.unimi.dsi.sux4j.bits.BitVector}.
 * 
 * <P>This abstract implementation provides almost all methods: you have to provide just
 * {@link it.unimi.dsi.fastutil.booleans.BooleanList#getBoolean(int)} and
 * {@link java.util.List#size()}. No attributes are defined.
 * 
 * <P>Note that the integer-set view provided by {@link #asIntSet()} is not cached: if you
 * want to cache the result of the first call, you must do your own caching.
 */
public abstract class AbstractBitVector extends AbstractBooleanList implements BitVector {
	
	public void set( final int index ) { set( index, true ); }
	public void clear( final int index ) { set( index, false ); }
	public void flip( final int index ) { set( index, ! getBoolean( index ) ); }
	
	public void fill( final boolean value ) { for( int i = size(); i-- != 0; ) set( i, value ); }
	public void fill( final int value ) { fill( value != 0 ); }
	public void flip() { for( int i = size(); i-- != 0; ) flip( i ); }

	public void fill( final int from, final int to, final boolean value ) { Arrays.ensureFromTo( size(), from, to ); for( int i = to; i-- != from; ) set( i, value ); }
	public void fill( final int from, final int to, final int value ) { fill( from, to, value != 0 ); }
	public void flip( final int from, final int to ) { Arrays.ensureFromTo( size(), from, to ); for( int i = to; i-- != from; ) flip( i ); }

	public int getInt( final int index ) { return getBoolean( index ) ? 1 : 0; }
	public void set( final int index, final int value ) { set( index, value != 0 ); }
	public void add( final int index, final int value ) { add( index, value != 0 ); }
	public void add( final int value ) { add( value != 0 ); }
	
	public BitVector copy() { return copy( 0, size() ); }

	public int count() {
		int c = 0;
		for( int i = size(); i-- != 0; ) c += getInt( i );
		return c;
	}
	
	public int mostSignificantBit() {
		for ( int i = size(); i-- != 0; ) if ( getBoolean( i ) ) return i;
		return -1;
	}
	
	public int leastSignificantBit() {
		final int size = size();
		for( int i = 0; i < size; i++ ) if ( getBoolean( i ) ) return i;
		return -1;
	}
	
	public int maximumCommonPrefixLength( final BitVector v ) {
		final int minSize = Math.min( size(), v.size() );
		for( int i = 0; i < minSize; i++ ) if ( getBoolean( i ) != v.getBoolean( i ) ) return i;
		return minSize;
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

	/** An integer sorted set view of a bit vector. 
	 * 
	 * <P>This class implements in the obvious way an integer set view
	 * of a bit vector. The vector is enlarged as needed (i.e., when
	 * a one beyond the current size is set), but it is never shrunk.
	 */
	
	public static class IntSetView extends AbstractIntSortedSet {
		private final BitVector bitVector;
		private final int from;
		private final int to;
		private final boolean top;
		
		public IntSetView( final BitVector bitVector, final int from, final int to, final boolean top ) {
			if ( from > to ) throw new IllegalArgumentException( "Start index (" + from + ") is greater than end index (" + to + ")" );
			this.bitVector = bitVector;
			this.from = from;
			this.to = to;
			this.top = top;
		}

		
		public boolean contains( final int index ) {
			if ( index < 0 ) throw new IllegalArgumentException( "The provided index (" + index + ") is negative" );
			if ( index < from || !top && index > to ) return false;
			return index < bitVector.size() && bitVector.getBoolean( index );
		}
		
		public boolean add( final int index ) {
			if ( index < 0 ) throw new IllegalArgumentException( "The provided index (" + index + ") is negative" );
			if ( index < from || !top && index > to ) return false;

			final int size = bitVector.size();
			if ( index >= size ) bitVector.size( index + 1 );
			final boolean oldValue = bitVector.getBoolean( index );
			bitVector.set( index );
			return ! oldValue;
		}

		public boolean remove( final int index ) {
			final int size = bitVector.size();
			if ( index >= size ) return false;
			final boolean oldValue = bitVector.getBoolean( index );
			bitVector.clear( index );
			return oldValue;
		}
		
		public int size() {
			return bitVector.count();
		}
		
		public IntBidirectionalIterator iterator( final int from ) {
			// TODO: implement 
			throw new UnsupportedOperationException();
		}
			
		public IntBidirectionalIterator iterator() {
			// TODO: implement bidirectionality
			return new AbstractIntBidirectionalIterator() {
				int pos = -1;
				boolean toAdvance = true;
				
				public boolean hasNext() {
					final int size = bitVector.size();
					if ( ! toAdvance ) return pos < size;
					toAdvance = false;
					while( ++pos < size && ! bitVector.getBoolean( pos ) );
					return pos < size;
				}
				
				public int nextInt() {
					if ( ! hasNext() ) throw new NoSuchElementException();
					toAdvance = true;
					return pos;
				}
				
				public int previousInt() {
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
		
		public int firstInt() {
			final int size = bitVector.size();
			for( int i = 0; i < size; i++ ) if ( bitVector.getBoolean( i ) ) return i;
			throw new NoSuchElementException();
		}

		public int lastInt() {
			for( int i = bitVector.size(); i-- != 0; ) if ( bitVector.getBoolean( i ) ) return i;
			throw new NoSuchElementException();
		}

		public IntComparator comparator() {
			return null;
		}
		
		public IntSortedSet headSet( final int to ) {
			if ( top ) return new IntSetView( bitVector, from, to, false );
			return to < this.to ? new IntSetView( bitVector, from, to, false ) : this;
		}

		public IntSortedSet tailSet( final int from ) {
			return from > this.from ? new IntSetView( bitVector, from, to, top ) : this;
		}

		public IntSortedSet subSet( int from, int to ) {
			if ( ! top ) to = to < this.to ? to : this.to;
			from = from > this.from ? from : this.from;
			if ( ! top && from == this.from && to == this.to ) return this;
			return new IntSetView( bitVector, from, to, false );
		}		
	}
	
	
	public IntSet asIntSet() {
		return new IntSetView( this, 0, 0, true );
	}	
	
	public SubBitVector subList( final int from, final int to ) {
		return new SubBitVector( this, from, to );
	}

	public BitVector subVector( final int from, final int to ) {
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
	
	public static class SubBitVector extends AbstractBooleanList.BooleanSubList implements BitVector {
		private static final long serialVersionUID = 1L;
		final protected BitVector bitVector;		

		public SubBitVector( final BitVector l, final int from, final int to) {
			super( l, from, to );
			bitVector = l;
		}

		public void set( final int index ) { set( index, true ); }
		public void clear( final int index ) { set( index, false ); }
		public void flip( final int index ) { set( index, ! getBoolean( index ) ); }
		
		public void fill( final boolean value ) { for( int i = size(); i-- != 0; ) set( i, value ); }
		public void fill( final int value ) { fill( value != 0 ); }
		public void flip() { for( int i = size(); i-- != 0; ) flip( i ); }

		public void fill( final int from, final int to, final boolean value ) { Arrays.ensureFromTo( size(), from, to ); for( int i = to; i-- != from; ) set( i, value ); }
		public void fill( final int from, final int to, final int value ) { fill( from, to, value != 0 ); }
		public void flip( final int from, final int to ) { Arrays.ensureFromTo( size(), from, to ); for( int i = to; i-- != from; ) flip( i ); }

		public int getInt( final int index ) { return getBoolean( index ) ? 1 : 0; }
		public void set( final int index, final int value ) { set( index, value != 0 ); }
		public void add( final int index, final int value ) { add( index, value != 0 ); }
		public void add( final int value ) { add( value != 0 ); }
		
		public BitVector copy() { return copy( 0, size() ); }

		public BitVector copy( final int from, final int to ) {
			Arrays.ensureFromTo( size(), from, to );
			return bitVector.copy( this.from + from, this.from + to );
		}
		
		public BitVector subVector( final int from, final int to ) {
			Arrays.ensureFromTo( size(), from, to );
			return new SubBitVector( bitVector, this.from + from, this.from + to );
		}

		public int count() {
			int c = 0;
			for( int i = size(); i-- != 0; ) c += getInt( i );
			return c;
		}
		
		public int mostSignificantBit() {
			for ( int i = size(); i-- != 0; ) if ( getBoolean( i ) ) return i;
			return -1;
		}
		
		public int leastSignificantBit() {
			final int size = size();
			for( int i = 0; i < size; i++ ) if ( getBoolean( i ) ) return i;
			return -1;
		}
		
		public int maximumCommonPrefixLength( final BitVector v ) {
			final int minSize = Math.min( size(), v.size() );
			for( int i = 0; i < minSize; i++ ) if ( getBoolean( i ) != v.getBoolean( i ) ) return i;
			return minSize;
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

		public IntSet asIntSet() {
			return new IntSetView( this, 0, 0, true );
		}	

	}
}
