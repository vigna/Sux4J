/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2022 Sebastiano Vigna
 *
 * This program and the accompanying materials are made available under the
 * terms of the GNU Lesser General Public License v2.1 or later,
 * which is available at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1-standalone.html,
 * or the Apache Software License 2.0, which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * SPDX-License-Identifier: LGPL-2.1-or-later OR Apache-2.0
 */

package it.unimi.dsi.sux4j.util;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;

import org.junit.Test;

import it.unimi.dsi.fastutil.longs.LongBigArrayBigList;
import it.unimi.dsi.util.XoRoShiRo128PlusRandom;

public class MappedEliasFanoMonotoneLongBigListTest {

	@Test
	public void testRandom() throws ClassNotFoundException, IOException {
		final File file = File.createTempFile(this.getClass().getName(), ".ef");
		file.deleteOnExit();
		new File(file + MappedEliasFanoMonotoneLongBigList.OBJECT_EXTENSION).deleteOnExit();
		new File(file + MappedEliasFanoMonotoneLongBigList.LOWER_BITS_EXTENSION).deleteOnExit();
		final LongBigArrayBigList l = new LongBigArrayBigList();
		final XoRoShiRo128PlusRandom random = new XoRoShiRo128PlusRandom(0);
		for (long i = 1000, c = 0; i-- != 0;) {
			c += Long.numberOfTrailingZeros(random.nextLong());
			l.add(c);
		}
		final EliasFanoMonotoneLongBigList e = new EliasFanoMonotoneLongBigList(l);
		e.dump(file.toString());
		MappedEliasFanoMonotoneLongBigList m = MappedEliasFanoMonotoneLongBigList.load(file.toString());
		assertEquals(m.copy(), e);
		assertEquals(l, e);
		m.close();

		e.dump(file.toString(), ByteOrder.BIG_ENDIAN);
		m = MappedEliasFanoMonotoneLongBigList.load(file.toString());
		assertEquals(m.copy(), e);
		assertEquals(m, e);
		m.close();

		e.dump(file.toString(), ByteOrder.LITTLE_ENDIAN);
		m = MappedEliasFanoMonotoneLongBigList.load(file.toString());
		assertEquals(m, e);
		assertEquals(m.copy(), e);
		m.close();


		file.delete();
		new File(file + MappedEliasFanoMonotoneLongBigList.OBJECT_EXTENSION).delete();
		new File(file + MappedEliasFanoMonotoneLongBigList.LOWER_BITS_EXTENSION).delete();
	}
}
