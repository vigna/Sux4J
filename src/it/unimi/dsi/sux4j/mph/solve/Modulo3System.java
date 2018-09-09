package it.unimi.dsi.sux4j.mph.solve;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Sux4J: Succinct data structures for Java
 *
 * Copyright (C) 2015-2017 Sebastiano Vigna
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
import it.unimi.dsi.fastutil.longs.LongBigList;

/** Solver for linear systems on <b>F</b><sub>3</sub>.
 *
 * @author Sebastiano Vigna
 */

public class Modulo3System {
	private final static Logger LOGGER = LoggerFactory.getLogger(Modulo3System.class);
		private final static boolean DEBUG = false;

	/** An equation on <b>F</b><sub>3</sub>. */
	protected static class Modulo3Equation {
		/** The vector representing the coefficients (two bits for each variable). */
		protected final LongArrayBitVector bitVector;
		/** The {@link LongArrayBitVector#bits() bv.bits()}, cached. */
		protected final long[] bits;
		/** A {@linkplain LongArrayBitVector#asLongBigList(int) 2-bit list view} of {@link #bitVector}, cached. */
		protected final LongBigList list;
		/** The constant term. */
		protected long c;
		/** The first variable. It is {@link Integer#MAX_VALUE} if the
		 * first variable is not known. This field must be updated by {@link #updateFirstVar()} to be meaningful. */
		protected int firstVar;
		/** The first coefficient. This field must be updated by {@link #updateFirstVar()} to be meaningful. */
		protected int firstCoeff;
		/** Whether any variable appears on the left side of the equation. */
		private boolean isEmpty;

		/** Creates a new equation.
		 *
		 * @param c the constant term.
		 * @param numVars the number of variables.
		 */
		public Modulo3Equation(final long c, final int numVars) {
			this.c = c;
			this.bitVector = LongArrayBitVector.ofLength(numVars * 2);
			this.bits = bitVector.bits();
			this.list = bitVector.asLongBigList(2);
			this.firstVar = Integer.MAX_VALUE;
			this.isEmpty = true;
		}

		protected Modulo3Equation(final Modulo3Equation equation){
			this.c = equation.c;
			this.bitVector = equation.bitVector.copy();
			this.bits = this.bitVector.bits();
			this.list = this.bitVector.asLongBigList(2);
			this.firstVar = equation.firstVar;
			this.firstCoeff = equation.firstCoeff;
			this.isEmpty = equation.isEmpty;
		}

		/** Adds a new variable with given coefficient.
		 *
		 * @param variable a variable.
		 * @param coefficient its coefficient.
		 * @return this equation.
		 * @throws IllegalStateException if you try to add twice the same variable.
		 */
		public Modulo3Equation add(final int variable, final int coefficient) {
			assert coefficient % 3 != 0 : coefficient;
			if (list.set(variable, coefficient) != 0) throw new IllegalStateException();
			isEmpty = false;
			return this;
		}

		/** Adds a new variable with coefficient equal to one.
		 *
		 * @param variable a variable.
		 * @return this equation.
		 */
		public Modulo3Equation add(final int variable) {
			return add(variable, 1);
		}

		/** Returns an array containing the variables in increasing order.
		 *
		 * <p>Mainly for debugging purposes.
		 *
		 * @return an array containing the variables in increasing order.
		 * @see #coefficients()
		 */
		public int[] variables() {
			final IntArrayList variables = new IntArrayList();
			for(int i = 0; i < list.size64(); i++) if (list.getLong(i) != 0) variables.add(i);
			return variables.toIntArray();
		}

		/** Returns an array containing the coefficients in variable increasing order.
		 *
		 * <p>Mainly for debugging purposes.
		 *
		 * @return an array, parallel to that returned by {@link #variables()}, containing the coefficients in variable increasing order.
		 * @see #variables()
		 */
		public int[] coefficients() {
			final IntArrayList coefficients = new IntArrayList();
			for(int i = 0, c; i < list.size64(); i++) if ((c = (int)list.getLong(i)) != 0) coefficients.add(c);
			return coefficients.toIntArray();
		}

		/** Eliminates the given variable from this equation, using the provided equation, by subtracting it multiplied by a suitable constant.
		 *
		 * @param var a variable.
		 * @param equation an equation in which {@code var} appears.
		 *
		 * @return this equation.
		 */
		public Modulo3Equation eliminate(final int var , final Modulo3Equation equation) {
			assert this.list.getLong(var) != 0;
			assert equation.list.getLong(var) != 0;
			final int mul = this.list.getLong(var) == equation.list.getLong(var) ? 1 : 2;
			sub(equation, mul);
			return this;
		}

		/** Subtract from this equation another equation multiplied by a provided constant.
		 *
		 * @param equation the subtrahend.
		 * @param mul a multiplier that will be applied to the subtrahend.
		 */
		public void sub(final Modulo3Equation equation, final int mul) {
			if (mul == 1) {
				c = (c + 2 * equation.c) % 3;
				subMod3(equation.bits);
			}
			else {
				c = (c + equation.c) % 3;
				addMod3(equation.bits);
			}
		}

		/** Adds two 64-bit words made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 *
		 * @param x a 64-bit word made of modulo-3 2-bit fields.
		 * @param y a 64-bit word made of modulo-3 2-bit fields.
		 * @return the field-by-field mod 3 sum of {@code x} and {@code y}.
		 */
		protected final static long addMod3(final long x, final long y) {
		    final long xy = x | y;
		    // x or y == 2 && x or y == 1
		    long mask = (xy << 1) & xy;
		    // x == 2 && y = 2
		    mask |= x & y;
		    mask &= 0x5555555555555555L << 1;
		    mask |= mask >>> 1;
		    return x + y - mask;
	    }

		/** Subtracts two 64-bit words made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 *
		 * @param x a 64-bit word made of modulo-3 2-bit fields.
		 * @param y a 64-bit word made of modulo-3 2-bit fields.
		 * @return the field-by-field mod 3 difference of {@code x} and {@code y}.
		 */
		protected final static long subMod3(final long x, long y) {
		    // y = 3 - y
		    y = y ^ 0xFFFFFFFFFFFFFFFFL; // now y > 0
		    // x == 2
		    long mask = x;
		    // (x == 2 && y == 2, 3) || y == 3;
		    mask |= ((x | y) << 1) & y;
		    mask &= 0x5555555555555555L << 1;
		    mask |= mask >>> 1;
		    return x + y - mask;
		}


		/** Adds to the left side of this equation a bit vectors made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 *
		 * @param y a bit vector made of modulo-3 2-bit fields.
		 */
		private final void addMod3(final long[] y) {
			final long[] x = this.bits;
			long isNotEmpty = 0;
			for(int i = x.length; i-- != 0;)
				isNotEmpty |= (x[i] = addMod3(x[i], y[i]));
			isEmpty = isNotEmpty == 0;
		}

		/** Subtracts from the left side of this equation a bit vectors made of 2-bit fields containing 00, 01 or 10, interpreted as values mod 3.
		 *
		 * @param y a bit vector made of modulo-3 2-bit fields.
		 */
		private final void subMod3(final long[] y) {
			final long[] x = this.bits;
			long isNotEmpty = 0;
			for(int i = x.length; i-- != 0;)
				isNotEmpty |= (x[i] = subMod3(x[i], y[i]));
			isEmpty = isNotEmpty == 0;
		}

		/** Updates the information contained in {@link #firstVar} and {@link #firstCoeff}. */
		public void updateFirstVar() {
			if (isEmpty) firstVar = Integer.MAX_VALUE;
			else {
				int i = -1;
				while(bits[++i] == 0);
				final int lsb = Long.numberOfTrailingZeros(bits[i]) / 2;
				firstVar = lsb + 32 * i;
				firstCoeff = (int)(bits[i] >> lsb * 2 & 3);
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
			return (int)HashCommon.murmurHash3(c ^ bitVector.hashCode());
		}

		@Override
		public boolean equals(final Object o) {
			if (! (o instanceof Modulo3Equation)) return false;
			final Modulo3Equation equation = (Modulo3Equation)o;
			return c == equation.c && bitVector.equals(equation.bitVector);
		}

		/** Writes in the provided array a normalized (all coefficients turned into ones) version of the {@linkplain #bits bit vector representing the equation}.
		 *
		 * @param result an array where the result will be stored; must be long at least as {@link #bits}.
		 */
		public void normalized(final long[] result) {
			final long[] bits = this.bits;
			// Turn all nonzero coefficients into 1
			for(int i = bits.length; i-- != 0;) result[i] = (bits[i] & 0x5555555555555555L) | (bits[i] & 0xAAAAAAAAAAAAAAAAL) >>> 1;
		}

		/** Returns the modulo-3 scalar product of the two provided bit vectors.
		 *
		 * <p>This implementation was suggested by Djamal Belazzougui.
		 *
		 * @param x a bit vector represented as an array of longs.
		 * @param y a bit vector represented as an array of longs.
		 * @return the modulo-3 scalar product of {@code x} and {code y}.
		 */
		public static int scalarProduct(final long[] x, final long[] y) {
			int sum = 0;

			for (int i = y.length; i-- != 0;) {
				final long z = x[i] | y[i];
				final long w = (z | z >> 1) & 0x5555555555555555L;
				final long t = x[i] ^ y[i] ^ w;
				sum += Long.bitCount(t & 0xAAAAAAAAAAAAAAAAL) * 2 + Long.bitCount(t & 0x5555555555555555L);
			}
			return sum;
		}

		@Override
		public String toString() {
			final StringBuilder b = new StringBuilder();
			boolean someNonZero = false;
			for(int i = 0; i < list.size64(); i++) {
				final long coeff = list.getLong(i);
				if (coeff != 0) {
					if (someNonZero) b.append(" + ");
					someNonZero = true;
					b.append(coeff == 1 ? "x" : "2x").append('_').append(i);
				}
	 		}
			if (! someNonZero) b.append('0');
			return b.append(" = ").append(c).toString();
		}

		public Modulo3Equation copy() {
			return new Modulo3Equation(this);
		}
	}

	/** The number of variables. */
	private final int numVars;
	/** The equations. */
	private final ArrayList<Modulo3Equation> equations;

	public Modulo3System(final int numVars) {
		equations = new ArrayList<>();
		this.numVars = numVars;
	}

	protected Modulo3System(final int numVars , final ArrayList<Modulo3Equation> system) {
		this.equations = system;
		this.numVars = numVars;
	}

	public Modulo3System copy() {
		final ArrayList<Modulo3Equation> list = new ArrayList<>(equations.size());
		for(final Modulo3Equation equation: equations) list.add(equation.copy());
		return new Modulo3System(numVars, list);
	}

	/** Adds an equation to the system.
	 *
	 * @param equation an equation with the same number of variables of the system.
	 */
	public void add(final Modulo3Equation equation) {
		if (equation.list.size64() != numVars) throw new IllegalArgumentException("The number of variables in the equation (" + equation.list.size64() + ") does not match the number of variables of the system (" + numVars + ")");
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
		final LongArrayBitVector solutions = LongArrayBitVector.ofLength(numVars * 2);
		final LongBigList list = solutions.asLongBigList(2);
		for(int i = solution.length; i-- != 0;) list.set(i, solution[i]);
		return check(solutions);
	}


	public boolean check(final LongArrayBitVector solutions) {
		assert solutions.length() == numVars * 2;
		final long[] solutionBits = solutions.bits();
		for(final Modulo3Equation equation: equations)
			if (equation.c != Modulo3Equation.scalarProduct(equation.bits, solutionBits) % 3) return false;
		return true;
	}


	private boolean echelonForm() {
		main: for (int i = 0; i < equations.size() - 1; i++) {
			assert equations.get(i).firstVar != Integer.MAX_VALUE;

			for (int j = i + 1; j < equations.size(); j++) {
				// Note that because of exchanges we cannot extract the first assignment
				final Modulo3Equation eqJ = equations.get(j);
				final Modulo3Equation eqI = equations.get(i);

				assert eqI.firstVar != Integer.MAX_VALUE;
				assert eqJ.firstVar != Integer.MAX_VALUE;

				final int firstVar = eqI.firstVar;

				if(firstVar == eqJ.firstVar) {
					eqI.eliminate(firstVar, eqJ);
					if (eqI.isUnsolvable()) return false;
					if (eqI.isIdentity()) continue main;
					eqI.updateFirstVar();
				}

				if (eqI.firstVar > eqJ.firstVar) Collections.swap(equations, i, j);
			}
		}

		return true;
	}


	/** Solves the system using Gaussian elimination and write the solution
	 * in an array of longs (mainly for testing purposes).
	 *
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public boolean gaussianElimination(final long[] solution) {
		assert solution.length == numVars;
		final LongArrayBitVector solutions = LongArrayBitVector.ofLength(numVars * 2);
		if (! gaussianElimination(solutions)) return false;
		final LongBigList list = solutions.asLongBigList(2);
		for(int i = solution.length; i-- != 0;) solution[i] = list.getLong(i);
		return true;
	}


	/** Solves the system using Gaussian elimination and write the solution
	 * in a bit vector.
	 *
	 * @param solution a bit vector where the solution will be written using
	 * two bits per value.
	 * @return true if the system is solvable.
	 */
	public boolean gaussianElimination(final LongArrayBitVector solution) {
		assert solution.length() == numVars * 2;
		for (final Modulo3Equation equation: equations) equation.updateFirstVar();

		if (! echelonForm()) return false;

		final long[] solutionBits = solution.bits();
		final LongBigList solutionList = solution.asLongBigList(2);

		for (int i = equations.size(); i-- != 0;) {
			final Modulo3Equation equation = equations.get(i);
			if (equation.isIdentity()) continue;

			assert solutionList.getLong(equation.firstVar) == 0 : equation.firstVar;

			long sum = (equation.c - Modulo3Equation.scalarProduct(equation.bits, solutionBits)) % 3;
			if (sum < 0) sum += 3;

			solutionList.set(equation.firstVar, sum == 0 ? 0 : equation.firstCoeff == sum ? 1 : 2);
		}

		return true;
	}
	/** Solves the system using lazy Gaussian elimination.
	 *
	 * <p><strong>Warning</strong>: this method is very inefficient, as it
	 * scans linearly the equations, builds from scratch the {@code var2Eq}
	 * parameter of {@link #lazyGaussianElimination(Modulo3System, int[][], int[], int[], long[])}
	 * and finally calls it. It should be used mainly to write unit tests.
	 *
	 * @param solution an array where the solution will be written.
	 * @return true if the system is solvable.
	 */
	public boolean lazyGaussianElimination(final long[] solution) {
		final int[][] var2Eq = new int[numVars][];
		final int[] d = new int[numVars];
		for(final Modulo3Equation equation: equations)
			for(int v = (int) equation.list.size64(); v-- != 0;)
				if (equation.list.getLong(v) != 0) d[v]++;

		for(int v = numVars; v-- != 0;) var2Eq[v] = new int[d[v]];
		Arrays.fill(d, 0);
		final long[] c = new long[equations.size()];
		for(int e = 0; e < equations.size(); e++) {
			c[e] = equations.get(e).c;
			final LongBigList list = equations.get(e).list;
			for(int v = (int) list.size64(); v-- != 0;)
				if (list.getLong(v) != 0) var2Eq[v][d[v]++] = e;
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
	public static boolean lazyGaussianElimination(final int var2Eq[][], final long c[], final int[] variable, final long[] solution) {
		return lazyGaussianElimination(null, var2Eq, c, variable, solution);
	}

	/** Solves a system using lazy Gaussian elimination.
	 *
	 * @param system a modulo-3 system, or {@code null}, in which case the system will be rebuilt
	 * from the other variables.
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
	public static boolean lazyGaussianElimination(Modulo3System system, final int var2Eq[][], final long c[], final int[] variable, final long[] solution) {
		final int numEquations = c.length;
		if (numEquations == 0) return true;

		final int numVars = var2Eq.length;
		assert solution.length == numVars;

		final boolean buildSystem = system == null;

		if (buildSystem) {
			system = new Modulo3System(numVars);
			for(int i = 0; i < c.length; i++) system.add(new Modulo3Equation(c[i], numVars));
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
			int currCoeff = 1;
			int j = 0;

			/* We count the number of appearances in an equation and compute
			 * the correct coefficient (which might be zero). If there are
			 * several appearances of the same equation, we compact the array
			 * and, in the end, replace it with a shorter one. */
			for(int i = 1; i < eq.length; i++) {
				if (eq[i] != currEq) {
					assert eq[i] > currEq;
					currCoeff = currCoeff % 3;
					if (currCoeff != 0) {
						if (buildSystem) system.equations.get(currEq).add(v, currCoeff);
						weight[v]++;
						priority[currEq]++;
						eq[j++] = currEq;
					}
					currEq = eq[i];
					currCoeff = 1;
				}
				else currCoeff++;
			}

			if (currCoeff != 3) {
				if (buildSystem) system.equations.get(currEq).add(v, currCoeff);
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
			final int[] u = new int[variable.length];
			final int[] count = new int[3 * numEquations + 1]; // CountSort
			for(int i = variable.length; i-- != 0;) count[weight[variable[i]]]++;
			for(int i = 1; i < count.length; i++) count[i] += count[i - 1];
			for(int i = variable.length; i-- != 0;) u[--count[weight[variable[i]]]] = variable[i];
			variables = IntArrayList.wrap(u);
		}

		// The equations that are not dense and have weight <= 1.
		final IntArrayList equationList = new IntArrayList();
		for(int i = priority.length; i-- != 0;) if (priority[i] <= 1) equationList.add(i);
		// The equations that are part of the dense system (entirely made of active variables).
		final ArrayList<Modulo3Equation> dense = new ArrayList<>();
		// The equations that define a solved variable in term of active variables.
		final ArrayList<Modulo3Equation> solved = new ArrayList<>();
		// The solved variables (parallel to solved).
		final IntArrayList pivots = new IntArrayList();

		final ArrayList<Modulo3Equation> equations = system.equations;
		final long[] normalized = new long[equations.get(0).bits.length];
		// A bit vector made of 2-bit blocks containing a 01 in correspondence of each idle variable.
		final long[] idleNormalized = new long[normalized.length];
		Arrays.fill(idleNormalized, 0x5555555555555555L);

		int numActive = 0;

		for(int remaining = equations.size(); remaining != 0;) {
			if (equationList.isEmpty()) {
				// Make another variable active
				int var;
				do var = variables.popInt(); while(weight[var] == 0);
				numActive++;
				idleNormalized[var / 32] ^= 1L << (var % 32) * 2;
				if (DEBUG) System.err.println("Making variable " + var + " of weight " + weight[var] + " active (" + remaining + " equations to go)");
				for(final int equationIndex: var2Eq[var])
					if (--priority[equationIndex] == 1) equationList.push(equationIndex);
			}
			else {
				remaining--;
				final int first = equationList.popInt(); // An equation of weight 0 or 1.
				final Modulo3Equation equation = equations.get(first);
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
					equation.normalized(normalized);
					int wordIndex = 0;
					while((normalized[wordIndex] & idleNormalized[wordIndex]) == 0) wordIndex++;
					final int pivot = wordIndex * 32 + Long.numberOfTrailingZeros(normalized[wordIndex] & idleNormalized[wordIndex]) / 2;

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
						equations.get(equationIndex).eliminate(pivot, equation);
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

		final Modulo3System denseSystem = new Modulo3System(numVars, dense);
		final LongArrayBitVector solutions = LongArrayBitVector.ofLength(numVars * 2);
		if (! denseSystem.gaussianElimination(solutions)) return false;  // numVars >= denseSystem.numVars

		final long[] solutionBits = solutions.bits();
		final LongBigList solutionList = solutions.asLongBigList(2);

		if (DEBUG) System.err.println("Solution (dense): " + solutionList);

		for (int i = solved.size(); i-- != 0;) {
			final Modulo3Equation equation = solved.get(i);
			final int pivot = pivots.getInt(i);
			assert solutionList.getLong(pivot) == 0 : pivot;

			final int pivotCoefficient = (int)equation.list.getLong(pivot);

			long sum = (equation.c - Modulo3Equation.scalarProduct(equation.bits, solutionBits)) % 3;
			if (sum < 0) sum += 3;

			assert pivotCoefficient != -1;
			solutionList.set(pivot,  sum == 0 ? 0 : pivotCoefficient == sum ? 1 : 2);
		}

		if (DEBUG) System.err.println("Solution (all): " + solutionList);

		assert system.check(solutions);

		// TODO: this could be significantly faster
		for(int i = 0; i < solution.length; i++) solution[i] = solutionList.getLong(i);
		return true;
	}
}
