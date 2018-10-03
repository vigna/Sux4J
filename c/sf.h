#include <inttypes.h>

typedef struct {
	uint64_t size;
	int width;
	int chunk_shift;
	uint64_t global_seed;
	uint64_t offset_and_seed_length;
	uint64_t *offset_and_seed;
	uint64_t array_length;
	uint64_t *array;
} sf;

sf *load_sf(int h);
int64_t get_byte_array(const sf *sf, char *key, uint64_t len);
int64_t get_uint64_t(const sf *mph, uint64_t key);
