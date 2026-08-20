CC      ?= cc
CFLAGS  ?= -std=c11 -O2 -Wall -Wextra -Wno-unused-parameter -Iinclude
LDFLAGS ?=
LDLIBS  ?= -lm

SRC := $(wildcard src/*.c)
OBJ := $(SRC:.c=.o)
BIN := catrobat_run

TEST_SRC := $(wildcard tests/*.c)
TEST_BIN := $(TEST_SRC:.c=)

.PHONY: all clean test run-example

all: $(BIN)

$(BIN): $(OBJ)
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS) $(LDLIBS)

src/%.o: src/%.c
	$(CC) $(CFLAGS) -c $< -o $@

tests/%: tests/%.c $(filter-out src/main.o,$(OBJ))
	$(CC) $(CFLAGS) $^ -o $@ $(LDFLAGS) $(LDLIBS)

test: $(TEST_BIN)
	@set -e; for t in $(TEST_BIN); do echo "== $$t =="; ./$$t; done
	@echo "All tests passed."

run-example: $(BIN)
	./$(BIN) examples/hello.xml

clean:
	rm -f $(OBJ) $(BIN) $(TEST_BIN)
