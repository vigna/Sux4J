# Welcome to Sux4J!

## Introduction

Sux is an umbrella nickname for the results of my fiddling with the
implementation of basic succinct data structures in
[C++](https://github.com/vigna/sux/),
[Java](https://github.com/vigna/Sux4J/), and
[Rust](https://github.com/vigna/sux-rs/). Please have a look at the
documentation for the main highlights in each language.

This is free software. The Rust and Java code is distributed under either
the [GNU Lesser General Public License
2.1+](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html) or the
[Apache Software License
2.0](https://www.apache.org/licenses/LICENSE-2.0). The C++ code is
distributed under the [GNU General Public License
3.0+](https://www.gnu.org/licenses/gpl-3.0.html) with a [Runtime Library
Exception](https://www.gnu.org/licenses/gcc-exception-3.1.html) (as the C
standard library).

## Building

You need [Ant](https://ant.apache.org/) and [Ivy](https://ant.apache.org/ivy/).
Then, run `ant ivy-setupjars jar`.

## Papers

* A [paper](http://vigna.di.unimi.it/papers.php#VigBIRSQ) on the broadword
  techniques used in the rank/select code, and in particular about the
  broadword implementation of select queries.

* A [paper](http://vigna.di.unimi.it/papers.php#BBPMMPH) on the theory of
  monotone minimal perfect hashing.

* An [experimental paper](http://vigna.di.unimi.it/papers.php#BBPTPMMPH2)
  on monotone minimal perfect hashing.

* A [paper](http://vigna.di.unimi.it/papers.php#GOVFSCF) on the current
  implementation of static and minimal perfect hash functions.

* A [paper](http://vigna.di.unimi.it/papers.php#GeVECSF) on the current
  implementation of compressed static functions.

* A [paper](http://vigna.di.unimi.it/papers.php#MaVCFTDRS) on the C++
  implementation dynamic ranking and selection using compact Fenwick trees.

* A [paper](http://vigna.di.unimi.it/papers.php#EGVRS) on the C++
  implementation of RecSplit.

* A [paper](http://vigna.di.unimi.it/papers.php#VigECS) on the Rust
  implementation of functions and filters based on ε-cost sharding.
