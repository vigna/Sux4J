package it.unimi.dsi.sux4j.scratch;

import it.unimi.dsi.sux4j.mph.solve.Modulo2System;
import it.unimi.dsi.sux4j.mph.solve.Modulo2System.Modulo2Equation;

public class GenerateDistancePolynomials {
	public static int BITS = 128;
	
	private static long s0 = -1, s1;

	// 55-14-36	63	x^128 + x^118 + x^117 + x^114 + x^112 + x^109 + x^108 + x^107 + x^106 + x^103 + x^102 + x^101 + x^99 + x^98 + x^96 + x^94 + x^93 + x^92 + x^91 + x^90 + x^89 + x^88 + x^85 + x^83 + x^80 + x^79 + x^78 + x^77 + x^76 + x^75 + x^71 + x^67 + x^65 + x^62 + x^60 + x^59 + x^58 + x^57 + x^56 + x^55 + x^54 + x^52 + x^50 + x^49 + x^46 + x^45 + x^42 + x^41 + x^40 + x^38 + x^37 + x^33 + x^31 + x^30 + x^29 + x^28 + x^23 + x^22 + x^21 + x^16 + x^15 + x^14 + 1
	
	private static void next() {
		final long t0 = s0;
		long t1 = s1;
		t1 ^= t0;
		s0 = Long.rotateLeft(t0, 55) ^ t1 ^ t1 << 14;
		s1 = Long.rotateLeft(t1, 36);
	}

	public static void main(String[] args) {
		final long[] v0 = new long[2 * BITS];
		final long[] v1 = new long[2 * BITS];
		for(int i = 0; i < 2 * BITS; i++) {
			v0[i] = s0;
			v1[i] = s1;
			next();
		}
		
		System.out.println("F.<x> = FiniteField(2^128, modulus=x^128 + x^118 + x^117 + x^114 + x^112 + x^109 + x^108 + x^107 + x^106 + x^103 + x^102 + x^101 + x^99 + x^98 + x^96 + x^94 + x^93 + x^92 + x^91 + x^90 + x^89 + x^88 + x^85 + x^83 + x^80 + x^79 + x^78 + x^77 + x^76 + x^75 + x^71 + x^67 + x^65 + x^62 + x^60 + x^59 + x^58 + x^57 + x^56 + x^55 + x^54 + x^52 + x^50 + x^49 + x^46 + x^45 + x^42 + x^41 + x^40 + x^38 + x^37 + x^33 + x^31 + x^30 + x^29 + x^28 + x^23 + x^22 + x^21 + x^16 + x^15 + x^14 + 1)");
		
		for(int i = 0; i < 63; i++) {
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
	}

}
