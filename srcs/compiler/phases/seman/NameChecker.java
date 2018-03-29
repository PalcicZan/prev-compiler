package compiler.phases.seman;

import java.util.*;

import common.report.*;
import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;

/**
 * A visitor that traverses (a part of) the AST and checks if all names used are
 * visible where they are used. This visitor uses another visitor, namely
 * {@link NameDefiner}, whenever a declaration is encountered during the AST
 * traversal.
 *
 * @author sliva && zan
 */
public class NameChecker implements AbsVisitor<Object, Object> {

	/** The symbol table. */
	private final SymbTable symbTable;

	private final NameDefiner nameDefiner;

	/** Testing purpuses of constIntEvaluator */
	private static final boolean useConstIntEvaluator = false;
	private final ConstIntEvaluator constIntEvaluator;

	/**
	 * Constructs a new name checker using the specified symbol table.
	 *
	 * @param symbTable The symbol table.
	 */
	public NameChecker(SymbTable symbTable) {
		this.symbTable = symbTable;
		nameDefiner = new NameDefiner(this.symbTable);
		constIntEvaluator = new ConstIntEvaluator();
	}

	private static final HashMap<Class, String> declMsg;

	static {
		declMsg = new HashMap<>();
		declMsg.put(AbsVarDecl.class, "variable");
		declMsg.put(AbsFunDecl.class, "function");
		declMsg.put(AbsTypeDecl.class, "type");
		declMsg.put(AbsParDecl.class, "parameter");
		declMsg.put(AbsCompDecl.class, "component");
	}


	private void useMatch(AbsName typeName, AbsDecl decl, Class shouldBeDecl) {
		if (decl.getClass().isAssignableFrom(shouldBeDecl)) {
			// dynamic check - slower but compact and works for each type
			SemAn.declAt().put(typeName, decl);
		} else {
			System.out.println(decl.getClass() + " " + shouldBeDecl);

		}
	}

	private void mismatch(AbsDecl decl, Location calleLoc, String shouldBeDeclMsg) {
		throw new Report.Error(calleLoc, "Use mismatch on '" + decl.name + "'! On [" + decl.location + "] declared as a " +
			declMsg.get(decl.getClass()) + " but used as a " + shouldBeDeclMsg + ".");
	}

	@Override
	public Object visit(AbsStmtExpr stmtExpr, Object visArg) {
		symbTable.newScope();
		stmtExpr.decls.accept(nameDefiner, null);
		stmtExpr.decls.accept(this, null);
		stmtExpr.stmts.accept(this, null);
		stmtExpr.expr.accept(this, null);
		symbTable.oldScope();
		return stmtExpr;
	}

	@Override
	public Object visit(AbsStmts stmts, Object visArg) {
		for (AbsStmt stmt : stmts.stmts()) {
			stmt.accept(this, null);
		}
		return null;
	}

	@Override
	public Object visit(AbsFunName funName, Object visArg) {
		try {
			AbsDecl funDecl = symbTable.fnd(funName.name);
			//useMatch(funName, funDecl, AbsFunDecl.class);
			if (funDecl instanceof AbsFunDecl) {
				SemAn.declAt().put(funName, funDecl);
			} else {
				mismatch(funDecl, funName.location, "function");
			}
			// check args if any
			funName.args.accept(this, null);
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(funName.location, "Cannot find name '" + funName.name + "'");
		}
		return null;
	}

	@Override
	public Object visit(AbsVarName varName, Object visArg) {
		try {
			AbsDecl varDecl = symbTable.fnd(varName.name);
			if (varDecl instanceof AbsVarDecl) {
				SemAn.declAt().put(varName, varDecl);
			} else {
				mismatch(varDecl, varName.location, "variable");
			}
			SemAn.declAt().put(varName, varDecl);
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(varName.location, "Cannot find name '" + varName.name + "'");
		}
		return null;
	}

	@Override
	public Object visit(AbsTypeName typeName, Object visArg) {
		try {
			AbsDecl typeDecl = symbTable.fnd(typeName.name);
			if (typeDecl instanceof AbsTypeDecl) {
				SemAn.declAt().put(typeName, typeDecl);
			} else {
				mismatch(typeDecl, typeName.location, "type");
			}
		} catch (SymbTable.CannotFndNameException e) {
			throw new Report.Error(typeName.location, "Cannot find name '" + typeName.name + "'");
		}
		return null;
	}

	@Override
	public Object visit(AbsFunDecl funDecl, Object visArg) {
		// check if parameters types exists in the scope
		funDecl.parDecls.accept(this, null);
		// check if return type exists in the scope
		funDecl.type.accept(this, null);

		// No body check needed -> don't create new scope
		return funDecl;
	}

	@Override
	public Object visit(AbsFunDef funDef, Object visArg) {
		// check if parameters types exists in the scope
		funDef.parDecls.accept(this, null);
		// check if return type exists in the scope
		funDef.type.accept(this, null);

		symbTable.newScope();
		// add parameter decelerations in the new scope
		funDef.parDecls.accept(nameDefiner, null);
		// check body
		funDef.value.accept(this, null);
		symbTable.oldScope();
		return null;
	}

	@Override
	public Object visit(AbsArgs args, Object visArg) {
		for (AbsExpr expr : args.args()) {
			expr.accept(this, null);
		}
		return null;
	}

	/**
	 * Passing visitors
	 * Expressions
	 **/

	@Override
	public Object visit(AbsAtomExpr atomExpr, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsUnExpr unExpr, Object visArg) {
		unExpr.subExpr.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsBinExpr binExpr, Object visArg) {
		binExpr.fstExpr.accept(this, null);
		binExpr.sndExpr.accept(this, null);
		return binExpr;
	}

	@Override
	public Object visit(AbsArrExpr arrExpr, Object visArg) {
		arrExpr.array.accept(this, null);
		if (useConstIntEvaluator) {
			Long idx = arrExpr.index.accept(constIntEvaluator, null);
			if (idx != null) {
				if (idx >= 0) {
					System.out.println(idx);
				} else {
					Report.warning(arrExpr.location, "Using negative indices '" + idx + "'.");
				}
			}
		}
		return null;
	}

	@Override
	public Object visit(AbsRecExpr recExpr, Object visArg) {
		//recExpr.comp.accept(this, null);
		recExpr.record.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsCastExpr castExpr, Object visArg) {
		castExpr.expr.accept(this, null);
		castExpr.type.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsNewExpr newExpr, Object visArg) {
		newExpr.type.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsDelExpr delExpr, Object visArg) {
		delExpr.expr.accept(this, null);
		return null;
	}

	/** Types **/

	@Override
	public Object visit(AbsAtomType atomType, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsArrType arrType, Object visArg) {
		arrType.elemType.accept(this, null);
		arrType.len.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsRecType recType, Object visArg) {
		recType.compDecls.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsPtrType ptrType, Object visArg) {
		ptrType.subType.accept(this, null);
		return null;
	}

	/** Statements **/

	@Override
	public Object visit(AbsExprStmt exprStmt, Object visArg) {
		exprStmt.expr.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsAssignStmt assignStmt, Object visArg) {
		assignStmt.src.accept(this, null);
		assignStmt.dst.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsIfStmt ifStmt, Object visArg) {
		ifStmt.cond.accept(this, null);
		ifStmt.thenBody.accept(this, null);
		ifStmt.elseBody.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsWhileStmt whileStmt, Object visArg) {
		whileStmt.cond.accept(this, null);
		whileStmt.body.accept(this, null);
		return null;
	}

	/** Declarations **/

	@Override
	public Object visit(AbsTypeDecl typeDecl, Object visArg) {
		typeDecl.type.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsVarDecl varDecl, Object visArg) {
		varDecl.type.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsDecls decls, Object visArg) {
		for (AbsDecl decl : decls.decls()) {
			decl.accept(this, null);
		}
		return null;
	}

	@Override
	public Object visit(AbsParDecls absPars, Object visArg) {
		for (AbsParDecl parDecl : absPars.parDecls()) {
			parDecl.type.accept(this, parDecl);
		}
		return null;
	}

	@Override
	public Object visit(AbsParDecl parDecl, Object visArg) {
		parDecl.type.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsCompDecls compDecls, Object visArg) {
		for (AbsCompDecl compDecl : compDecls.compDecls()) {
			compDecl.accept(this, null);
		}
		return null;
	}

	@Override
	public Object visit(AbsCompDecl compDecl, Object visArg) {
		compDecl.type.accept(this, null);
		return null;
	}

}
