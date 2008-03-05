package it.unimi.dsi.sux4j.mph;

import static it.unimi.dsi.bits.Fast.length;
import it.unimi.dsi.Util;
import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.BitVectors;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.bits.TransformationStrategy;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.Reference2LongMap;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import it.unimi.dsi.io.FastBufferedReader;
import it.unimi.dsi.io.FileLinesCollection;
import it.unimi.dsi.io.LineIterator;
import it.unimi.dsi.sux4j.bits.Rank9;
import it.unimi.dsi.sux4j.bits.SimpleSelect;
import it.unimi.dsi.sux4j.util.TwoSizesLongBigList;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
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

public class HollowTrie<T> extends AbstractHash<T> implements Serializable {
	private static final Logger LOGGER = Util.getLogger( HollowTrie.class );
	private static final long serialVersionUID = 0L;

	private static final boolean ASSERTS = false;
	private static final boolean DEBUG = false;
	
	protected TwoSizesLongBigList skips;
	protected transient BitVector trie;
	public final Rank9 rank9;
	public final SimpleSelect select;
	private final TransformationStrategy<? super T> transform;
	private int size;
	
	private final static class Node {
		Node left, right;
		int skip;
		
		public Node( final Node left, final Node right, final int skip ) {
			this.left = left;
			this.right = right;
			this.skip = skip;
			if ( ASSERTS ) assert skip >= 0;
		}
	}
	
	
	@SuppressWarnings("unchecked")
	public long getLong( final Object object ) {
		if ( size <= 1 ) return size - 1;
		final BitVector bitVector = transform.toBitVector( (T)object );
		long p = 0, r = 0, length = bitVector.length(), index = 0, a = 0, b = 0, t;
		int s = 0;
		
		for(;;) {
			if ( ( s += (int)skips.getLong( r ) ) >= length ) return -1;

			//System.out.print( "Turning " + ( bitVector.getBoolean( s ) ? "right" : "left" ) + " at bit " + s + "... " );
			if ( bitVector.getBoolean( s ) ) p = 2 * r + 2;
			else p = 2 * r + 1;

			t = 2 * rank9.rank( a, b + 1 );
			a = b + 1;
			b += t;
			
			index += p - a - rank9.rank( a, p );

			//System.err.println( a + " " + b + " " + p + " " + index );
			
			if ( ASSERTS ) assert p < trie.length();
			if ( ! trie.getBoolean( p ) ) break;

			r = rank9.rank( p + 1 ) - 1;
			
			s++;
		}
		
		// System.out.println();
		// Complete computation of leaf index
		
		for(;;) {
			p = select.select( ( r = rank9.rank( p + 1 ) ) - 1 );
			if ( p < a ) break;
			p = r * 2;
			
			t = 2 * rank9.rank( a, b + 1 );
			a = b + 1;
			b += t;
			
			index += p - a + 1 - rank9.rank( a, p + 1 );
			
			//System.err.println( "Scanning; " + a + " " + b + " " + p + " " + index );
		}

		//System.err.println( "Returning " + index );

		return index;
	}
	
	public HollowTrie( final Iterable<? extends T> iterable, final TransformationStrategy<? super T> transform ) {
		this( iterable.iterator(), transform );
	}
		
	public HollowTrie( final Iterator<? extends T> iterator, final TransformationStrategy<? super T> transform ) {

		this.transform = transform;

		int size = 0;
		
		Node root = null, node, parent;
		int prefix, numNodes = 0, cmp;

		if ( iterator.hasNext() ) {
			BitVector prev = transform.toBitVector( iterator.next() ).copy(), curr;
			size++;

			while( iterator.hasNext() ) {
				size++;
				curr = transform.toBitVector( iterator.next() );
				cmp = prev.compareTo( curr );
				if ( cmp == 0 ) throw new IllegalArgumentException( "The input bit vectors are not distinct" );
				if ( cmp > 0 ) throw new IllegalArgumentException( "The input bit vectors are not lexicographically sorted" );
				prefix = (int)curr.longestCommonPrefixLength( prev );
				if ( prefix == prev.length() ) throw new IllegalArgumentException( "The input bit vectors are not prefix-free" );

				node = root;
				parent = null;
				Node n = null;
				while( node != null ) {
					if ( prefix < node.skip ) {
						n = new Node( node, null, prefix );
						numNodes++;
						if ( parent == null ) {
							root.skip -= prefix + 1;
							if ( ASSERTS ) assert root.skip >= 0;
							root = n;
						}
						else {
							parent.right = n;
							node.skip -= prefix + 1;
							if ( ASSERTS ) assert node.skip >= 0;
						}
						break;
					}

					prefix -= node.skip + 1;
					parent = node;
					node = node.right;
				}

				if ( node == null ) {
					if ( parent == null ) root = new Node( null, null, prefix );
					else parent.right = new Node( null, null, prefix );
					numNodes++;
				}

				if ( ASSERTS ) {
					long s = 0;
					Node m = root;
					while( m != null ) {
						s += m.skip;
						if ( curr.getBoolean( s ) ) {
							if ( m.right == null ) break;
						}
						else if ( m.left == null ) break;
						m = curr.getBoolean( s ) ? m.right : m.left;
						s++;
					}
					assert parent == null || ( node == null ? m == parent.right : m == n );
				}

				prev = curr.copy();
			}
		}
		
		this.size = size;

		if ( size <= 1 ) {
			rank9 = new Rank9( LongArrays.EMPTY_ARRAY, 0 );
			select = new SimpleSelect( LongArrays.EMPTY_ARRAY, 0 );
			trie = BitVectors.EMPTY_VECTOR;
			return;
		}

		final BitVector bitVector = LongArrayBitVector.getInstance( 2 * numNodes + 1 );
		final ObjectArrayList<Node> queue = new ObjectArrayList<Node>();
		final IntArrayList skips = new IntArrayList();
		int p = 0, maxSkip = Integer.MIN_VALUE;
		long skipsLength = 0;
		bitVector.add( 1 );
		queue.add( root );

		/*StringBuilder result = new StringBuilder();
		recToString( root, new StringBuilder(), result, new StringBuilder(), 0 );
		System.out.println( result );*/
		
		root = null;
		Node n;
		
		while( p < queue.size() ) {
			n = queue.get( p );
			if ( maxSkip < n.skip ) maxSkip = n.skip;
			skips.add( n.skip );
			skipsLength += length( n.skip );
			bitVector.add( n.left != null );
			bitVector.add( n.right != null );
			if ( n.left != null ) queue.add( n.left );
			if ( n.right != null ) queue.add( n.right );
			p++;
		}
		
		trie = bitVector;
		rank9 = new Rank9( bitVector );
		select = new SimpleSelect( bitVector );
		final int skipWidth = Fast.ceilLog2( maxSkip );

		LOGGER.info( "Max skip: " + maxSkip );
		LOGGER.info( "Max skip width: " + skipWidth );
		LOGGER.info( "Bits per skip: " + ( skipsLength * 2.0 ) / ( numNodes - 1 ) );
		/*this.skips = LongArrayBitVector.getInstance( skipsLength );
		final LongArrayBitVector borders = LongArrayBitVector.getInstance( skipsLength );
		int s = skips.size();
		int x;
		for( IntIterator i = skips.iterator(); s-- != 0; ) {
			x = i.nextInt();
			this.skips.append( x, length( x ) );
			borders.append( 1, length( x ) );
		}
		
		borders.append( 1, 1 ); // Sentinel
		if ( this.skips.trim() ) throw new AssertionError();
		if ( borders.trim() ) throw new AssertionError();*/
		
		this.skips = new TwoSizesLongBigList( skips );
		
		if ( DEBUG ) {
			System.err.println( skips );
			System.err.println( this.skips );
			//System.err.println( borders );
		}
		
		//TODO: try with SDArray
		//skipLocator = new SparseSelect( borders );
		
		//LOGGER.info( "Bits for skips: " +(  this.skips.length() + skipLocator.numBits() ))
		
		final long numBits = rank9.numBits() + select.numBits() + trie.length() + this.skips.numBits() + /*skipLocator.numBits() +*/ transform.numBits();
		LOGGER.info( "Bits: " + numBits + " bits/string: " + (double)numBits / size );
	}
	
	
	
	public HollowTrie( final Iterator<T> iterator, final Iterator<T> endsIterator, TransformationStrategy<T> transformationStrategy ) {
		this.transform = transformationStrategy;
		ObjectArrayList<BitVector> elements = new ObjectArrayList<BitVector>();
		while( iterator.hasNext() ) elements.add( transformationStrategy.toBitVector( iterator.next() ).copy() );
		ObjectArrayList<BitVector> ends = new ObjectArrayList<BitVector>();
		while( endsIterator.hasNext() ) ends.add( transformationStrategy.toBitVector( endsIterator.next() ).copy() );

		Reference2LongOpenHashMap<BitVector> original = new Reference2LongOpenHashMap<BitVector>();
		original.defaultReturnValue( -1 );
		for( int i = 0; i < elements.size() - 1; i++ ) original.put( elements.get( i ), i );

		assert ends.size() == elements.size();
		
		// Move last end to the elements list
		elements.add( ends.remove( ends.size() - 1 ) );
		
		final ObjectArrayList<Node> queue = new ObjectArrayList<Node>();
		Node root = buildTrie( elements, ends, 0, original );
		queue.add( root );
		
		final BitVector bitVector = LongArrayBitVector.getInstance( 4 * elements.size() + 1 );
		final IntArrayList skips = new IntArrayList();
		int p = 0, maxSkip = Integer.MIN_VALUE;
		long skipsLength = 0;
		bitVector.add( 1 );

		Node n;

		for( int i = 0; i < elements.size(); i++ ) getVector( root, elements.get( i ) );
		
		while( p < queue.size() ) {
			n = queue.get( p );
			if ( maxSkip < n.skip ) maxSkip = n.skip;
			skips.add( n.skip );
			skipsLength += length( n.skip );
			bitVector.add( n.left != null );
			bitVector.add( n.right != null );
			if ( n.left != null ) queue.add( n.left );
			else size++;
			if ( n.right != null ) queue.add( n.right );
			else size++;
			p++;
		}
		
		LOGGER.info( "Trie size:" + size );
		
		trie = bitVector;
		rank9 = new Rank9( bitVector );
		select = new SimpleSelect( bitVector );
		final int skipWidth = Fast.ceilLog2( maxSkip );

		LOGGER.info( "Max skip: " + maxSkip );
		LOGGER.info( "Max skip width: " + skipWidth );
		LOGGER.info( "Bits per skip: " + ( skipsLength * 2.0 ) / ( elements.size() - 1 ) );
		
		this.skips = new TwoSizesLongBigList( skips );
				
		final long numBits = rank9.numBits() + select.numBits() + trie.length() + this.skips.numBits() + /*skipLocator.numBits() +*/ transform.numBits();
		LOGGER.info( "Bits: " + numBits + " bits/string: " + (double)numBits / size );
	}
		
	
	protected int getVector( Node n, BitVector bv ) {
		int pos = 0;
		
		for(;;) {
			pos += n.skip;
			
			System.err.print( "Skipping " + n.skip + " bits... " );
			if ( bv.getBoolean( pos++ ) ) {
				System.err.print( "Turning right... " );
				n = n.right;
			}
			else {
				n = n.left;
				System.err.print( "Turning left... " );
			}
			if ( n == null ) {
				System.err.println();
				return 0;
			}
		}
	}
	
	/** Builds a trie recursively. 
	 * 
	 * <p>The trie will contain the suffixes of words in <code>words</code> starting at <code>pos</code>.
	 * 
	 * @param elements a list of elements.
	 * @param pos a starting position.
	 * @return a trie containing the suffixes of words in <code>words</code> starting at <code>pos</code>.
	 */
		
	protected Node buildTrie( final ObjectList<BitVector> elements, final ObjectList<BitVector> ends, final int pos, final Reference2LongMap<BitVector> original ) {
		// TODO: on-the-fly check for lexicographical order
		
		for( int i = 0; i < elements.size() - 1; i++ ) assert elements.get( i ).compareTo( elements.get( i + 1 ) ) < 0;
		
		if ( elements.size() == 1 ) return null;

		//System.err.println( elements );
		
		BitVector first = elements.get( 0 ), curr;
		int prefix = first.size(), change = -1, j;

		// 	Find maximum common prefix. change records the point of change (for splitting the word set).
		for( ListIterator<BitVector> i = elements.listIterator( 1 ); i.hasNext(); ) {
			curr = i.next();
			assert ! curr.equals( first );
			assert curr.longestCommonPrefixLength( first ) != curr.length();
			assert curr.longestCommonPrefixLength( first ) != first.length();
			
			if ( curr.size() < prefix ) prefix = curr.size(); 
			for( j = pos; j < prefix; j++ ) if ( first.get( j ) != curr.get( j ) ) break;
			if ( j < prefix ) {
				change = i.previousIndex();
				prefix = j;
			}
		}
		
		assert change >= 0;

		ObjectArrayList<BitVector> leftList = new ObjectArrayList<BitVector>( elements.subList( 0, change ) );
		assert ! ends.isEmpty();
		assert ! leftList.isEmpty();
		
		//assert elements.get( change - 1 ).subVector( 0, prefix ).equals( elements.get( change ).subVector( 0, prefix ) );
		assert ! elements.get( change - 1 ).subVector( 0, prefix + 1 ).equals( elements.get( change ).subVector( 0, prefix + 1 ) );
		
		System.err.println( "prefix: " + prefix );
		
		int index = (int)original.removeLong( elements.get( change - 1 ) ); 
		if ( index >= 0 ) leftList.add( ends.get( index ) );
		
		if ( index >= 0 ) System.err.println( "Adding end of index " + index );
		
		assert index == -1 || ends.get( index ).longestCommonPrefixLength( leftList.get( 0 ) ) >= prefix;
		
		return new Node( buildTrie( leftList, ends, prefix + 1, original ), 
				buildTrie( elements.subList( change, elements.size() ), ends, prefix + 1, original ), prefix - pos );
	}

	public int size() {
		return size;
	}

	public long numBits() {
		return rank9.numBits() + select.numBits() + trie.length() + this.skips.numBits() + /*skipLocator.numBits() +*/ transform.numBits();
	}
	
	private void recToString( final Node n, final StringBuilder printPrefix, final StringBuilder result, final StringBuilder path, final int level ) {
		if ( n == null ) return;
		
		result.append( printPrefix ).append( '(' ).append( level ).append( ')' );
		
		if ( n.skip >= 0 ) result.append( " skip: " ).append( n.skip );

		result.append( '\n' );
		
		path.append( '0' );
		recToString( n.left, printPrefix.append( '\t' ).append( "0 => " ), result, path, level + 1 );
		path.setCharAt( path.length() - 1, '1' ); 
		recToString( n.right, printPrefix.replace( printPrefix.length() - 5, printPrefix.length(), "1 => "), result, path, level + 1 );
		path.delete( path.length() - 1, path.length() ); 
		printPrefix.delete( printPrefix.length() - 6, printPrefix.length() );
	}
	
	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		trie = rank9.bitVector();
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
		
		final HollowTrie<CharSequence> hollowTrie;
		
		LOGGER.info( "Building trie..." );

		if ( stringFile == null ) {
			hollowTrie = new HollowTrie<CharSequence>( new LineIterator( new FastBufferedReader( new InputStreamReader( System.in, encoding ), bufferSize ) ), TransformationStrategies.prefixFreeUtf16() );
		}
		else {
			FileLinesCollection collection = new FileLinesCollection( stringFile, encoding.toString(), zipped );
			hollowTrie = new HollowTrie<CharSequence>( collection, huTucker ? new HuTuckerTransformationStrategy( collection, true ) : TransformationStrategies.prefixFreeUtf16() );
		}
		
		LOGGER.info( "Writing to file..." );		
		BinIO.storeObject( hollowTrie, trieName );
		LOGGER.info( "Completed." );
	}
}
