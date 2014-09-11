package it.unimi.dsi.sux4j.util;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2014 Sebastiano Vigna 
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */


import it.unimi.dsi.big.util.ShiftAddXorSignedStringMap;
import it.unimi.dsi.big.util.StringMap;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.Size64;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import it.unimi.dsi.sux4j.mph.TwoStepsLcpMonotoneMinimalPerfectHashFunction;
import it.unimi.dsi.sux4j.mph.ZFastTrieDistributorMonotoneMinimalPerfectHashFunction;

import java.io.IOException;
import java.io.Serializable;

import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.UnflaggedOption;

/** A string map based on a signed function. 
 * 
 * <p>This class is a very thin wrapper around a signed {@linkplain Object2LongFunction function} on {@linkplain CharSequence character sequences}. Starting with version 3.1,
 * most succinct function implementations can be signed directly, without the help of a wrapper class such as {@link ShiftAddXorSignedStringMap}.
 * The new signature system is much faster and uses a higher-quality hash.
 * 
 * <p>Nonetheless, since all functions in Sux4J are generic (they can map any object) we need a thin adapter (this class) that exposes
 * a generic function as a {@linkplain StringMap string map} (e.g., for usage in <a href="http://mg4j.di.unimi.it/">MG4J</a>).
 * 
 * <p>This adapter does not (of course) implement {@link #list()}.
 * 
 * @author Sebastiano Vigna
 * @since 3.1.1
 */

public class SignedFunctionStringMap extends AbstractObject2LongFunction<CharSequence> implements StringMap<CharSequence>, Serializable {
	private static final long serialVersionUID = 0L;

	/** The underlying function. */
	protected final Object2LongFunction<? extends CharSequence> function;
	
	/** Creates a new string map by wrapping a specified signed function.
	 * 
	 * @param function a signed function.
	 */
	public SignedFunctionStringMap( final Object2LongFunction<? extends CharSequence> function ) {
		this.function = function;
	}

	/** Creates a new string map by creating and wrapping a {@link ZFastTrieDistributorMonotoneMinimalPerfectHashFunction}.
	 * 
	 * @param keys the keys used to populate the string map.
	 */
	public SignedFunctionStringMap( final Iterable<? extends CharSequence> keys ) throws IOException {
		this.function = new TwoStepsLcpMonotoneMinimalPerfectHashFunction.Builder<CharSequence>().keys( keys ).transform( TransformationStrategies.prefixFreeUtf16() ).build();
	}

	public long getLong( Object o ) {
		return function.getLong( o );
	}

	public Long get( Object o ) {
		final CharSequence s = (CharSequence)o;
		final long index = function.getLong( s );
		return index == -1 ? null : Long.valueOf( index );
	}

	public boolean containsKey( Object o ) {
		return function.getLong( o ) != -1;
	}

	@Override
	@Deprecated
	public int size() {
		return function.size();
	}

	@Override
	public long size64() {
		// A bit of a kluge.
		return function instanceof Size64 ? ((Size64)function).size64() : function.size();
	}

	@Override
	public ObjectBigList<CharSequence> list() {
		return null;
	}
	
	@Override
	public String toString() {
		return function.toString();
	}

	@SuppressWarnings("unchecked")
	public static void main( final String[] arg ) throws IOException, JSAPException, ClassNotFoundException {
		final SimpleJSAP jsap = new SimpleJSAP( SignedFunctionStringMap.class.getName(), "Saves a string map wrapping a signed function on character sequences.",
				new Parameter[] {
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename of a signed function defined on character sequences." ),
			new UnflaggedOption( "map", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename of the resulting string map." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String mapName = jsapResult.getString( "map" );
		BinIO.storeObject( new SignedFunctionStringMap( (Object2LongFunction<? extends CharSequence>)BinIO.loadObject( functionName ) ), mapName );
	}
}
