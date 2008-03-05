package it.unimi.dsi.sux4j.scratch;

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.io.FastByteArrayOutputStream;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongFunction;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.InputBitStream;
import it.unimi.dsi.io.OutputBitStream;
import it.unimi.dsi.lang.MutableString;
import it.unimi.dsi.logging.ProgressLogger;
import it.unimi.dsi.sux4j.mph.HollowTrie;
import it.unimi.dsi.util.ImmutableBinaryTrie;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.ListIterator;

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

public class BitStreamImmutableBinaryTrie<T> extends AbstractObject2LongFunction<T> {
	private final static Logger LOGGER = Util.getLogger( BitStreamImmutableBinaryTrie.class );
	private static final long serialVersionUID = 1L;
	private static final boolean DEBUG = false;
	private byte[] trie;
	private int size;
	private final TransformationStrategy<T> transformationStrategy;
	
	private final static class BitstreamTrie<T> {
		private final static boolean ASSERTS = false;
		
		/** A node in the trie. */
		protected static class Node implements Serializable {
			private static final long serialVersionUID = 1L;
			public Node left, right;
			/** An array containing the path compacted in this node (<code>null</code> if there is no compaction at this node). */
			final public long[] path;
			/** The length of the path compacted in this node (0 if there is no compaction at this node). */
			final public int pathLength;
			
			final public int skip;
			
			/** Creates a node representing a word. 
			 * 
			 * <p>Note that the long array contained in <code>path</code> will be stored inside the node.
			 * 
			 * @param path the path compacted in this node, or <code>null</code> for the empty path.
			 */
			
			public Node( final BitVector path, final int skip ) {
				if ( path == null ) {
					this.path = null;
					this.pathLength = 0;
				}
				else {
					this.path = path.bits();
					this.pathLength = path.size();
				}
				this.skip = skip;
			}

			/** Returns true if this node is a leaf.
			 * 
			 * @return  true if this node is a leaf.
			 */
			public boolean isLeaf() {
				return right == null && left == null;
			}
			
			public String toString() {
				return "[" + path + ", " + skip + "]";
			}
			
		}
			
		/** The root of the trie. */
		protected final Node root;
		/** The number of words in this trie. */
		private final TransformationStrategy<T> transformationStrategy;
		
		/** Creates a trie from a set of elements.
		 * 
		 * @param elements a set of elements
		 * @param transformationStrategy a transformation strategy that must turn <code>elements</code> into a list of
		 * distinct, lexicographically increasing (in iteration order) binary words.
		 */
		
		public BitstreamTrie( final Iterable<? extends T> elements, final Iterable<? extends T> ends, final TransformationStrategy<T> transformationStrategy ) {
			this.transformationStrategy = transformationStrategy;
			// Check order
			final Iterator<? extends T> iterator = elements.iterator();
			final Iterator<? extends T> endsIterator = ends.iterator();
			final ObjectList<LongArrayBitVector> words = new ObjectArrayList<LongArrayBitVector>();
			final ObjectList<LongArrayBitVector> endsList = new ObjectArrayList<LongArrayBitVector>();
			int cmp;
			if ( iterator.hasNext() ) {
				final LongArrayBitVector prev = LongArrayBitVector.copy( transformationStrategy.toBitVector( iterator.next() ) );
				words.add( prev.copy() );
				endsList.add( LongArrayBitVector.copy( transformationStrategy.toBitVector( endsIterator.next() ) ) );
				BitVector curr;

				while( iterator.hasNext() ) {
					curr = transformationStrategy.toBitVector( iterator.next() );
					cmp = prev.compareTo( curr );
					if ( cmp == 0 ) throw new IllegalArgumentException( "The trie elements are not unique" );
					if ( cmp > 0 ) throw new IllegalArgumentException( "The trie elements are not sorted" );
					prev.replace( curr );
					words.add( prev.copy() );
					endsList.add( LongArrayBitVector.copy( transformationStrategy.toBitVector( endsIterator.next() ) ) );
				}
			}
			root = buildTrie( words, endsList, 0 );
			LOGGER.info( "Gain: " + gain );
		}

		/** Builds a trie recursively. 
		 * 
		 * <p>The trie will contain the suffixes of words in <code>words</code> starting at <code>pos</code>.
		 * 
		 * @param elements a list of elements.
		 * @param pos a starting position.
		 * @return a trie containing the suffixes of words in <code>words</code> starting at <code>pos</code>.
		 */
		
		protected int gain;
			
		protected Node buildTrie( final ObjectList<LongArrayBitVector> elements, final ObjectList<LongArrayBitVector> ends, final int pos ) {
			// TODO: on-the-fly check for lexicographical order
			
			if ( elements.size() == 0 ) return null;

			BitVector first = elements.get( 0 ), curr;
			int prefix = first.size(), change = -1, j;

			// We rule out the case of a single word (it would work anyway, but it's faster)
			if ( elements.size() == 1 ) {
				long lcp = elements.get( 0 ).longestCommonPrefixLength( ends.get( 0 ) );
				if ( lcp + 1 < prefix ) gain += prefix - Math.max( pos, lcp + 1 );
				
				return new Node( pos < Math.min( prefix, lcp + 1 ) ? LongArrayBitVector.copy( first.subVector( pos, Math.min( prefix, lcp + 1 ) ) ) : null, prefix - pos );
			}
			
			// 	Find maximum common prefix. change records the point of change (for splitting the word set).
			for( ListIterator<LongArrayBitVector> i = elements.listIterator( 1 ); i.hasNext(); ) {
				curr = i.next();
				
				if ( curr.size() < prefix ) prefix = curr.size(); 
				for( j = pos; j < prefix; j++ ) if ( first.get( j ) != curr.get( j ) ) break;
				if ( j < prefix ) {
					change = i.previousIndex();
					prefix = j;
				}
			}
			
			long lcp = elements.get( change ).longestCommonPrefixLength( ends.get( change ) );
			assert lcp < elements.get( change ).length();
			assert lcp < ends.get( change ).length();
			if ( lcp + 1 < prefix ) gain += prefix - Math.max( pos, lcp + 1 );
			
			final Node n;
			n = new Node( pos < Math.min( prefix, lcp + 1 ) ? LongArrayBitVector.copy( first.subVector( pos, Math.min( prefix, lcp + 1 ) ) ) : null, prefix - pos ); // There's some common prefix
			n.left = buildTrie( elements.subList( 0, change ), ends.subList( 0, change ), prefix + 1 );
			n.right = buildTrie( elements.subList( change, elements.size() ), ends.subList( change, elements.size() ), prefix + 1 );
			return n;
		}

		public int toStream( OutputBitStream trie ) throws IOException {
			return toStream( root, trie );
		}
		
		private static int toStream( Node n, OutputBitStream trie ) throws IOException {
			if ( n == null ) return 0;
			
			if ( ( n.left != null ) != ( n.right != null ) ) throw new IllegalStateException();
			
			// We recursively create the stream of the left and right trees
			final FastByteArrayOutputStream leftStream = new FastByteArrayOutputStream();
			final OutputBitStream left = new OutputBitStream( leftStream, 0 );
			int leavesLeft = toStream( n.left, left );
			long leftBits = left.writtenBits();
			left.flush();
			
			final FastByteArrayOutputStream rightStream = new FastByteArrayOutputStream();
			final OutputBitStream right = new OutputBitStream( rightStream, 0 );
			int leavesRight = toStream( n.right, right );
			long rightBits = right.writtenBits();
			right.flush();
			
			trie.writeLongDelta( n.left == null ? 0 : leftBits ); // Skip pointer (nonzero if non leaf)
			
			trie.writeDelta( n.pathLength );
			final long wb = trie.writtenBits();
			if ( n.pathLength > 0 ) for( int i = 0; i < n.path.length; i++ ) trie.writeLong( n.path[ i ], Math.min( Long.SIZE, n.pathLength - i * Long.SIZE ) );
			assert trie.writtenBits() == wb + n.pathLength;

			trie.writeDelta( n.skip - n.pathLength );
			
			if ( n.left != null ) {
				trie.writeLongDelta( leavesLeft ); // The number of leaves in the left subtree
				trie.writeLongDelta( leavesRight ); // The number of leaves in the right subtree


				// Write left and right tree
				trie.write( leftStream.array, leftBits );
				trie.write( rightStream.array, rightBits );
				// Concatenate right tree
				return leavesLeft + leavesRight;
			}

			return 1;
		}
	}
	
	public BitStreamImmutableBinaryTrie( Iterable<? extends T> elements, Iterable<? extends T> ends, TransformationStrategy<T> transformationStrategy ) throws IOException {

		this.transformationStrategy = transformationStrategy;
		BitstreamTrie<T> immutableBinaryTrie = new BitstreamTrie<T>( elements, ends, transformationStrategy );
		FastByteArrayOutputStream fbStream = new FastByteArrayOutputStream();
		OutputBitStream trie = new OutputBitStream( fbStream, 0 );
		int numLeaves = immutableBinaryTrie.toStream( trie );
		size = numLeaves;
		
		LOGGER.info(  "trie bit size:" + trie.writtenBits() );
		
		if ( DEBUG ) System.err.println( new ImmutableBinaryTrie<T>( elements, transformationStrategy ) );
		trie.flush();
		fbStream.trim();
		this.trie = fbStream.array;

		MutableString s = new MutableString();
		if ( DEBUG ) recToString( new InputBitStream( this.trie ), new MutableString(), s, new MutableString(), 0 );
		if ( DEBUG ) System.err.println( s );

	}
	
	
	@SuppressWarnings("unchecked")
	public long getLong( Object o ) {
		try {
			if ( DEBUG ) System.err.println( "Getting " + o + "...");
			BitVector v = transformationStrategy.toBitVector( (T)o );
			final InputBitStream trie = new InputBitStream( this.trie );
			trie.position( 0 );

			int pos = 0;
			int leaf = 0;
			for( ;; ) {
				long skip = trie.readLongDelta();

				int pathLength = trie.readDelta();
				if ( DEBUG ) System.err.println( "Path length: " + pathLength );
				int lcp = 0;

				long xor = 0, t = 0;
				int i;
				
				long readBits = trie.readBits();
				
				for( i = 0; i < ( pathLength + Long.SIZE - 1 ) / Long.SIZE; i++ ) {
					int size = Math.min( Long.SIZE, pathLength - i * Long.SIZE );
					xor = v.getLong( pos, Math.min( v.length(), pos += size ) ) ^ ( t = trie.readLong( size ) );
					if ( xor != 0 ) {
						lcp += Fast.leastSignificantBit( xor );
						break;
					}
					else lcp += size;
				}

				if ( xor != 0 ) {
					if ( DEBUG ) System.err.println( "Path mismatch: " +  ( ( ( ( xor & -xor ) & t ) != 0 ) ? "smaller" : "greater" ) + " than trie path" );
					if ( ( ( xor & -xor ) & t ) != 0 ) return leaf - 1;
					else {
						if ( skip == 0 ) return leaf;
						// Skip remaining path, if any and missing bits count
						trie.skip( pathLength - ( trie.readBits() - readBits ) );
						trie.readDelta();
						return leaf + trie.readDelta() + trie.readDelta() - 1;
					}
				}

				int missing = trie.readDelta();
				if ( DEBUG ) System.err.println( "Missing bits: " + missing );
				
				if ( skip == 0 ) {
					if ( DEBUG ) System.err.println( "Exact match" );
					return leaf;
				}
				
				// Increment pos by missing bits
				pos += missing;
				
				int leavesLeft = trie.readDelta();
				trie.readDelta(); // Skip number of right leaves
				
				if ( DEBUG ) System.err.println( "Path coincides for " + lcp + " bits " );
				if ( lcp != pathLength ) throw new AssertionError();

				if ( pos >= v.length() ) return leaf;
				
				if ( v.getBoolean( pos++ ) ) {
					// Right
					trie.skip( skip );
					leaf += leavesLeft;
					if ( DEBUG ) System.err.println( "Turining right (" + leaf + " leaves on the left)..." );
				}
				else {
					if ( DEBUG ) System.err.println( "Turining left (" + leaf + " leaves on the left)..." );
					// Left
				}

			} 
		}catch( IOException e ) {
			throw new RuntimeException( e );
		}
	}
	

	private void recToString( final InputBitStream trie, final MutableString printPrefix, final MutableString result, final MutableString path, final int level ) throws IOException {
		int skip = trie.readDelta();
		
		//System.err.println( "Called with prefix " + printPrefix );
		
		result.append( printPrefix ).append( '(' ).append( level ).append( ')' );
		
		int pathLength = trie.readDelta();
		LongArrayBitVector p = LongArrayBitVector.getInstance( pathLength );
		
		for( int i = 0; i < ( pathLength + Long.SIZE - 1 ) / Long.SIZE; i++ ) {
			int size = Math.min( Long.SIZE, pathLength - i * Long.SIZE );
			p.append( trie.readLong( size ), size );
		}

		int missing = trie.readDelta();

		path.append( p );
		result.append( " path:" ).append( p );
		while( missing-- != 0 ) result.append( '*' );
		
		result.append( '\n' );

		if ( skip == 0 ) return; // Leaf

		trie.readDelta(); // Skip number of leaves in the left subtree
		trie.readDelta(); // Skip number of leaves in the right subtree

		
		path.append( '0' );
		recToString( trie, printPrefix.append( '\t' ).append( "0 => " ), result, path, level + 1 );
		path.charAt( path.length() - 1, '1' ); 
		recToString( trie, printPrefix.replace( printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1 );
		path.delete( path.length() - 1, path.length() ); 
		printPrefix.delete( printPrefix.length() - 6, printPrefix.length() );
		
		//System.err.println( "Path now: " + path + " Going to delete from " + ( path.length() - n.pathLength));
		
		path.delete( path.length() - pathLength, path.length() );
	}

	public static void main( final String[] arg ) throws NoSuchMethodException, IOException, JSAPException {

		final SimpleJSAP jsap = new SimpleJSAP( HollowTrie.class.getName(), "Builds a hollow trie reading a newline-separated list of terms.",
				new Parameter[] {
					new FlaggedOption( "bufferSize", JSAP.INTSIZE_PARSER, "64Ki", JSAP.NOT_REQUIRED, 'b',  "buffer-size", "The size of the I/O buffer used to read terms." ),
					new FlaggedOption( "encoding", ForNameStringParser.getParser( Charset.class ), "UTF-8", JSAP.NOT_REQUIRED, 'e', "encoding", "The term file encoding." ),
					new Switch( "zipped", 'z', "zipped", "The term list is compressed in gzip format." ),
					new Switch( "huTucker", 'h', "hu-tucker", "Use Hu-Tucker coding to increase entropy (only available for offline construction)." ),
					new FlaggedOption( "termFile", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.NOT_REQUIRED, 'o', "offline", "Read terms from this file (without loading them into core memory) instead of standard input." ),
					new UnflaggedOption( "trie", JSAP.STRING_PARSER, JSAP.NO_DEFAULT, JSAP.REQUIRED, JSAP.NOT_GREEDY, "The filename for the serialised hollow trie." )
		});
		
		JSAPResult jsapResult = jsap.parse( arg );
		if ( jsap.messagePrinted() ) return;
		
		final int bufferSize = jsapResult.getInt( "bufferSize" );
		final String trieName = jsapResult.getString( "trie" );
		final String stringFile = jsapResult.getString( "termFile" );
		final Charset encoding = (Charset)jsapResult.getObject( "encoding" );
		final boolean zipped = jsapResult.getBoolean( "zipped" );
		final boolean huTucker = jsapResult.getBoolean( "huTucker" );
		
		if ( huTucker && stringFile == null ) throw new IllegalArgumentException( "Hu-Tucker coding requires offline construction" );
		
		final BitStreamImmutableBinaryTrie<CharSequence> hollowTrie;
		
		LOGGER.info( "Building trie..." );

		/*if ( stringFile == null ) {
			hollowTrie = new BitStreamImmutableBinaryTrie<CharSequence>( new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ) ), TransformationStrategies.prefixFreeUtf16() );
		} 
		else {*/
			FileLinesCollection collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
			hollowTrie = new BitStreamImmutableBinaryTrie<CharSequence>( collection, collection, huTucker ? new HuTuckerTransformationStrategy( collection, true ) : TransformationStrategies.prefixFreeUtf16() );
		//}
		
		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( hollowTrie, trieName );
		LOGGER.info( "Completed." );
	}

	public boolean containsKey( Object arg0 ) {
		return true;
	}

	public long numBits() {
		return trie.length * Byte.SIZE;
	}

	public int size() {
		return size;
	}

}
