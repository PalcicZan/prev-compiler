package compiler.phases.lexan;

import java.io.*;
import java.util.HashMap;

import common.report.*;
import compiler.phases.*;

/**
 * Lexical analysis.
 *
 * @author sliva
 */
public class LexAn extends Phase {

	/** Settings for warning/errors and debug mode */
	private final boolean debug = false;
	private final boolean completePhase = true;

	/** The name of the source file. */
	private final String srcFileName;

	/** The source file reader. */
	private final BufferedReader srcFile;

	/** StringBuilder to build longest lexeme */
	private StringBuilder lexemeBuilder;

	/** Identification for column and line for positioning */
	private int line;
	private int column;
	/** Current character */
	private int c;

	// @formatter:off
	/** Hash set of keywords and literals for fast access. */
	private static final HashMap<String, Term> keywords;
	static {
		keywords = new HashMap<String, Term>();
		keywords.put("arr", Term.ARR); keywords.put("bool", Term.BOOL); keywords.put("char", Term.CHAR);
		keywords.put("del", Term.DEL); keywords.put("do", Term.DO); keywords.put("else", Term.ELSE);
		keywords.put("end", Term.END); keywords.put("fun", Term.FUN); keywords.put("if", Term.IF);
		keywords.put("int", Term.INT); keywords.put("new", Term.NEW); keywords.put("ptr", Term.PTR);
		keywords.put("rec", Term.REC); keywords.put("then", Term.THEN); keywords.put("typ", Term.TYP);
		keywords.put("var", Term.VAR); keywords.put("void", Term.VOID); keywords.put("where", Term.WHERE);
		keywords.put("while", Term.WHILE);
	}
	private static final HashMap<String, Term> symbols;
	static {
		symbols = new HashMap<String, Term>();
		symbols.put("!", Term.NOT); symbols.put("|", Term.IOR); symbols.put("^", Term.XOR);
		symbols.put("&", Term.AND); symbols.put("<", Term.LTH); symbols.put(">", Term.GTH);
		symbols.put("+", Term.ADD); symbols.put("-", Term.SUB); symbols.put("*", Term.MUL);
		symbols.put("/", Term.DIV);	symbols.put("%", Term.MOD); symbols.put("$", Term.MEM);
		symbols.put("@", Term.VAL); symbols.put("!=", Term.NEQ); symbols.put("<=", Term.LEQ);
		symbols.put(">=", Term.GEQ); symbols.put("==", Term.EQU); symbols.put("=", Term.ASSIGN);
		symbols.put(".", Term.DOT); symbols.put(",", Term.COMMA); symbols.put(":", Term.COLON);
		symbols.put(";", Term.SEMIC); symbols.put("[", Term.LBRACKET); symbols.put("]", Term.RBRACKET);
		symbols.put("(", Term.LPARENTHESIS); symbols.put(")", Term.RPARENTHESIS); symbols.put("{", Term.LBRACE);
		symbols.put("}", Term.RBRACE);
	}
	private static final HashMap<String, Term> literalsConst;
	static {
		literalsConst = new HashMap<String, Term>();
		literalsConst.put("none", Term.VOIDCONST); literalsConst.put("true", Term.BOOLCONST);
		literalsConst.put("false", Term.BOOLCONST); literalsConst.put("null", Term.PTRCONST);
	}
	// @formatter:on


	/**
	 * Constructs a new lexical analysis phase.
	 */
	public LexAn() {
		super("lexan");
		srcFileName = compiler.Main.cmdLineArgValue("--src-file-name");
		try {
			srcFile = new BufferedReader(new FileReader(srcFileName));
			// read first character in advance
			try {
				column = 0;
				line = 1;
				c = readNext();
			} catch (EOFException ___) {
				Report.warning("Source file '" + this.srcFileName + "' is empty.");
			}
		} catch (IOException ___) {
			throw new Report.Error("Cannot open source file '" + srcFileName + "'.");
		}
		lexemeBuilder = new StringBuilder();
	}

	/**
	 * The lexer.
	 * <p>
	 * This method returns the next symbol from the source file. To perform the
	 * lexical analysis of the entire source file, this method must be called
	 * until it returns EOF. This method calls {@link #lexify()}, logs its
	 * result if requested, and returns it.
	 *
	 * @return The next symbol from the source file.
	 */
	public Symbol lexer() {
		Symbol symb = lexify();
		symb.log(logger);
		return symb;
	}

	@Override
	public void close() {
		try {
			srcFile.close();
		} catch (IOException ___) {
			Report.warning("Cannot close source file '" + this.srcFileName + "'.");
		}
		super.close();
	}

	/**
	 *
	 *
	 * */
	private char readNext() throws IOException {
		// check if new line
		if (c == '\n') {
			column = 1;
			line++;
		} else {
			column++;
		}

		c = srcFile.read();

		// check if non-ascii - warning and skip\error
		while (128 < c) {
			report(new Location(line, column++), "Non ASCII character: " + (char) c + " [" + c + "]");
			c = srcFile.read();
		}

		// check if end of file
		if (c == -1) {
			throw new EOFException();
		}

		return (char) c;
	}


	/** Set warning message and continue compilation or throw an error */
	private void report(Location location, String msg) {
		if (completePhase) {
			Report.warning(location, msg);
		} else {
			throw new Report.Error(location, msg);
		}
	}

	/** States of state machine */
	private enum State {
		INITIAL, IDENTIFIER, LITERALCHAR, LITERALINT, SYMBOL, LITERALCHAREND, ADVANCEANDFINISH
	}

	/**
	 * Performs the lexical analysis of the source file.
	 * <p>
	 * This method returns the next symbol from the source file. To perform the
	 * lexical analysis of the entire source file, this method must be called
	 * until it returns EOF.
	 *
	 * @return The next symbol from the source file or EOF if no symbol is
	 * available any more.
	 */
	private Symbol lexify() throws Report.Error {

		Term token = Term.EOF;
		// reset lexeme builder
		lexemeBuilder.setLength(0);

		// local symbol location
		int begColumn = column;
		int begLine = line;
		int endColumn = column;
		int endLine = line;

		// end of file
		if (c == -1) return new Symbol(Term.EOF, "", new Location(line, column));

		char currChar = (char) c;
		State state = State.INITIAL;
		try {

			// skip comments and white spaces
			while (currChar == '#' || currChar == ' ' || currChar == '\t' || currChar == '\r' || currChar == '\n') {
				if (currChar == '#') {
					while (currChar != '\n') {
						currChar = readNext();
					}
				}
				currChar = readNext();
			}

			begColumn = column;
			begLine = line;

			boolean step = true;
			boolean isAlphaUnderscore;
			/* state machine implementation */
			while (step) {
				switch (state) {
					case INITIAL:
						// start of identifier
						isAlphaUnderscore = ('a' <= currChar && currChar <= 'z') ||
								('A' <= currChar && currChar <= 'Z') ||
								currChar == '_';
						if (isAlphaUnderscore) {
							token = Term.IDENTIFIER;
							state = State.IDENTIFIER;
							// start of literal of type char
						} else if (currChar == '\'') {
							state = State.LITERALCHAR;
							token = Term.CHARCONST;
							// start of a symbol
						} else if (symbols.containsKey(String.valueOf(currChar))) {
							token = symbols.get(String.valueOf(currChar));
							state = State.SYMBOL;
							// start of literal of type int
						} else if ('0' <= currChar && currChar <= '9') {
							token = Term.INTCONST;
							state = State.LITERALINT;
						} else {
							token = Term.ERROR;
							report(new Location(line, column), "Unknown character: " + currChar);
							state = State.ADVANCEANDFINISH;
						}
						break;
					case IDENTIFIER:
						isAlphaUnderscore = ('a' <= currChar && currChar <= 'z') ||
								('A' <= currChar && currChar <= 'Z') ||
								currChar == '_';
						if (isAlphaUnderscore || ('0' <= currChar && currChar <= '9')) {
							token = Term.IDENTIFIER;
							state = State.IDENTIFIER;
						} else {
							step = false;
						}
						break;
					case SYMBOL:
						if (currChar == '=' && (lexemeBuilder.charAt(0) == '!' || (lexemeBuilder.charAt(0) == '<') ||
								(lexemeBuilder.charAt(0) == '>') || (lexemeBuilder.charAt(0) == '='))) {
							token = symbols.get(lexemeBuilder.toString());
							state = State.ADVANCEANDFINISH;
						} else {
							step = false;
						}
						break;
					case LITERALCHAR:
						if (currChar < ' ' || '~' < currChar) {
							report(new Location(line, column), "Not valid character: " + (int) currChar + ".");
						}
						token = Term.CHARCONST;
						state = State.LITERALCHAREND;
						break;
					case LITERALCHAREND:
						if (currChar != '\'') {
							report(new Location(begLine, begColumn, line, column), "Char const too long/not closed.");
						}
						token = Term.CHARCONST;
						state = State.ADVANCEANDFINISH;
						break;
					case LITERALINT:
						if ('0' <= currChar && currChar <= '9') {
							token = Term.INTCONST;
							state = State.LITERALINT;
						} else {
							step = false;
						}
						break;
					case ADVANCEANDFINISH:
						step = false;
				}

				// remember position and read next character
				if (step) {
					lexemeBuilder.append(currChar);
					endColumn = column;
					endLine = line;
					currChar = readNext();
				}
			}
		} catch (EOFException ___) {
			// Not enclosed
			if (state == State.LITERALCHAR || state == State.LITERALCHAREND) {
				report(new Location(begLine, begColumn, endLine, endColumn), "Char constant not closed.");
			} else if (token == Term.EOF) {
				return new Symbol(token, "", new Location(line, column));
			}
		} catch (IOException ___) {
			throw new Report.InternalError();
		}

		// get lexeme and correct token to literal/keyword if necessary
		String lexeme = lexemeBuilder.toString();
		if (keywords.containsKey(lexeme)) {
			token = keywords.get(lexeme);
		} else if (literalsConst.containsKey(lexeme)) {
			token = literalsConst.get(lexeme);
		}

		if (debug) System.out.println("Lex: " + lexeme);
		return new Symbol(token, lexeme, new Location(begLine, begColumn, endLine, endColumn));
	}

}
