/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2020 Sebastiano Vigna
 *
 *  This library is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU Lesser General Public License as published by the Free
 *  Software Foundation; either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This library is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses/>.
 *
 */

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.bits.HuTuckerTransformationStrategy;
import it.unimi.dsi.bits.TransformationStrategies;
import it.unimi.dsi.fastutil.io.BinIO;

public class VLLcpMonotoneMinimalPerfectHashFunctionTest {


	public static String binary(final int l) {
		final String s = "0000000000000000000000000000000000000000000000000000000000000000000000000" + Integer.toBinaryString(l);
		return s.substring(s.length() - 32);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testSortedNumbers() throws IOException, ClassNotFoundException {

		final String[] s = new String[1000];
		final int[] v = new int[s.length];
		for (int i = s.length; i-- != 0;)
			s[v[i] = i] = binary(i);

		VLLcpMonotoneMinimalPerfectHashFunction<String> mph = new VLLcpMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s), TransformationStrategies.prefixFreeUtf16());

		for (int i = s.length; i-- != 0;)
			assertEquals(i, mph.getLong(s[i]));

		// Exercise code for negative results
		for (int i = 1000; i-- != 0;)
			mph.getLong(binary(i * i + 1000));

		File temp = File.createTempFile(getClass().getSimpleName(), "test");
		temp.deleteOnExit();
		BinIO.storeObject(mph, temp);
		mph = (VLLcpMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
		for (int i = s.length; i-- != 0;)
			assertEquals(i, mph.getLong(s[i]));


		mph = new VLLcpMonotoneMinimalPerfectHashFunction<>(Arrays.asList(s), new HuTuckerTransformationStrategy(Arrays.asList(s), true));

		for (int i = s.length; i-- != 0;)
			assertEquals(i, mph.getLong(s[i]));

		temp = File.createTempFile(getClass().getSimpleName(), "test");
		temp.deleteOnExit();
		BinIO.storeObject(mph, temp);
		mph = (VLLcpMonotoneMinimalPerfectHashFunction<String>)BinIO.loadObject(temp);
		for (int i = s.length; i-- != 0;)
			assertEquals(i, mph.getLong(s[i]));

	}
}
