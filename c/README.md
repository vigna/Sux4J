C implementations
=================

This directory contains a few C implementations of Sux4J data structures.
Only the lookup part is implemented: the data structures can be generated
using the dump() method where available (e.g., in GOV3Function and
GOVMinimalPerfectHashFunction). The code is bare bones and does not make any
kind of check of validity.

The script `comp.sh` will compile three testing programs which accept a
data structure and a file containing at least NKEYS strings or binary
64-bit integers. The keys will not be randomized so you should provide
a randomized set.
