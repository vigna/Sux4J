package it.unimi.dsi.sux4j.mph.solve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2015-2019 Sebastiano Vigna
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

import it.unimi.dsi.Util;
import it.unimi.dsi.bits.LongArrayBitVector;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Solver for linear systems on <b>F</b><sub>2</sub>.
 * Variables are <sup>k</sup>-dimensional vectors on <b>F</b><sub>2</sub>, with 0 &le; <var>k</var> &le; 64.
 *
 * @author Sebastiano Vigna
 */

public class Modulo2System {
	private final static Logger LOGGER = LoggerFactory.getLogger(Modulo2System.class);
	private final static boolean DEBUG = false;

	/** An equation on <b>F</b><sub>2</sub>. */
	public static class Modulo2Equation {
		/** The vector representing the coefficients (one bit for each variable). */
		protected final LongArrayBitVector bitVector;
		/** The {@link LongArrayBitVector#bits() bv.bits()}, cached. */
		protected final long[] bits;
		/** The constant term. */
		protected long c;
		/** The first variable. It is {@link Integer#MAX_VALUE} if the
		 * first variable is not known. This field must be updated by {@link #updateFirstVar()} to be meaningful. */
		protected int firstVar;
		/** Whether any variable appears on the left side of the equation. */
		private boolean isEmpty;

		/** Creates a new equation.
		 *
		 * @param c the constant term.
		 * @param numVars the number of variables.
		 */
		public Modulo2Equation(final long c, final int numVars){
			this.c = c;
			this.bitVector = LongArrayBitVector.ofLength(numVars);
			this.bits = bitVector.bits();
			this.firstVar = Integer.MAX_VALUE;
			this.isEmpty = true;
		}

		protected Modulo2Equation(final Modulo2Equation equation){
			this.c = equation.c;
			this.bitVector = equation.bitVector.copy();
			this.bits = this.bitVector.bits();
			this.firstVar = equation.firstVar;
			this.isEmpty = equation.isEmpty;
		}

		/** Adds a new variable.
		 *
		 * @param variable a variable.
		 * @return this equation.
		 * @throws IllegalStateException if you try to add twice the same variable.
		 */
		public Modulo2Equation add(final int variable) {
			assert ! bitVector.getBoolean(variable);
			bitVector.set(variable);
			isEmpty = false;
			return this;
		}


		/** Returns an array containing the variables in increasing order.
		 *
		 * <p>Mainly for debugging purposes.
		 *
		 * @return an array containing the variables in increasing order.
		 */
		public int[] variables() {
			final IntArrayList variables = new IntArrayList();
			for(int i = 0; i < bitVector.length(); i++) if (bitVector.getBoolean(i)) variables.add(i);
			return variables.toIntArray();
		}

		/** Add another equation to this equation.
		 *
		 * @param equation an equation.
		 */
		public void add(final Modulo2Equation equation) {
			this.c ^= equation.c;
			final long[] x = this.bits, y = equation.bits;
			long isNotEmpty = 0;
			for(int i = x.length; i-- != 0;) isNotEmpty |= (x[i] ^= y[i]);
			isEmpty = isNotEmpty == 0;
		}

		/** Updates the information contained in {@link #firstVar}. */
		public void updateFirstVar() {
			if (isEmpty) firstVar = Integer.MAX_VALUE;
			else {
				int i = -1;
				while(bits[++i] == 0);
				firstVar = i * 64 + Long.numberOfTrailingZeros(bits[i]);
			}
		}

		public boolean isUnsolvable() {
			return isEmpty && c != 0;
		}

		public boolean isIdentity() {
			return isEmpty && c == 0;
		}

		@Override
		public int hashCode() {
			return (int) HashCommon.murmurHash3(c ^ bitVector.hashCode());
		}

		@Override
		public boolean equals(final Object o) {
			if (! (o instanceof Modulo2Equation)) return false;
			final Modulo2Equation equation = (Modulo2Equation)o;
			return c == equation.c && bitVector.equals(equation.bitVector);
		}

		/** Returns the modulo-2 scalar product of the two provided bit vectors.
		 *
		 * @param bits a bit vector represented as an array of longs.
		 * @param values an array of long representing the 64-bit values associated with each variable.
		 * @return the modulo-2 scalar product of {@code x} and {code y}.
		 */
		public static long scalarProduct(final long[] bits, final long[] values) {
			long sum = 0;

			for(int i = bits.length; i-- != 0;) {
				final int offset = i * 64;
				long word = bits[i];
				while(word != 0) {
					final int lsb = Long.numberOfTrailingZeros(word);
					sum ^= values[offset + lsb];
					word &= word - 1;
				}
			}

			return sum;
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			boolean someNonZero = false;
			for(int i = 0; i < bitVector.length(); i++) {
				if (bitVector.getBoolean(i)) {
					if (someNonZero) b.append(" + ");
					someNonZero = true;
					b.append("x_").append(i);
				}
	 		}
			if (! someNonZero) b.append('0');
			return b.append(" = ").append(c).toString();
		}

		public Modulo2Equation copy() {
			return new Modulo2Equation(this);
		}
	}

	/** The number of variables. */
	private final int numVars;
	/** The equations. */
	private final ArrayList<Modulo2Equation> equations;

	public Modulo2System(final int numVars) {
		equations = new ArrayList<>();
		this.numVars = numVars;
	}

	protected Modulo2System(final int numVars , final ArrayList<Modulo2Equation> equations) {
		this.equations = equations;
		this.numVars = numVars;
	}

	public Modulo2System copy() {
		final ArrayList<Modulo2Equation> list = new ArrayList<>(equations.size());
		for(final Modulo2Equation equation: equations) list.add(equation.copy());
		return new Modulo2System(numVars, list);
	}

	/** Adds an equation to the system.
	 *
	 * @param equation an equation with the same number of variables of the system.
	 */
	public void add(final Modulo2Equation equation) {
		if (equation.bitVector.length() != numVars) throw new IllegalArgumentException("The number of variables in the equation (" + equation.bitVector.length() + ") does not match the number of variables of the system (" + numVars + ")");
		equations.add(equation);
	}

	@Override
	public String toString() {
		final StringBuilder b = new StringBuilder();
		for (int i = 0; i < equations.size(); i++) b.append(equations.get(i)).append('\n');
		return b.toString();
	}


	public boolean check(final long[] solution) {
		assert solution.length == numVars;

		for(final Modulo2Equation equation: equations)
			if (equation.c != Modulo2Equation.scalarProduct(equation.bits, solution)) return false;
		return true;
	}


	private boolean echelonForm() {
		main: for (int i = 0; i < equations.size() - 1; i++) {
			assert equations.get(i).firstVar != Integer.MAX_VALUE;

			for (int j = i + 1; j < equations.size(); j++) {
				// Note that because of exchanges we cannot extract the first assignment
				final Modulo2Equation eqJ = equations.get(j);
				final Modulo2Equation eqI = equations.get(i);

				assert eqI.firstVar != Integer.MAX_VALUE;
				assert eqJ.firstVar != Integer.MAX_VALUE;

				final int firstVar = eqI.firstVar;

				if(firstVar == eqJ.firstVar) {
					eqI.add(eqJ);
					if (eqI.isUnsolvable()) return false;
					if (eqI.isIdentity()) continue main;
					eqI.updateFirstVar();
				}

				if (eqI.firstVar > eqJ.firstVar) Collections.swap(equations, i, j);
			}
		}
		return true;
	}

	/** Solves the system using Gaussian elimination.
	 *
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public boolean gaussianElimination(final long[] solution) {
		assert solution.length == numVars;
		for (final Modulo2Equation equation: equations) equation.updateFirstVar();

		if (! echelonForm()) return false;

		for (int i = equations.size(); i-- != 0;) {
			final Modulo2Equation equation = equations.get(i);
			if (equation.isIdentity()) continue;
			assert solution[equation.firstVar] == 0 : equation.firstVar;
			solution[equation.firstVar] = equation.c ^ Modulo2Equation.scalarProduct(equation.bits, solution);
		}

		return true;
	}

	/** Solves the system using lazy Gaussian elimination.
	 *
	 * <p><strong>Warning</strong>: this method is very inefficient, as it
	 * scans linearly the equations, builds from scratch the {@code var2Eq}
	 * parameter of {@link #lazyGaussianElimination(Modulo2System, int[][], long[], int[], long[])},
	 * and finally calls it. It should be used mainly to write unit tests.
	 *
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public boolean lazyGaussianElimination(final long[] solution) {
		final int[][] var2Eq = new int[numVars][];
		final int[] d = new int[numVars];
		for(final Modulo2Equation equation: equations)
			for(int v = (int)equation.bitVector.length(); v-- != 0;)
				if (equation.bitVector.getBoolean(v)) d[v]++;

		for(int v = numVars; v-- != 0;) var2Eq[v] = new int[d[v]];
		Arrays.fill(d, 0);
		final long[] c = new long[equations.size()];
		for(int e = 0; e < equations.size(); e++) {
			c[e] = equations.get(e).c;
			final LongArrayBitVector bitVector = equations.get(e).bitVector;
			for(int v = (int)bitVector.length(); v-- != 0;)
				if (bitVector.getBoolean(v)) var2Eq[v][d[v]++] = e;
		}

		return lazyGaussianElimination(this, var2Eq, c, Util.identity(numVars), solution);
	}

	/** Solves a system using lazy Gaussian elimination.
	 *
	 * @param var2Eq an array of arrays describing, for each variable, in which equation it appears;
	 * equation indices must appear in nondecreasing order; an equation
	 * may appear several times for a given variable, in which case the final coefficient
	 * of the variable in the equation is given by the number of appearances modulo 2 (this weird format is useful
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
	 * @param system a modulo-2 system, or {@code null}, in which case the system will be rebuilt
	 * from the other variables.
	 * @param var2Eq an array of arrays describing, for each variable, in which equation it appears;
	 * equation indices must appear in nondecreasing order; an equation
	 * may appear several times for a given variable, in which case the final coefficient
	 * of the variable in the equation is given by the number of appearances modulo 2 (this weird format is useful
	 * when calling this method from a {@link Linear3SystemSolver}). The resulting system
	 * must be identical to {@code system}. Note that this array
	 * will be altered if some equation appears multiple time associated with a variable.
	 * @param c the array of known terms, one for each equation.
	 * @param variable the variables with respect to which the system should be solved
	 * (variables not appearing in this array will be simply assigned zero).
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public static boolean lazyGaussianElimination(Modulo2System system, final int var2Eq[][], final long[] c, final int[] variable, final long[] solution) {
		final int numEquations = c.length;
		if (numEquations == 0) return true;

		final int numVars = var2Eq.length;
		assert solution.length == numVars;

		final boolean buildSystem = system == null;

		if (buildSystem) {
			system = new Modulo2System(numVars);
			for(int i = 0; i < c.length; i++) system.add(new Modulo2Equation(c[i], numVars));
		}

		/* The weight of each variable, that is, the number of equations still
		 * in the queue in which the variable appears. We use zero to
		 * denote pivots of solved equations. */
		final int weight[] = new int[numVars];

		// The priority of each equation still to be examined (the number of idle variables).
		final int[] priority = new int[numEquations];

		for(final int v : variable) {
			final int[] eq = var2Eq[v];
			if (eq.length == 0) continue;

			int currEq = eq[0];
			boolean currCoeff = true;
			int j = 0;

			/* We count the number of appearances in an equation and compute
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

		// The equations that are not dense and have weight <= 1.
		final IntArrayList equationList = new IntArrayList();
		for(int i = priority.length; i-- != 0;) if (priority[i] <= 1) equationList.add(i);
		// The equations that are part of the dense system (entirely made of active variables).
		final ArrayList<Modulo2Equation> dense = new ArrayList<>();
		// The equations that define a solved variable in term of active variables.
		final ArrayList<Modulo2Equation> solved = new ArrayList<>();
		// The solved variables (parallel to solved).
		final IntArrayList pivots = new IntArrayList();

		final ArrayList<Modulo2Equation> equations = system.equations;
		// A bit vector containing a 1 in correspondence of each idle variable.
		final long[] idleNormalized = new long[equations.get(0).bits.length];
		Arrays.fill(idleNormalized, -1);

		int numActive = 0;

		for(int remaining = equations.size(); remaining != 0;) {
			if (equationList.isEmpty()) {
				// Make another variable active
				int var;
				do var = variables.popInt(); while(weight[var] == 0);
				numActive++;
				idleNormalized[var / 64] ^= 1L << (var % 64);
				if (DEBUG) System.err.println("Making variable " + var + " of weight " + weight[var] + " active (" + remaining + " equations to go)");
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
					 * is needed, as all its variables are active. */
					dense.add(equation);
				}
				else if (priority[first] == 1) {
					/* This is solved (in terms of the active variables). Let's find the pivot, that is,
					 * the only idle variable. Note that we do not need to update varEquation[] of any variable, as they
					 * are all either active (the non-pivot), or appearing only in this equation (the pivot). */
					int wordIndex = 0;
					while((equation.bits[wordIndex] & idleNormalized[wordIndex]) == 0) wordIndex++;
					final int pivot = wordIndex * 64 + Long.numberOfTrailingZeros(equation.bits[wordIndex] & idleNormalized[wordIndex]);

					// Record the idle variable and the equation for computing it later.
					if (DEBUG) System.err.println("Adding to solved variables x_" + pivot + " by equation " + equation);
					pivots.add(pivot);
					solved.add(equation);
					// This forces to skip the pivot when looking for a new variable to be made active.
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


		LOGGER.debug("Active variables: " + numActive + " (" + Util.format(numActive * 100 / numVars) + "%)");

		if (DEBUG) {
			System.err.println("Dense equations: " + dense);
			System.err.println("Solved equations: " + solved);
			System.err.println("Pivots: " + pivots);
		}

		final Modulo2System denseSystem = new Modulo2System(numVars, dense);
		if (! denseSystem.gaussianElimination(solution)) return false;  // numVars >= denseSystem.numVars

		if (DEBUG) System.err.println("Solution (dense): " + Arrays.toString(solution));

		for (int i = solved.size(); i-- != 0;) {
			final Modulo2Equation equation = solved.get(i);
			final int pivot = pivots.getInt(i);
			assert solution[pivot] == 0 : pivot;
			solution[pivot] = equation.c ^ Modulo2Equation.scalarProduct(equation.bits, solution);
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

		final Modulo2System system = new Modulo2System(numVars);
		for(int i = 0; i < c.length; i++) system.add(new Modulo2Equation(c[i], numVars));

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
