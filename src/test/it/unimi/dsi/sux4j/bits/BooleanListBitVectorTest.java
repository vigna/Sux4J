package test.it.unimi.dsi.sux4j.bits;

import java.io.IOException;
import java.util.Arrays;

import it.unimi.dsi.sux4j.bits.BitVector;
import it.unimi.dsi.sux4j.bits.BooleanListBitVector;
import it.unimi.dsi.sux4j.bits.LongArrayBitVector;
import junit.framework.TestCase;

public class BooleanListBitVectorTest extends TestCase {

	public void testSetClearFlip() {
		BooleanListBitVector v = BooleanListBitVector.getInstance();
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
		BooleanListBitVector v = BooleanListBitVector.getInstance();
		v.size( 100 );
		BitVectorTest.testFillFlip( v );
		BitVectorTest.testFillFlip( v.subVector( 0, 90 ) );
		BitVectorTest.testFillFlip( v.subVector( 5, 90 ) );
	}
	
	public void testRemove() {
		BitVectorTest.testRemove( BooleanListBitVector.getInstance() );
	}

	public void testAdd() {
		BitVectorTest.testAdd( BooleanListBitVector.getInstance() );
	}

	public void testCopy() {
		BitVectorTest.testCopy( BooleanListBitVector.getInstance() );
	}

	public void testBits() {
		BitVectorTest.testBits( BooleanListBitVector.getInstance() );
	}
		
	public void testLongBigListView() {
		BitVectorTest.testLongBigListView( BooleanListBitVector.getInstance() );
	}

	public void testFirstLast() {
		BitVectorTest.testFirstLastPrefix( BooleanListBitVector.getInstance() );
	}
	
	public void testLogicOperators() {
		BitVectorTest.testLogicOperators( BooleanListBitVector.getInstance() );
	}

	public void testSerialisation() throws IOException, ClassNotFoundException {
		BitVectorTest.testSerialisation( BooleanListBitVector.getInstance() );
	}

}
