package it.unimi.dsi.sux4j.bits;


import static org.junit.Assert.assertEquals;
import it.unimi.dsi.bits.LongArrayBitVector;

import org.junit.Test;

public class Rank9SelectSlowTest {

	@Test
	public void testVeryLarge() {
		LongArrayBitVector v = LongArrayBitVector.getInstance(2200000000L);
		for (int i = 0; i < 2200000000L / 64; i++)
			v.append(0x5555555555555555L, 64);
		Rank9 rank9;
		Select9 select9 = new Select9(rank9 = new Rank9(v));
		for (int i = 0; i < 1100000000; i++)
			assertEquals(i * 2L, select9.select(i));
		for (int i = 0; i < 1100000000; i++)
			assertEquals(i, rank9.rank(i * 2L));
	}

}
