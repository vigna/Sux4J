/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2016-2020 Sebastiano Vigna
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

import java.util.Arrays;

import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Commodity class implementing the <em>selfless algorithm</em> for the orientation of a 3-hypergraph.
 * The algorithm has been described by Martin Dietzfelbinger, Andreas Goerdt, Michael Mitzenmacher, Andrea Montanari, Rasmus Pagh, and Michael Rink
 * in &ldquo;Tight thresholds for cuckoo hashing via XORSAT&rdquo;, <i>Automata, Languages
 * and Programming</i>, Lecture Notes in Computer Science, vol. 6198, pp. 213&minus;225, Springer (2010).
 *
 * <p>Note that the method {@link #orient(int[][], int[], int[], int[], int[], int[]) orient()} is tailored to the needs
 * of {@link Linear3SystemSolver}.
 */

public class Orient3Hypergraph {
	private static final boolean ASSERTS = false;

	private Orient3Hypergraph() {}

	private static void remove(final IntArrayList queue, final int v, final int[] posInQueue) {
		final int position = posInQueue[v];
		assert position < queue.size();
		final int last = queue.popInt();
		if (position == queue.size()) return;
		queue.set(posInQueue[last] = position, last);
	}

	private static void move(final IntArrayList[] queue, final int[] posInQueue, final int v, final int queueBefore, final int queueAfter) {
		if (queueBefore != queueAfter) {
			remove(queue[queueBefore], v, posInQueue);
			posInQueue[v] = queue[queueAfter].size();
			queue[queueAfter].push(v);
		}
	}

	private static void decrease(final IntArrayList[] queue , final int[] posInQueue , final int[] priority , final int[] d , final int v , final int w) {
		assert priority[v] > 0 || d[v] == 0;
		final int queueBefore = Math.min(7, priority[v]);
		if (d[v] == 0) remove(queue[queueBefore], v, posInQueue);
		else {
			if (d[v] == 1) priority[v] = 0;
			else priority[v] -= 6 / w;
			final int queueAfter = Math.min(7, priority[v]);
			move(queue, posInQueue, v, queueBefore, queueAfter);
		}
	}

	private static void increase(final IntArrayList[] queue, final int[] posInQueue, final int[] priority, final int v, final int update) {
		assert update > 0;
		final int queueBefore = Math.min(7, priority[v]);
		priority[v] += update;
		assert priority[v] > 0;
		final int queueAfter = Math.min(7, priority[v]);
		move(queue, posInQueue, v, queueBefore, queueAfter);
	}

	/** Orients the edges of a 3-hypergraph.
	 *
	 * @param edges the edge array (an array of vertices for each hyperedge).
	 * @param d the degree array.
	 * @param vertex0 the first vertex of each edge.
	 * @param vertex1 the second vertex of each edge.
	 * @param vertex2 the third vertex of each edge.
	 * @param hinges the vector where hinges will be stored.
	 *
	 * @return true if direction was successful.
	 */
	public static boolean orient(final int[][] edges , final int[] d , final int[] vertex0 , final int[] vertex1 , final int[] vertex2 , final int[] hinges) {
		final int numVertices = d.length;
		final int numEdges = vertex0.length;
		final int[] weight = new int[numEdges];
		final boolean[] isHinge = new boolean[numVertices];
		final boolean[] isDone = new boolean[numEdges];
		Arrays.fill(weight, 3);

		if (ASSERTS) {
			final int[] testd = new int[d.length];
			for (int i = 0; i < numEdges; i++) {
				testd[vertex0[i]]++;
				testd[vertex1[i]]++;
				testd[vertex2[i]]++;
			}
			if (! Arrays.equals(testd, d)) throw new AssertionError("Degree array not valid: " + Arrays.toString(testd) + " != " + Arrays.toString(d));
		}

		/* - queues of index 0,1,..,6 contains vertices with that priority.
		 * - the queue of index 7 contains all vertices with priority > 6. */
		final IntArrayList[] queue = new IntArrayList[8];
		for(int i = queue.length; i-- != 0;) queue[i] = new IntArrayList();
		// For each vertex, its position in the queue it lives in.
		final int[] posInQueue = new int[numVertices];

		// Priorities, multiplied by 6 (so they are all integers)
		final int[] priority = new int[numVertices];
		for (int i = 0; i < numVertices; i++) priority[i] += 2 * d[i];

		for (int i = 0; i < d.length; i++) if (d[i] > 0) {
			final IntArrayList q = queue[Math.min(7, priority[i])];
			posInQueue[i] = q.size();
			q.add(i);
		}

		for (int t = 0; t < numEdges; t++) {
			// Find hinge by looking at the node with minimum priority
			int minPriority = 0;
			while(minPriority < 8 && queue[minPriority].isEmpty()) minPriority++;
			if (minPriority == 8) return false;
			final int hinge = queue[minPriority].popInt();
			int edge = -1;
			int minWeight = Integer.MAX_VALUE;
			for(int i = edges[hinge].length; i-- != 0;) {
				final int e = edges[hinge][i];
				if (! isDone[e] && weight[e] < minWeight) {
					edge = e;
					minWeight = weight[e];
				}
			}

			assert edge != -1;

			if (ASSERTS) {
				int minEdgeWeight = Integer.MAX_VALUE;
				int minEdge = -1;
				for(int i = 0; i < numEdges; i++) if (! isDone[i]) {
					if (vertex0[i] == hinge || vertex1[i] == hinge || vertex2[i] == hinge) {
						if (weight[i] < minEdgeWeight) {
							minEdgeWeight = weight[i];
							minEdge = i;
						}
					}

				}
				if (weight[edge] != weight[minEdge]) throw new AssertionError("Min edge " + t + ": " + minEdge + " != " + edge);
			}


			if (priority[hinge] > 6) return false;
			hinges[edge] = hinge;
			isHinge[hinge] = true;
			isDone[edge] = true;

			for(int i = edges[hinge].length; i-- != 0;) {
				final int e = edges[hinge][i];
				if (isDone[e]) continue;
				final int v0 = vertex0[e];
				final int v1 = vertex1[e];
				final int v2 = vertex2[e];
				assert hinge == v0 || hinge == v1 || hinge == v2 : hinge + " != " + v0 + ", " + v1 + ", " + v2;

				final int update = -6 / weight[e] + 6 / --weight[e];

				if (! isHinge[v0]) increase(queue, posInQueue, priority, v0, update);
				if (! isHinge[v1]) increase(queue, posInQueue, priority, v1, update);
				if (! isHinge[v2]) increase(queue, posInQueue, priority, v2, update);
			}

			final int v0 = vertex0[edge];
			final int v1 = vertex1[edge];
			final int v2 = vertex2[edge];

			assert hinge == v0 || hinge == v1 || hinge == v2 : hinge + " != " + v0 + ", " + v1 + ", " + v2;

			d[v0]--;
			if (! isHinge[v0]) decrease(queue, posInQueue, priority, d, v0, weight[edge]);

			d[v1]--;
			if (! isHinge[v1]) decrease(queue, posInQueue, priority, d, v1, weight[edge]);

			d[v2]--;
			if (! isHinge[v2]) decrease(queue, posInQueue, priority, d, v2, weight[edge]);

			if (ASSERTS) {
				final double[] pri = new double[numVertices];
				for(int i = 0; i < numEdges; i++) if (! isDone[i]) {
					int w = 0;
					if (! isHinge[vertex0[i]]) w++;
					if (! isHinge[vertex1[i]]) w++;
					if (! isHinge[vertex2[i]]) w++;
					pri[vertex0[i]] += 6 / w;
					pri[vertex1[i]] += 6 / w;
					pri[vertex2[i]] += 6 / w;
					if (weight[i] != w) throw new AssertionError("Edge " + i + ": " + w + " != " + weight[i]);
				}
				for(int i = 0; i < numVertices; i++) if (! isHinge[i] && d[i] > 1 && pri[i] != priority[i]) throw new AssertionError("Vertex " + i + ": " + pri[i] + " != " + priority[i]);
			}
		}
		return true;
	}
}
