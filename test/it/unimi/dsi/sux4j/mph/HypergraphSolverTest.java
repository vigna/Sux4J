/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2010-2020 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.mph;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.sux4j.mph.solve.Orient3Hypergraph;
import it.unimi.dsi.util.XoRoShiRo128PlusRandomGenerator;


public class HypergraphSolverTest {

	public static int[][] vertex2Edge(final int numVars, final int[] vertex0, final int[] vertex1, final int[] vertex2) {
		final int[][] vertex2Edge = new int[numVars][];
		final int[] d = new int[numVars];
		for(int i = vertex0.length; i-- != 0;) {
			d[vertex0[i]]++;
			d[vertex1[i]]++;
			d[vertex2[i]]++;
		}

		for(int v = numVars; v-- != 0;) vertex2Edge[v] = new int[d[v]];
		Arrays.fill(d, 0);
		for(int i = vertex0.length; i-- != 0;) {
			vertex2Edge[vertex0[i]][d[vertex0[i]]++] = i;
			vertex2Edge[vertex1[i]][d[vertex1[i]]++] = i;
			vertex2Edge[vertex2[i]][d[vertex2[i]]++] = i;
		}

		return vertex2Edge;
	}

	@Test
	public void smallTest() {
		final int[] vertex0 = { 0, 1, 2, 3 };
		final int[] vertex1 = { 1, 2, 0, 1 };
		final int[] vertex2 = { 2, 3, 4, 0 };
		final int[] d = { 3, 3, 3, 2, 1 };
		final int[] hinges = new int[vertex1.length];
		assertTrue(Orient3Hypergraph.orient(vertex2Edge(5, vertex0, vertex1, vertex2), d, vertex0, vertex1, vertex2, hinges));
	}

	@Test
	public void randomTest() {
		final XoRoShiRo128PlusRandomGenerator random = new XoRoShiRo128PlusRandomGenerator(1);
		for(final int n : new int[] { 5, 10, 100, 1000 }) {
			for(int count = 0; count < 10; count++) {
				final int size = (int)(.9 * n);
				final int[] d = new int[n];
				final int[] vertex0 = new int[size];
				final int[] vertex1 = new int[size];
				final int[] vertex2 = new int[size];
				final int[] hinges = new int[size];
				final IntOpenHashSet edge[] = new IntOpenHashSet[size];

				int v, w;
				for (int i = 0; i < size; i++) {
					boolean alreadySeen;
					do {
						vertex0[i] = i;

						do v = random.nextInt(n); while(v == i);
						vertex1[i] = v;

						do w = random.nextInt(n); while(w == i || w == v);
						vertex2[i] = w;

						edge[i] = new IntOpenHashSet();
						edge[i].add(i);
						edge[i].add(v);
						edge[i].add(w);

						alreadySeen = false;
						for(int j = 0; j < i; j++)
							if (edge[j].equals(edge[i])) {
							alreadySeen = true;
							break;
						}
					} while(alreadySeen);

					d[i]++;
					d[v]++;
					d[w]++;
				}

				assertTrue("size: " + n + ", count: " + count, Orient3Hypergraph.orient(vertex2Edge(d.length, vertex0, vertex1, vertex2), d, vertex0, vertex1, vertex2, hinges));
			}
		}
	}
}
