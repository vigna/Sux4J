#!/bin/bash

gcc -O3 -fomit-frame-pointer test_mph_byte_array.c mph.c spooky.c -o test_mph_byte_array
gcc -O3 -fomit-frame-pointer test_sf_byte_array.c sf.c spooky.c -o test_sf_byte_array
gcc -O3 -fomit-frame-pointer test_mph_uint64_t.c mph.c spooky.c -o test_mph_uint64_t
