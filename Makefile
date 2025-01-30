JAVAC = javac
JAVA = java
CLASSPATH = lib/SaM-2.6.2
SRC = BaliCompiler.java
BIN = bin
TESTS = tests
EXECUTABLE = BaliCompiler

# Ensure the bin directory exists
$(BIN):
	mkdir -p $(BIN)

# Compile BaliCompiler.java
$(BIN)/$(EXECUTABLE).class: $(SRC) | $(BIN)
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(SRC)

# Run BaliCompiler on all test files in tests/
run-all: $(BIN)/$(EXECUTABLE).class
	@for file in $(TESTS)/*; do \
		echo "Running BaliCompiler on $$file..."; \
		$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE) $$file; \
	done

# Run a specific test file (requires TEST=<filename>)
run: $(BIN)/$(EXECUTABLE).class
	@if [ -z "$(TEST)" ]; then \
		echo "Usage: make run TEST=<filename>"; \
		exit 1; \
	fi
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE) $(TESTS)/$(TEST)

# Clean compiled files
clean:
	rm -rf $(BIN)/*
