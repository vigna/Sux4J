package test.it.unimi.dsi.sux4j.bits;

import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import junit.framework.TestCase;

public class LongArrayBitVectorTest extends TestCase {

	public void testSetClearFlip() {
		LongArrayBitVector v = LongArrayBitVector.getInstance();
		v.size( 1 );
		BitVectorTest.testSetClearFlip( v );
		v.size( 64 );
		BitVectorTest.testSetClearFlip( v );
		v.size( 80 );
		BitVectorTest.testSetClearFlip( v );
		v.size( 150 );
		BitVectorTest.testSetClearFlip( v );
		
		BitVectorTest.testSetClearFlip( v.subVector( 0, 90 ) );
		BitVectorTest.testSetClearFlip( v.subVector( 5, 90 ) );
	}

	public void testFillFlip() {
		LongArrayBitVector v = LongArrayBitVector.getInstance();
		v.size( 100 );
		BitVectorTest.testFillFlip( v );
		BitVectorTest.testFillFlip( v.subVector( 0, 90 ) );
		BitVectorTest.testFillFlip( v.subVector( 5, 90 ) );
	}
	
	public void testRemove() {
		BitVectorTest.testRemove( LongArrayBitVector.getInstance() );
	}

	public void testAdd() {
		BitVectorTest.testAdd( LongArrayBitVector.getInstance() );
	}

	public void testCopy() {
		BitVectorTest.testCopy( LongArrayBitVector.getInstance() );
	}
}
