package compiler.phases.seman;

import java.util.*;

import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.type.*;

/**
 * Constructs semantic representation of each type.
 * <p>
 * Methods of this visitor return the constructed semantic type if the AST node
 * represents a type or {@code null} otherwise. In either case methods leave
 * their results in {@link SemAn#descType()}.
 *
 * @author sliva
 */
public class TypeDefiner implements AbsVisitor<SemType, Object> {

	private ConstIntEvaluator constIntEvaluator = new ConstIntEvaluator();
	private static final boolean onlyPositiveArrLen = true;

	@Override
	public SemType visit(AbsAtomType atomType, Object visArg) {
		SemType semType = null;
		switch (atomType.type) {
			case INT:
				semType = new SemIntType();
				break;
			case CHAR:
				semType = new SemCharType();
				break;
			case BOOL:
				semType = new SemBoolType();
				break;
			case VOID:
				semType = new SemVoidType();
				break;
		}
		return SemAn.descType().put(atomType, semType);
	}

	@Override
	public SemType visit(AbsArrType arrType, Object visArg) {
		Long val = arrType.len.accept(constIntEvaluator, null);
		SemType type = arrType.elemType.accept(this, null);
		SemAn.check(val != null, "Array's length must be provided as const int.", arrType, true);
		SemAn.check(!onlyPositiveArrLen || val > 0, "Array's length must be a positive int.", arrType);
		SemAn.check(type != null, "Array's element type must be well-defined.", arrType);
		return SemAn.descType().put(arrType, new SemArrType(val, type));
	}

	@Override
	public SemType visit(AbsPtrType ptrType, Object visArg) {
		SemType type = ptrType.subType.accept(this, null);
		SemAn.check(type != null, "Pointer's type must be well-defined.", ptrType);
		return SemAn.descType().put(ptrType, new SemPtrType(type));
	}

	@Override
	public SemType visit(AbsRecType recType, Object visArg) {
		SemAn.check(recType.compDecls.compDecls().size() > 0, "At least one component must be declared in a record.", recType);
		Vector<SemType> compTypes = new Vector<>();
		Vector<String> compNames = new Vector<>();
		SymbTable recSymb = new SymbTable();
		recSymb.newScope();
		for (AbsCompDecl decl : recType.compDecls.compDecls()) {
			try {
				recSymb.ins(decl.name, decl);
			} catch (SymbTable.CannotInsNameException e) {
				SemAn.check(false, "Component name '" + decl.name + "' already exists on " + e.msg + ".", decl);
			}
			SemType currType = decl.accept(this, null);
			SemAn.check(currType != null, "Records's component type must be well-defined.", decl);
			compTypes.add(currType);
			compNames.add(decl.name);
		}
		SemRecType rec = new SemRecType(compNames, compTypes);
		SemAn.recSymbTable().put(rec, recSymb);
		return SemAn.descType().put(recType, rec);
	}

	@Override
	public SemType visit(AbsTypeName typeName, Object visArg) {
		AbsDecl decl = SemAn.declAt().get(typeName);
		SemAn.check(decl instanceof AbsTypeDecl, SemAn.mismatchMsg(decl, "type"), typeName);

		SemType type =  new SemNamedType((AbsTypeDecl) decl);
		return SemAn.descType().put(typeName, type);
		//	return SemAn.descType().put(typeName, decl.type.accept(this, null));
	}

	@Override
	public SemType visit(AbsVarDecl varDecl, Object visArg) {
		return varDecl.type.accept(this, null);
	}

	@Override
	public SemType visit(AbsStmtExpr stmtExpr, Object visArg) {
		stmtExpr.decls.accept(this, null);
		stmtExpr.stmts.accept(this, null);
		stmtExpr.expr.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsStmts stmts, Object visArg) {
		for (AbsStmt stmt : stmts.stmts()) {
			stmt.accept(this, null);
		}
		return null;
	}

	@Override
	public SemType visit(AbsFunName funName, Object visArg) {
		funName.args.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsVarName varName, Object visArg) {
		return null;
	}

	@Override
	public SemType visit(AbsFunDecl funDecl, Object visArg) {
		funDecl.parDecls.accept(this, visArg);
		return funDecl.type.accept(this, null);
	}

	@Override
	public SemType visit(AbsFunDef funDef, Object visArg) {
		funDef.parDecls.accept(this, visArg);
		SemType returnType = funDef.type.accept(this, null);
		return returnType;
	}

	@Override
	public SemType visit(AbsArgs args, Object visArg) {
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
	public SemType visit(AbsAtomExpr atomExpr, Object visArg) {
		return null;
	}

	@Override
	public SemType visit(AbsUnExpr unExpr, Object visArg) {
		unExpr.subExpr.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsBinExpr binExpr, Object visArg) {
		binExpr.fstExpr.accept(this, null);
		binExpr.sndExpr.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsArrExpr arrExpr, Object visArg) {
		arrExpr.array.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsRecExpr recExpr, Object visArg) {
		recExpr.comp.accept(this, null);
		recExpr.record.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsCastExpr castExpr, Object visArg) {
		castExpr.expr.accept(this, null);
		castExpr.type.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsNewExpr newExpr, Object visArg) {
		newExpr.type.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsDelExpr delExpr, Object visArg) {
		delExpr.expr.accept(this, null);
		return null;
	}

	/** Statements **/

	@Override
	public SemType visit(AbsExprStmt exprStmt, Object visArg) {
		exprStmt.expr.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsAssignStmt assignStmt, Object visArg) {
		assignStmt.src.accept(this, null);
		assignStmt.dst.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsIfStmt ifStmt, Object visArg) {
		ifStmt.cond.accept(this, null);
		ifStmt.thenBody.accept(this, null);
		ifStmt.elseBody.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsWhileStmt whileStmt, Object visArg) {
		whileStmt.cond.accept(this, null);
		whileStmt.body.accept(this, null);
		return null;
	}

	/** Declarations **/

	@Override
	public SemType visit(AbsTypeDecl typeDecl, Object visArg) {
		typeDecl.type.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsDecls decls, Object visArg) {
		for (AbsDecl decl : decls.decls()) {
			decl.accept(this, null);
		}
		return null;
	}

	@Override
	public SemType visit(AbsParDecls absPars, Object visArg) {
		if (visArg != null) {
			AbsFunName funName = (AbsFunName) visArg;
			SemAn.check(funName.args.args().size() == absPars.parDecls().size(),
				"Number of arguments must be same as number of declared function parameters.", funName);
			for (int i = 0; i < funName.args.args().size() && i < absPars.parDecls().size(); i++) {
				SemType declType = absPars.parDecl(i).accept(this, null);
				SemType argType = SemAn.isOfType().get(funName.args.arg(i));
				SemAn.check(declType.matches(argType), "Argument's type must match declared parameter's type.",
					funName.args.arg(i));
			}
		} else {
			for (AbsParDecl parDecl : absPars.parDecls()) {
				parDecl.accept(this, null);
			}
		}
		return null;
	}

	@Override
	public SemType visit(AbsParDecl parDecl, Object visArg) {
		return parDecl.type.accept(this, null);
	}

	@Override
	public SemType visit(AbsCompDecls compDecls, Object visArg) {
		for (AbsCompDecl compDecl : compDecls.compDecls()) {
			compDecl.accept(this, null);
		}
		return null;
	}

	@Override
	public SemType visit(AbsCompDecl compDecl, Object visArg) {
		return compDecl.type.accept(this, null);

	}
}
