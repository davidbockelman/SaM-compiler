JAVAC = javac
JAVA = java
CLASSPATH = lib/SaM-2.6.2
SRC = BaliCompiler.java
BIN = bin
TESTS = tests
EXECUTABLE = BaliCompiler
OUTPUT = output.sam
INTERPRETER = edu/cornell/cs/sam/ui/SamText
SUBMISSION = compiler.jar

# Ensure the bin directory exists
$(BIN):
	mkdir -p $(BIN)

# Compile BaliCompiler.java
$(BIN)/$(EXECUTABLE).class: $(SRC) | $(BIN)
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(SRC)

# Compile SaM Interpreter
$(BIN)/$(INTERPRETER).class: $(BIN) $(CLASSPATH)/$(INTERPRETER).java $(CLASSPATH)/edu/cornell/cs/sam/core/SamAssembler.java
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(CLASSPATH)/$(INTERPRETER).java
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(CLASSPATH)/edu/cornell/cs/sam/core/instructions/*.java 

# Run BaliCompiler on all test files in tests/
run-all: $(BIN)/$(EXECUTABLE).class
	@for file in $(TESTS)/*; do \
		echo "Running BaliCompiler on $$file..."; \
		$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE) $$file $(OUTPUT); \
	done

# Run a specific test file (requires TEST=<filename>)
run: $(BIN)/$(EXECUTABLE).class
	@if [ -z "$(TEST)" ]; then \
		echo "Usage: make run TEST=<filename>"; \
		exit 1; \
	fi
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE) $(TESTS)/$(TEST) $(OUTPUT)

# Run the SaM interpreter on the output file
interpret: $(BIN)/$(INTERPRETER).class $(OUTPUT)
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(INTERPRETER) $(OUTPUT)

# Create a submission jar
submission: $(BIN)/$(EXECUTABLE).class
	jar cvfe $(SUBMISSION) $(EXECUTABLE) -C $(BIN) .

# Clean compiled files
clean:
	rm -rf $(BIN)/*
