package it.unimi.dsi.sux4j.bits;

import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

import java.util.Random;

import org.junit.Test;

public class SparseSelectTest extends RankSelectTestCase {

	@Test
	public void testEmpty() {
		SparseSelect select;

		select = new SparseSelect(new long[1], 64);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[2], 128);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[1], 63);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[2], 65);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[3], 129);
		assertEquals(-1, select.select(0));
		assertEquals(-1, select.select(1));

	}

	@Test
	public void testSingleton() {
		SparseSelect select;

		select = new SparseSelect(new long[] { 1L << 63, 0 }, 64);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[] { 1 }, 64);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[] { 1L << 63, 0 }, 128);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[] { 1L << 63, 0 }, 65);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));

		select = new SparseSelect(new long[] { 1L << 63, 0, 0 }, 129);
		assertSelect(select);
		assertEquals(63, select.select(0));
		assertEquals(-1, select.select(1));
	}

	@Test
	public void testDoubleton() {
		SparseSelect select;

		select = new SparseSelect(new long[] { 1 | 1L << 32 }, 64);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(32, select.select(1));
		assertEquals(-1, select.select(2));

		select = new SparseSelect(new long[] { 1, 1 }, 128);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(64, select.select(1));
		assertEquals(-1, select.select(2));

		select = new SparseSelect(new long[] { 1 | 1L << 32, 0 }, 63);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(32, select.select(1));
		assertEquals(-1, select.select(2));

		select = new SparseSelect(new long[] { 1, 1, 0 }, 129);
		assertSelect(select);
		assertEquals(0, select.select(0));
		assertEquals(64, select.select(1));
		assertEquals(-1, select.select(2));
	}

	@Test
	public void testAlternating() {
		SparseSelect select;
		int i;

		select = new SparseSelect(new long[] { 0xAAAAAAAAAAAAAAAAL }, 64);
		assertSelect(select);
		for (i = 32; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SparseSelect(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 128);
		assertSelect(select);
		for (i = 64; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SparseSelect(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAAAAAL }, 64 * 5);
		assertSelect(select);
		for (i = 32 * 5; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SparseSelect(new long[] { 0xAAAAAAAAL }, 33);
		assertSelect(select);
		for (i = 16; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));

		select = new SparseSelect(new long[] { 0xAAAAAAAAAAAAAAAAL, 0xAAAAAAAAAAAAL }, 128);
		assertSelect(select);
		for (i = 56; i-- != 1;)
			assertEquals(i * 2 + 1, select.select(i));
	}

	@Test
	public void testSelect() {
		SparseSelect select;
		select = new SparseSelect(LongArrayBitVector.of(1, 0, 1, 1, 0, 0, 0).bits(), 7);
		assertSelect(select);
		assertEquals(0, select.select(0));
	}

	@Test
	public void testSparse() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(256 * 1024);
		bitVector.set(1);
		bitVector.set(100000);
		bitVector.set(199999);
		SparseSelect select;

		select = new SparseSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(64 * 1024);
		bitVector.set(1);
		bitVector.set(40000);
		bitVector.set(49999);

		select = new SparseSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		bitVector.set(1);
		bitVector.set(20000);
		bitVector.set(29999);

		select = new SparseSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testDense() {
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance().length(16 * 1024);

		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 2);
		SparseSelect select;

		select = new SparseSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 4);

		select = new SparseSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 8);

		select = new SparseSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(16 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 16);

		select = new SparseSelect(bitVector);
		assertSelect(select);

		bitVector = LongArrayBitVector.getInstance().length(32 * 1024);
		for (int i = 0; i <= 512; i++)
			bitVector.set(i * 32);

		select = new SparseSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testRandom() {
		Random r = new XoRoShiRo128PlusRandom(1);
		LongArrayBitVector bitVector = LongArrayBitVector.getInstance(1000);
		for (int i = 0; i < 1000; i++)
			bitVector.add(r.nextBoolean());
		SparseSelect select;

		select = new SparseSelect(bitVector);
		assertSelect(select);
	}

	@Test
	public void testAllSizes() {
		LongArrayBitVector v;
		SparseSelect r;
		for (int size = 0; size <= 4096; size++) {
			v = LongArrayBitVector.getInstance().length(size);
			for (int i = (size + 1) / 2; i-- != 0;)
				v.set(i * 2);
			r = new SparseSelect(v);
			for (int i = size / 2; i-- != 0;)
				assertEquals(i * 2, r.select(i));

			v = LongArrayBitVector.getInstance().length(size);
			v.fill(true);
			r = new SparseSelect(v);
			for (int i = size; i-- != 0;)
				assertEquals(i, r.select(i));
		}
	}
}
