JAVAC = javac
JAVA = java
CLASSPATH = lib/SaM-2.6.2
SRC = BaliCompiler.java
SRC_X86 = BaliCompiler_x86.java
BIN = bin
TESTS_DIR = tests
EXECUTABLE = BaliCompiler
EXECUTABLE_X86 = BaliCompiler_x86
OUTPUT = output.sam
OUTPUT_X86 = output.asm
X86_OBJECTS = macro.o io.o
INTERPRETER = edu/cornell/cs/sam/ui/SamText
SUBMISSION = compiler.jar

NUM_TESTS := $(shell ls tests/test*.bali | wc -l)

# Ensure the bin directory exists
$(BIN):
	mkdir -p $(BIN)

# Compile BaliCompiler.java
$(BIN)/$(EXECUTABLE).class: $(SRC) | $(BIN)
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(SRC)

# Compile BaliCompiler_x86.java
$(BIN)/$(EXECUTABLE_X86).class: $(SRC_X86) | $(BIN)
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(SRC_X86)

# Compile SaM Interpreter
$(BIN)/$(INTERPRETER).class: $(BIN) $(CLASSPATH)/$(INTERPRETER).java $(CLASSPATH)/edu/cornell/cs/sam/core/SamAssembler.java
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(CLASSPATH)/$(INTERPRETER).java
	$(JAVAC) -cp $(CLASSPATH) -d $(BIN) $(CLASSPATH)/edu/cornell/cs/sam/core/instructions/*.java 

# Compile necassary object files for x86
$(X86_OBJECTS): 
	gcc /usr/share/sasm/NASM/macro.c -c -o macro.o -m32
	nasm -g -f elf32 /usr/share/sasm/include/io.inc -i /usr/share/sasm/include/ -o io.o

# Run a specific test file
compile: $(BIN)/$(EXECUTABLE).class
	@if [ -z "$(test)" ]; then \
		echo "Usage: make run test=<filename>"; \
		exit 1; \
	fi
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE) $(TESTS_DIR)/$(test).bali $(OUTPUT)

# Run the SaM interpreter on the output file
interpret: $(BIN)/$(INTERPRETER).class $(OUTPUT)
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(INTERPRETER) $(OUTPUT)

# Compile and run SaM interpreter on a specific test file
run: compile interpret

# Run all tests
run-all:
	@count = 0; \
	for i in $$(seq 1 $(NUM_TESTS)); do \
		echo "Running test $$i..."; \
		if grep -q '// \*Bad testcase\*' tests/test$$i.bali; then \
			make run test=test$$i; \
			if [ $$? -ne 0 ]; then \
				echo "Test$$i passed (expected failure)"; \
				count=$$((count + 1)); \
			else \
				echo "Test$$i failed (expected failure)"; \
			fi; \
		else \
			expected=$$(grep -oP '// Expected result: \K.*' tests/test$$i.bali); \
			actual=$$(make run test=test$$i | awk -F': ' '/^Exit Status:/ {print $$2}'); \
			if [ "$$actual" = "$$expected" ]; then \
				echo "Test$$i passed (expected success: $$expected)"; \
				count=$$((count + 1)); \
			else \
				echo "Test$$i failed (expected: $$expected, got: $$actual)"; \
			fi; \
		fi; \
	done; \
	echo "Passed $$count out of $(NUM_TESTS) tests."

compile-x86: $(BIN)/$(EXECUTABLE_X86).class
	@if [ -z "$(test)" ]; then \
		echo "Usage: make run-x86 test=<filename>"; \
		exit 1; \
	fi
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE_X86) $(TESTS_DIR)/$(test).bali $(OUTPUT_X86)


# Compile asm into executable
assemble-x86: $(OUTPUT_X86) $(X86_OBJECTS)
	nasm -g -f elf32 $(OUTPUT_X86) -i /usr/share/sasm/include/ -o tmp.o
	gcc $(X86_OBJECTS) tmp.o -m32 -o a.out

# Run the compiled x86 code
run-x86:
	@if [ ! -f a.out ]; then \
		$(MAKE) assemble-x86; \
	fi
	./a.out

# Do all x86 steps in one
x86: compile-x86 assemble-x86 run-x86

# Run all tests
run-all-x86:
	@count = 0; \
	for i in $$(seq 1 $(NUM_TESTS)); do \
		echo "Running test $$i..."; \
		if grep -q '// \*Bad testcase\*' tests/test$$i.bali; then \
			make x86 test=test$$i; \
			if [ $$? -ne 0 ]; then \
				echo "Test$$i passed (expected failure)"; \
				count=$$((count + 1)); \
			else \
				echo "Test$$i failed (expected failure)"; \
			fi; \
		else \
			expected=$$(grep -oP '// Expected result: \K.*' tests/test$$i.bali); \
			actual=$$(make x86 test=test$$i | awk '/^\.\/a\.out$$/{getline; print}'); \
			if [ "$$actual" = "$$expected" ]; then \
				echo "Test$$i passed (expected success: $$expected)"; \
				count=$$((count + 1)); \
			else \
				echo "Test$$i failed (expected: $$expected, got: $$actual)"; \
			fi; \
		fi; \
	done; \
	echo "Passed $$count out of $(NUM_TESTS) tests."


# Create a submission jar
submission: $(BIN)/$(EXECUTABLE).class
	jar cvfe $(SUBMISSION) $(EXECUTABLE) -C $(BIN) .

# Create a submission jar for x86
submission-x86: $(BIN)/$(EXECUTABLE_X86).class
	jar cvfe $(SUBMISSION) $(EXECUTABLE_X86) -C $(BIN) .

# Clean compiled files
clean:
	rm -rf $(BIN)/*
	rm -f $(OUTPUT)
	rm -f $(OUTPUT_X86)
	rm -f a.out
	
