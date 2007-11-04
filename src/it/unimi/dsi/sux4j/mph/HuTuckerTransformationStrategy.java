package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.fastutil.chars.Char2IntMap;
import it.unimi.dsi.fastutil.chars.Char2IntOpenHashMap;
import it.unimi.dsi.mg4j.compression.HuTuckerCodec;
import it.unimi.dsi.mg4j.compression.PrefixCoder;
import it.unimi.dsi.sux4j.bits.AbstractBitVector;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.BitVector.TransformationStrategy;

import java.util.Iterator;

/** A transformation strategy mapping strings to their {@linkplain HuTuckerCodec Hu-Tucker encoding}. The
 * encoding is guaranteed to preserve lexicographical ordering. */

public class HuTuckerTransformationStrategy implements TransformationStrategy<CharSequence> {
	private static final long serialVersionUID = 1;
	
	private cern.colt.bitvector.BitVector[] codeWord;
	private Char2IntOpenHashMap char2symbol;

	/** Creates a Hu-Tucker transformation strategy for the character sequences returned by the given iterable. The
	 * strategy will map a string to its Hu-Tucker encoding.
	 * 
	 * @param iterable an iterable object returning character sequences.
	 */
	public HuTuckerTransformationStrategy( final Iterable<? extends CharSequence> iterable ) {
		// First of all, we gather frequencies for all Unicode characters
		int[] frequency = new int[ Character.MAX_VALUE + 1 ]; 
		int maxWordLength = 0;
		CharSequence s;
		int count = 0;

		for( Iterator<? extends CharSequence> i = iterable.iterator(); i.hasNext(); ) {
			s = i.next();
			maxWordLength = Math.max( s.length(), maxWordLength );
			for( int j = s.length(); j-- != 0; ) frequency[ s.charAt( j ) ]++;
			count++;
		}
		
		// Then, we compute the number of actually used characters. We count from the start the stop character.
		count = 1;
		for( int i = frequency.length; i-- != 0; ) if ( frequency[ i ] != 0 ) count++;

		/* Now we remap used characters in f, building at the same time the map from characters to symbols (except for the stop character). */
		int[] packedFrequency = new int[ count ];
		char2symbol = new Char2IntOpenHashMap( count );
		
		for( int i = frequency.length, k = count; i-- != 0; ) {
			if ( frequency[ i ] != 0 ) {
				packedFrequency[ --k ] = frequency[ i ];
				char2symbol.put( (char)i, k );
			}
		}
		
		packedFrequency[ 0 ] = count - 1; // The stop character appears once in each string.
		
		// We now build the coder used to code the strings
		codeWord = ((PrefixCoder)new HuTuckerCodec( packedFrequency ).getCoder()).codeWords();
	}
	

	private final static class CodedCharSequenceBitVector extends AbstractBitVector {

		private final HuTuckerTransformationStrategy strategy;
		private final CharSequence s;
		private long length = -1;
		
		public CodedCharSequenceBitVector( final CharSequence s, HuTuckerTransformationStrategy strategy ) {
			this.s = s;
			this.strategy = strategy;
			
		}
		public boolean getBoolean( long index ) {
			final cern.colt.bitvector.BitVector[] codeWord = strategy.codeWord;
			final Char2IntMap char2symbol = strategy.char2symbol;
			// Optimise this for linear scans
			int pos = 0, bits = 0, incr = 0;
			final int length = s.length();
			// TODO: check for characters not mapped
			while( pos < length && bits + ( incr = codeWord[ char2symbol.get( s.charAt( pos ) ) ].size() ) <= index ) {
				pos++; 
				bits += incr;
			}
			if ( pos == length ) {
				if ( index - bits < codeWord[ 0 ].size() ) return codeWord[ 0 ].get( (int)( index - bits ) );
				else throw new IllegalArgumentException();
			}
			//System.err.println( s + ": index: " + index + " bits:  " + bits + "pos : " + pos + " " + s.charAt( pos ) + " " + char2symbol.get( s.charAt(pos))+ " " + codeWord[ char2symbol.get( s.charAt( pos ) ) ] + " size:" + codeWord[ char2symbol.get( s.charAt( pos ) ) ].size());
			
			//System.err.println( s + ": Returning " + codeWord[ char2symbol.get( s.charAt( pos ) ) ].get( (int)( index - bits ) ) );
			return codeWord[ char2symbol.get( s.charAt( pos ) ) ].get( (int)( index - bits ) );
		}

		public long length() {
			if ( length != -1 ) return length;
			final cern.colt.bitvector.BitVector[] codeWord = strategy.codeWord;
			final Char2IntMap char2symbol = strategy.char2symbol;
			length = codeWord[ 0 ].size();
			int symbol;
			for( int i = s.length(); i-- != 0; ) {
				if ( ( symbol = char2symbol.get( s.charAt( i ) ) ) == -1 ) return -1;
				length += codeWord[ symbol ].size();
			}
			
			return length;
		}
		
	}
	
	public BitVector toBitVector( CharSequence s ) {
		return new CodedCharSequenceBitVector( s, this );
	}
	
	public long numBits() {
		long numBits = 0;
		for( int i = codeWord.length; i-- != 0; ) numBits += codeWord[ i ].size();
		return numBits;
	}
}
