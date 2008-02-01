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


import it.unimi.dsi.sux4j.bits.BitVector.TransformationStrategy;

/** A class providing static methods and objects that do useful things with bit vectors.
 * 
 * @see it.unimi.dsi.sux4j.bits.BitVector
 */

public class BitVectors {
	

	private BitVectors() {}	
	
	/** A trivial transformation for data already in {@link BitVector} form. */
	private final static BitVector.TransformationStrategy<BitVector> IDENTITY = new BitVector.TransformationStrategy<BitVector>() {
		private static final long serialVersionUID = 1L;

		public BitVector toBitVector( final BitVector object ) {
			return object;
		}
		
		public long numBits() { return 0; }
	};
	
	
	@SuppressWarnings("unchecked")
	public static <T extends BitVector> BitVector.TransformationStrategy<T> identity() {
		return (TransformationStrategy<T>)IDENTITY;
	}
 	
	
    public static void ensureFromTo( final long bitVectorLength, final long from, final long to ) {
        if ( from < 0 ) throw new ArrayIndexOutOfBoundsException( "Start index (" + from + ") is negative" );
        if ( from > to ) throw new IllegalArgumentException( "Start index (" + from + ") is greater than end index (" + to + ")" );
        if ( to > bitVectorLength ) throw new ArrayIndexOutOfBoundsException( "End index (" + to + ") is greater than array length (" + bitVectorLength + ")" );
    }
 	
	/** An immutable, singleton empty bit vector. */ 
	public final static BitVector EMPTY_VECTOR = new AbstractBitVector() {
		public final long length() { return 0; }
		public final BitVector copy( final long from, final long to ) { 
			ensureFromTo( 0, from, to );
			return EMPTY_VECTOR; 
		}
		public final boolean getBoolean( final long index ) { throw new IndexOutOfBoundsException(); }
	};

	/** An immutable bit vector of length one containing a zero. */ 
	public final static BitVector ZERO = new AbstractBitVector() {
		public final long length() { return 1; }
		public final BitVector copy( final long from, final long to ) { 
			ensureFromTo( 1, from, to );
			return from == to ? EMPTY_VECTOR : this; 
		}
		public final boolean getBoolean( final long index ) { if ( index > 0 ) throw new IndexOutOfBoundsException(); else return false; } 
	};

	/** An immutable bit vector of length one containing a one. */ 
	public final static BitVector ONE = new AbstractBitVector() {
		public final long length() { return 1; }
		public final BitVector copy( final long from, final long to ) { 
			ensureFromTo( 1, from, to );
			return from == to ? EMPTY_VECTOR : this; 
		}
		public final boolean getBoolean( final long index ) { if ( index > 0 ) throw new IndexOutOfBoundsException(); else return true; } 
	};
}
