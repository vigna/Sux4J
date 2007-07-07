package it.unimi.dsi.sux4j.mph;

import java.util.Iterator;

import cern.colt.bitvector.BitVector;
import cern.colt.bitvector.QuickBitVector;

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
	
	public HollowTrie( final Iterator<? extends CharSequence> iterator ) {

		if ( ! iterator.hasNext() ) return;
		
		
		CharSequence s = iterator.next();
		BitVector b = new BitVector( s.length() * 16 );
		for( int i = 0; i < s.length(); i++ ) b.
		
		
	}
	
}
