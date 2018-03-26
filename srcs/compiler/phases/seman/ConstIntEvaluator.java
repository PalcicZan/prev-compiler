package compiler.phases.seman;

import common.report.*;
import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;

/**
 * A visitor that computes the value of a constant integer expression.
 *
 * @author sliva && zan
 */
public class ConstIntEvaluator implements AbsVisitor<Long, Object> {

	private static final boolean useExact = true;
	private static final boolean completePhase = false;

	/** Set warning message and continue compilation or throw an error. */
	private void report(Location location, String msg) {
		if (completePhase) {
			Report.warning(location, msg);
		} else {
			throw new Report.Error(location, msg);
		}
	}

	@Override
	public Long visit(AbsAtomExpr atomExpr, Object visArg) {
		if (atomExpr.type == AbsAtomExpr.Type.INT) {
			try {
				return Long.parseLong(atomExpr.expr);
			} catch (NumberFormatException e) {
				report(atomExpr.location, "Expected 64bit integer value. Got '" + atomExpr.expr + "'.");
			}
		}
		return null;
	}

	@Override
	public Long visit(AbsBinExpr binExpr, Object visArg) {
		Long fst = binExpr.fstExpr.accept(this, null);
		Long snd = binExpr.sndExpr.accept(this, null);

		if (fst != null && snd != null) {
			try {
				switch (binExpr.oper) {
					case ADD:
						return useExact ? Math.addExact(fst, snd) : fst + snd;
					case SUB:
						return useExact ? Math.subtractExact(fst, snd) : fst - snd;
					case MUL:
						return useExact ? Math.multiplyExact(fst, snd) : fst * snd;
					case DIV:
						return useExact ? Math.floorDiv(fst, snd) : fst / snd;
					case MOD:
						return useExact ? Math.floorMod(fst, snd) : fst % snd;
					default:
						return null;
				}
			} catch (ArithmeticException e) {
				report(binExpr.location, e.getMessage());
			}

		}
		return null;
	}

	@Override
	public Long visit(AbsUnExpr unExpr, Object visArg) {
		Long expr = unExpr.subExpr.accept(this, null);
		if (expr != null) {
			switch (unExpr.oper) {
				case ADD:
					return expr;
				case SUB:
					return -expr;
				default:
					return null;
			}
		}
		return null;
	}

	/**
	 * Passing visitors
	 * Expressions
	 **/

	@Override
	public Long visit(AbsArrExpr arrExpr, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsRecExpr recExpr, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsCastExpr castExpr, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsNewExpr newExpr, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsDelExpr delExpr, Object visArg) {
		return null;
	}

	/** Types **/

	@Override
	public Long visit(AbsAtomType atomType, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsArrType arrType, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsRecType recType, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsPtrType ptrType, Object visArg) {
		return null;
	}

	/** Statements **/

	@Override
	public Long visit(AbsExprStmt exprStmt, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsAssignStmt assignStmt, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsIfStmt ifStmt, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsWhileStmt whileStmt, Object visArg) {
		return null;
	}

	/** Declarations **/

	@Override
	public Long visit(AbsTypeDecl typeDecl, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsVarDecl varDecl, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsDecls decls, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsParDecls absPars, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsParDecl parDecl, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsCompDecls compDecls, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsCompDecl compDecl, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsArgs args, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsFunDecl funDecl, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsFunDef funDef, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsFunName funName, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsStmtExpr stmtExpr, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsStmts stmts, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsTypeName typeName, Object visArg) {
		return null;
	}

	@Override
	public Long visit(AbsVarName varName, Object visArg) {
		return null;
	}
}
