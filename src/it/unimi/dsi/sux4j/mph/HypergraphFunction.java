package it.unimi.dsi.sux4j.mph;

/*		 
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2002-2008 Sebastiano Vigna 
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

import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.mg4j.io.FileLinesCollection;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.Fast;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.LongBigList;
import it.unimi.dsi.sux4j.bits.BitVector.TransformationStrategy;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Random;

import org.apache.log4j.Logger;

import test.it.unimi.dsi.sux4j.mph.HypergraphVisit;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.UnflaggedOption;
import com.martiansoftware.jsap.stringparsers.ForNameStringParser;

/** A read-only function stored using hypergraph techniques.
 * 
 * @author Sebastiano Vigna
 * @since 0.2
 */

public class HypergraphFunction<T> extends AbstractHash<T> implements Serializable {
    public static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Fast.getLogger( HypergraphFunction.class );
		
	/** The number of elements. */
	final protected int n;
	/** The number of vertices of the intermediate hypergraph. */
	final protected int m;
	/** Initialisation value for {@link Hashes#jenkins(BitVector, long, long[]) Jenkins's hash}. */
	final protected long init;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */
	final protected LongBigList bits;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	final protected TransformationStrategy<? super T> transform;
    
	/** Creates a new function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>. 
	 */

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public HypergraphFunction( final Iterable<? extends T> elements, final BitVector.TransformationStrategy<? super T> transform, final LongList values, final int width ) {
		this.transform = transform;
		
		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		if ( elements instanceof Collection ) n = ((Collection<? extends T>)elements).size();
		else {
			int c = 0;
			// Do not add a suppression annotation--it breaks javac
			for( T o: elements ) c++;
			n = c;
		}
		
		HypergraphVisit<T> visit = new HypergraphVisit<T>( n );

		m = visit.numVertices;
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance();
		bits = bitVector.asLongBigList( width );
		bits.size( m );

		final Random r = new Random();
		long seed;
		do {
			LOGGER.info( "Generating random hypergraph..." );
			seed = r.nextLong();
		} while ( ! visit.visit( elements.iterator(), transform, seed ) );
		
		init = seed;
		
		/* We assign values. */
		/** Whether a specific node has already been used as perfect hash value for an item. */
		final BitVector used = LongArrayBitVector.getInstance( m ).length( m );
		
		final int[] stack = visit.stack;
		final int[][] edge = visit.edge;
		int top = n, k, s, v = 0;
		while( top > 0 ) {
			k = stack[ --top ];

			s = 0;

			for ( int j = 0; j < 3; j++ ) {
				if ( ! used.getBoolean( edge[ j ][ k ] ) ) v = j;
				else s ^= bits.getLong( edge[ j ][ k ] );
				used.set( edge[ j ][ k ] );
			}
			
			bits.set( edge[ v ][ k ], ( values == null ? k : values.getLong( k ) ) ^ s );
		}

		LOGGER.info( "Completed." );
	}


	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		final long[] h = new long[ 3 ];
		final int[] e = new int[ 3 ];
		Hashes.jenkins( transform.toBitVector( (T)o ), init, h );
		HypergraphVisit.hashesToEdge( h, e, m );
		return bits.getLong( e[ 0 ] ) ^ bits.getLong( e[ 1 ] ) ^ bits.getLong( e[ 2 ] );
	}
	
	/** Returns the number of elements in the function domain.
	 *
	 * @return the number of the elements in the function domain.
	 */
	public int size() {
		return n;
	}


	/** Creates a new function by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param function the function to be copied.
	 */
	protected HypergraphFunction( final HypergraphFunction<T> function ) {
		this.n = function.n;
		this.m = function.m;
		this.init = function.init;
		this.bits = function.bits;
		this.transform = function.transform.copy();
	}
	

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MinimalPerfectHash.class.getName(), "Builds a function reading a newline-separated list of strings; strings are mapped to their ordinal position.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new FlaggedOption( "stringFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read strings from this file (without loading them into core memory) instead of standard input." ),
			new UnflaggedOption( "table", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised minimal perfect hash table." )
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String tableName = jsapResult.getString( "table" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );

		final HypergraphFunction<CharSequence> function;

		LOGGER.info( "Building function..." );
		//new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, "UTF-8" ) ) )
		
		final FileLinesCollection flc = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final int size = flc.size();
		function = new HypergraphFunction<CharSequence>( flc, new Utf16TransformationStrategy(), null, Fast.ceilLog2( size ) );

		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( function, tableName );
		LOGGER.info( "Completed." );
	}


}
