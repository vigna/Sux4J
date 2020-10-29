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

package it.unimi.dsi.sux4j.mph.solve;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.sux4j.mph.solve.Modulo2System.Modulo2Equation;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;

public class Modulo2SystemTest {

	@Test
	public void testBuilder() {
		final Modulo2Equation equation = new Modulo2Equation(2, 3).add(2).add(0).add(1);
		assertEquals(2, equation.c);
		assertEquals(3, equation.variables().length);
		assertArrayEquals(new int[] { 0, 1, 2 }, equation.variables());
	}

	@Test
	public void testSub0() {
		final Modulo2Equation equation0 = new Modulo2Equation(2, 11).add(1).add(4).add(9);
		final Modulo2Equation equation1 = new Modulo2Equation(1, 11).add(1).add(4).add(10);
		equation0.add(equation1);
		assertArrayEquals(new int[] { 9, 10 }, equation0.variables());
	}

	@Test
	public void testOne() {
		final Modulo2System system = new Modulo2System(2);
		system.add(new Modulo2Equation(2, 2).add(0));
		final long[] solution = new long[2];
		assertTrue(system.copy().gaussianElimination(solution));
		assertTrue(system.check(solution));
		Arrays.fill(solution, 0);
		assertTrue(system.copy().lazyGaussianElimination(solution));
		assertTrue(system.check(solution));
	}

	@Test
	public void testImpossible() {
		final Modulo2System system = new Modulo2System(1);
		system.add(new Modulo2Equation(2, 1).add(0));
		system.add(new Modulo2Equation(1, 1).add(0));
		final long[] solution = new long[1];
		assertFalse(system.copy().gaussianElimination(solution));
		assertFalse(system.copy().lazyGaussianElimination(solution));
	}

	@Test
	public void testRedundant() {
		final Modulo2System system = new Modulo2System(1);
		system.add(new Modulo2Equation(2, 1).add(0));
		system.add(new Modulo2Equation(2, 1).add(0));
		final long[] solution = new long[1];
		assertTrue(system.copy().gaussianElimination(solution));
		assertTrue(system.check(solution));
		Arrays.fill(solution, 0);
		assertTrue(system.copy().lazyGaussianElimination(solution));
		assertTrue(system.check(solution));
	}

	@Test
	public void testSmall() {
		final Modulo2System system = new Modulo2System(11);
		system.add(new Modulo2Equation(0, 11).add(1).add(4).add(10));
		system.add(new Modulo2Equation(2, 11).add(1).add(4).add(9));
		system.add(new Modulo2Equation(0, 11).add(0).add(6).add(8));
		system.add(new Modulo2Equation(1, 11).add(0).add(6).add(9));
		system.add(new Modulo2Equation(2, 11).add(2).add(4).add(8));
		system.add(new Modulo2Equation(0, 11).add(2).add(6).add(10));
		final long[] solution = new long[11];
		assertTrue(system.copy().gaussianElimination(solution));
		assertTrue(system.check(solution));
		Arrays.fill(solution, 0);
		assertTrue(system.copy().lazyGaussianElimination(solution));
		assertTrue(system.check(solution));
	}

	@Test
	public void testRandom() {
		final XoRoShiRo128PlusRandomGenerator random = new XoRoShiRo128PlusRandomGenerator(1);
		for(final int size: new int[] { 1000 }) {
			final Modulo2System system = new Modulo2System(size);
			// Few equations
			for(int i = 0; i < 2 * size / 3; i++) system.add(new Modulo2Equation(random.nextInt(100), size).add(random.nextInt(size / 3)).add(size / 3 + random.nextInt(size / 3)).add(2 * size / 3 + random.nextInt(size / 3)));
			final long[] solution = new long[size];
			assertTrue(system.copy().gaussianElimination(solution));
			assertTrue(system.check(solution));
			Arrays.fill(solution, 0);
			assertTrue(system.copy().lazyGaussianElimination(solution));
			assertTrue(system.check(solution));
		}
	}

	@Test
	public void testRandom2() {
		final XoRoShiRo128PlusRandomGenerator random = new XoRoShiRo128PlusRandomGenerator(1);
		for(final int size: new int[] { 10, 100, 1000, 10000 }) {
			final Modulo2System system = new Modulo2System(size);


			final IntOpenHashSet edge[] = new IntOpenHashSet[size];
			int x, v, w;
			for (int i = 0; i < 2 * size / 3; i++) {
				boolean alreadySeen;
				do {
					x = random.nextInt(size);
					do v = random.nextInt(size); while(v == i);
					do w = random.nextInt(size); while(w == i || w == v);

					edge[i] = new IntOpenHashSet();
					edge[i].add(x);
					edge[i].add(v);
					edge[i].add(w);

					alreadySeen = false;
					for(int j = 0; j < i; j++)
						if (edge[j].equals(edge[i])) {
						alreadySeen = true;
						break;
					}
				} while(alreadySeen);
			}

			for(int i = 0; i < 2 * size / 3; i++) {
				final Modulo2Equation equation = new Modulo2Equation(random.nextInt(100), size);
				for(final IntIterator iterator = edge[i].iterator(); iterator.hasNext();) equation.add(iterator.nextInt());
				system.add(equation);
			}
			final long[] solution = new long[size];
			assertTrue(system.copy().gaussianElimination(solution));
			assertTrue(system.check(solution));
			Arrays.fill(solution, 0);
			assertTrue(system.copy().lazyGaussianElimination(solution));
			assertTrue(system.check(solution));
		}
	}

}
