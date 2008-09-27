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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.bits.Rank16;
import it.unimi.dsi.util.LongBigList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Random;
import java.util.zip.GZIPInputStream;

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

/** An immutable function stored using the Majewski-Wormald-Havas-Czech {@linkplain HypergraphSorter 3-hypergraph technique}.
 * 
 * <p>Instance of this class store a function from keys to values. Keys are provided by an {@linkplain Iterable iterable object} (whose iterators
 * must return elements in a consistent order), whereas values are provided by a {@link LongList}. 
 * A convenient {@linkplain #MWHCFunction(Iterable, TransformationStrategy) constructor} 
 * automatically assigns to each key its ordinal value. 
 * 
 * <P>As a commodity, this class provides a main method that reads from
 * standard input a (possibly <samp>gzip</samp>'d) sequence of newline-separated strings, and
 * writes a serialised function mapping each element of the list to its position.
 * 
 * <h2>Implementation Details</h2>
 * 
 * <p>After generating a random 3-hypergraph with suitably sorted edges,
 * we assign to each vertex a value in such a way that for each 3-hyperedge the 
 * xor of the three values associated to its vertices is the required value for the corresponding
 * element of the function domain. Then, we measure whether it is favourable to store nonzero values
 * in a separate array, using a ranked marker array to record the positions of the values equal to zero.
 * 
 * @author Sebastiano Vigna
 * @since 0.2
 */

public class MWHCFunction<T> extends AbstractObject2LongFunction<T> implements Serializable {
    public static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Util.getLogger( MWHCFunction.class );
	private static final boolean ASSERTS = false;
		
	/** The number of elements. */
	final protected int n;
	/** The number of vertices of the intermediate hypergraph. */
	final protected int m;
	/** The data width. */
	final protected int width;
	/** The seed of the underlying 3-hypergraph. */
	final protected long seed;
	/** The final magick&mdash;the list of modulo-3 values that define the output of the minimal hash function. */
	final protected LongBigList data;
	/** Optionally, a {@link #rank} structure built on this bit array is used to mark positions containing non-zero value; indexing in {@link #data} is
	 * made by ranking if this field is non-<code>null</code>. */
	final protected LongArrayBitVector marker;
	/** The ranking structure on {@link #marker}. */
	final protected Rank16 rank;
	/** The transformation strategy to turn objects of type <code>T</code> into bit vectors. */
	final protected TransformationStrategy<? super T> transform;

	/** Creates a new function for the given elements, assigning to each element its ordinal position.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 */
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform ) {
		this( elements, transform, null, -1 );
	}

	/** Creates a new function for the given elements and values.
	 * 
	 * @param elements the elements in the domain of the function.
	 * @param transform a transformation strategy for the elements.
	 * @param values values to be assigned to each element, in the same order of the iterator returned by <code>elements</code>; if <code>null</code>, the
	 * assigned value will the the ordinal number of each element.
	 * @param width the bit width of the <code>values</code>. 
	 */

	@SuppressWarnings("unused") // TODO: move it to the first for loop when javac has been fixed
	public MWHCFunction( final Iterable<? extends T> elements, final TransformationStrategy<? super T> transform, final LongList values, final int width ) {
		this.transform = transform;

		// First of all we compute the size, either by size(), if possible, or simply by iterating.
		if ( elements instanceof Collection ) n = ((Collection<? extends T>)elements).size();
		else {
			int c = 0;
			// Do not add a suppression annotation--it breaks javac
			for( T o: elements ) c++;
			n = c;
		}
		
		if ( n == 0 ) {
			data = null;
			marker = null;
			rank = null;
			seed = m = this.width = 0;
			return;
		}

		this.width = width == -1 ? Fast.ceilLog2( n ) : width;

		LOGGER.debug( "Generating MWHC function with " + this.width + " output bits..." );
		
		HypergraphSorter<T> sorter = new HypergraphSorter<T>( n );

		m = sorter.numVertices;
		// Candidate data; might be discarded for compaction.
		final LongBigList data = LongArrayBitVector.getInstance().asLongBigList( this.width );
		data.size( m );

		final Random r = new Random();
		long seed;

		do {
			LOGGER.info( "Generating random hypergraph..." );
			seed = r.nextLong();
		} while ( ! sorter.generateAndSort( elements.iterator(), transform, seed ) );
		
		this.seed = seed;
		
		/* We assign values. */
		/** Whether a specific node has already been used as perfect hash value for an item. */
		final BitVector used = LongArrayBitVector.ofLength( m );
		
		final int[] stack = sorter.stack;
		final int[][] edge = sorter.edge;
		int top = n, k, v = 0;
		long s;
		while( top > 0 ) {
			k = stack[ --top ];

			s = 0;

			for ( int j = 0; j < 3; j++ ) {
				if ( ! used.getBoolean( edge[ j ][ k ] ) ) v = j;
				else s ^= data.getLong( edge[ j ][ k ] );
				used.set( edge[ j ][ k ] );
			}
			
			data.set( edge[ v ][ k ], ( values == null ? k : values.getLong( k ) ) ^ s );
			if ( ASSERTS ) assert ( values == null ? k : values.getLong( k ) ) == ( data.getLong( edge[ 0 ][ k ] ) ^ data.getLong( edge[ 1 ][ k ] ) ^ data.getLong( edge[ 2 ][ k ] ) );
		}

		// Check for compaction
		long c = 0;
		for( int i = 0; i < data.length(); i++ ) if ( data.getLong( i ) != 0 ) c++;
		
		// We estimate size using Rank16
		if (  c * this.width + m * 1.126 < (long)m * this.width ) {
			LOGGER.info( "Compacting..." );
			marker = LongArrayBitVector.ofLength( m );
			final LongBigList newData = LongArrayBitVector.getInstance().asLongBigList( this.width ).length( c );
			c = 0;
			for( int i = 0; i < data.size(); i++ ) {
				final long value = data.getLong( i ); 
				if ( value != 0 ) {
					marker.set( i );
					newData.set( c++, value );
				}
			}
			
			rank = new Rank16( marker );

			if ( ASSERTS ) {
				for( int i = 0; i < data.size(); i++ ) {
					final long value = data.getLong( i ); 
					assert ( value != 0 ) == marker.getBoolean( i );
					if ( value != 0 ) assert value == newData.getLong( rank.rank( i ) ) : value + " != " + newData.getLong( rank.rank( i ) );
				}
			}
			this.data = newData;
		}
		else {
			this.data = data;
			marker = null;
			rank = null;
		}
		
		LOGGER.info( "Completed." );
		LOGGER.debug( "Forecast bit cost per element: " + ( marker == null ?
				HypergraphSorter.GAMMA * this.width :
				HypergraphSorter.GAMMA + this.width + 0.126 ) );
		LOGGER.debug( "Actual bit cost per element: " + (double)numBits() / n );
	}


	@SuppressWarnings("unchecked")
	public long getLong( final Object o ) {
		if ( n == 0 ) return -1;
		final int[] e = new int[ 3 ];
		HypergraphSorter.bitVectorToEdge( transform.toBitVector( (T)o ), seed, m, e );
		return rank == null ?
				data.getLong( e[ 0 ] ) ^ data.getLong( e[ 1 ] ) ^ data.getLong( e[ 2 ] ) :
				( marker.getBoolean( e[ 0 ] ) ? data.getLong( rank.rank( e[ 0 ] ) ) : 0 ) ^
				( marker.getBoolean( e[ 1 ] ) ? data.getLong( rank.rank( e[ 1 ] ) ) : 0 ) ^
				( marker.getBoolean( e[ 2 ] ) ? data.getLong( rank.rank( e[ 2 ] ) ) : 0 );
	}
	
	
	/** Returns the number of elements in the function domain.
	 *
	 * @return the number of the elements in the function domain.
	 */
	public int size() {
		return n;
	}

	/** Returns the number of bits used by this structure.
	 * 
	 * @return the number of bits used by this structure.
	 */
	public long numBits() {
		if ( n == 0 ) return 0;
		return ( marker != null ? rank.numBits() + marker.length() : 0 ) + ( data != null ? data.length() : 0 ) * width;
	}

	/** Creates a new function by copying a given one; non-transient fields are (shallow) copied.
	 * 
	 * @param function the function to be copied.
	 */
	protected MWHCFunction( final MWHCFunction<T> function ) {
		this.n = function.n;
		this.m = function.m;
		this.width = function.width;
		this.seed = function.seed;
		this.data = function.data;
		this.rank = function.rank;
		this.marker = function.marker;
		this.transform = function.transform.copy();
	}
	
	public boolean containsKey( final Object o ) {
		return true;
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( MWHCFunction.class.getName(), "Builds an MWHC function mapping a newline-separated list of strings to their ordinal position.",
				new Parameter[] {
			new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The string file encoding." ),
			new Switch( "iso", 'i', "iso", "Use ISO-8859-1 coding (i.e., just use the lower eight bits of each character)." ),
			new Switch( "zipped", 'z', "zipped", "The string list is compressed in gzip format." ),
			new UnflaggedOption( "function", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised MWHC function." ),
			new UnflaggedOption( "stringFile", JSAP.STRING_PARSER, "-", JSAP.NOT_REQUIRED, JSAP.NOT_GREEDY, "The name of a file containing a newline-separated list of strings, or - for standard input; in the first case, strings will not be loaded into core memory." ),
		});

		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;

		final String functionName = jsapResult.getString( "function" );
		final String stringFile = jsapResult.getString( "stringFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean iso = jsapResult.getBoolean( "iso" );

		final Collection<MutableString> collection;
		if ( "-".equals( stringFile ) ) {
			final ProgressLogger pl = new ProgressLogger( LOGGER );
			pl.start( "Loading strings..." );
			collection = new LineIterator( new FastBufferedReader( new InputStreamReader( zipped ? new GZIPInputStream( System.in ) : System.in, encoding ) ), pl ).allLines();
			pl.done();
		}
		else collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
		final TransformationStrategy<CharSequence> transformationStrategy = iso
				? TransformationStrategies.iso() 
				: TransformationStrategies.utf16();

		BinIO.storeObject( new MWHCFunction<CharSequence>( collection, transformationStrategy ), functionName );
		LOGGER.info( "Completed." );
	}
}
