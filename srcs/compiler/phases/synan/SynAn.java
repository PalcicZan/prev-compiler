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


	private static final boolean debug = false;
	private final boolean completePhase = false;

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

	/** Expressions CFG nonterminals for different levels of precedence */
	private static final Nont[] exprNont = {
		Nont.ExprXorOr, Nont.ExprAnd, Nont.ExprCompare, Nont.ExprAddSub,
		Nont.ExprMulDiv, Nont.ExprUnary, Nont.ExprAccess
	};

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
			if (debug) System.out.println("Skip: " + currSymb.token + " [" + currSymb.lexeme + "]");
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
			report(currSymb, errorMsg);
		currSymb = skip(node);
	}


	private void dump(String msg) {
		if (debug) System.out.println(msg + ": '" + currSymb.lexeme + "' (" + currSymb.token + ").");
	}


	private void report(Symbol symbol, String msg) {
		String finalMsg = "Unexpected '" + symbol.lexeme + "' (" + symbol.token + "): " + msg;
		if (completePhase) {
			Report.warning(symbol.location(), finalMsg);
		} else {
			throw new Report.Error(symbol.location(), finalMsg);
		}
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
			case ADD: case SUB:
			case NOT: case MEM: case VAL: case NEW: case DEL:
			case IDENTIFIER:
			case VOIDCONST: case BOOLCONST: case CHARCONST: case INTCONST: case PTRCONST:
			case LPARENTHESIS: case LBRACE: case LBRACKET:
				node.add(parseExprOnLevel(2));
				node.add(parseExprHelper(1));
				break;
			default:
				report(currSymb, "Expressions cannot start with that symbol.");
		}
		return node;
	}


	private DerNode parseExprOnLevel(int level) {
		DerNode node = new DerNode(exprNont[level - 1]);
		getNextSymbol();
		dump("Parse expr [" + level + "]");
		if (level < 6) {
			switch (currSymb.token) {
				case ADD: case SUB:
				case NOT: case MEM: case VAL: case NEW: case DEL:
				case IDENTIFIER:
				case VOIDCONST: case BOOLCONST: case CHARCONST: case INTCONST: case PTRCONST:
				case LPARENTHESIS: case LBRACE: case LBRACKET:
					node.add(parseExprOnLevel(level + 1));
					node.add(parseExprHelper(level));
					break;
				default:
					report(currSymb, "Not an expression [" + level + "].");
			}
		} else if (level == 6) {
			switch (currSymb.token) {
				case ADD: case SUB: case NOT:
				case MEM: case VAL: case DEL:
					currSymb = skip(node);
					node.add(parseExprOnLevel(level));
					break;
				case NEW:
					currSymb = skip(node);
					node.add(parseType());
					break;
				case LBRACKET:
					currSymb = skip(node);
					node.add(parseType());
					addLeafSymbol(node, Term.RBRACKET, "Type cast not closed with ']'");
					node.add(parseExprOnLevel(level));
					break;
				case IDENTIFIER:
				case VOIDCONST: case BOOLCONST: case CHARCONST: case INTCONST: case PTRCONST:
				case LBRACE: case LPARENTHESIS:
					node.add(parseExprOnLevel(level + 1));
					break;
				default:
					report(currSymb, "Not an expression [" + level + "].");
			}
		} else if (level == 7) {
			switch (currSymb.token) {
				case IDENTIFIER:
				case VOIDCONST: case BOOLCONST: case CHARCONST: case INTCONST: case PTRCONST:
				case LBRACE: case LPARENTHESIS:
					node.add(parseTerm());
					node.add(parseAccess());
					break;
				default:
					report(currSymb, "Not an expression on level: " + level + ".");
			}
		}
		return node;
	}


	private DerNode parseExprHelper(int level) {
		DerNode node = new DerNode(exprNont[level - 1]);
		getNextSymbol();
		dump("Parse exprHelp [" + level + "]");
		switch (currSymb.token) {
			case IOR: case XOR:
				if (level > 1) break;
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case AND:
				if (level > 2) break;
				else if (level < 2) report(currSymb, "Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case EQU: case NEQ: case LEQ: case GEQ: case LTH: case GTH:
				if (level > 3) break;
				else if (level < 3) report(currSymb, "Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				break;
			case ADD: case SUB:
				if (level > 4) break;
				else if (level < 4) report(currSymb, "Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case DIV: case MUL: case MOD:
				if (level < 5) report(currSymb, "Not suitable symbol.");
				currSymb = skip(node);
				node.add(parseExprOnLevel(level + 1));
				node.add(parseExprHelper(level));
				break;
			case RBRACKET: case RPARENTHESIS: case RBRACE: case COMMA: case COLON: case SEMIC:
			case WHERE: case DO: case THEN: case END: case ASSIGN: case ELSE: case EOF:
				break;
			default:
				report(currSymb, "Not suitable symbol.");
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
				addLeafSymbol(node, Term.RPARENTHESIS, "Expected ')' to enclose enclosed expression.");
				break;
			case IDENTIFIER:
				currSymb = skip(node);
				node.add(parseArgs());
				break;
			case LBRACE:
				currSymb = skip(node);
				node.add(parseStmt());
				node.add(parseStmtExtention());
				addLeafSymbol(node, Term.COLON, "Expected ':'.");
				node.add(parseExpr());
				node.add(parseWhere());
				addLeafSymbol(node, Term.RBRACE, "Expected ']'.");
				break;
			default:
				report(currSymb, "Not suitable symbol to start a term expression.");
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
				addLeafSymbol(node, Term.RPARENTHESIS, "Expression not closed with ')'.");
				break;
			case IOR: case XOR: case AND: case EQU: case NEQ: case LEQ: case GEQ: case LTH: case GTH: case ADD:
			case SUB: case MUL: case DIV: case MOD: case RBRACKET: case RPARENTHESIS: case COMMA: case COLON:
			case RBRACE: case SEMIC: case WHERE: case ASSIGN: case THEN: case ELSE: case END: case DO: case EOF:
			case LBRACKET: case DOT:
				break;
			default:
				report(currSymb, "Not suitable to start arguments.");
				break;

		}
		return node;
	}


	private DerNode parseArg() {
		DerNode node = new DerNode(Nont.Arg);
		getNextSymbol();
		dump("Parse arg");
		switch (currSymb.token) {
			case ADD: case SUB: case BOOLCONST: case INTCONST: case CHARCONST: case PTRCONST:
			case VOIDCONST: case LPARENTHESIS: case IDENTIFIER: case LBRACE: case NEQ:
			case MEM: case VAL: case NEW: case DEL: case LBRACKET:
				// function call with args
				node.add(parseExpr());
				node.add(parseArgExtension());
				break;
			case RPARENTHESIS:
				break;
			default:
				report(currSymb, "Not suitable symbol for arguments.");
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
				report(currSymb, "Not suitable symbol for arguments.");
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
				addLeafSymbol(node, Term.RBRACKET, "Expected right bracket to enclose element access.");
				node.add(parseAccess());
				break;
			case DOT:
				currSymb = skip(node);
				addLeafSymbol(node, Term.IDENTIFIER, "Expected identifier to access component.");
				node.add(parseAccess());
				break;
			case IOR: case XOR: case AND: case EQU: case NEQ: case LEQ: case GEQ: case LTH: case GTH: case ADD:
			case SUB: case MUL: case DIV: case MOD: case RBRACKET: case RPARENTHESIS: case COMMA: case COLON:
			case RBRACE: case SEMIC: case WHERE: case ASSIGN: case THEN: case ELSE: case END: case DO: case EOF:
				break;
			default:
				report(currSymb, "Not suitable symbol for component/element access.");
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
				addLeafSymbol(node, Term.LBRACKET, "Array type; Expected '['.");
				node.add(parseExpr());
				addLeafSymbol(node, Term.RBRACKET, "Array type; Expected ']' to enclose array definition.");
				node.add(parseType());
				break;
			case REC:
				currSymb = skip(node);
				addLeafSymbol(node, Term.LPARENTHESIS, "Record type; Expected '('.");
				addLeafSymbol(node, Term.IDENTIFIER, "Record type; Expected identifier.");
				addLeafSymbol(node, Term.COLON, "Record type; Expected ':'.");
				node.add(parseType());
				node.add(parseIdentifiersExtension());
				addLeafSymbol(node, Term.RPARENTHESIS, "Record type; Expected ')' at the end of rec type declaration.");
				break;
			case PTR:
				currSymb = skip(node);
				node.add(parseType());
				break;
			default:
				report(currSymb, "Not suitable symbol for a type.");
		}
		return node;
	}


	private DerNode parseIdentifiers() {
		DerNode node = new DerNode(Nont.Identifiers);
		getNextSymbol();
		dump("Parse identifiers");
		switch (currSymb.token) {
			case IDENTIFIER:
				currSymb = skip(node);
				addLeafSymbol(node, Term.COLON, "Expected ':'.");
				node.add(parseType());
				node.add(parseIdentifiersExtension());
				break;
			case RPARENTHESIS:
				break; // no identifiers
			default:
				report(currSymb, "Not suitable symbol for arguments declaration.");
		}
		return node;
	}


	private DerNode parseIdentifiersExtension() {
		DerNode node = new DerNode(Nont.IdentifiersExtension);
		getNextSymbol();
		dump("Parse identifiers");
		switch (currSymb.token) {
			case COMMA:
				currSymb = skip(node);
				addLeafSymbol(node, Term.IDENTIFIER, "Expected identifier.");
				addLeafSymbol(node, Term.COLON, "Expected ':'.");
				node.add(parseType());
				node.add(parseIdentifiersExtension());
				break;
			case RPARENTHESIS:
				break;
			default:
				report(currSymb, "Not suitable symbol for identifiers.");
		}
		return node;
	}


	private DerNode parseStmt() {
		DerNode node = new DerNode(Nont.Stmt);
		getNextSymbol();
		dump("Parse stmt");
		switch (currSymb.token) {
			case ADD: case SUB: case BOOLCONST: case INTCONST: case CHARCONST: case PTRCONST:
			case VOIDCONST: case LPARENTHESIS: case IDENTIFIER: case LBRACE: case NOT:
			case VAL: case MEM: case NEW: case DEL: case LBRACKET:
				node.add(parseExpr());
				node.add(parseAssign());
				break;
			case IF:
				currSymb = skip(node);
				node.add(parseExpr());
				addLeafSymbol(node, Term.THEN, "Expected 'then' symbol.");
				node.add(parseStmt());
				node.add(parseStmtExtention());
				node.add(parseElse());
				addLeafSymbol(node, Term.END, "Expected 'end' symbol.");
				break;
			case WHILE:
				currSymb = skip(node);
				node.add(parseExpr());
				addLeafSymbol(node, Term.DO, "Expected 'do' symbol.");
				node.add(parseStmt());
				node.add(parseStmtExtention());
				addLeafSymbol(node, Term.END, "Expected 'end' symbol.");
				break;
			default:
				report(currSymb, "Not suitable symbol for statement.");
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
				report(currSymb, "Expected ';' symbol for more statements, ':', 'end' or 'else' symbols.");
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
				report(currSymb, "Expected 'else' or 'end' symbol.");
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
				report(currSymb, "Expected '=' to declare function body.");
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
				report(currSymb, "Not suitable symbol for where statement. Expected 'where' or '}' symbol.");
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
				addLeafSymbol(node, Term.IDENTIFIER, "Expected typ/var identifier.");
				addLeafSymbol(node, Term.COLON, "Expected ':' symbol.");
				node.add(parseType());
				break;
			case FUN:
				currSymb = skip(node);
				addLeafSymbol(node, Term.IDENTIFIER, "Expected function identifier.");
				addLeafSymbol(node, Term.LPARENTHESIS, "Expected '(' symbol.");
				node.add(parseIdentifiers());
				addLeafSymbol(node, Term.RPARENTHESIS, "Expected ')' symbol.");
				addLeafSymbol(node, Term.COLON, "Expected ':' symbol.");
				node.add(parseType());
				node.add(parseAssign());
				break;
			default:
				report(currSymb, "Not suitable symbol for declaration. Expected 'fun', 'var' or 'typ' symbol.");
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
				report(currSymb, "Expected ';' for more decelerations or '}' symbol.");
		}
		return node;
	}
}
