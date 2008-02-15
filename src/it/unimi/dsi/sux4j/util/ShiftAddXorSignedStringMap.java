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


import it.unimi.dsi.Util;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.Utf16TransformationStrategy;
import it.unimi.dsi.fastutil.Function;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.Object2LongFunction;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.MWHCFunction;
import it.unimi.dsi.sux4j.mph.MinimalPerfectHash;
import it.unimi.dsi.util.LongBigList;
import it.unimi.dsi.util.StringMap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** A string map based on a minimal perfect hash signed using Shift-Add-Xor hashes. 
 * 
 * <p>A minimal perfect hash maps a set of string to an initial segment of the natural
 * numbers, but will actually map <em>any</em> string to that segment. By signing
 * each output value with a hash of the string, we get a set-like functionality with a rate
 * error that can be balanced with space occupancy (signatures can go from 1 to {@link Long#SIZE} bits). 
 * 
 * <p>See &ldquo;Performance in practice of string hashing functions&rdquo;, by
 * M.V. Ramakrishna and Justin Zobel, <i>Proc. of the Fifth International Conference on 
 * Database Systems for Advanced Applications</i>, 1997, pages 215&minus;223.
 *
 * @author Sebastiano Vigna
 * @since 1.1.2
 */



public class ShiftAddXorSignedStringMap extends AbstractObject2LongFunction<CharSequence> implements StringMap<CharSequence>, Serializable {
	private static final long serialVersionUID = 0L;
	private static final Logger LOGGER = Util.getLogger( ShiftAddXorSignedStringMap.class );

	/** The underlying map. */
	protected final Object2LongFunction<CharSequence> hash;
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
	
	public ShiftAddXorSignedStringMap( final Iterator<? extends CharSequence> iterator, final Object2LongFunction<CharSequence> map ) {
		this( iterator, map, 32 );
	}

	/** Creates a new shift-add-xor signed string map using a given hash map.
	 * 
	 * @param iterator an iterator enumerating a set of strings.
	 * @param map a minimal perfect hash for the strings enumerated by <code>iterator</code>; it must support {@link Function#size() size()}
	 * and have default return value -1.
	 * @param signatureWidth the width, in bits, of the signature of each string.
	 */
	
	public ShiftAddXorSignedStringMap( final Iterator<? extends CharSequence> iterator, final Object2LongFunction<CharSequence> map, final int signatureWidth ) {
		CharSequence s;
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

	private long signature( final CharSequence s ) {
		int i, l = s.length();
		long h = 42;
		
		for ( i = l; i-- != 0; ) h ^= ( h << 5 ) + s.charAt( i ) + ( h >>> 2 );
		return ( h >>> shift ) ^ ( h & mask );
	}
	
	private boolean checkSignature( final CharSequence s, final long index ) {
		return signatures.getLong( index ) == signature( s );
	}

	@SuppressWarnings("unchecked")
	public long getLong( Object o ) {
		final CharSequence s = (CharSequence)o;
		final long index = hash.getLong( s );
		return index != -1 && index < hash.size() && checkSignature( s, index ) ? index : defRetValue;
	}

	@SuppressWarnings("unchecked")
	public Long get( Object o ) {
		final CharSequence s = (CharSequence)o;
		final long index = hash.getLong( s );
		return checkSignature( s, index ) ? Long.valueOf( index ) : null;
	}

	@SuppressWarnings("unchecked")
	public boolean containsKey( Object o ) {
		final CharSequence s = (CharSequence)o;
		return checkSignature( s, hash.getLong( s ) );
	}

	public int size() {
		return hash.size();
	}

	public List<CharSequence> list() {
		return null;
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( ShiftAddXorSignedStringMap.class.getName(), "Builds a shift-add-xor signed string map reading a newline-separated list of strings.",
				new Parameter[] {
			new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read strings." ),
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new FlaggedOption( "stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read strings from this file (without loading them into core memory) instead of standard input." ),
			new FlaggedOption( "width", JSAP.INTEGER_PARSER, Integer.toString( Integer.SIZE ), JSAP.NOT_REQUIRED, 'w', "width", "The signature width in bits." ),
			new Switch( "preserveOrder", 'p', "preserve-order", "The value assigned to each string will be exactly its ordinal position in the list." ),
			new UnflaggedOption( "map", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised signed string map." )
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final int bufferSize = jsapResult.getInt( "bufferSize" );
		final String mapName = jsapResult.getString( "map" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final int width = jsapResult.getInt( "width" );
		final boolean preserveOrder = jsapResult.getBoolean( "preserveOrder" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );

		final Object2LongFunction<CharSequence> hash;
		final Collection<MutableString> collection;

		if ( stringFile == null ) { // TODO: replace with new LineIterator
			collection = new ArrayList<MutableString>();
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.itemsName = "strings";
			final LineIterator stringIterator = new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ), pl );

			pl.start( "Reading strings..." );
			while( stringIterator.hasNext() ) collection.add( stringIterator.next().copy() );
			pl.done();

			LOGGER.info( "Building minimal perfect hash table..." );
		}
		else collection = new FileLinesCollection( stringFile, "UTF-8", zipped );

		LOGGER.info( "Building minimal perfect hash table..." );
		hash = preserveOrder ? 
				new MWHCFunction<CharSequence>( collection, new Utf16TransformationStrategy() ) :
				new MinimalPerfectHash<CharSequence>( collection, new Utf16TransformationStrategy() );

		LOGGER.info( "Signing..." );
		ShiftAddXorSignedStringMap map = new ShiftAddXorSignedStringMap( collection.iterator(), hash, width );
		
		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( map, mapName );
		LOGGER.info( "Completed." );
	}
}
