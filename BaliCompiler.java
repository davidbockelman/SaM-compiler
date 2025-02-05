import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.IOException;

import edu.cornell.cs.sam.io.SamTokenizer;
import edu.cornell.cs.sam.io.Tokenizer;
import edu.cornell.cs.sam.io.Tokenizer.TokenType;

public class BaliCompiler
{
	private static int labelCount = 0;

	static String compiler(String fileName) 
	{
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

	/*
	 * Parses PROGRAM non-terminal.
	 * Production:
	 * PROGRAM -> METH_DECL*
	 */
	static String getProgram(SamTokenizer f)
	{
		try
		{
			String pgm="";
			while(f.peekAtKind()!=TokenType.EOF)
			{
				pgm += getMethodDeclaration(f);
			}
			String call_main = "PUSHIMM 0\n" + "LINK\n" + "JSR main\n" + "POPFBR\n" + "STOP\n";
			return call_main + pgm;
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
		for (int i = 0; i < num_formals; i++)
		{
			symbol_table.put(formals_names.get(i), -(num_formals - i));
		}
		symbol_table.put("return", -(num_formals + 1));
		f.check(')');  // must be an closing parenthesis
		// BODY
		String body = getBody(f, symbol_table);
		return methodName + ":\n" + body;
	}

	/*
	 * Parses FORMALS non-terminal.
	 * Production:
	 * FORMALS -> TYPE ID (',' TYPE ID)*
	 */
	static String getFormals(SamTokenizer f, ArrayList<String> formals_names)
	{
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
	 */
	static String getBody(SamTokenizer f, HashMap<String, Integer> symbol_table)
	{
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
		// STMT*
		String stmts = "";
		String prologue = "ADDSP " + num_locals + "\n";
		String fEnd_label = "fEnd" + labelCount++;
		String epilogue = "STOREOFF " + symbol_table.get("return") + "\n" + "ADDSP -" + num_locals + "\n" + "JUMPIND\n";
		while (f.peekAtKind() != TokenType.EOF && !f.test('}'))
		{
			stmts += getStatement(f, symbol_table, fEnd_label);
		}
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
		String ret = "";
		// TYPE
		f.check("int");
		// ID
		String varName = getId(f);
		// allocate space for local var on stack
		symbol_table.put(varName, local_var_off[0]);
		local_var_off[0]++;
		String exp = "";
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
						// STMT -> return EXP ';'
						// EXP
						String exp = getExp(f, symbol_table);
						// JUMP fEnd
						// ';'
						f.check(';');
						return exp + "JUMP " + fEnd_label + "\n";
					case "if":
						// STMT -> if '(' EXP ')' STMT else STMT
						// consume the if
						f.check("if");
						// '('
						f.check('(');
						// EXP
						String c = getExp(f, symbol_table);
						// ')'
						f.check(')');
						// STMT
						String s1 = getStatement(f, symbol_table, fEnd_label);
						// else
						f.check("else");
						// STMT
						String s2 = getStatement(f, symbol_table, fEnd_label);
						String l1 = "L" + labelCount++;
						String l2 = "L" + labelCount++;
						return c + "JUMPC " + l1 + "\n" + s2 + "JUMP " + l2 + "\n" + l1 + ":\n" + s1 + l2 + ":\n";
					case "while":
						// STMT -> while '(' EXP ')' STMT
						// consume the while
						f.check("while");
						// '('
						f.check('(');
						// EXP
						c = getExp(f, symbol_table);
						// ')'
						f.check(')');
						// STMT
						s1 = getStatement(f, symbol_table, fEnd_label);
						l1 = "L" + labelCount++;
						l2 = "L" + labelCount++;
						return l1 + ":\n" + c + "ISNIL\n" + "JUMPC " + l2 + "\n" + s1 + "JUMP " + l1 + "\n" + l2 + ":\n";
					case "break":
						// STMT -> break ';'
						// consume the break
						f.check("break");
						// ';'
						f.check(';');
						break;
					default:
						// already consumed the location
						f.pushBack();
						// STMT -> ASSIGN ';'
						String assign = getAssignment(f, symbol_table);
						// ';'
						if (!f.check(';'))
						{
							System.out.println("Fatal error: could not compile program");
							return "STOP\n";
						}
						return assign;
				}
				break;
		
			case OPERATOR:
				// BLOCK, ';'
				if (f.test('{')) 
				{
					// STMT -> BLOCK
					return getBlock(f, symbol_table, fEnd_label);
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

	/*
	 * Parses BLOCK non-terminal.
	 * Production:
	 * BLOCK -> '{' STMT* '}'
	 */
	static String getBlock(SamTokenizer f, HashMap<String, Integer> symbol_table, String fEnd_label)
	{
		// '{'
		f.check('{');
		// STMT*
		String stmts = "";
		while (f.peekAtKind() != TokenType.EOF && !f.test('}'))
		{
			stmts += getStatement(f, symbol_table, fEnd_label);
		}
		// '}'
		f.check('}');
		return stmts;
	}

	/*
	 * Parses ASSIGN non-terminal.
	 * Production:
	 * ASSIGN -> LOCATION '=' EXP
	 */
	static String getAssignment(SamTokenizer f, HashMap<String, Integer> symbol_table)
	{
		// LOCATION
		String location = getLocation(f);
		// '='
		f.check('=');
		// EXP
		String exp = getExp(f, symbol_table);
		return exp + "STOREOFF " + symbol_table.get(location) + "\n";
	}

	/*
	 * Parses LOCATION non-terminal.
	 * Production:
	 * LOCATION -> ID
	 */
	static String getLocation(SamTokenizer f)
	{
		return getId(f);
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
		if (f.peekAtKind() == TokenType.WORD) {
			// LOCATION, LITERAL, METHOD
			if (f.test("true") || f.test("false")) 
			{
				// EXP -> LITERAL
				return getLiteral(f);
			} 
			else 
			{
				// LOCATION -> ID or METHOD -> ID
				String reference = getId(f);
				if (f.test('(')) 
				{
					// using EXP -> METHOD '(' ACTUALS? ')' production (function call)
					// '('
					f.check('(');
					// ACTUALS?
					String actuals = "";
					int[] num_actuals = {0};
					if (!f.test(')'))
					{
						actuals += getActuals(f, symbol_table, num_actuals);
					}
					// ')'
					f.check(')');
					String call_preamble = "PUSHIMM 0\n";
					String call_postamble = "LINK\nJSR " + reference + "\nPOPFBR\nADDSP -" + num_actuals[0] + "\n";
					return call_preamble + actuals + call_postamble;
				}
				// EXP -> LOCATION
				int fbr_offset = symbol_table.get(reference);
				return "PUSHOFF " + fbr_offset + "\n";
			}
		} else if (f.peekAtKind() == TokenType.INTEGER) {
			// EXP -> LITERAL
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
			return exp + "NOT\n";
		}
		
		// EXP
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
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "SUB\n";
			case '*':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "TIMES\n";
			case '/':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "DIV\n";
			case '&':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "AND\n";
			case '|':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "OR\n";
			case '<':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "LESS\n";
			case '>':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "GREATER\n";
			case '=':
				exp2 = getExp(f, symbol_table);
				// ')'
				f.check(')');
				return exp + exp2 + "EQUAL\n";
			default:
			// error
				return null;
		}
	}

	/*
	 * Parses ACTUALS non-terminal.
	 * Production:
	 * ACTUALS -> EXP (',' EXP)*
	 */
	static String getActuals(SamTokenizer f, HashMap<String, Integer> symbol_table, int[] num_actuals)
	{
		num_actuals[0] = 0;
		// EXP
		String exp = getExp(f, symbol_table);
		num_actuals[0]++;
		while (f.test(',')) 
		{
			// consume the comma
			f.check(',');
			// EXP
			exp += getExp(f, symbol_table);
			num_actuals[0]++;
		}
		return exp;
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
		if (f.peekAtKind() == TokenType.WORD || f.peekAtKind() == TokenType.CHARACTER)
		{
			return f.getWord();
		}
		return null;
	}
	

	public static void main(String[] args) {
		if (args.length != 2) {
            System.err.println("Usage: java BaliCompiler <filename> <outputfile>");
            System.exit(1);
        }

        String filename = args[0];
		String outputfile = args[1];
        System.out.println("Processing file: " + filename);
		String pgm = compiler(filename);
		try (FileWriter writer = new FileWriter(outputfile)) { // Overwrites the file
            writer.write(pgm);
            System.out.println(outputfile + " written successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
	}
}
