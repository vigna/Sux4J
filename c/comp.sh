#!/bin/bash

gcc $@ -O3 -fomit-frame-pointer test_mph_byte_array.c mph.c spooky.c -o test_mph_byte_array
gcc $@ -O3 -fomit-frame-pointer test_mph_uint64_t.c mph.c spooky.c -o test_mph_uint64_t

gcc $@ -O3 -fomit-frame-pointer test_sf3_byte_array.c sf.c sf3.c spooky.c -o test_sf3_byte_array
gcc $@ -O3 -fomit-frame-pointer test_sf4_byte_array.c sf.c sf4.c spooky.c -o test_sf4_byte_array

gcc $@ -O3 -fomit-frame-pointer test_csf3_byte_array.c csf.c csf3.c spooky.c -o test_csf3_byte_array
gcc $@ -O3 -fomit-frame-pointer test_csf4_byte_array.c csf.c csf4.c spooky.c -o test_csf4_byte_array
