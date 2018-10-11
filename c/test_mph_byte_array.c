#include "spooky.h"
#include "mph.h"
#include <stdio.h>
#include <inttypes.h>
#include <fcntl.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>
#include <sys/time.h>
#include <sys/resource.h>

#define SUX4J_MAP mph
#define SUX4J_LOAD_MAP load_mph
#define SUX4J_GET_BYTE_ARRAY mph_get_byte_array

#include "test_byte_array.c"