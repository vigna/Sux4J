package it.unimi.dsi.sux4j.scratch;

import it.unimi.dsi.sux4j.mph.solve.Modulo2System;
import it.unimi.dsi.sux4j.mph.solve.Modulo2System.Modulo2Equation;

public class GenerateDistancePolynomials16 {
	public static int BITS = 32;

	private static short s0 = -1, s1;

	private static short rotl(short x, int k) {
		return (short) ((x << k) | ((x & 0xFFFF) >>> (16 - k)));
	}

	// 8-5-13	x^32 + x^19 + x^10 + x^9 + x^8 + x^6 + x^5 + x^4 + x^2 + x + 1

	private static void next() {
		final short t0 = s0;
		short t1 = s1;
		t1 ^= t0;
		s0 = (short) (rotl(t0, 8) ^ t1 ^ t1 << 5);
		s1 = rotl(t1, 13);
	}

	public static void main(String[] args) {
		final short[] v0 = new short[2 * BITS];
		final short[] v1 = new short[2 * BITS];
		for(int i = 0; i < 2 * BITS; i++) {
			v0[i] = s0;
			v1[i] = s1;
			next();
		}

		System.out.println("F.<x> = FiniteField(2^32, modulus=x^32 + x^19 + x^10 + x^9 + x^8 + x^6 + x^5 + x^4 + x^2 + x + 1)");

		for(int i = 0; i < 15; i++) {
			final Modulo2System system = new Modulo2System(BITS);
			for(int b = 0; b < BITS; b++) {
				Modulo2Equation eq = new Modulo2Equation((v0[b] & 1L << i) != 0 ? 1 : 0, BITS);
				for(int v = 0; v < BITS; v++) if ((v0[b+v] & 1L << i + 1) != 0) eq.add(v);
				system.add(eq);
			}

			final long[] solution = new long[BITS];
			if (! system.lazyGaussianElimination(solution)) throw new IllegalStateException();
			System.out.print("print((");
			for(int e = 0; e < BITS; e++) if (solution[e] != 0) System.out.print(" + x^" + e);
			System.out.println(").log(x))");
		}

		/*for(int i = 0; i < BITS; i++) System.out.print(v0[i] & 1);
		System.out.println();

		s0 = -1;
		s1 = 0;

		for(int i = 0; i < 1950270530; i++) next();

		for(int i = 0; i < BITS; i++) {
			System.out.print((s0 & 2) != 0 ? 1 : 0);
			next();
		}
		System.out.println();
		*/
	}


}
