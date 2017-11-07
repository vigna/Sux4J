package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

public class PaCoTrieDistributorMonotoneMinimalPerfectHashFunctionTest {


	public static String binary(int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}


	@Test
	public void testSmall() throws IOException {
		final String[] s = { "a", "b", "c", "d", "e", "f" };
		final PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
				TransformationStrategies.prefixFreeIso());

		for (int i = s.length; i-- != 0;)
			assertEquals(i, mph.getLong(s[i]));
	}

	@Test
	public void testPrefix() throws IOException {

		final String[] s = { "0", "00", "000", "0000", "00000", "000000", "00000001", "0000000101", "00000002" };
		final PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
				TransformationStrategies.prefixFreeUtf16());

		for (int i = s.length; i-- != 0;)
			assertEquals(i, mph.getLong(s[i]));

	}


	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		for (int d = 10; d < 10000; d *= 10) {

			final String[] s = new String[d];
			final int[] v = new int[s.length];
			for (int i = s.length; i-- != 0;)
				s[v[i] = i] = binary(i);

			PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s),
					TransformationStrategies.prefixFreeUtf16());

			for (int i = s.length; i-- != 0;)
				assertEquals(i, mph.getLong(s[i]));

			// Exercise code for negative results
			for (int i = d; i-- != 0;)
				mph.getLong(binary(i * i + d));

			File temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
			for (int i = s.length; i-- != 0;)
				assertEquals(i, mph.getLong(s[i]));


			mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s), new HuTuckerTransformationStrategy(Arrays.asList(s), true));

			for (int i = s.length; i-- != 0;)
				assertEquals(i, mph.getLong(s[i]));

			temp = File.createTempFile(getClass().getSimpleName(), "test");
			temp.deleteOnExit();
			BinIO.storeObject(mph, temp);
			mph = (PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
			for (int i = s.length; i-- != 0;)
				assertEquals(i, mph.getLong(s[i]));

		}
	}

	@Test
	public void testManyLengths() throws IOException {
		final String[] s = new String[2051];
		for (int i = s.length; i-- != 0;) s[i] = binary(i);
		for (final int n: new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 15, 16, 17, 31, 32, 33, 127, 128, 129, 510, 511, 512, 513, 514, 1022, 1023, 1024, 1025, 1026, 2046, 2047, 2048, 2049, 2050 }) {
			System.err.println("Testing size " + n + "...");
			final PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<String> mph = new PaCoTrieDistributorMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s).subList(0, n),
						TransformationStrategies.prefixFreeUtf16());

			for (int i = n; i-- != 0;) assertEquals(i, mph.getLong(s[i]));
		}
	}
}
