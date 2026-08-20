CC      ?= cc
CFLAGS  ?= -std=c11 -O2 -Wall -Wextra -Wno-unused-parameter -Iinclude
LDFLAGS ?=
LDLIBS  ?= -lm

SRC := $(wildcard src/*.c)
OBJ := $(SRC:.c=.o)
BIN := newcode

TEST_SRC := $(wildcard tests/*.c)
TEST_BIN := $(TEST_SRC:.c=)

.PHONY: all clean test run-example emit-example

all: $(BIN)

$(BIN): $(OBJ)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS) $(LDLIBS)

src/%.o: src/%.c
	$(CC) $(CFLAGS) -c $< -o $@

# встроенная копия nc_rt.h влияет на cat_compiler.o
src/cat_compiler.o: src/nc_rt_data.h include/nc_rt.h

tests/%: tests/%.c $(filter-out src/main.o,$(OBJ))
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS) $(LDLIBS)

test: $(TEST_BIN)
	@set -e; for t in $(TEST_BIN); do echo "== $$t =="; ./$$t; done
	@echo "All tests passed."

# Основной режим: проект компилируется в машинный код и запускается.
run-example: $(BIN)
	./$(BIN) run examples/c_memory.xml

# Посмотреть сгенерированный C-код.
emit-example: $(BIN)
	./$(BIN) emit examples/hello.xml > /tmp/hello_generated.c
	@echo "C-код проекта записан в /tmp/hello_generated.c"

clean:
	rm -f $(OBJ) $(BIN) $(TEST_BIN)
