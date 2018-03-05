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

	/** The name of the source file. */
	private final String srcFileName;

	/** The source file reader. */
	private final Reader srcFile;

	/** StringBuilder to build longest lexeme */
	private StringBuilder lexemeBuilder;

	/** Indentification of column and line number for positioning */
	private int line;
	private int column;
	private char currChar;
	private boolean eof = false;

	/** Hash set of keywords and literals for fast access. */
	private static final HashMap<String, Term> keywords;

	// @formatter:off
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
			srcFile.mark(2);
		} catch (IOException ___) {
			throw new Report.Error("Cannot open source file '" + srcFileName + "'.");
		}
		lexemeBuilder = new StringBuilder();
		column = 1;
		line = 1;
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

	// --- LEXER ---
	private char readNext() throws IOException {
		int currChar = srcFile.read();

		// check if non-ascii and skip
		while (128 < currChar) {
			currChar = srcFile.read();
			column++;
			Report.warning(new Location(line, column), "Skipping non ascii character " + currChar);
		}

		// check if new line
		if (currChar == '\n') {
			column = 0;
			line++;
		} else {
			column++;
		}

		// check if end of file
		if (currChar == -1) {
			eof = true;
			throw new EOFException();
		}

		return (char) currChar;
	}

	private enum State {INITIAL, IDENTIFIER, LITERALCHAR, LITERALINT, SYMBOL, LITERALCHAREND, FINAL}

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
		try {
			if (eof) {
				return new Symbol(Term.EOF, "", new Location(line, column, endLine, endColumn));
			}
			currChar = (column == 1 && line == 1) ? readNext() : currChar;
			// System.out.println("Curr: " + currChar);
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
			State state = State.INITIAL;

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
							lexemeBuilder.append(currChar);
							token = Term.IDENTIFIER;
							state = State.IDENTIFIER;
							// start of literal of type char
						} else if (currChar == '\'') {
							lexemeBuilder.append(currChar);
							state = State.LITERALCHAR;
							// start of a symbol
						} else if (symbols.containsKey(String.valueOf(currChar))) {
							lexemeBuilder.append(currChar);
							token = symbols.get(String.valueOf(currChar));
							state = State.SYMBOL;
							// start of literal of type int
						} else if ('0' <= currChar && currChar <= '9') {
							lexemeBuilder.append(currChar);
							token = Term.INTCONST;
							state = State.LITERALINT;
						} else {
							step = false;
						}
						break;
					case IDENTIFIER:
						isAlphaUnderscore = ('a' <= currChar && currChar <= 'z') ||
								('A' <= currChar && currChar <= 'Z') ||
								currChar == '_';
						if (isAlphaUnderscore || ('0' <= currChar && currChar <= '9')) {
							lexemeBuilder.append(currChar);
							token = Term.IDENTIFIER;
							state = State.IDENTIFIER;
						} else {
							step = false;
						}
						break;
					case SYMBOL:
						if (currChar == '=' && (lexemeBuilder.charAt(0) == '!' || (lexemeBuilder.charAt(0) == '<') ||
								(lexemeBuilder.charAt(0) == '>') || (lexemeBuilder.charAt(0) == '='))) {
							lexemeBuilder.append(currChar);
							token = symbols.get(lexemeBuilder.toString());
							state = State.FINAL;
						} else {
							step = false;
						}
						break;
					case LITERALCHAR:
						if (currChar < ' ' || '~' < currChar) {
							throw new Report.Error(new Location(endLine, endColumn), "Wrong character code.");
						}
						lexemeBuilder.append(currChar);
						token = Term.CHARCONST;
						state = State.LITERALCHAREND;
						//step = true;
						break;
					case LITERALCHAREND:
						if (currChar != '\'') {
							throw new Report.Error(new Location(endLine, endColumn), "Char const too long.");
						}
						lexemeBuilder.append(currChar);
						token = Term.CHARCONST;
						state = State.FINAL;
						break;
					case LITERALINT:
						if ('0' <= currChar && currChar <= '9') {
							lexemeBuilder.append(currChar);
							token = Term.INTCONST;
							state = State.LITERALINT;
						} else {
							step = false;
						}
						break;
					case FINAL:
						step = false;
					default:
				}

				// read next character
				if (step) {
					endColumn = column;
					endLine = line;
					currChar = readNext();
				}
			}
		} catch (EOFException e) {
			// TODO: check if EOF is okay
		} catch (IOException e) {
			throw new Report.InternalError();
		}

		// get lexeme and correct token to literal/keyword if necessary
		String lexeme = lexemeBuilder.toString();
		if (keywords.containsKey(lexeme)) {
			token = keywords.get(lexeme);
		} else if (literalsConst.containsKey(lexeme)) {
			token = literalsConst.get(lexeme);
		}

		//System.out.println("Lex: " + lexeme);
		return new Symbol(token, lexeme, new Location(begLine, begColumn, endLine, endColumn));
	}

}
