#include <stdio.h>
#include <inttypes.h>
#include <fcntl.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>
#include <sys/time.h>
#include <sys/resource.h>
#include "csf3.h"

#define SUX4J_MAP csf
#define SUX4J_LOAD_MAP load_csf
#define SUX4J_GET_BYTE_ARRAY csf3_get_byte_array

#include "test_byte_array.c"
