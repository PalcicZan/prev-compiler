package compiler.phases.seman;

import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;

public class AddrChecker implements AbsVisitor<Boolean, Object> {

	@Override
	public Boolean visit(AbsVarName varName, Object visArg) {
		AbsDecl decl = SemAn.declAt().get(varName);
		Boolean isLval = decl.getClass() == AbsVarDecl.class || decl.getClass() == AbsParDecl.class;
		return SemAn.isLValue().put(varName, isLval);
	}

	@Override
	public Boolean visit(AbsUnExpr unExpr, Object visArg) {
		Boolean subExpr = unExpr.subExpr.accept(this, null);
		if (unExpr.oper == AbsUnExpr.Oper.VAL)
			return SemAn.isLValue().put(unExpr, subExpr);
		return false;
	}

	@Override
	public Boolean visit(AbsArrExpr arrExpr, Object visArg) {
		arrExpr.index.accept(this, null);
		Boolean arr = arrExpr.array.accept(this, null);
		return SemAn.isLValue().put(arrExpr, arr);
	}

	@Override
	public Boolean visit(AbsRecExpr recExpr, Object visArg) {
		return SemAn.isLValue().put(recExpr, recExpr.record.accept(this, null));
	}

	@Override
	public Boolean visit(AbsStmtExpr stmtExpr, Object visArg) {
		stmtExpr.decls.accept(this, null);
		stmtExpr.stmts.accept(this, null);
		stmtExpr.expr.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsStmts stmts, Object visArg) {
		for (AbsStmt stmt : stmts.stmts()) {
			stmt.accept(this, null);
		}
		return false;
	}

	@Override
	public Boolean visit(AbsFunName funName, Object visArg) {
		funName.args.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsTypeName typeName, Object visArg) {
		return false;
	}

	@Override
	public Boolean visit(AbsFunDecl funDecl, Object visArg) {
		funDecl.parDecls.accept(this, null);
		funDecl.type.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsFunDef funDef, Object visArg) {
		funDef.parDecls.accept(this, null);
		funDef.type.accept(this, null);
		funDef.value.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsArgs args, Object visArg) {
		for (AbsExpr expr : args.args()) {
			expr.accept(this, null);
		}
		return false;
	}

	/**
	 * Passing visitors
	 * Expressions
	 **/

	@Override
	public Boolean visit(AbsAtomExpr atomExpr, Object visArg) {
		return false;
	}

	@Override
	public Boolean visit(AbsBinExpr binExpr, Object visArg) {
		binExpr.fstExpr.accept(this, null);
		binExpr.sndExpr.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsCastExpr castExpr, Object visArg) {
		castExpr.expr.accept(this, null);
		castExpr.type.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsNewExpr newExpr, Object visArg) {
		newExpr.type.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsDelExpr delExpr, Object visArg) {
		delExpr.expr.accept(this, null);
		return false;
	}

	/** Types **/

	@Override
	public Boolean visit(AbsAtomType atomType, Object visArg) {
		return false;
	}

	@Override
	public Boolean visit(AbsArrType arrType, Object visArg) {
		arrType.elemType.accept(this, null);
		arrType.len.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsRecType recType, Object visArg) {
		recType.compDecls.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsPtrType ptrType, Object visArg) {
		ptrType.subType.accept(this, null);
		return false;
	}

	/** Statements **/

	@Override
	public Boolean visit(AbsExprStmt exprStmt, Object visArg) {
		exprStmt.expr.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsAssignStmt assignStmt, Object visArg) {
		assignStmt.src.accept(this, null);
		assignStmt.dst.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsIfStmt ifStmt, Object visArg) {
		ifStmt.cond.accept(this, null);
		ifStmt.thenBody.accept(this, null);
		ifStmt.elseBody.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsWhileStmt whileStmt, Object visArg) {
		whileStmt.cond.accept(this, null);
		whileStmt.body.accept(this, null);
		return false;
	}

	/** Declarations **/

	@Override
	public Boolean visit(AbsTypeDecl typeDecl, Object visArg) {
		typeDecl.type.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsVarDecl varDecl, Object visArg) {
		varDecl.type.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsDecls decls, Object visArg) {
		for (AbsDecl decl : decls.decls()) {
			decl.accept(this, null);
		}
		return false;
	}

	@Override
	public Boolean visit(AbsParDecls absPars, Object visArg) {
		for (AbsParDecl parDecl : absPars.parDecls()) {
			parDecl.type.accept(this, parDecl);
		}
		return false;
	}

	@Override
	public Boolean visit(AbsParDecl parDecl, Object visArg) {
		parDecl.type.accept(this, null);
		return false;
	}

	@Override
	public Boolean visit(AbsCompDecls compDecls, Object visArg) {
		for (AbsCompDecl compDecl : compDecls.compDecls()) {
			compDecl.accept(this, null);
		}
		return false;
	}

	@Override
	public Boolean visit(AbsCompDecl compDecl, Object visArg) {
		compDecl.type.accept(this, null);
		return false;
	}

}
