#include <stdio.h>
#include <inttypes.h>
#include <fcntl.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>
#include <string.h>
#include <sys/time.h>
#include <sys/resource.h>
#include "sf4.h"

#define SUX4J_MAP sf
#define SUX4J_LOAD_MAP load_sf
#define SUX4J_GET_BYTE_ARRAY sf4_get_byte_array

#include "test_byte_array.c"
