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

/** A class providing static methods and objects that do useful things with bit vectors.
 * 
 * @see it.unimi.dsi.sux4j.bits.BitVector
 */

public class BitVectors {

	private BitVectors() {}
	
	/** An immutable, singleton empty bit vector. */ 
	public final static BitVector EMPTY_VECTOR = new AbstractBitVector() {
		public final int size() { return 0; }
		public final BitVector copy( final int from, final int to ) { 
			Arrays.ensureFromTo( 0, from, to );
			return EMPTY_VECTOR; 
		}
		public final boolean getBoolean( final int index ) { throw new IndexOutOfBoundsException(); } 
	};

	/** An immutable bit vector of length one containing a zero. */ 
	public final static BitVector ZERO = new AbstractBitVector() {
		public final int size() { return 1; }
		public final BitVector copy( final int from, final int to ) { 
			Arrays.ensureFromTo( 1, from, to );
			return from == to ? EMPTY_VECTOR : this; 
		}
		public final boolean getBoolean( final int index ) { if ( index > 0 ) throw new IndexOutOfBoundsException(); else return false; } 
	};

	/** An immutable bit vector of length one containing a one. */ 
	public final static BitVector ONE = new AbstractBitVector() {
		public final int size() { return 1; }
		public final BitVector copy( final int from, final int to ) { 
			Arrays.ensureFromTo( 1, from, to );
			return from == to ? EMPTY_VECTOR : this; 
		}
		public final boolean getBoolean( final int index ) { if ( index > 0 ) throw new IndexOutOfBoundsException(); else return true; } 
	};
}
