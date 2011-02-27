package it.unimi.dsi.sux4j.util;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;
import it.unimi.dsi.fastutil.objects.ObjectBidirectionalIterator;
import it.unimi.dsi.sux4j.util.ZFastTrie;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import junit.framework.TestCase;

public class ZFastTrieTest extends TestCase {


	public static String binary( int l ) {
		String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString( l );
		return s.substring( s.length() - 32 );
	}

	@SuppressWarnings("unchecked")
	public void testEmpty() throws IOException, ClassNotFoundException {
		String[] s = {};
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		assertFalse( zft.contains( "" ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		assertFalse( zft.contains( "" ) );
	}

	@SuppressWarnings("unchecked")
	public void testSingleton() throws IOException, ClassNotFoundException {
		String[] s = { "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		zft.remove( "a" );
		assertFalse( zft.contains( "a" ) );
	}

	@SuppressWarnings("unchecked")
	public void testDoubleton() throws IOException, ClassNotFoundException {
		String[] s = { "a", "c" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testDoubleton2() throws IOException, ClassNotFoundException {
		String[] s = { "c", "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testTriple() throws IOException, ClassNotFoundException {
		String[] s = { "a", "b", "c" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testTriple2() throws IOException, ClassNotFoundException {
		String[] s = { "c", "b", "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testExitNodeIsLeaf() throws IOException, ClassNotFoundException {
		String[] s = { "a", "aa", "aaa" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testExitNodeIsLeaf3() throws IOException, ClassNotFoundException {
		String[] s = { "a", "aa", "aaa" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}


	@SuppressWarnings("unchecked")
	public void testSmallest() throws IOException, ClassNotFoundException {
		String[] s = { "a", "b", "c", "d", "e", "f", "g" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
		assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testSmallest2() throws IOException, ClassNotFoundException {
		String[] s = { "g", "f", "e", "d", "c", "b", "a" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; )
		assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testSmall() throws IOException, ClassNotFoundException {
		String[] s = { "-", "0", "1", "4", "5", "a", "b", "c", "d", "e", "f", "g", "}" };
		ZFastTrie<String> zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );
		for ( int i = s.length; i-- != 0; ) assertTrue( s[ i ], zft.contains( s[ i ] ) );
		File temp = File.createTempFile( getClass().getSimpleName(), "test" );
		temp.deleteOnExit();
		BinIO.storeObject( zft, temp );
		zft = (ZFastTrie<String>)BinIO.loadObject( temp );
		for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

		for ( int i = s.length; i-- != 0; ) {
			assertTrue( zft.remove( s[ i ] ) );
			assertFalse( zft.contains( s[ i ] ) );
		}
	}

	@SuppressWarnings("unchecked")
	public void testNumbers() throws IOException, ClassNotFoundException {
		ZFastTrie<String> zft;
		File temp;
		Random random = new Random( 0 );

		for ( int d = 10; d < 10000; d *= 10 ) {
			String[] s = new String[ d ];

			for( int rand = 0; rand < 2; rand++ ) {
				for ( int i = s.length; i-- != 0; )
					s[ i ] = binary( i );

				for( int pass = 0; pass < 2; pass++ ) {

					zft = new ZFastTrie<String>( Arrays.asList( s ), TransformationStrategies.prefixFreeIso() );

					for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

					// Exercise code for negative results
					for ( int i = 1000; i-- != 0; ) zft.contains( binary( i * i + d ) );

					temp = File.createTempFile( getClass().getSimpleName(), "test" );
					temp.deleteOnExit();
					BinIO.storeObject( zft, temp );
					zft = (ZFastTrie<String>)BinIO.loadObject( temp );
					for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

					zft = new ZFastTrie<String>( Arrays.asList( s ), new HuTuckerTransformationStrategy( Arrays.asList( s ), true ) );

					for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

					temp = File.createTempFile( getClass().getSimpleName(), "test" );
					temp.deleteOnExit();
					BinIO.storeObject( zft, temp );
					zft = (ZFastTrie<String>)BinIO.loadObject( temp );
					for ( int i = s.length; i-- != 0; ) assertTrue( zft.contains( s[ i ] ) );

					Collections.sort( Arrays.asList( s ) );

					int p = 0;
					ObjectBidirectionalIterator<String> iterator;
					for( iterator = zft.iterator(); iterator.hasNext(); ) assertEquals( iterator.next(), s[ p++ ] );
					while( iterator.hasPrevious() ) assertEquals( iterator.previous(), s[ --p ] );

					for( int i = 0; i < s.length / 100; i++ ) {
						p = i;
						for( iterator = zft.iterator( s[ i ] ); iterator.hasNext(); ) assertEquals( iterator.next(), s[ p++ ] );
						while( iterator.hasPrevious() ) assertEquals( iterator.previous(), s[ --p ] );
					}

					for ( int i = s.length; i-- != 0; ) {
						assertTrue( zft.remove( s[ i ] ) );
						assertFalse( zft.contains( s[ i ] ) );
					}
					
					Collections.shuffle( Arrays.asList( s ), new Random( 0 ) );
				}
			}
			
			for ( int i = s.length; i-- != 0; )
				s[ i ] = binary( random.nextInt( Integer.MAX_VALUE ) );

		}
	}
}
