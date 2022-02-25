/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2015-2022 Sebastiano Vigna
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

package it.unimi.dsi.sux4j.mph.solve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import it.unimi.dsi.Util;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntListIterator;

public class Modulo2SparseSystem {

	public static class Modulo2Equation {
		/** The variables. */
		public final IntArrayList variables;
		/** The constant term. */
		public int c;

		/** Creates a new equation.
		 *
		 * @param c the constant term.
		 */
		public Modulo2Equation(final int c) {
			variables = new IntArrayList();
			this.c = c;
		}

		public Modulo2Equation add(final int variable){
			if (! variables.isEmpty() && variable < variables.getInt(variables.size() - 1)) throw new IllegalArgumentException();
			variables.add(variable);
			return this;
		}

		protected Modulo2Equation(final Modulo2Equation equation) {
			this.variables = equation.variables.clone();
			this.c = equation.c;
		}

		public void eliminate(final Modulo2Equation equation) {
			assert this.variables.getInt(0) == equation.variables.getInt(0) : this.variables.getInt(0) + " != " + equation.variables.getInt(0);
			add(equation);
		}

		public int firstVar() {
			return variables.size() != 0 ? variables.getInt(0) : Integer.MAX_VALUE;
		}

		/** Adds the provided equation to this equation.
		 *
		 * @param equation an equation.
		 */

		public void add(final Modulo2Equation equation) {
			int i = 0, j = 0, k = 0;
			final int s = variables.size(), t = equation.variables.size();
			final int[] a = variables.elements(), b = equation.variables.elements(), result = new int[s + t];

			if (t != 0 && s != 0) {
				for (;;) {
					if (a[i] < b[j]) {
						result[k++]  = a[i++];
						if (i == s) break;
					}
					else if (a[i] > b[j]) {
						result[k++]  = b[j++];
						if (j == t) break;
					}
					else {
						i++;
						j++;
						if (i == s) break;
						if (j == t) break;
					}
				}
			}

			while (i < s) result[k++] = a[i++];
			while (j < t) result[k++] = b[j++];

			c ^= equation.c;
			variables.size(k);
			System.arraycopy(result, 0, variables.elements(), 0, k);
		}

		public boolean isUnsolvable() {
			return variables.isEmpty() && c != 0;
		}

		public boolean isIdentity() {
			return variables.isEmpty() && c == 0;
		}

		public Modulo2Equation copy() {
			return new Modulo2Equation(this);
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			boolean someNonZero = false;
			for(final IntIterator iterator = variables.iterator(); iterator.hasNext();) {
				final int v = iterator.nextInt();
				if (someNonZero) b.append(" + ");
				someNonZero = true;
				b.append("x_").append(v);
	 		}
			if (! someNonZero) b.append('0');
			return b.append(" = ").append(c).toString();
		}

		public static long scalarProduct(final Modulo2Equation e, final long[] solution) {
			long sum = 0;
			for(final IntListIterator iterator = e.variables.iterator(); iterator.hasNext();)
				sum ^= solution[iterator.nextInt()];
			return sum;
		}

	}

	private static final boolean DEBUG = false;

	private final ArrayList<Modulo2Equation> equations;
	private final int numVars;

	public Modulo2SparseSystem(final int numVars) {
		this.numVars = numVars;
		equations = new ArrayList<>();
	}

	protected Modulo2SparseSystem(final int numVars, final ArrayList<Modulo2Equation> system) {
		this.numVars = numVars;
		this.equations = system;
	}

	public void print() {
		for (final Modulo2Equation equation : equations) System.out.println(equation);
	}

	public void add(final Modulo2Equation equation) {
		equations.add(equation);
	}


	public boolean solve(final int[] solution) {
		if (! echelonForm()) return false;
		for (int i = equations.size(); i-- != 0;) {
			final Modulo2Equation equation = equations.get(i);
			if (equation.variables.isEmpty()) continue;

			int count = equation.variables.size();
			final int c = equation.c;
			final int size = equation.variables.size();
			for(int j = size / 2; j-- != 0;)
				Collections.swap(equation.variables, j, size - j - 1);
			final IntListIterator iterator = equation.variables.iterator();
			while(iterator.hasNext()) {
				final int e = iterator.nextInt();
				if (count == 1) {
					assert solution[e] == 0;
					solution[e] = c;
				}
				count--;
			}
		}

		return true;
	}


	public int size() {
		return equations.size();
	}

	public Modulo2SparseSystem copy() {
		final Modulo2SparseSystem s = new Modulo2SparseSystem(numVars);
		for (final Modulo2Equation e: equations) s.add(e.copy());
		return s;
	}

	public boolean check(final long solution[]) {
		for(final Modulo2Equation equation: equations) {
			int sum = 0;
			for(final IntListIterator i = equation.variables.iterator(); i.hasNext();) {
				final int e = i.nextInt();
				sum ^= solution[e];
			}
			if (equation.c != sum) {
				System.err.println(equation + " " + Arrays.toString(solution));
				return false;
			}
		}
		return true;
	}


	private boolean echelonForm() {
		main: for (int i = 0; i < equations.size() - 1; i++) {
			assert equations.get(i).firstVar() != Integer.MAX_VALUE;

			for (int j = i + 1; j < equations.size(); j++) {
				// Note that because of exchanges we cannot extract the first assignment
				final Modulo2Equation eqJ = equations.get(j);
				final Modulo2Equation eqI = equations.get(i);

				assert eqI.firstVar() != Integer.MAX_VALUE;
				assert eqJ.firstVar() != Integer.MAX_VALUE;

				final int firstVar = eqI.firstVar();

				if(firstVar == eqJ.firstVar()) {
					eqI.add(eqJ);
					if (eqI.isUnsolvable()) return false;
					if (eqI.isIdentity()) continue main;
				}

				if (eqI.firstVar() > eqJ.firstVar()) Collections.swap(equations, i, j);
			}
		}
		return true;
	}

	public boolean gaussianElimination(final long[] solution) {
		assert solution.length == numVars;

		if (! echelonForm()) return false;

		for (int i = equations.size(); i-- != 0;) {
			final Modulo2Equation equation = equations.get(i);
			if (equation.isIdentity()) continue;

			assert solution[equation.firstVar()] == 0 : equation.firstVar();

			solution[equation.firstVar()] = equation.c ^ Modulo2Equation.scalarProduct(equation, solution);
		}

		return true;
	}

	/** Solves the system using lazy Gaussian elimination.
	 *
	 * <p><strong>Warning</strong>: this method is very inefficient, as it
	 * scans linearly the equations, builds from scratch the {@code var2Eq}
	 * parameter of {@link #lazyGaussianElimination(Modulo2SparseSystem, int[][], long[], int[], long[])},
	 * and finally calls it. It should be used mainly to write unit tests.
	 *
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public boolean lazyGaussianElimination(final long[] solution) {
		final int[][] var2Eq = new int[numVars][];
		final int[] d = new int[numVars];
		for(final Modulo2Equation equation: equations)
			for(final IntListIterator iterator = equation.variables.iterator(); iterator.hasNext();)
				d[iterator.nextInt()]++;

		for(int v = numVars; v-- != 0;) var2Eq[v] = new int[d[v]];
		Arrays.fill(d, 0);
		final long[] c = new long[equations.size()];
		for(int e = 0; e < equations.size(); e++) {
			c[e] = equations.get(e).c;
			for(final IntListIterator iterator = equations.get(e).variables.iterator(); iterator.hasNext();) {
				final int v = iterator.nextInt();
				var2Eq[v][d[v]++] = e;
			}
		}

		return lazyGaussianElimination(this, var2Eq, c, Util.identity(numVars), solution);
	}

	/** Solves a system using lazy Gaussian elimination.
	 *
	 * @param var2Eq an array of arrays describing, for each variable, in which equation it appears;
	 * equation indices must appear in nondecreasing order; an equation
	 * may appear several times for a given variable, in which case the final coefficient
	 * of the variable in the equation is given by the number of appearances modulo 3 (this weird format is useful
	 * when calling this method from a {@link Linear3SystemSolver}). Note that this array
	 * will be altered if some equation appears multiple time associated with a variable.
	 * @param c the array of known terms, one for each equation.
	 * @param variable the variables with respect to which the system should be solved
	 * (variables not appearing in this array will be simply assigned zero).
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public static boolean lazyGaussianElimination(final int var2Eq[][], final long[] c, final int[] variable, final long[] solution) {
		return lazyGaussianElimination(null, var2Eq, c, variable, solution);
	}

	/** Solves a system using lazy Gaussian elimination.
	 *
	 * @param system a modulo-3 system.
	 * @param var2Eq an array of arrays describing, for each variable, in which equation it appears;
	 * equation indices must appear in nondecreasing order; an equation
	 * may appear several times for a given variable, in which case the final coefficient
	 * of the variable in the equation is given by the number of appearances modulo 3 (this weird format is useful
	 * when calling this method from a {@link Linear3SystemSolver}). The resulting system
	 * must be identical to {@code system}. Note that this array
	 * will be altered if some equation appears multiple time associated with a variable.
	 * @param c the array of known terms, one for each equation.
	 * @param variable the variables with respect to which the system should be solved
	 * (variables not appearing in this array will be simply assigned zero).
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public static boolean lazyGaussianElimination(Modulo2SparseSystem system, final int var2Eq[][], final long[] c, final int[] variable, final long[] solution) {
		final int numEquations = c.length;
		if (numEquations == 0) return true;

		final int numVars = var2Eq.length;
		assert solution.length == numVars;

		final boolean buildSystem = system == null;

		if (buildSystem) {
			system = new Modulo2SparseSystem(numVars);
			for (final long element : c) system.add(new Modulo2Equation((int)element));
		}

		/* The weight of each variable, that is, the number of equations still
		 * in the queue in which the variable appears. We use zero to
		 * denote pivots of solved equations. */
		final int weight[] = new int[numVars];

		// The priority of each equation still to be examined (the number of light variables).
		final int[] priority = new int[numEquations];

		for(final int v : variable) {
			final int[] eq = var2Eq[v];
			if (eq.length == 0) continue;

			int currEq = eq[0];
			boolean currCoeff = true;
			int j = 0;

			/** We count the number of appearances in an equation and compute
			 * the correct coefficient (which might be zero). If there are
			 * several appearances of the same equation, we compact the array
			 * and, in the end, replace it with a shorter one. */
			for(int i = 1; i < eq.length; i++) {
				if (eq[i] != currEq) {
					assert eq[i] > currEq;
					if (currCoeff) {
						if (buildSystem) system.equations.get(currEq).add(v);
						weight[v]++;
						priority[currEq]++;
						eq[j++] = currEq;
					}
					currEq = eq[i];
					currCoeff = true;
				}
				else currCoeff = ! currCoeff;
			}

			if (currCoeff) {
				if (buildSystem) system.equations.get(currEq).add(v);
				weight[v]++;
				priority[currEq]++;
				eq[j++] = currEq;
			}

			// In case we found duplicates, we replace the array with a uniquified one.
			if (j != eq.length) var2Eq[v] = Arrays.copyOf(var2Eq[v], j);
		}

		if (DEBUG) {
			System.err.println();
			System.err.println("===== Going to solve... ======");
			System.err.println();
			System.err.println(system);
		}


		// All variables in a stack returning heavier variables first.
		final IntArrayList variables;
		{
			final int[] t = Util.identity(numVars);
			final int[] u = new int[t.length];
			final int[] count = new int[numEquations + 1]; // CountSort
			for(int i = t.length; i-- != 0;) count[weight[t[i]]]++;
			for(int i = 1; i < count.length; i++) count[i] += count[i - 1];
			for(int i = t.length; i-- != 0;) u[--count[weight[t[i]]]] = t[i];
			variables = IntArrayList.wrap(u);
		}

		// The equations that are neither dense, nor solved, and have weight <= 1.
		final IntArrayList equationList = new IntArrayList();
		for(int i = priority.length; i-- != 0;) if (priority[i] <= 1) equationList.add(i);
		// The equations that are part of the dense system (entirely made of heavy variables).
		final ArrayList<Modulo2Equation> dense = new ArrayList<>();
		// The equations that define a light variable in term of heavy variables.
		final ArrayList<Modulo2Equation> solved = new ArrayList<>();
		// The light variable corresponding to each solved equation.
		final IntArrayList pivots = new IntArrayList();

		final ArrayList<Modulo2Equation> equations = system.equations;
		// A bit vector containing a 1 in correspondence of each light variable.
		final boolean[] idle = new boolean[numVars];
		Arrays.fill(idle, true);

		for(int remaining = equations.size(); remaining != 0;) {
			if (equationList.isEmpty()) {
				// Make another variable heavy
				int var;
				do var = variables.popInt(); while(weight[var] == 0);
				idle[var] = false;
				if (DEBUG) System.err.println("Making variable " + var + " of weight " + weight[var] + " heavy (" + remaining + " equations to go)");
				for(final int equationIndex: var2Eq[var])
					if (--priority[equationIndex] == 1) equationList.push(equationIndex);
			}
			else {
				remaining--;
				final int first = equationList.popInt(); // An equation of weight 0 or 1.
				final Modulo2Equation equation = equations.get(first);
				if (DEBUG) System.err.println("Looking at equation " + first + " of priority " + priority[first] + " : " + equation);

				if (priority[first] == 0) {
					if (equation.isUnsolvable()) return false;
					if (equation.isIdentity()) continue;
					/* This equation must be necessarily solved by standard Gaussian elimination. No updated
					 * is needed, as all its variables are heavy. */
					dense.add(equation);
				}
				else if (priority[first] == 1) {
					/* This is solved (in terms of the heavy variables). Let's find the pivot, that is,
					 * the only idle variable. Note that we do not need to update varEquation[] of any variable, as they
					 * are all either heavy (the non-pivot), or appearing only in this equation (the pivot). */
					int pivot = -1;
					for(final IntIterator iterator = equation.variables.iterator(); iterator.hasNext();)
						if (idle[pivot = iterator.nextInt()]) break;

					// Record the light variable and the equation for computing it later.
					if (DEBUG) System.err.println("Adding to solved variables x_" + pivot + " by equation " + equation);
					pivots.add(pivot);
					solved.add(equation);
					// This forces to skip the pivot when looking for a new variable to be made heavy.
					weight[pivot] = 0;

					// Now we need to eliminate the variable from all other equations containing it.
					for(final int equationIndex: var2Eq[pivot]) {
						if (equationIndex == first) continue;
						if (--priority[equationIndex] == 1) equationList.add(equationIndex);
						if (DEBUG) System.err.print("Replacing equation (" + equationIndex + ") " + equations.get(equationIndex) + " with ");
						equations.get(equationIndex).add(equation);
						if (DEBUG) System.err.println(equations.get(equationIndex));
					}
				}
			}
		}

		final Modulo2SparseSystem denseSystem = new Modulo2SparseSystem(numVars, dense);
		if (! denseSystem.gaussianElimination(solution)) return false;  // numVars >= denseSystem.numVars

		if (DEBUG) System.err.println("Solution (dense): " + Arrays.toString(solution));

		for (int i = solved.size(); i-- != 0;) {
			final Modulo2Equation equation = solved.get(i);
			final int pivot = pivots.getInt(i);
			assert solution[pivot] == 0 : pivot;
			solution[pivot] = equation.c ^ Modulo2Equation.scalarProduct(equation, solution);
		}

		if (DEBUG) System.err.println("Solution (all): " + Arrays.toString(solution));

		return true;
	}

	/* Temporary method to test speedup due to lazy Gaussian elimination. */
	public static boolean gaussianElimination(final int var2Eq[][], final long[] c, final int[] variable, final long[] solution) {
		final int numEquations = c.length;
		if (numEquations == 0) return true;

		final int numVars = var2Eq.length;
		assert solution.length == numVars;

		final Modulo2SparseSystem system = new Modulo2SparseSystem(numVars);
		for (final long element : c) system.add(new Modulo2Equation((int)element));

		for(final int v : variable) {
			final int[] eq = var2Eq[v];
			if (eq.length == 0) continue;

			int currEq = eq[0];
			boolean currCoeff = true;
			int j = 0;

			/** We count the number of appearances in an equation and compute
			 * the correct coefficient (which might be zero). If there are
			 * several appearances of the same equation, we compact the array
			 * and, in the end, replace it with a shorter one. */
			for(int i = 1; i < eq.length; i++) {
				if (eq[i] != currEq) {
					assert eq[i] > currEq;
					if (currCoeff) {
						system.equations.get(currEq).add(v);
						eq[j++] = currEq;
					}
					currEq = eq[i];
					currCoeff = true;
				}
				else currCoeff = ! currCoeff;
			}

			if (currCoeff) {
				system.equations.get(currEq).add(v);
				eq[j++] = currEq;
			}

			// In case we found duplicates, we replace the array with a uniquified one.
			if (j != eq.length) var2Eq[v] = Arrays.copyOf(var2Eq[v], j);
		}

		if (! system.gaussianElimination(solution)) return false;  // numVars >= denseSystem.numVars

		return true;
	}
}
