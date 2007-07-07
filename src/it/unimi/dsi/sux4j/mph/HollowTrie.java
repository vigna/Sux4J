package it.unimi.dsi.sux4j.mph;

import java.util.Iterator;

import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import it.unimi.dsi.sux4j.bits.RankAndSelect;

public class HollowTrie {

	private final RankAndSelect trie;
	private final int[] skip;
	
	private final static class Node {
		Node left, right;
		int skip;
		
		public Node( final Node left, final Node right, final int skip ) {
			this.left = left;
			this.right = right;
			this.skip = skip;
		}
	}
	
	private Node root;
	
	
	private final static LongArrayBitVector seq2bv( CharSequence s ) {
		LongArrayBitVector b = LongArrayBitVector.getInstance();
		for( int i = 0; i < s.length(); i++ )
			for( int j = 16; j-- != 0; ) b.add( s.charAt( i ) & 1 << j );
		return b;
	}
	
	
	public HollowTrie( final Iterator<? extends CharSequence> iterator ) {

		//if ( ! iterator.hasNext() ) return;
		
		CharSequence s = iterator.next();
		LongArrayBitVector prev = seq2bv( s ), curr;
		int prefix;
		Node p;
		
		while( iterator.hasNext() ) {
			curr = seq2bv( iterator.next() );
			prefix = prev.maximumCommonPrefixLength( prev );
			
			p = root;
			
			while( p != null ) {
			
			}
			
			
			
			prev = curr;
		}
		
		throw new UnsupportedOperationException();
		
		
	}
	
}
