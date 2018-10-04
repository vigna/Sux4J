C implementations
=================

This directory contains a few C implementations of Sux4J data structures.
Only the lookup part is implemented: the data structures can be generated
using the dump() method where available (e.g., in GOV3Function and
GOVMinimalPerfectHashFunction). The code is bare bones and does not make any
kind of check of validity.

The script `comp.sh` will compile a few testing programs which accept a
data structure and a file containing at least NKEYS strings or binary
64-bit integers. The keys will not be randomized so you should provide
a randomized set. Sources containing the string `byte_array` expect
data structures built using the `TransformationStrategies.RAW_BYTE_ARRAY`
transformation strategy; sources containing the string `uint64_t` expect
data structures built using the `TransformationStrategies.RAW_LONG`
transformation strategy. There is no check that the right kind of structure
or strategy is being loaded, so watch your steps.
