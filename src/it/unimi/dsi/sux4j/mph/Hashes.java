package it.unimi.dsi.sux4j.mph;

public class Hashes {

	private Hashes() {}
	
	/** Computes Paul Hsieh's SuperFastHash.
	 * 
	 * @param data an array of characters.
	 * @param init an initialisation value for the hash.
	 * @param len the number of characters to digest.
	 * @return the hash.
	 */
	
    public static int superFastHash( char[] data, int init, int len ) {
		int hash = init, tmp;

		for ( int i = 0; i < len - 1; i += 2 ) {
			hash += data[ i ];
			tmp = ( data[ i + 1 ] << 11 ) ^ hash;
			hash = ( hash << 16 ) ^ tmp;
			hash += hash >>> 11;
		}

		if ( ( len & 1 ) != 0 ) {
			hash += data[ len - 1 ];
			hash ^= hash << 11;
			hash += hash >>> 17;
		}

		hash ^= hash << 3;
		hash += hash >>> 5;
		hash ^= hash << 4;
		hash += hash >>> 17;
		hash ^= hash << 25;
		hash += hash >>> 6;

		return hash;
	}

}
