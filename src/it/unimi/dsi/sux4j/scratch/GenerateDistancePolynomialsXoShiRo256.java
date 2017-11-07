package it.unimi.dsi.sux4j.scratch;

import java.util.Scanner;

import it.unimi.dsi.sux4j.mph.solve.Modulo2System;
import it.unimi.dsi.sux4j.mph.solve.Modulo2System.Modulo2Equation;

public class GenerateDistancePolynomialsXoShiRo256 {
	public static int BITS = 256;

	private static long s0 = -1, s1, s2, s3;
	private static int A, B;

	private static void next() {
		final long t = s1 << A;
		s2 ^= s0;
		s3 ^= s1;
		s1 ^= s2;
		s0 ^= s3;
		s2 ^= t;
		s3 = Long.rotateLeft(s3, B);
	}

	public static void main(String[] args) {
		final boolean xor = args.length > 0;
		@SuppressWarnings("resource")
		final Scanner scanner = new Scanner(System.in);
		final String[] shifts = scanner.next().split("-");
		A = Integer.parseInt(shifts[0]);
		B = Integer.parseInt(shifts[1]);

		scanner.next();
		final String polynomial = scanner.nextLine();


		final long[] v0 = new long[2 * BITS];
		for(int i = 0; i < 2 * BITS; i++) {
			v0[i] = xor ? (s0 ^ s1) : s0;
			next();
		}

		System.out.println("F.<x> = FiniteField(2^256, modulus=" + polynomial + ")");
		System.out.println("@parallel");
		System.out.println("def parlog(dummy,p): return (p).log(x)");
		System.out.println("for v in sorted(list(parlog([");

		for(int i = 0; i < 63; i++) {
			final Modulo2System system = new Modulo2System(BITS);
			for(int b = 0; b < BITS; b++) {
				final Modulo2Equation eq = new Modulo2Equation((v0[b] & 1L << i + 1) != 0 ? 1 : 0, BITS);
				for(int v = 0; v < BITS; v++) if ((v0[b+v] & 1L << i) != 0) eq.add(v);
				system.add(eq);
			}

			final long[] solution = new long[BITS];
			if (! system.lazyGaussianElimination(solution)) throw new IllegalStateException();

			if (i != 0) System.out.println(",");
			System.out.print("(" + i + ", ");
			for(int e = 0; e < BITS; e++) if (solution[e] != 0) System.out.print(" + x^" + e);
			System.out.print(")");
		}

		System.out.println("]))): print(v[1])");
	}
}
