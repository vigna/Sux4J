#include <inttypes.h>

typedef struct {
	uint64_t size;
	int chunk_shift;
	uint64_t global_seed;
	uint64_t edge_offset_and_seed_length;
	uint64_t *edge_offset_and_seed;
	uint64_t array_length;
	uint64_t *array;
} mph;

mph *load_mph(int h);
int64_t get(const mph *mph, char *key, uint64_t len);
