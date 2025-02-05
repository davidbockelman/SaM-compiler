import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.ArrayList;

import edu.cornell.cs.sam.io.SamTokenizer;
import edu.cornell.cs.sam.io.Tokenizer;
import edu.cornell.cs.sam.io.Tokenizer.TokenType;

public class BaliCompiler
{
	private static int labelCount = 0;

	static String compiler(String fileName) 
	{
		System.out.println("compiler");
		//returns SaM code for program in file
		try 
		{
			SamTokenizer f = new SamTokenizer (fileName);
			String pgm = getProgram(f);
			return pgm;
		} 
		catch (Exception e) 
		{
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}
	}

	static String getProgram(SamTokenizer f)
	{
		System.out.println("getProgram");
		try
		{
			String pgm="";
			if(f.peekAtKind()!=TokenType.EOF)
			{
				pgm+= getMethodDeclaration(f);
			}
			return pgm;
		}
		catch(Exception e)
		{
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}		
	}

	/*
	 * Parses METH_DECL non-terminal.
	 * Production:
	 * METH_DECL  -> TYPE ID '(' FORMALS? ')' BODY
	 */
	static String getMethodDeclaration(SamTokenizer f)
	{
		// TYPE
		if (!f.check("int")) //must match at begining
		{
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}
		// ID
		String methodName = getId(f); 
		System.out.println("Method Name: " + methodName);
		// '(' FORMALS? ')'
		if (!f.check ('(')) // must be an opening parenthesis
		{
			System.out.println("Fatal error: could not compile program");
			return "STOP\n";
		}
		HashMap<String, Integer> symbol_table = new HashMap<String, Integer>();
		ArrayList<String> formals_names = new ArrayList<String>();
		if (f.test("int")) 
		{
			getFormals(f, formals_names);
		}
		int num_formals = formals_names.size();
		System.out.println("Formals: " + formals_names + " Number of Formals: " + num_formals);
		for (int i = 0; i < num_formals; i++)
		{
			symbol_table.put(formals_names.get(i), -(num_formals - i));
		}
		symbol_table.put("return", -(num_formals + 1));
		f.check(')');  // must be an closing parenthesis
		// BODY
		String body = getBody(f, symbol_table);
		System.out.println("Body: " + body);
		return null;
	}

	/*
	 * Parses FORMALS non-terminal.
	 * Production:
	 * FORMALS -> TYPE ID (',' TYPE ID)*
	 */
	static String getFormals(SamTokenizer f, ArrayList<String> formals_names)
	{
		System.out.println("getFormals");
		// TYPE
		f.check("int"); 
		// ID
		String formalName = getId(f);
		formals_names.add(formalName);
		// (',' TYPE ID)*
		while (f.test(',')) 
		{
			// consume the comma
			f.check(',');
			// TYPE
			f.check("int");
			// ID
			formalName = getId(f);
			formals_names.add(formalName);
		}
		return null;
	}

	
	static String getType(SamTokenizer f)
	{
		
		return null;
	}

	/*
	 * Parses BODY non-terminal.
	 * Production:
	 * BODY -> '{' VAR_DECL* STMT* '}'
	 * STOREOFF n: Pop TOS and write value into
		location Stack[FBR+n]

	 */
	static String getBody(SamTokenizer f, HashMap<String, Integer> symbol_table)
	{
		System.out.println("getBody");
		// '{'
		f.check('{');
		// VAR_DECL*
		String varDecls = "";
		int[] local_var_off = {2};
		while (f.test("int")) 
		{
			varDecls += getVariableDeclaration(f, symbol_table, local_var_off);
		}
		int num_locals = local_var_off[0] - 2;
		System.out.println("Variable Declaration Done: " + varDecls + " Number of Local Variables: " + num_locals);
		// STMT*
		String stmts = "";
		String prologue = "ADDSP " + num_locals + "\n";
		String fEnd_label = "fEnd" + labelCount++;
		String epilogue = "STOREOFF " + symbol_table.get("return") + "\n" + "ADDSP -" + num_locals + "\n" + "JUMPIND\n";
		if (f.peekAtKind() != TokenType.EOF && !f.test('}'))
		{
			stmts += getStatement(f, symbol_table, fEnd_label);
		}
		System.out.println("Statement Done: " + stmts);
		// '}'
		f.check('}');
		return prologue + varDecls + stmts + fEnd_label + ":\n" + epilogue;
	}

	/*
	 * Parses VAR_DECL non-terminal.
	 * Production:
	 * VAR_DECL -> TYPE ID ('=' EXP)? (',' ID ('=' EXP)?)* ';'
	 */
	static String getVariableDeclaration(SamTokenizer f, HashMap<String, Integer> symbol_table, int[] local_var_off)
	{
		System.out.println("getVariableDeclaration");
		String ret = "";
		// TYPE
		f.check("int");
		// ID
		String varName = getId(f);
		System.out.println("Variable Name: " + varName);
		// allocate space for local var on stack
		symbol_table.put(varName, local_var_off[0]);
		local_var_off[0]++;
		String exp = "";
		// ('=' EXP)? (one or zero)
		if (f.test('='))
		{
			System.out.println("Assignment");
			// consume the '='
			f.check('=');
			// EXP
			exp = getExp(f, symbol_table);
			// store the value of EXP in the location of varName
			ret += exp + "STOREOFF " + symbol_table.get(varName) + "\n";
		}
		// (',' ID ('=' EXP)?)*
		while (f.test(',')) 
		{
			// consume the comma
			f.check(',');
			// ID
			varName = f.getString();
			// allocate space for local var on stack
			symbol_table.put(varName, local_var_off[0]);
			local_var_off[0]++;
			// ('=' EXP)? (one or zero)
			if (f.test('='))
			{
				// consume the '='
				f.check('=');
				// EXP
				exp = getExp(f, symbol_table);
				// store the value of EXP in the location of varName
				ret += exp + "STOREOFF " + symbol_table.get(varName) + "\n";
			}
		}
		// ';'
		f.check(';');
		return ret;
	}

	/*
	 * Parses STMT non-terminal.
	 * Production:
	 * STMT -> ASSIGN ';'
          | return EXP ';'
          | if '(' EXP ')' STMT else STMT
          | while '(' EXP ')' STMT
          | break ';'
          | BLOCK
          | ';'
	 */
	static String getStatement(SamTokenizer f, HashMap<String, Integer> symbol_table, String fEnd_label)
	{
		switch (f.peekAtKind()) {
			case WORD:
				// ASSIGN, return, if, while, break
				switch (f.getWord()) {
					case "return":
						System.out.println("Return Statement");
						// STMT -> return EXP ';'
						// EXP
						String exp = getExp(f, symbol_table);
						// JUMP fEnd
						// ';'
						f.check(';');
						return exp + "JUMP " + fEnd_label + "\n";
					case "if":
						System.out.println("If Statement");
						// STMT -> if '(' EXP ')' STMT else STMT
						// consume the if
						f.check("if");
						// '('
						f.check('(');
						// EXP
						getExp(f, symbol_table);
						// ')'
						f.check(')');
						// STMT
						getStatement(f, symbol_table, fEnd_label);
						// else
						f.check("else");
						// STMT
						getStatement(f, symbol_table, fEnd_label);
						break;
					case "while":
						// STMT -> while '(' EXP ')' STMT
						// consume the while
						f.check("while");
						// '('
						f.check('(');
						// EXP
						getExp(f, symbol_table);
						// ')'
						f.check(')');
						// STMT
						getStatement(f, symbol_table, fEnd_label);
						break;
					case "break":
						// STMT -> break ';'
						// consume the break
						f.check("break");
						// ';'
						f.check(';');
						break;
					default:
						// STMT -> ASSIGN ';'
						getAssignment(f);
						// ';'
						if (!f.check(';'))
						{
							System.out.println("Fatal error: could not compile program");
							return "STOP\n";
						}
						break;
				}
				break;
		
			case CHARACTER:
				// BLOCK, ';'
				if (f.test('{')) 
				{
					// STMT -> BLOCK
					getBlock(f);
				} 
				else 
				{
					// STMT -> ';'
					f.check(';');
				}
				break;
		}
		return null;
	}

	static String getBlock(SamTokenizer f)
	{
		return null;
	}

	static String getAssignment(SamTokenizer f)
	{
		return null;
	}

	static String getLocation(SamTokenizer f)
	{
		return null;
	}

	static String getMethod(SamTokenizer f)
	{
		return null;
	}

	/*
	 * Parses EXP non-terminal.
	 * Production:
	 * EXP -> LOCATION
          | LITERAL
          | METHOD '(' ACTUALS? ')'
          | '('EXP '+' EXP')'
          | '('EXP '-' EXP')'
          | '('EXP '*' EXP')'
          | '('EXP '/' EXP')'
          | '('EXP '&' EXP')'
          | '('EXP '|' EXP')'
          | '('EXP '<' EXP')'
          | '('EXP '>' EXP')'
          | '('EXP '=' EXP')'
          | '(''-' EXP')'
          | '(''!' EXP')'
          | '(' EXP ')'
	 */
	static String getExp(SamTokenizer f, HashMap<String, Integer> symbol_table) 
	{
		System.out.println("getExp: " + f.peekAtKind());
		if (f.peekAtKind() == TokenType.WORD) {
			// LOCATION, LITERAL, METHOD
			if (f.test("true") || f.test("false")) 
			{
				// LITERAL
				System.out.println("Literal");
				return getLiteral(f);
			} 
			else 
			{
				// LOCATION -> ID or METHOD -> ID
				String reference = getId(f);
				System.out.println("Location or Method: " + reference);
				if (f.test('(')) 
				{
					// using EXP -> METHOD '(' ACTUALS? ')' production (function call)
					// '('
					f.check('(');
					// ACTUALS?
					if (f.test("int"))
					{
						getActuals(f);
					}
					// ')'
					f.check(')');
					return null;
				}
				// EXP -> LOCATION
				int fbr_offset = symbol_table.get(reference);
				return "LOADOFF " + fbr_offset + "\n";
			}
		} else if (f.peekAtKind() == TokenType.INTEGER) {
			// LITERAL
			System.out.println("Literal");
			return getLiteral(f);
		}
		// '('
		f.check('(');
		String exp = "";
		// '-' or '!'
		if (f.test('-'))
		{
			// EXP -> '(''-' EXP')'
			f.check('-');
			exp = getExp(f, symbol_table);
			f.check(')');
			return exp + "PUSHIMM -1\n" + "TIMES\n";
		}
		else if (f.test('!'))
		{
			// EXP -> '(''!' EXP')'
			f.check('!');
			exp = getExp(f, symbol_table);
			f.check(')');
			return exp + "PUSHIMM 1\n" + "CMP\n";
		}
		
		// EXP
		System.out.println("Binary Operator");
		exp = getExp(f, symbol_table);
		String exp2 = "";
		// '+', '-', '*', '/', '&', '|', '<', '>', '='
		switch (f.getOp()) {
			case '+':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "ADD\n";
			case '-':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case '*':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case '/':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case '&':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case '|':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case '<':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case '>':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case '=':
				getExp(f, symbol_table);
				// ')'
				f.check(')');
				break;
			case ')':
			// unary operator
				break;
		}
		return null;
	}

	static String getActuals(SamTokenizer f)
	{
		return null;
	}

	/*
	 * Parses LITERAL non-terminal.
	 * Production:
	 * LITERAL -> INT
		  | true
		  | false
	 */
	static String getLiteral(SamTokenizer f)
	{
		System.out.println("getLiteral");
		String literal = "";
		if (f.test("true")) 
		{
			f.check("true");
			literal = "PUSHIMM 1\n";
		}
		else if (f.test("false")) 
		{
			f.check("false");
			literal = "PUSHIMM 0\n";
		}
		else 
		{
			literal = "PUSHIMM " + f.getInt() + "\n";
		}
		System.out.println("Literal: " + literal);
		return literal;
	}

	static String getInt(SamTokenizer f)
	{
		return null;
	}

	/*
	 * Parses ID non-terminal.
	 * Production:
	 * ID -> [a-zA-Z] ( [a-zA-Z] | [0-9] | '_' )*
	 */
	static String getId(SamTokenizer f)
	{
		if (f.peekAtKind() == TokenType.WORD) 
		{
			return f.getWord();
		}
		return null;
	}
	

	public static void main(String[] args) {
		if (args.length != 1) {
            System.err.println("Usage: java BaliCompiler <filename>");
            System.exit(1);
        }

        String filename = args[0];
        System.out.println("Processing file: " + filename);
		String pgm = compiler(filename);
		System.out.println(pgm);
	}
}
