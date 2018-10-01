package it.unimi.dsi.sux4j.scratch;

import it.unimi.dsi.sux4j.mph.solve.Modulo3System;
import it.unimi.dsi.util.XoShiRo256StarStarRandom;

public class TestRandom3Systems extends Modulo3System {

	public TestRandom3Systems(final int numVars) {
		super(numVars);
	}

	public static void main(final String[] args) {
		final XoShiRo256StarStarRandom r = new XoShiRo256StarStarRandom();
		for(int e = 100; e < 1000000; e *= 10) {
			final int v = (int) Math.ceil(e * 1.10);
			int solvable = 0;
			for(int k = 100; k-- != 0;) {
				final Modulo3System s = new Modulo3System(v);
				for(int i = 0; i < e; i++) {
					final Modulo3Equation t = new Modulo3Equation(r.nextLong(3), v);
					final int a = r.nextInt(v);
					int b;
					do b = r.nextInt(v); while(b == a);
					int c;
					do c = r.nextInt(v); while(c == a || c == b);
					t.add(a, r.nextInt(2) + 1);
					t.add(b, r.nextInt(2) + 1);
					t.add(c, r.nextInt(2) + 1);
					s.add(t);
				}

				if (s.lazyGaussianElimination(new long[v])) solvable++;
			}
			System.out.println("Equations: " + e + " Solvable: " + solvable  + "%");
		}
	}

}
