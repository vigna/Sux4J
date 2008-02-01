package it.unimi.dsi.sux4j.mph;

import it.unimi.dsi.sux4j.bits.AbstractBitVector;
import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.BitVector.TransformationStrategy;

import java.io.Serializable;



/** A trivial transformation from strings to bit vectors. It concatenates the bits of the UTF-16 representation and completes
 * the representation with 16 zeroes to guarantee lexicographical ordering and prefix-freeness. As a result, an
 * {@link IllegalArgumentException} will be thrown if any string contains an ASCII NUL. */

public class Utf16TransformationStrategy implements BitVector.TransformationStrategy<CharSequence>, Serializable {
	private static final long serialVersionUID = 1L;
	Utf16CharSequenceBitVector utf16CharSequenceBitVector = new Utf16CharSequenceBitVector();
	
	private static class Utf16CharSequenceBitVector extends AbstractBitVector implements Serializable {
		private static final long serialVersionUID = 1L;
		private transient CharSequence s;
		private transient long length;
		private transient long actualEnd;

		public Utf16CharSequenceBitVector wrap( final CharSequence s ) {
			this.s = s;
			actualEnd = s.length() * Character.SIZE;
			length = actualEnd + Character.SIZE;
			return this;
		}
		
		public boolean getBoolean( long index ) {
			if ( index > length ) throw new IndexOutOfBoundsException();
			if ( index >= actualEnd ) return false;
			final int charIndex = (int)( index / Character.SIZE );
			return ( s.charAt( charIndex ) & 0x8000 >>> index % Character.SIZE ) != 0; 
		}

		public long getLong( final long from, final long to ) {
			if ( from % Long.SIZE == 0 && to % Character.SIZE == 0 ) {
				long l;
				int pos = (int)( from / Character.SIZE );
				if ( to == from + Long.SIZE ) l = ( ( to > actualEnd ? 0 : (long)s.charAt( pos + 3 ) ) << 48 | (long)s.charAt( pos + 2 ) << 32 | (long)s.charAt( pos + 1 ) << 16 | s.charAt( pos ) );
				else {
					l = 0;
					final int residual = (int)( Math.min( actualEnd, to ) - from );
					for( int i = residual / Character.SIZE; i-- != 0; ) 
						l |= (long)s.charAt( pos + i ) << i * Character.SIZE; 
				}

				l = ( l & 0x5555555555555555L ) << 1 | ( l >>> 1 ) & 0x5555555555555555L;
				l = ( l & 0x3333333333333333L ) << 2 | ( l >>> 2 ) & 0x3333333333333333L;
				l = ( l & 0x0f0f0f0f0f0f0f0fL ) << 4 | ( l >>> 4 ) & 0x0f0f0f0f0f0f0f0fL;
				return ( l & 0x00ff00ff00ff00ffL ) << 8 | ( l >>> 8 ) & 0x00ff00ff00ff00ffL;
			}

			return super.getLong( from, to );
		}
		
		public long length() {
			return length;
		}
		
		
	}

	public BitVector toBitVector( final CharSequence s ) {
		return utf16CharSequenceBitVector.wrap( s );
	}

	public long numBits() { return 0; }

	public TransformationStrategy<CharSequence> copy() {
		return new Utf16TransformationStrategy();
	}
}