package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2008 Sebastiano Vigna 
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


import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;

import java.io.Serializable;
import java.util.Iterator;
import java.util.List;

/** A string map based on a minimal perfect hash signed using Shift-Add-Xor hashes. 
 * 
 * <p>A minimal perfect hash maps a set of string to an initial segment of the natural
 * numbers, but will actually map <em>any</em> string to that segment. By signing
 * each output value with a hash of the string, we get a set-like functionality with 
 * 
 * <p>See &ldquo;Performance in practice of string hashing functions&rdquo;, by
 * M.V. Ramakrishna and Justin Zobel, <i>Proc. of the Fifth International Conference on 
 * Database Systems for Advanced Applications</i>, 1997, pages 215&minus;223.
 *
 * @author Sebastiano Vigna
 * @since 1.1.2
 */



public class ShiftAddXorSignedStringMap<S extends CharSequence> extends AbstractObject2LongFunction<S> implements StringMap<S>, Serializable {
	private static final long serialVersionUID = 0L;

	/** The underlying map. */
	protected final Object2LongFunction<S> hash;
	/** Signatures. */
	protected final LongBigList signatures;
	/** The width in bits of each signature. */
	protected final int width;
	/** The left shift to get only {@link #width} nonzero bits. */
	protected final int shift;
	/** The mask to get only {@link #width} nonzero bits. */
	protected final long mask;

	/** Creates a new shift-add-xor signed string map using a given hash map and 32-bit signatures.
	 * 
	 * @param iterator an iterator enumerating a set of strings.
	 * @param map a minimal perfect hash for the strings enumerated by <code>iterator</code>; it must support {@link Function#size() size()}
	 * and have default return value -1.
	 */
	
	public ShiftAddXorSignedStringMap( final Iterator<S> iterator, final Object2LongFunction<S> map ) {
		this( iterator, map, 32 );
	}

	/** Creates a new shift-add-xor signed string map using a given hash map.
	 * 
	 * @param iterator an iterator enumerating a set of strings.
	 * @param map a minimal perfect hash for the strings enumerated by <code>iterator</code>; it must support {@link Function#size() size()}
	 * and have default return value -1.
	 * @param signatureWidth the width, in bits, of the signature of each string.
	 */
	
	public ShiftAddXorSignedStringMap( final Iterator<S> iterator, final Object2LongFunction<S> map, final int signatureWidth ) {
		S s;
		this.hash = map;
		this.width = signatureWidth;
		this.defRetValue = -1;
		shift = Long.SIZE - width;
		mask = width == Long.SIZE ? 0 : ( 1L << width ) - 1;
		final int n = map.size();
		signatures = LongArrayBitVector.getInstance().asLongBigList( signatureWidth ).length( n );

		for( int i = 0; i < n; i++ ) {
			s = iterator.next();
			signatures.set( map.getLong( s ), signature( s ) );
		}
	}

	private long signature( final S s ) {
		int i, l = s.length();
		long h = 42;
		
		for ( i = l; i-- != 0; ) h ^= ( h << 5 ) + s.charAt( i ) + ( h >>> 2 );
		return ( h >>> shift ) ^ ( h & mask );
	}
	
	private boolean checkSignature( final S s, final long index ) {
		return signatures.getLong( index ) == signature( s );
	}

	@SuppressWarnings("unchecked")
	public long getLong( Object o ) {
		final S s = (S)o;
		final long index = hash.getLong( s );
		return index != -1 && checkSignature( s, index ) ? index : defRetValue;
	}

	@SuppressWarnings("unchecked")
	public Long get( Object o ) {
		final S s = (S)o;
		final long index = hash.getLong( s );
		return checkSignature( s, index ) ? Long.valueOf( index ) : null;
	}

	@SuppressWarnings("unchecked")
	public boolean containsKey( Object o ) {
		final S s = (S)o;
		return checkSignature( s, hash.getLong( s ) );
	}

	public int size() {
		return hash.size();
	}

	public List<S> list() {
		return null;
	}
}
