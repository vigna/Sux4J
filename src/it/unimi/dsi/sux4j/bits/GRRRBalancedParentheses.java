package it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.bits.BitVector;
import it.unimi.dsi.bits.Fast;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.sux4j.util.EliasFanoLongBigList;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.Collections;

import static it.unimi.dsi.bits.Fast.ONES_STEP_4;
import static it.unimi.dsi.bits.Fast.ONES_STEP_8;

public class GRRRBalancedParentheses implements BalancedParentheses {
	private static final long serialVersionUID = 1L;
	private static final boolean ASSERTS = true;
	private static final boolean DEBUG = false;
	private transient long[] bits;
	protected final BitVector bitVector;
	private final SparseSelect openingPioneers;
	private final SparseRank openingPioneersRank;
	private final EliasFanoLongBigList openingPioneerMatches;
	private final SparseSelect closingPioneers;
	private final SparseRank closingPioneersRank;
	private final EliasFanoLongBigList closingPioneerMatches;

	public final static int countFarOpen( long word, int l ) {
		int c = 0, e = 0;
		while( l-- != 0 ) {
			if ( ( word & 1L << l ) != 0 ) {
				if ( ++e > 0 ) c++;
			}
			else {
				if ( e > 0 ) e = -1;
				else --e;
			}
		}

		return c;
	}

	public final static int findFarOpen( long word, int l, int k ) {
		int e = 0;
		while( l-- != 0 ) {
			if ( ( word & 1L << l ) != 0 ) {
				if ( ++e > 0 && k-- == 0 ) return l;
			}
			else {
				if ( e > 0 ) e = -1;
				else --e;
			}
		}

		return -1;
	}

	public final static int countFarClose( long word, int l ) {
		int c = 0, e = 0;
		for( int i = 0; i < l; i++ ) {
			if ( ( word & 1L << i ) != 0 ) {
				if ( e > 0 ) e = -1;
				else --e;
			}
			else {
				if ( ++e > 0 ) c++;
			}
		}

		return c;
	}

	public final static int findFarClose( long word, int l, int k ) {
		int e = 0;
		for( int i = 0; i < l; i++ ) {
			if ( ( word & 1L << i ) != 0 ) {
				if ( e > 0 ) e = -1;
				else --e;
			}
			else {
				if ( ++e > 0 && k-- == 0 ) return i;
			}
		}

		return -1;
	}

	public final static int findNearClose( long word ) {
		if ( ( word & 2 ) == 0 ) return 1;
		
		final long word2 = word - ( ( word & 0xa * ONES_STEP_4 ) >>> 1 );
		
		if ( ( word2 >>> 2 & 3 ) == 0 ) return 3;

        final long word4 = ( word2 & 3 * ONES_STEP_4 ) + ( ( word2 >>> 2 ) & 3 * ONES_STEP_4 );
        int e = 2 * (int)( word4 & 0xF ) - 4;
        
        if ( ASSERTS ) assert e > 0;
        
        if ( e <= 4 - 2 * ( word4 >>> 4 & 0xF ) ) {
        	if ( e <= 2 - 2 * ( word2 >>> 4 & 3 ) ) return 5;
        	else return 7;	
        }

        final long word8 = ( word4 + ( word4 >>> 4 ) ) & 0x0f * ONES_STEP_8;
        e = 2 * (int)( word8 & 0xFF ) - 8;
        
        if ( ASSERTS ) assert e > 0;
        
        if ( e <= 8 - 2 * ( word8 >>> 8 & 0xFF ) ) {
        	if ( e <= 4 - 2 * ( word4 >>> 8 & 0xF ) ) {
            	if ( e <= 2 - 2 * ( word2 >>> 8 & 3 ) ) return 9;
            	else return 11;	
        	}
        	else {
        		e -= 4 - 2 * ( word4 >>> 8 & 0xF );
            	if ( e <= 2 - 2 * ( word2 >>> 12 & 3 ) ) return 13;
            	else return 15;	
        	}
        }
        
        final long word16 = ( word8 + ( word8 >>> 8 ) ) & 0x00ff00ff00ff00ffL;
        e = 2 * (int)( word16 & 0xFFFF ) - 16;

        if ( ASSERTS ) assert e > 0;
        System.err.println( e + ", " + (16 - 2 * ( word16 >>> 16 & 0xFFFF ))+ ", " + (2 - 2 * ( word2 >>> 16 & 3 )));
        if ( e <= 16 - 2 * ( word16 >>> 16 & 0xFFFF ) ) {
            System.err.println( "HERE");
            if ( e <= 8 - 2 * ( word8 >>> 16 & 0xFF ) ) {
            	if ( e <= 4 - 2 * ( word4 >>> 16 & 0xF ) ) {
                	if ( e <= 2 - 2 * ( word2 >>> 16 & 3 ) ) return 17;
                	else return 19;	
            	}
            	else {
            		e -= 4 - 2 * ( word4 >>> 16 & 0xF );
                	if ( e <= 2 - 2 * ( word2 >>> 20 & 3 ) ) return 21;
                	else return 23;
            	}
            }
            else {
            	e -= 8 - 2 * ( word8 >>> 16 & 0xFF );

            	if ( e <= 4 - 2 * ( word4 >>> 24 & 0xF ) ) {
                	if ( e <= 2 - 2 * ( word2 >>> 24 & 3 ) ) return 25;
                	else return 27;
            	}
            	else {
            		e -= 4 - 2 * ( word4 >>> 24 & 0xF );
                	if ( e <= 2 - 2 * ( word2 >>> 28 & 3 ) ) return 29;
                	else return 31;	
            	}
            }
        }
		
        final long word32 = ( word16 + ( word16 >>> 16 ) ) & 0x0000ffff0000ffffL;
        e = 2 * (int)( word32 & 0xFFFFFFFFL ) - 32;

        if ( ASSERTS ) assert e > 0;

        if ( e <= 32 - 2 * ( word32 >>> 32 & 0xFFFFFFFFL ) ) {
            if ( e <= 16 - 2* ( word16 >>> 32 & 0xFFFF ) ) {
                if ( e <= 8 - 2 * ( word8 >>> 32 & 0xFF ) ) {
                	if ( e <= 4 - 2 * ( word4 >>> 32 & 0xF ) ) {
                    	if ( e <= 2 - 2 * ( word2 >>> 32 & 3 ) ) return 33;
                    	else return 35;	
                	}
                	else {
                		e -= 4 - 2 * ( word4 >>> 32 & 0xF );
                    	if ( e <= 2 - 2 * ( word2 >>> 36 & 3 ) ) return 37;
                    	else return 39;
                	}
                }
                else {
                	e -= 8 - 2 * ( word8 >>> 32 & 0xFF );

                	if ( e <= 4 - 2 * ( word4 >>> 40 & 0xF ) ) {
                    	if ( e <= 2 - 2 * ( word2 >>> 40 & 3 ) ) return 41;
                    	else return 43;
                	}
                	else {
                		e -= 4 - 2 * ( word4 >>> 40 & 0xF );
                    	if ( e <= 2 - 2 * ( word2 >>> 44 & 3 ) ) return 45;
                    	else return 47;	
                	}
                }
            }
            else {
            	e -= 16 - 2 * ( word16 >>> 32 & 0xFFFFL );

            	if ( e <= 16 - 2 * ( word16 >>> 48 & 0xFFFF ) ) {
            		if ( e <= 8 - 2 * ( word8 >>> 48 & 0xFF ) ) {
            			if ( e <= 4 - 2 * ( word4 >>> 48 & 0xF ) ) {
            				if ( e <= 2 - 2 * ( word2 >>> 48 & 3 ) ) return 49;
            				else return 51;	
            			}
            			else {
            				e -= 4 - 2 * ( word4 >>> 48 & 0xF );
            				if ( e <= 2 - 2 * ( word2 >>> 52 & 3 ) ) return 53;
            				else return 55;
            			}
            		}
            		else {
            			e -= 8 - 2 * ( word8 >>> 48 & 0xFF );

            			if ( e <= 4 - 2 * ( word4 >>> 56 & 0xF ) ) {
            				if ( e <= 2 - 2 * ( word2 >>> 56 & 3 ) ) return 57;
            				else return 59;
            			}
            			else {
            				e -= 4 - 2 * ( word4 >>> 56 & 0xF );
            				if ( e <= 2 - 2 * ( word2 >>> 60 & 3 ) ) return 61;
            				else return 63;	
            			}
            		}
            	}
            }
        }
        
        return -1;
	}

	public GRRRBalancedParentheses( final BitVector bv ) {
		this( bv, true, true, true );
	}
	
	public GRRRBalancedParentheses( final long[] bits, final long length ) {
		this( LongArrayBitVector.wrap(  bits, length ) );
	}

	public GRRRBalancedParentheses( final BitVector bitVector, final boolean findOpen, final boolean findClose, final boolean enclose ) {
		if ( ! findOpen && ! findClose && ! enclose ) throw new IllegalArgumentException( "You must specify at least one implemented method" );
		this.bitVector = bitVector;
		this.bits = bitVector.bits();
		final long length = bitVector.length();
		final int numWords = (int)( ( length + Long.SIZE - 1 ) / Long.SIZE );
		
		final byte count[] = new byte[ numWords ];
		final byte residual[] = new byte[ numWords ];

		LongArrayList closingPioneers = null, closingPioneerMatches = null, openingPioneers = null, openingPioneerMatches = null;

		if ( findOpen ) {
			closingPioneers = new LongArrayList();
			closingPioneerMatches = new LongArrayList();
			for( int block = 0; block < numWords; block++ ) {
				if ( DEBUG ) System.err.println( "Scanning word " + block + " (" + LongArrayBitVector.wrap( new long[] { bits[ block ] } ) + ")" );
				final int l = (int)Math.min( Long.SIZE, length - block * Long.SIZE );

				if ( block > 0 ) {
					int excess = 0;
					int countFarClosing = countFarClose( bits[ block ], l );

					for( int j = 0; j < l; j++ ) {
						if ( ( bits[ block ] & 1L << j ) != 0 ) {
							if ( excess > 0 ) excess = -1;
							else --excess;
						}
						else {
							if ( ++excess > 0 ) {
								// Find block containing matching far open parenthesis
								int matchingBlock = block;
								while( count[ --matchingBlock ] == 0 );
								countFarClosing--;
								if ( --count[ matchingBlock ] == 0 || countFarClosing == 0 ) {
									// This is a closing pioneer
									if ( DEBUG ) System.err.println( "+) " + ( block * Long.SIZE + j ) + " " + Arrays.toString(  count ) );
									closingPioneers.add( block * Long.SIZE + j );
									closingPioneerMatches.add( ( block * Long.SIZE + j ) - ( matchingBlock * Long.SIZE + findFarOpen( bits[ matchingBlock ], Long.SIZE, residual[ matchingBlock ] ) ) );
								}
								residual[ matchingBlock ]++;
							}
						}
					}
				}
				count[ block ] = (byte)countFarOpen( bits[ block ], l );
				if ( DEBUG ) System.err.println( "Stack updated: " + Arrays.toString(  count ) );
			}

			for( int i = count.length; i-- != 0; ) if ( count[ i ] != 0 ) throw new IllegalArgumentException( "Unbalanced parentheses" );
			if ( DEBUG ) System.err.println( "):" + closingPioneers );
			if ( DEBUG ) System.err.println( "):" + closingPioneerMatches );
		}

		if ( findClose ) {
			ByteArrays.fill( residual, (byte)0 );

			openingPioneers = new LongArrayList();
			openingPioneerMatches = new LongArrayList();

			for( int block = numWords; block-- != 0; ) {
				if ( DEBUG ) System.err.println( "Scanning word " + block + " (" + LongArrayBitVector.wrap( new long[] { bits[ block ] } ) + ")" );
				final int l = (int)Math.min( Long.SIZE, length - block * Long.SIZE );

				if ( block != numWords -1 ) {
					int excess = 0;
					int countFarOpening = countFarOpen( bits[ block ], l );
					boolean somethingAdded = false;

					for( int j = l; j-- != 0; ) {
						if ( ( bits[ block ] & 1L << j ) == 0 ) {
							if ( excess > 0 ) excess = -1;
							else --excess;
						}
						else {
							if ( ++excess > 0 ) {
								// Find block containing matching far close parenthesis
								int matchingBlock = block;
								while( count[ ++matchingBlock ] == 0 );
								countFarOpening--;
								if ( --count[ matchingBlock ] == 0 || countFarOpening == 0 ) {
									// This is an opening pioneer
									if ( DEBUG ) System.err.println( "+( " + ( block * Long.SIZE + j ) + " " + Arrays.toString(  count ) );
									openingPioneers.add( block * Long.SIZE + j );
									openingPioneerMatches.add( - ( block * Long.SIZE + j ) + ( matchingBlock * Long.SIZE + findFarClose( bits[ matchingBlock ], Long.SIZE, residual[ matchingBlock ]) ) );
									//if ( block == 14 ) System.err.println( "Adding " + block * Long.SIZE + j );
									if ( ASSERTS ) somethingAdded = true;
								}
								residual[ matchingBlock ]++;
							}
						}					
					}				
					if ( ASSERTS ) assert somethingAdded || countFarOpen( bits[ block ], l ) == 0 : "No pioneers for block " + block + " " + LongArrayBitVector.wrap(  new long[] { bits[ block ] }, l ) + " (" + l + ") " + countFarOpen( bits[ block ], l );
				}
				count[ block ] = (byte)countFarClose( bits[ block ], l );
				if ( DEBUG ) System.err.println( "Stack updated: " + Arrays.toString(  count ) );
			}

			for( int i = count.length; i-- != 0; ) if ( count[ i ] != 0 ) throw new IllegalArgumentException( "Unbalanced parentheses" );
			if ( DEBUG ) System.err.println( "(:" + openingPioneers );
			if ( DEBUG ) System.err.println( "(:" + openingPioneerMatches );

			Collections.reverse( openingPioneers );
			Collections.reverse( openingPioneerMatches );
		}

		this.closingPioneers = closingPioneers != null ? new SparseSelect( closingPioneers ) : null;
		this.closingPioneersRank = closingPioneers != null ? this.closingPioneers.getRank() : null;
		this.closingPioneerMatches = closingPioneers != null ? new EliasFanoLongBigList( closingPioneerMatches ) : null;
		this.openingPioneers = openingPioneers != null ? new SparseSelect( openingPioneers ) : null;
		this.openingPioneersRank = openingPioneers != null ? this.openingPioneers.getRank() : null;
		this.openingPioneerMatches = openingPioneers != null ? new EliasFanoLongBigList( openingPioneerMatches ) : null;
}
	
	public long enclose( long pos ) {
		throw new UnsupportedOperationException();
	}

	public long findClose( final long pos ) {
		if ( DEBUG ) System.err.println( "findClose(" + pos + ")..." );
		final int word = (int)( pos / Long.SIZE );
		int bit = (int)( pos & LongArrayBitVector.WORD_MASK );
		if ( ( bits[ word ] & 1L << bit ) == 0 ) throw new IllegalArgumentException();

		int result = findNearClose( bits[ word ] >>> bit );

		if ( ASSERTS ) {
			int c = 1;
			int b = bit;
			while( ++b < Long.SIZE ) {
				if ( ( bits[ word ] & 1L << b ) == 0 ) c--;
				else c++;
				if ( c == 0 ) break;
			}

			if ( ASSERTS ) assert ( c != 0 ) == ( result == -1 || result >= Long.SIZE - bit ) : "c: " + c + " bit: " + (b - bit) + " result:" + result + " " + LongArrayBitVector.wrap(  new long[] { bits[ word ] >>> bit }, Long.SIZE - bit ) + " (" +(Long.SIZE -bit)+ ")";
			if ( ASSERTS ) assert ( c == 0 ) && ( b == bit + result ) : b + " != " + ( bit + result ) + " (bit:" + bit + ")" + LongArrayBitVector.wrap(  new long[] { bits[ word ] >>> bit } );
		}
		if ( result > Long.SIZE - bit ) {
			if ( DEBUG ) System.err.println( "Returning in-word value: " + ( word * Long.SIZE + bit + result ) );
			return word * Long.SIZE + bit + result;
		}

		final long pioneerIndex = openingPioneersRank.rank( pos + 1 ) - 1;
		final long pioneer = openingPioneers.select( pioneerIndex );
		final long match = pioneer + openingPioneerMatches.getLong( pioneerIndex );

		if ( pos == pioneer ) {
			if ( DEBUG ) System.err.println( "Returning exact pioneer match: " + match );
			return match;
		}
		
		if ( DEBUG ) System.err.println( "pioneer: " + pioneer + "; match: " + match );
		int dist = (int)( pos - pioneer );
		
		if ( ASSERTS ) assert word == pioneer / Long.SIZE : "pos: " + pos + " word:" + word + " pioneer: " + pioneer + " word:" + pioneer / Long.SIZE;
		if ( ASSERTS ) assert word != match / Long.SIZE;
		if ( ASSERTS ) assert pioneer < pos;
		
		int e = 2 * Fast.count( ( bits[ word ] >>> ( pioneer % Long.SIZE ) ) & ( 1L << dist ) - 1 ) - dist; 
		if ( ASSERTS ) {
			assert e >= 1;
			int ee = 0;
			for( long p = pioneer; p < pos; p++ ) if ( ( bits[ (int)( p / Long.SIZE ) ] & 1L << ( p % Long.SIZE ) ) != 0 ) ee++;
			else ee--;
			assert ee == e: ee + " != " + e;
		}
		if ( DEBUG ) System.err.println( "eccess: " + e );
		
		final int matchWord = (int)( match / Long.SIZE );
		final int matchBit = (int)( match % Long.SIZE );
		
		final int numFarClose = matchBit - 2 * Fast.count( bits[ matchWord ] & ( 1L << matchBit ) - 1 );

		if ( DEBUG ) System.err.println( "far close before match: " + numFarClose );
		if ( DEBUG ) System.err.println( "Finding far close of rank " + ( numFarClose - e ) + " at " + findFarClose( bits[ matchWord ], matchBit, numFarClose - e ) );		
		if ( DEBUG ) System.err.println( "Returning value: " + ( matchWord * Long.SIZE + findFarClose( bits[ matchWord ], matchBit, numFarClose - e ) ) );
		return matchWord * Long.SIZE + findFarClose( bits[ matchWord ], matchBit, numFarClose - e );
	}

	public long findOpen( long pos ) {
		throw new UnsupportedOperationException();
	}

	public long numBits() {
		return 
			( openingPioneers != null ? ( openingPioneers.numBits() + openingPioneersRank.numBits() + openingPioneerMatches.numBits() ) : 0 ) + 
			( closingPioneers != null ? closingPioneers.numBits() + closingPioneersRank.numBits() + closingPioneerMatches.numBits() : 0 );
	}

	private void readObject( final ObjectInputStream s ) throws IOException, ClassNotFoundException {
		s.defaultReadObject();
		bits = bitVector.bits();
	}

	public BitVector bitVector() {
		return bitVector;
	}

}
