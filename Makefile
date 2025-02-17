JAVAC = javac
JAVA = java
CLASSPATH = lib/SaM-2.6.2
SRC = BaliCompiler.java
SRC_X86 = BaliCompiler_x86.java
BIN = bin
TESTS = tests
EXECUTABLE = BaliCompiler
EXECUTABLE_X86 = BaliCompiler_x86
OUTPUT = output.sam
OUTPUT_X86 = output.asm
X86_OBJECTS = macro.o io.o
INTERPRETER = edu/cornell/cs/sam/ui/SamText
SUBMISSION = compiler.jar

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
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE) $(TESTS)/$(test).bali $(OUTPUT)

compile-x86: $(BIN)/$(EXECUTABLE_X86).class
	@if [ -z "$(test)" ]; then \
		echo "Usage: make run-x86 test=<filename>"; \
		exit 1; \
	fi
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(EXECUTABLE_X86) $(TESTS)/$(test).bali $(OUTPUT_X86)

# Run the SaM interpreter on the output file
interpret: $(BIN)/$(INTERPRETER).class $(OUTPUT)
	$(JAVA) -cp $(CLASSPATH):$(BIN) $(INTERPRETER) $(OUTPUT)

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

# Create a submission jar
submission: $(BIN)/$(EXECUTABLE).class
	jar cvfe $(SUBMISSION) $(EXECUTABLE) -C $(BIN) .

# Clean compiled files
clean:
	rm -rf $(BIN)/*
	rm -f $(OUTPUT)
	rm -f $(OUTPUT_X86)
	rm -f a.out
	
