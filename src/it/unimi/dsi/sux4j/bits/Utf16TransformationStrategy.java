package it.unimi.dsi.sux4j.bits;

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
			return ( s.charAt( (int)( index / Character.SIZE ) ) & 0x8000 >>> index % Character.SIZE ) != 0; 
		}

		// TODO: make this FAST.
		/*public long getLong( final long from, final long to ) {
			long l;
			int pos = from / Character.SIZE;
			if ( from % Long.SIZE == 0 && to % Character.SIZE == 0 ) {
				if ( to == from + Long.SIZE ) l = s.charAt( pos ) << 48 | s.charAt( pos + 1 ) << 32 | s.charAt( pos + 2 ) << 16 | s.charAt( pos + 3 );
				else for( int i = 0; i < ( to - from ) / Character.SIZE ) l |= 
			}
		}*/
		
		public long length() {
			return length;
		}
		
	}

	public BitVector toBitVector( final CharSequence s ) {
		return utf16CharSequenceBitVector.wrap( s );
	}

	public long numBits() { return 0; }
}