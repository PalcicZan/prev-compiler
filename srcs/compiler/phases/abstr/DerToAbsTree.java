package compiler.phases.abstr;

import java.util.*;

import common.report.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.lexan.Term;
import compiler.phases.synan.*;
import compiler.phases.synan.dertree.*;

/**
 * Transforms a derivation tree into an abstract syntax tree.
 *
 * @author sliva
 */
public class DerToAbsTree implements DerVisitor<AbsTree, AbsTree> {

	private static final boolean debug = false;

	private void dump(DerTree node, String msg) {
		if (debug) System.out.println("[" + node.location() + "]: " + msg);
	}

	private void skip(DerNode node, int index) {
		node.subtree(index).accept(this, null);
	}

	@Override
	public AbsTree visit(DerLeaf leaf, AbsTree visArg) {
		dump(leaf, "Visit leaf: " + leaf.symb + ": " + visArg);
		switch (leaf.symb.token) {
			case INTCONST:
				return new AbsAtomExpr(leaf.location(), AbsAtomExpr.Type.INT, leaf.symb.lexeme);
			case VOIDCONST:
				return new AbsAtomExpr(leaf.location(), AbsAtomExpr.Type.VOID, leaf.symb.lexeme);
			case BOOLCONST:
				return new AbsAtomExpr(leaf.location(), AbsAtomExpr.Type.BOOL, leaf.symb.lexeme);
			case PTRCONST:
				return new AbsAtomExpr(leaf.location(), AbsAtomExpr.Type.PTR, leaf.symb.lexeme);
			case CHARCONST:
				return new AbsAtomExpr(leaf.location(), AbsAtomExpr.Type.CHAR, leaf.symb.lexeme);
			case INT:
				return new AbsAtomType(leaf.location(), AbsAtomType.Type.INT);
			case BOOL:
				return new AbsAtomType(leaf.location(), AbsAtomType.Type.BOOL);
			case VOID:
				return new AbsAtomType(leaf.location(), AbsAtomType.Type.VOID);
			case CHAR:
				return new AbsAtomType(leaf.location(), AbsAtomType.Type.CHAR);
			case IDENTIFIER:
				dump(leaf, "Identifier " + visArg + ".");
				if (visArg instanceof AbsTypeDecl) {
					return new AbsTypeName(leaf.location(), leaf.symb.lexeme);
				} else if (visArg instanceof AbsArgs) {
					return new AbsFunName(leaf.location(), leaf.symb.lexeme, (AbsArgs) visArg);
				} else {
					return new AbsVarName(leaf.location(), leaf.symb.lexeme);
				}
			default:
				return visArg;
		}
	}

	@Override
	public AbsTree visit(DerNode node, AbsTree visArg) {
		dump(node, "Visit node: " + node.label);
		if (node.location() == null) return visArg;

		AbsTree at;
		switch (node.label) {
			case Source:
				at = node.subtree(0).accept(this, null);
				break;
			case Expr:
				at = node.subtree(0).accept(this, visArg);
				at = node.subtree(1).accept(this, at);
				break;
			case ExprXorOr: case ExprAnd: case ExprCompare: case ExprAddSub: case ExprMulDiv:
				at = parseAbsBinExpr(node, visArg);
				break;
			case ExprUnary:
				at = node.subtree(0).accept(this, visArg);
				if (node.subtree(0) instanceof DerLeaf) {
					at = node.subtree(1).accept(this, at);
					switch (((DerLeaf) node.subtree(0)).symb.token) {
						case NOT:
							at = new AbsUnExpr(node.location(), AbsUnExpr.Oper.NOT, (AbsExpr) at);
							break;
						case SUB:
							at = new AbsUnExpr(node.location(), AbsUnExpr.Oper.SUB, (AbsExpr) at);
							break;
						case ADD:
							at = new AbsUnExpr(node.location(), AbsUnExpr.Oper.ADD, (AbsExpr) at);
							break;
						case MEM:
							at = new AbsUnExpr(node.location(), AbsUnExpr.Oper.MEM, (AbsExpr) at);
							break;
						case VAL:
							at = new AbsUnExpr(node.location(), AbsUnExpr.Oper.VAL, (AbsExpr) at);
							break;
						case NEW:
							at = new AbsNewExpr(node.location(), (AbsType) at);
							break;
						case DEL:
							at = new AbsDelExpr(node.location(), (AbsExpr) at);
							break;
						case LBRACKET:
							// typecast
							skip(node, 2); // right bracket
							AbsTree expr = node.subtree(3).accept(this, at);
							at = new AbsCastExpr(node.location(), (AbsType) at, (AbsExpr) expr);
							break;
					}
				}
				break;
			case ExprAccess:
				at = node.subtree(0).accept(this, visArg);
				at = node.subtree(1).accept(this, at);
				break;
			case Access:
				node.subtree(0).accept(this, visArg);
				at = node.subtree(1).accept(this, visArg);
				if (node.subtree(0) instanceof DerLeaf) {
					switch (((DerLeaf) node.subtree(0)).symb.token) {
						case LBRACKET:
							// array access
							AbsExpr arrExpr = new AbsArrExpr(node.location(), (AbsExpr) visArg, (AbsExpr) at);
							at = arrExpr.relocate(new Location(visArg, arrExpr));
							at = node.subtree(2).accept(this, at);
							at = node.subtree(3).accept(this, at);
							break;
						case DOT:
							// record access
							DerLeaf var = (DerLeaf) node.subtree(1);
							AbsExpr recExpr = new AbsRecExpr(node.location(), (AbsExpr) visArg, new AbsVarName(var.location(), var.symb.lexeme));
							at = recExpr.relocate(new Location(visArg, recExpr));
							at = node.subtree(2).accept(this, at);
							break;
					}
				}
				break;
			case Stmt:
				DerTree firstTree = node.subtree(0);
				at = firstTree.accept(this, visArg);
				// if or while statement
				if (firstTree instanceof DerLeaf) {
					switch (((DerLeaf) firstTree).symb.token) {
						case IF:
							skip(node, 2); // then
							skip(node, 6); // end
							AbsExpr cond = (AbsExpr) node.subtree(1).accept(this, null);
							AbsStmts thenBody = getStmts(node, 3);
							AbsStmts elseBody = (AbsStmts) node.subtree(5).accept(this, null);
							if (elseBody == null) elseBody = new AbsStmts(null, new Vector<>());
							at = new AbsIfStmt(node.location(), cond, thenBody, elseBody);
							break;
						case WHILE:
							skip(node, 2); // do
							skip(node, 5); // end
							AbsExpr whileCond = (AbsExpr) node.subtree(1).accept(this, null);
							AbsStmts whileBody = getStmts(node, 3);
							at = new AbsWhileStmt(node.location(), whileCond, whileBody);
							break;
						default:
							throw new Report.Error("Not valid statement!");
					}
				} else {
					AbsExpr assignExpr = (AbsExpr) node.subtree(1).accept(this, null);
					if (assignExpr == null) {
						// expression
						at = new AbsExprStmt(node.location(), (AbsExpr) at);
					} else {
						// assign
						at = new AbsAssignStmt(node.location(), (AbsExpr) at, assignExpr);
					}
				}
				break;
			case Else:
				skip(node, 0);
				at = getStmts(node, 1);
				break;
			case Term:
				at = node.subtree(0).accept(this, visArg);
				if (node.subtree(0) instanceof DerLeaf) {
					switch (((DerLeaf) node.subtree(0)).symb.token) {
						case LBRACE:
							skip(node, 3); // colon
							skip(node, 6); // right brace

							// statements
							AbsStmts stmts = getStmts(node, 1);
							// expression
							AbsExpr expr = (AbsExpr) node.subtree(4).accept(this, visArg);
							// decls
							AbsDecls absDecls = (AbsDecls) node.subtree(5).accept(this, visArg);
							if (absDecls == null) absDecls = new AbsDecls(null, new Vector<>());
							at = new AbsStmtExpr(node.location(), absDecls, stmts, expr);
							break;
						case LPARENTHESIS:
							// enclosed expression
							skip(node, 2); // right parenthesis
							at = node.subtree(1).accept(this, at);
							if (at instanceof AbsBinExpr) {
								at = ((AbsBinExpr) at).relocate(node.location());
							}
							break;
						case IDENTIFIER:
							at = node.subtree(1).accept(this, null);
							String idName = getIdName(node, 0);
							if (node.subtree(1).location() == null) {
								at = new AbsVarName(node.location(), idName);
							} else {
								at = new AbsFunName(node.location(), idName, (AbsArgs) at);
							}
							break;
					}
				}
				break;
			case StmtExtension:
				skip(node, 0);
				at = getStmts(node, 1);
				break;
			case Type:
				at = node.subtree(0).accept(this, visArg);
				// not an atom type
				if (node.subtree(0) instanceof DerLeaf) {
					switch (((DerLeaf) node.subtree(0)).symb.token) {
						case PTR:
							at = new AbsPtrType(node.location(), (AbsType) node.subtree(1).accept(this, at));
							break;
						case ARR:
							skip(node, 1); // left bracket
							skip(node, 3); // right bracket
							at = new AbsArrType(node.location(),
								(AbsExpr) node.subtree(2).accept(this, at),
								(AbsType) node.subtree(4).accept(this, at));
							break;
						case REC:
							skip(node, 1); // left parenthesis
							skip(node, 3); // colon
							skip(node, 6); // right parenthesis
							AbsCompDecls recDecls = getCompDecls(node, 2);
							at = new AbsRecType(node.location(), recDecls);
							break;
						case IDENTIFIER:
							at = new AbsTypeName(node.location(), ((DerLeaf) node.subtree(0)).symb.lexeme);
							break;
					}
				}
				break;
			case Where:
				skip(node, 0); // where
				at = getDecls(node, 1, visArg);
				break;
			case Decl:
				at = node.subtree(0).accept(this, visArg);
				switch (((DerLeaf) node.subtree(0)).symb.token) {
					case TYP:
						skip(node, 2); // colon
						String name = getIdName(node, 1);
						AbsType type = (AbsType) node.subtree(3).accept(this, visArg);
						at = new AbsTypeDecl(node.location(), name, type);
						break;
					case FUN:
						skip(node, 2); // left parenthesis
						skip(node, 4); // right parenthesis
						skip(node, 5); // colon

						String funName = getIdName(node, 1);
						AbsParDecls absParDecls = (AbsParDecls) node.subtree(3).accept(this, at);

						if (absParDecls == null) {
							absParDecls = new AbsParDecls(null, new Vector<>());
						}

						AbsType returnType = (AbsType) node.subtree(6).accept(this, null);
						AbsExpr body = (AbsExpr) node.subtree(7).accept(this, null);

						if (body == null) {
							dump(node, "Function declaration.");
							at = new AbsFunDecl(node.location(), funName, absParDecls, returnType);
						} else {
							dump(node, "Function definition.");
							at = new AbsFunDef(node.location(), funName, absParDecls, returnType, body);
						}
						break;
					case VAR:
						skip(node, 1);
						skip(node, 2);
						at = new AbsVarDecl(node.location(),
							((DerLeaf) node.subtree(1)).symb.lexeme,
							(AbsType) node.subtree(3).accept(this, visArg));
						break;
				}
				break;
			case DeclExtension:
				skip(node, 0);
				at = getDecls(node, 1, visArg);
				break;
			case Identifiers:
				skip(node, 1);
				at = getParDecls(node, 0);
				break;
			case IdentifiersExtension:
				skip(node, 0); // comma
				skip(node, 2); // colon
				if (visArg instanceof AbsParDecl) {
					at = getParDecls(node, 1);
				} else {
					at = getCompDecls(node, 1);
				}
				break;
			case Assign:
				skip(node, 0);
				at = node.subtree(1).accept(this, visArg);
				if (visArg instanceof AbsStmt) {
					at = new AbsAssignStmt(node.location(), (AbsExpr) at, (AbsExpr) visArg);
				}
				break;
			case Args:
				skip(node, 0);
				skip(node, 2);
				at = node.subtree(1).accept(this, new AbsArgs(node.location(), new Vector<>()));
				break;
			case Arg:
				at = getArgs(node, 0, visArg);
				break;
			case ArgExtension:
				skip(node, 0);
				at = getArgs(node, 1, visArg);
				break;
			default:
				return visArg;
		}
		return (at == null) ? visArg : at;
	}

	private String getIdName(DerNode node, int index) {
		DerLeaf id = (DerLeaf) node.subtree(index);
		node.subtree(index).accept(this, null);
		return id.symb.lexeme;
	}

	private <T extends AbsTree> Location getVecLocation(Vector<T> vec) {
		return new Location(vec.firstElement().location(), vec.lastElement().location());
	}

	private AbsParDecls getParDecls(DerNode node, int index) {
		Vector<AbsParDecl> parDecls = new Vector<>();
		DerLeaf id = (DerLeaf) node.subtree(index);
		node.subtree(0).accept(this, null);
		AbsType type = (AbsType) node.subtree(index + 2).accept(this, null);
		AbsParDecl parDecl = new AbsParDecl(new Location(id.location(), type.location()), id.symb.lexeme, type);
		parDecls.add(parDecl);
		AbsTree at = node.subtree(index + 3).accept(this, parDecl);
		if (at instanceof AbsParDecls) {
			parDecls.addAll(((AbsParDecls) at).parDecls());
		}
		return new AbsParDecls(node.location(), parDecls);
	}

	private AbsArgs getArgs(DerNode node, int index, AbsTree visArg) {
		Vector<AbsExpr> argsVec = new Vector<>();
		argsVec.add((AbsExpr) node.subtree(index).accept(this, visArg));
		AbsArgs absArgs = (AbsArgs) node.subtree(index + 1).accept(this, null);
		if (absArgs != null) {
			argsVec.addAll(absArgs.args());
		}
		return new AbsArgs(getVecLocation(argsVec), argsVec);
	}

	private AbsDecls getDecls(DerNode node, int index, AbsTree visArg) {
		Vector<AbsDecl> declsVec = new Vector<>();
		declsVec.add((AbsDecl) node.subtree(index).accept(this, visArg));
		AbsDecls declsExt = (AbsDecls) node.subtree(index + 1).accept(this, visArg);
		if (declsExt != null) {
			declsVec.addAll(declsExt.decls());
		}
		return new AbsDecls(getVecLocation(declsVec), declsVec);
	}

	private AbsCompDecls getCompDecls(DerNode node, int startIndex) {
		Vector<AbsCompDecl> decls = new Vector<>();
		String name = getIdName(node, startIndex);
		AbsType type = (AbsType) node.subtree(startIndex + 2).accept(this, null);
		decls.add(new AbsCompDecl(new Location(node.subtree(startIndex).location(), type.location()), name, type));
		AbsCompDecls declsExtention = (AbsCompDecls) node.subtree(startIndex + 3).accept(this, null);
		if (declsExtention != null) {
			decls.addAll(declsExtention.compDecls());
		}
		return new AbsCompDecls(getVecLocation(decls), decls);
	}

	private AbsStmts getStmts(DerNode node, int startIndex) {
		Vector<AbsStmt> thenStmts = new Vector<>();
		AbsStmt firstStmt = (AbsStmt) node.subtree(startIndex).accept(this, null);
		AbsStmts stmtsExt = (AbsStmts) node.subtree(startIndex + 1).accept(this, null);
		thenStmts.add(firstStmt);
		if (stmtsExt != null) {
			thenStmts.addAll(stmtsExt.stmts());
		}
		return new AbsStmts(getVecLocation(thenStmts), thenStmts);
	}

	private static final HashMap<Term, AbsBinExpr.Oper> TermToOperator;

	static {
		TermToOperator = new HashMap<>();
		TermToOperator.put(Term.ADD, AbsBinExpr.Oper.ADD);
		TermToOperator.put(Term.SUB, AbsBinExpr.Oper.SUB);
		TermToOperator.put(Term.AND, AbsBinExpr.Oper.AND);
		TermToOperator.put(Term.IOR, AbsBinExpr.Oper.IOR);
		TermToOperator.put(Term.XOR, AbsBinExpr.Oper.XOR);
		TermToOperator.put(Term.MOD, AbsBinExpr.Oper.MOD);
		TermToOperator.put(Term.MUL, AbsBinExpr.Oper.MUL);
		TermToOperator.put(Term.EQU, AbsBinExpr.Oper.EQU);
		TermToOperator.put(Term.NEQ, AbsBinExpr.Oper.NEQ);
		TermToOperator.put(Term.LTH, AbsBinExpr.Oper.LTH);
		TermToOperator.put(Term.GTH, AbsBinExpr.Oper.GTH);
		TermToOperator.put(Term.LEQ, AbsBinExpr.Oper.LEQ);
		TermToOperator.put(Term.GEQ, AbsBinExpr.Oper.GEQ);
	}

	private AbsTree parseAbsBinExpr(DerNode node, AbsTree visArg) {
		if (node.location() == null) return visArg;
		AbsTree at = node.subtree(0).accept(this, null);
		at = node.subtree(1).accept(this, at);
		if (node.subtree(0) instanceof DerLeaf) {
			if (TermToOperator.containsKey(((DerLeaf) node.subtree(0)).symb.token)) {
				AbsExpr be = new AbsBinExpr(node.location(), TermToOperator.get(((DerLeaf) node.subtree(0)).symb.token), (AbsExpr) visArg, (AbsExpr) at);
				at = be.relocate(new Location(visArg, at));
				if (node.subtrees().size() > 2) {
					at = node.subtree(2).accept(this, at);
				}
			}
		}
		return at;
	}

}
