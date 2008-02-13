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


import java.io.Serializable;
import java.util.Iterator;
import java.util.List;


public class ShiftAddXorSignedStringMap<S extends CharSequence> implements StringMap<S>, Serializable {
	private static final long serialVersionUID = 0L;
	private static final boolean ASSERTS = false;

	/** The underlying map. */
	protected final StringMap<S> map;
	/** Signatures. */
	protected final int[] signature;

	public ShiftAddXorSignedStringMap( final Iterable<S> iterable, final StringMap<S> map ) {
		S s;
		this.map = map;
		final int n = map.size();
		signature = new int[ n ];

		final Iterator<S> iterator = iterable.iterator();
		for( int i = 0; i < n; i++ ) {
			s = iterator.next();
			if ( ASSERTS ) assert map.getLong( s ) == i : "On " + s + ", " + map.getLong( s ) + " != " + i; 
			signature[ i ] = signature( s );
		}

	}

	private int signature( final S s ) {
		int i, h = 42, l = s.length();
		for ( i = l; i-- != 0; )
			h ^= ( h << 5 ) + s.charAt( i ) + ( h >>> 2 );
		return h;
	}
	
	private boolean checkSignature( final S s, final long index ) {
		return signature[ (int)index ] == signature( s );
	}

	@SuppressWarnings("unchecked")
	public long getLong( Object o ) {
		final S s = (S)o;
		final long index = map.getLong( s );
		return checkSignature( s, index ) ? index : map.defaultReturnValue();
	}

	@SuppressWarnings("unchecked")
	public Long get( Object o ) {
		final S s = (S)o;
		final long index = map.getLong( s );
		return checkSignature( s, index ) ? Long.valueOf( index ) : null;
	}

	@SuppressWarnings("unchecked")
	public boolean containsKey( Object o ) {
		final S s = (S)o;
		return checkSignature( s, map.getLong( s ) );
	}

	public long defaultReturnValue() {
		return map.defaultReturnValue();
	}

	public void defaultReturnValue( long rv ) {
		map.defaultReturnValue( rv );
	}

	public int size() {
		return map.size();
	}

	public List<S> list() {
		return map.list();
	}

	public long put( S key, long value ) {
		throw new UnsupportedOperationException();
	}

	public long removeLong( Object key ) {
		throw new UnsupportedOperationException();
	}

	public void clear() {
		throw new UnsupportedOperationException();
	}

	public Long put( S key, Long value ) {
		throw new UnsupportedOperationException();
	}

	public Long remove( Object key ) {
		throw new UnsupportedOperationException();
	}
}
