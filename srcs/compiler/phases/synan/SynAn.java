package compiler.phases.synan;

import common.report.*;
import compiler.phases.*;
import compiler.phases.lexan.*;
import compiler.phases.synan.dertree.*;

/**
 * Syntax analysis.
 *
 * @author sliva
 */
public class SynAn extends Phase {


	private static final boolean debug = true;
	private static final boolean simplify = true;

	/** The constructed derivation tree. */
	private static DerTree derTree = null;

	/**
	 * Returns the constructed derivation tree.
	 *
	 * @return The constructed derivation tree.
	 */
	public static DerTree derTree() {
		return derTree;
	}

	/** The lexical analyzer used by this syntax analyzer. */
	private final LexAn lexAn;

	/**
	 * Constructs a new syntax analysis phase.
	 */
	public SynAn() {
		super("synan");
		lexAn = new LexAn();
	}

	/** The lookahead buffer (of length 1). */
	private Symbol currSymb = null;

	/**
	 * Appends the current symbol in the lookahead buffer to the node of the
	 * derivation tree that is currently being expanded by the parser.
	 * <p>
	 * Hence, the statement {@code currSymb = skip(node);} can be used for (a)
	 * appending the current symbol in the lookahead buffer {@code currSymb} to
	 * the node of the derivation tree and (b) eliminating this symbol from the
	 * lookahead buffer.
	 *
	 * @param node The node of the derivation tree currently being expanded by
	 *             the parser.
	 * @return {@code null}.
	 */
	private Symbol skip(DerNode node) {
		if (currSymb != null) {
			if (debug) System.out.println("Add as a leaf: " + currSymb.token + " [" + currSymb.lexeme + "]");
			node.add(new DerLeaf(currSymb));
		}
		return null;
	}

	/**
	 * The parser.
	 * <p>
	 * This method returns the derivation tree of the program in the source
	 * file. It calls method {@link #parseSource()} that starts a recursive
	 * descent parser implementation of an LL(1) parsing algorithm.
	 *
	 * @return The derivation tree.
	 */
	public DerTree parser() {
		derTree = parseSource();
		currSymb = currSymb == null ? lexAn.lexer() : currSymb;
		if (currSymb.token != Term.EOF)
			throw new Report.Error(currSymb, "Unexpected '" + currSymb + "' at the end of a program.");
		derTree.accept(new DerLogger(logger), null);
		return derTree;
	}

	@Override
	public void close() {
		lexAn.close();
		super.close();
	}


	private void getNextSymbol() {
		if (currSymb == null) currSymb = lexAn.lexer();
	}


	private void addLeafSymbol(DerNode node, Term matchingTerm, String errorMsg) {
		getNextSymbol();
		if (currSymb.token != matchingTerm)
			throw new Report.Error(currSymb.location(), errorMsg);
		currSymb = skip(node);
	}


	private void dump(String msg) {
		if (debug) System.out.println(msg + ": " + currSymb.token);
	}

	// --- PARSER ---

	private DerNode parseSource() {
		DerNode node = new DerNode(Nont.Source);
		node.add(parseExpr());
		return node;
	}


	private DerNode parseExpr() {
		DerNode node = new DerNode(Nont.Expr);
		getNextSymbol();
		switch (currSymb.token) {
			case VOIDCONST: case BOOLCONST: case CHARCONST: case INTCONST: case PTRCONST:
				// literal
				node.add(parseExprOnLevel(2));
				node.add(parseExprHelper(1));
				break;
			case IDENTIFIER:
				node.add(parseExprOnLevel(2));
				node.add(parseExprHelper(1));
				break;
			case LBRACE:
				node.add(parseExprOnLevel(2));
				node.add(parseExprHelper(1));
				break;
			case ADD: case SUB:
				node.add(parseExprOnLevel(2));
				node.add(parseExprHelper(1));
				break;
			case NOT: case MEM: case NEW: case DEL: case LBRACKET:
				node.add(parseExprOnLevel(2));
				node.add(parseExprHelper(1));
				break;
			default:
				throw new Report.Error("Not an expression.");
		}
		return node;
	}

	// Unary operators
	// case NOT: case MEM: case NEW: case DEL: case LBRACKET:

	private DerNode parseExprOnLevel(int level) {
		DerNode node = new DerNode(Nont.Expr);
		getNextSymbol();

		dump("Expr level [" + level + "]");
		if (level < 6) {
			switch (currSymb.token) {
				case VOIDCONST: case BOOLCONST: case CHARCONST: case INTCONST: case PTRCONST:
					// literal
					node.add(parseExprOnLevel(level + 1));
					node.add(parseExprHelper(level));
					break;
				case IDENTIFIER:
					node.add(parseExprOnLevel(level + 1));
					node.add(parseExprHelper(level));
					break;
				case LBRACE:
					node.add(parseExprOnLevel(level + 1));
					node.add(parseExprHelper(level));
					break;
				case NOT: case MEM: case NEW: case DEL: case LBRACKET:
					node.add(parseExprOnLevel(level + 1));
					node.add(parseExprHelper(level));
					break;
				case ADD: case SUB:
					node.add(parseExprOnLevel(level + 1));
					node.add(parseExprHelper(level));
					break;
				case RPARENTHESIS: case COMMA: case COLON: case RBRACE: case WHERE: case SEMIC:
				case ASSIGN: case END: case ELSE: case DO: case EOF: case RBRACKET:
					//case THEN:
					break;
				default:
					throw new Report.Error("Not an expression [" + level + "].");
			}
		} else {
			switch (currSymb.token) {
				case VOIDCONST: case BOOLCONST: case CHARCONST: case INTCONST: case PTRCONST:
					// literal
					node.add(parseTerm());
					node.add(parseAccess());
					break;
				case IDENTIFIER:
					node.add(parseTerm());
					node.add(parseAccess());
					break;
				case LBRACE:
					node.add(parseTerm());
					node.add(parseAccess());
					break;
				case MEM: case DEL:
					currSymb = skip(node);
					node.add(parseTerm());
					break;
				case NEW:
					currSymb = skip(node);
					node.add(parseType());
					break;
				case LBRACKET:
					currSymb = skip(node);
					node.add(parseType());
					addLeafSymbol(node, Term.RBRACKET, "Type cast not closed with \"]\"");
					break;
				case ADD: case SUB: case NOT:
					currSymb = skip(node);
					node.add(parseTerm());
					break;
				default:
					throw new Report.Error("Not an expression [" + level + "].");
			}
		}

		if (simplify && node.subtree(1).location() == null) {
			return (DerNode) node.subtree(0);
		}
		return node;
	}


	private DerNode parseExprHelper(int level) {
		DerNode node = new DerNode(Nont.Expr1);
		getNextSymbol();
		dump("Expr level helper [" + level + "]");
		switch (currSymb.token) {
			case IOR: case XOR:
				if (level > 1) break;
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case AND:
				if (level > 2) break;
				else if (level < 2) throw new Report.Error("Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case EQU: case NEQ: case LEQ: case GEQ: case LTH: case GTH:
				if (level > 3) break;
				else if (level < 3) throw new Report.Error("Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case ADD: case SUB:
				if (level > 4) break;
				else if (level < 4) throw new Report.Error("Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case DIV: case MUL: case MOD:
				if (level < 5) throw new Report.Error("Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case RPARENTHESIS: case COMMA: case COLON: case RBRACE: case WHERE: case SEMIC:
			case ASSIGN: case THEN: case END: case ELSE: case DO: case RBRACKET: case EOF:
				break;
			default:
				throw new Report.Error("Not suitable symbol.");
		}
		return node;
	}


	private DerNode parseTerm() {
		DerNode node = new DerNode(Nont.Term);
		getNextSymbol();
		dump("Parse term");
		switch (currSymb.token) {
			case BOOLCONST: case INTCONST: case CHARCONST: case PTRCONST: case VOIDCONST:
				currSymb = skip(node);
				break;
			case LPARENTHESIS:
				currSymb = skip(node);
				node.add(parseExpr());
				addLeafSymbol(node, Term.RPARENTHESIS, "Expression not closed with \")\"");
				break;
			case IDENTIFIER:
				currSymb = skip(node);
				node.add(parseArgs());
				break;
			case LBRACE:
				currSymb = skip(node);
				node.add(parseStmt());
				node.add(parseStmtExtention());
				addLeafSymbol(node, Term.COLON, "Expected colon.");
				node.add(parseExpr());
				node.add(parseWhere());
				addLeafSymbol(node, Term.RBRACE, "Expected right brace.");
		}
		return node;
	}


	private DerNode parseArgs() {
		DerNode node = new DerNode(Nont.Args);
		getNextSymbol();
		dump("Parse args");
		switch (currSymb.token) {
			case LPARENTHESIS:
				// function call with args
				currSymb = skip(node);
				node.add(parseArg());
				addLeafSymbol(node, Term.RPARENTHESIS, "Expression not closed with \")\"");
				break;
			case BOOLCONST: case INTCONST: case CHARCONST: case PTRCONST: case VOIDCONST: case IDENTIFIER:
			case RBRACE: case NOT: case MEM: case NEW: case DEL: case VOID: case BOOL: case CHAR: case INT:
			case ARR: case REC: case PTR: case IF: case WHILE: case TYP: case FUN: case VAR:
				throw new Report.Error("Not suitable symbol as arguments.");
			default:
				// identifier access
				break;

		}
		return node;
	}


	private DerNode parseArg() {
		DerNode node = new DerNode(Nont.Arg);
		getNextSymbol();
		dump("Parse arg");
		switch (currSymb.token) {
			case ADD: case SUB: case BOOLCONST: case INTCONST: case CHARCONST: case PTRCONST: case VOIDCONST:
			case LPARENTHESIS: case IDENTIFIER: case LBRACE: case NEQ: case MEM: case NEW: case DEL: case LBRACKET:
				// function call with args
				node.add(parseExpr());
				node.add(parseArgExtension());
				break;
			case RPARENTHESIS:
				break;
			default:
				throw new Report.Error("Not suitable symbol as arguments.");
		}
		return node;

	}


	private DerNode parseArgExtension() {
		DerNode node = new DerNode(Nont.ArgExtension);
		getNextSymbol();
		dump("Parse argExtension");
		switch (currSymb.token) {
			case COMMA:
				currSymb = skip(node);
				node.add(parseExpr());
				node.add(parseArgExtension());
				break;
			case RPARENTHESIS:
				break;
			default:
				throw new Report.Error("Not suitable symbol as arguments.");
		}
		return node;

	}


	private DerNode parseAccess() {
		DerNode node = new DerNode(Nont.Access);
		getNextSymbol();
		dump("Parse access");
		switch (currSymb.token) {
			case LBRACKET:
				currSymb = skip(node);
				node.add(parseExpr());
				addLeafSymbol(node, Term.RBRACKET, "Expression not closed.");
				break;
			case DOT:
				currSymb = skip(node);
				addLeafSymbol(node, Term.IDENTIFIER, "Expression not identifier.");
				break;
			case IOR: case AND: case XOR: case EQU: case NEQ: case LEQ: case GEQ: case LTH: case GTH: case ADD:
			case SUB: case MUL: case DIV: case MOD: case RBRACKET: case RPARENTHESIS: case COMMA: case COLON:
			case RBRACE: case SEMIC: case WHERE: case ASSIGN: case THEN: case ELSE: case END: case DO: case EOF:
				break;
			default:
				throw new Report.Error("Not suitable symbol as arguments.");
		}
		return node;
	}


	private DerNode parseType() {
		DerNode node = new DerNode(Nont.Type);
		getNextSymbol();
		dump("Parse type");
		switch (currSymb.token) {
			case IDENTIFIER: case VOID: case BOOL: case CHAR: case INT:
				currSymb = skip(node);
				break;
			case ARR:
				currSymb = skip(node);
				addLeafSymbol(node, Term.LBRACKET, "Array type - Expected left closing bracket.");
				node.add(parseExpr());
				addLeafSymbol(node, Term.RBRACKET, "Array type - Expected right closing bracket.");
				node.add(parseType());
				break;
			case REC:
				currSymb = skip(node);
				addLeafSymbol(node, Term.LPARENTHESIS, "Record type - Expected left closing parenthesis.");
				addLeafSymbol(node, Term.IDENTIFIER, "Record type - Expected identifier.");
				addLeafSymbol(node, Term.COLON, "Record type - Expected colon.");
				node.add(parseType());
				node.add(parseIdentifiersExtension());
				addLeafSymbol(node, Term.RPARENTHESIS, "Record type - Expected right closing parenthesis.");
				break;
			case PTR:
				currSymb = skip(node);
				node.add(parseType());
				break;
			default:
				throw new Report.Error("Not suitable symbol for a type.");
		}
		return node;
	}


	private DerNode parseIdentifiers() {
		DerNode node = new DerNode(Nont.Identifiers);
		getNextSymbol();
		dump("Parse identifiers");
		switch (currSymb.token) {
			case RPARENTHESIS:
				break; // no identifiers
			case IDENTIFIER:
				currSymb = skip(node);
				addLeafSymbol(node, Term.COLON, "Expected right closing bracket.");
				node.add(parseType());
				node.add(parseIdentifiersExtension());
				break;
			default:
				throw new Report.Error("Not suitable symbol.");
		}
		return node;
	}


	private DerNode parseIdentifiersExtension() {
		DerNode node = new DerNode(Nont.IdentifiersExtention);
		getNextSymbol();
		dump("Parse identifiers");
		switch (currSymb.token) {
			case RPARENTHESIS:
				break;
			case COMMA:
				currSymb = skip(node);
				addLeafSymbol(node, Term.IDENTIFIER, "Expected identifier.");
				addLeafSymbol(node, Term.COLON, "Expected colon.");
				node.add(parseType());
				node.add(parseIdentifiersExtension());
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;
	}


	private DerNode parseStmt() {
		DerNode node = new DerNode(Nont.Stmt);
		getNextSymbol();
		dump("Parse stmt");
		switch (currSymb.token) {
			case ADD: case SUB: case BOOLCONST: case INTCONST: case CHARCONST: case PTRCONST: case VOIDCONST:
			case LPARENTHESIS: case IDENTIFIER: case LBRACE: case NEQ: case MEM: case NEW: case DEL: case LBRACKET:
				node.add(parseExpr());
				node.add(parseAssign());
				break;
			case IF:
				currSymb = skip(node);
				node.add(parseExpr());
				addLeafSymbol(node, Term.THEN, "[\"" + currSymb.lexeme + "\", " + currSymb.token + "]: Expected [\"then\", THEN] symbol.");
				node.add(parseStmt());
				node.add(parseStmtExtention());
				node.add(parseElse());
				addLeafSymbol(node, Term.END, "Expected end symbol.");
				break;
			case WHILE:
				currSymb = skip(node);
				node.add(parseExpr());
				addLeafSymbol(node, Term.DO, "Expected then symbol.");
				node.add(parseStmt());
				node.add(parseStmtExtention());
				addLeafSymbol(node, Term.END, "Expected end symbol.");
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;
	}


	private DerNode parseStmtExtention() {
		DerNode node = new DerNode(Nont.StmtExtension);
		getNextSymbol();
		dump("Parse stmts");
		switch (currSymb.token) {
			case SEMIC:
				currSymb = skip(node);
				node.add(parseStmt());
				node.add(parseStmtExtention());
				break;
			case COLON: case END: case ELSE:
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;
	}


	private DerNode parseElse() {
		DerNode node = new DerNode(Nont.Else);
		getNextSymbol();
		dump("Parse else");
		switch (currSymb.token) {
			case ELSE:
				currSymb = skip(node);
				node.add(parseStmt());
				node.add(parseStmtExtention());
				break;
			case END:
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;
	}


	private DerNode parseAssign() {
		DerNode node = new DerNode(Nont.Assign);
		getNextSymbol();
		dump("Parse assign");
		switch (currSymb.token) {
			case ASSIGN:
				currSymb = skip(node);
				node.add(parseExpr());
				break;
			case COLON: case RBRACE: case SEMIC: case END: case ELSE:
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;
	}


	private DerNode parseDecl() {
		DerNode node = new DerNode(Nont.Decl);
		getNextSymbol();
		dump("Parse declaration");
		switch (currSymb.token) {
			case TYP: case VAR:
				currSymb = skip(node);
				addLeafSymbol(node, Term.IDENTIFIER, "Expected identifier.");
				addLeafSymbol(node, Term.COLON, "Expected colon.");
				node.add(parseType());
				break;
			case FUN:
				currSymb = skip(node);
				addLeafSymbol(node, Term.IDENTIFIER, "Expected identifier.");
				addLeafSymbol(node, Term.LPARENTHESIS, "Expected left parenthesis.");
				node.add(parseIdentifiers());
				addLeafSymbol(node, Term.RPARENTHESIS, "Expected right parenthesis.");
				addLeafSymbol(node, Term.COLON, "Expected colon.");
				node.add(parseType());
				node.add(parseAssign());
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;
	}


	private DerNode parseWhere() {
		DerNode node = new DerNode(Nont.Where);
		getNextSymbol();
		dump("Parse where");
		switch (currSymb.token) {
			case WHERE:
				currSymb = skip(node);
				node.add(parseDecl());
				node.add(parseDeclExtension());
				break;
			case RBRACE:
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;

	}


	private DerNode parseDeclExtension() {
		DerNode node = new DerNode(Nont.DeclExtension);
		getNextSymbol();
		dump("Parse declarations");
		switch (currSymb.token) {
			case SEMIC:
				currSymb = skip(node);
				node.add(parseDecl());
				node.add(parseDeclExtension());
				break;
			case RBRACE:
				break;
			default:
				throw new Report.Error("Not suitable symbol for identifiers.");
		}
		return node;
	}
}
