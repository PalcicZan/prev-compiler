package compiler.phases.seman;

import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.type.*;


/**
 * Tests whether expressions are well typed.
 * <p>
 * Methods of this visitor return the semantic type of a phrase being tested if
 * the AST node represents an expression or {@code null} otherwise. In the first
 * case methods leave their results in {@link SemAn#isOfType()}.
 *
 * @author sliva
 */
public class TypeChecker implements AbsVisitor<SemType, Object> {

	private static TypeDeclarator typeDeclarator = new TypeDeclarator();
	private static TypeDefiner typeDefiner = new TypeDefiner();
	private static TypeTester typeTester = new TypeTester();

	private static final boolean onlyActualTypes = false;

	/** Types **/

	@Override
	public SemType visit(AbsAtomType atomType, Object visArg) {
		return null;
	}

	@Override
	public SemType visit(AbsArrType arrType, Object visArg) {
		arrType.elemType.accept(this, null);
		arrType.len.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsPtrType ptrType, Object visArg) {
		ptrType.subType.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsRecType recType, Object visArg) {
		recType.compDecls.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsAtomExpr atomExpr, Object visArg) {
		switch (atomExpr.type) {
			case INT:
				return SemAn.isOfType().put(atomExpr, new SemIntType());
			case BOOL:
				return SemAn.isOfType().put(atomExpr, new SemBoolType());
			case VOID:
				if (atomExpr.expr.equals("none")) {
					return SemAn.isOfType().put(atomExpr, new SemVoidType());
				}
				break;
			case CHAR:
				return SemAn.isOfType().put(atomExpr, new SemCharType());
			case PTR:
				if (atomExpr.expr.equals("null")) {
					return SemAn.isOfType().put(atomExpr, new SemPtrType(new SemVoidType()));
				}
				break;
		}
		return null;
	}

	@Override
	public SemType visit(AbsArrExpr arrExpr, Object visArg) {
		SemType arrType = arrExpr.array.accept(this, null);
		SemType idType = arrExpr.index.accept(this, null);
		SemAn.check(arrType.isAKindOf(SemArrType.class), "Array expression must have array type.", arrExpr, true);
		SemAn.check(idType.isAKindOf(SemIntType.class), "Index of array must be of int type.", arrExpr);
		SemType type = ((SemArrType) arrType).elemType;
		if (onlyActualTypes) type = type.actualType();
		return SemAn.isOfType().put(arrExpr, type);
	}

	@Override
	public SemType visit(AbsStmtExpr stmtExpr, Object visArg) {
		stmtExpr.decls.accept(typeDeclarator, null);
		stmtExpr.decls.accept(typeDefiner, null);
		stmtExpr.decls.accept(typeTester, null);
		stmtExpr.decls.accept(this, null);
		stmtExpr.stmts.accept(typeDefiner, null);

		SemType stmtType = stmtExpr.stmts.accept(this, null);
		SemAn.check(stmtType.isAKindOf(SemVoidType.class), "Statements must be of void type.", stmtExpr);

		stmtExpr.expr.accept(typeDefiner, null);
		SemType exprType = stmtExpr.expr.accept(this, null);

		if (onlyActualTypes) exprType = exprType.actualType();
		return SemAn.isOfType().put(stmtExpr, exprType);
	}

	@Override
	public SemType visit(AbsStmts stmts, Object visArg) {
		for (AbsStmt stmt : stmts.stmts()) {
			SemType stmtType = stmt.accept(this, null);
			SemAn.check(stmtType.isAKindOf(SemVoidType.class), "Statement must be of void type.", stmt);
		}
		return new SemVoidType();
	}

	@Override
	public SemType visit(AbsFunName funName, Object visArg) {
		// get type of arguments
		funName.args.accept(this, null);
		AbsDecl funDecl = SemAn.declAt().get(funName);
		SemAn.check(funDecl instanceof AbsFunDecl, "Function call must have function definition.", funName);
		//Vector<SemType> argsTypes = new Vector<>();
		//for(AbsExpr arg: funName.args.args()){
		//	argsTypes.add(arg.accept(this, null));
		//}
		//SemType returnType = SemAn.descType().get(funDecl.type);
		SemType returnType = funDecl.accept(typeDefiner, funName);
		if (onlyActualTypes) returnType = returnType.actualType();
		return SemAn.isOfType().put(funName, returnType);
	}

	@Override
	public SemType visit(AbsVarName varName, Object visArg) {
		AbsDecl decl = SemAn.declAt().get(varName);
		SemAn.check(decl instanceof AbsVarDecl, "Variable must have variable declaration.", varName);
		SemType type = SemAn.descType().get(decl.type);
		if (onlyActualTypes) type = type.actualType();
		return SemAn.isOfType().put(varName, type);
	}

	@Override
	public SemType visit(AbsTypeName typeName, Object visArg) {
		return null;
	}

	@Override
	public SemType visit(AbsFunDecl funDecl, Object visArg) {
		funDecl.parDecls.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsFunDef funDef, Object visArg) {
		funDef.parDecls.accept(this, null);
		funDef.value.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsArgs args, Object visArg) {
		for (AbsExpr expr : args.args()) {
			SemAn.isOfType().put(expr, expr.accept(this, null));
		}
		return null;
	}

	/**
	 * Passing visitors
	 * Expressions
	 **/

	@Override
	public SemType visit(AbsUnExpr unExpr, Object visArg) {
		SemType type = unExpr.subExpr.accept(this, null);
		switch (unExpr.oper) {
			case ADD: case SUB:
				SemAn.check(type.isAKindOf(SemIntType.class), "Unary subexpression of '+'/'-' must be of int type.", unExpr);
				return SemAn.isOfType().put(unExpr, new SemIntType());
			case NOT:
				SemAn.check(type.isAKindOf(SemBoolType.class), "Unary subexpression of '!' must be of bool type.", unExpr);
				return SemAn.isOfType().put(unExpr, new SemBoolType());
			case MEM:
				SemAn.check(!(type.isAKindOf(SemVoidType.class)), "Memory expression must not be of void type.", unExpr);
				SemAn.check(SemAn.isLValue().get(unExpr.subExpr), "Memory expression must be evaluated to a lvalue.", unExpr);
				if (onlyActualTypes) type = type.actualType();
				return SemAn.isOfType().put(unExpr, new SemPtrType(type));
			case VAL:
				SemAn.check(type.isAKindOf(SemPtrType.class), "Value expression must be a pointer.", unExpr, true);
				SemType subType = ((SemPtrType) type.actualType()).subType;
				SemAn.check(!subType.isAKindOf(SemVoidType.class), "Value expression must not be a void pointer.", unExpr, true);
				if (onlyActualTypes) subType = subType.actualType();
				return SemAn.isOfType().put(unExpr, subType);
		}
		return null;
	}

	@Override
	public SemType visit(AbsBinExpr binExpr, Object visArg) {
		SemType fstExprType = binExpr.fstExpr.accept(this, null);
		SemType sndExprType = binExpr.sndExpr.accept(this, null);
		switch (binExpr.oper) {
			case SUB: case ADD: case MUL: case DIV: case MOD:
				// both integer type
				SemAn.check((fstExprType.isAKindOf(SemIntType.class) && sndExprType.isAKindOf(SemIntType.class)),
					"An operation " + binExpr.oper + " must have all operands of int type.", binExpr);
				return SemAn.isOfType().put(binExpr, new SemIntType());
			case XOR: case AND: case IOR:
				// both boolean type
				SemAn.check((fstExprType.isAKindOf(SemBoolType.class) && sndExprType.isAKindOf(SemBoolType.class)),
					"An operation " + binExpr.oper + " must have all operands of bool type.", binExpr);
				return SemAn.isOfType().put(binExpr, new SemBoolType());
			case EQU: case GEQ: case GTH: case LEQ: case LTH: case NEQ:
				// equal type
				SemAn.check(fstExprType.matches(sndExprType),
					"Comparing operation " + binExpr.oper + "must have both operands of the same type.", binExpr);
				// basic types - check only one
				SemAn.check((fstExprType instanceof SemBoolType || fstExprType instanceof SemIntType ||
						fstExprType instanceof SemCharType || fstExprType instanceof SemPtrType),
					"Comparing operation " + binExpr.oper + "can only compare two bools, chars, ints or ptrs.", binExpr);
				return SemAn.isOfType().put(binExpr, new SemBoolType());
		}
		return null;
	}

	@Override
	public SemType visit(AbsRecExpr recExpr, Object visArg) {
		SemType recType = recExpr.record.accept(this, null);
		SemAn.check(recType.isAKindOf(SemRecType.class), "Record expression must be of record type.", recExpr);
		SymbTable recSymbols = SemAn.recSymbTable().get((SemRecType) recType.actualType());
		SemType type;
		try {
			AbsDecl recDecl = recSymbols.fnd(recExpr.comp.name);
			// declare and confirm
			SemAn.declAt().put(recExpr.comp, recDecl);
			type = SemAn.descType().get(recDecl.type);
		} catch (SymbTable.CannotFndNameException e) {
			SemAn.check(false, "Record has no component called '" + recExpr.comp.name + "'.", recExpr.comp);
			type = new SemErrorType();
		}
		if (onlyActualTypes) type = type.actualType();
		return SemAn.isOfType().put(recExpr, type);
	}

	@Override
	public SemType visit(AbsCastExpr castExpr, Object visArg) {
		SemType castType = SemAn.descType().get(castExpr.type);
		SemType exprType = castExpr.expr.accept(this, null);
		if (castType.isAKindOf(SemVoidType.class)) {
			SemAn.check(!(exprType.isAKindOf(SemNamedType.class)), "Can't cast named type to void ptr.", castExpr);
		} else if (castType.isAKindOf(SemIntType.class)) {
			SemAn.check(exprType.isAKindOf(SemCharType.class) ||
					exprType.isAKindOf(SemIntType.class) ||
					exprType.isAKindOf(SemBoolType.class),
				"Can't cast expression of type '" + exprType.actualType() + "' to int", castExpr);
		} else if (castType.isAKindOf(SemPtrType.class)) {
			SemType ptrType = ((SemPtrType) castType).subType;
			SemAn.check(!ptrType.isAKindOf(SemNamedType.class) && !ptrType.isAKindOf(SemErrorType.class),
				"Can't cast expression to ptr of arbitrary type.", castExpr);
//			if (ptrType instanceof SemNamedType)
//				throw new Report.Error(castExpr.location,
//					"Can't cast expression to PTR of arbitrary type.");

			SemAn.check(exprType.isAKindOf(SemPtrType.class) &&
					((SemPtrType) exprType).subType.isAKindOf(SemVoidType.class),
				"To cast expression to ptr, expression must be of type void ptr.", castExpr);
		} else {
			SemAn.check(false, "Cannot cast '" + exprType + "' to '" + castType + "'.", castExpr);
		}
		if (onlyActualTypes) castType = castType.actualType();
		return SemAn.isOfType().put(castExpr, castType);
	}

	@Override
	public SemType visit(AbsNewExpr newExpr, Object visArg) {
		SemType type = newExpr.type.accept(this, null);
		SemAn.check(!(type instanceof SemVoidType), "New expression must not be of void type.", newExpr);
		if (onlyActualTypes) type = type.actualType();
		return SemAn.isOfType().put(newExpr, new SemPtrType(type));
	}

	@Override
	public SemType visit(AbsDelExpr delExpr, Object visArg) {
		SemType expr = delExpr.expr.accept(this, null);
		SemAn.check(expr instanceof SemPtrType, "Del expression must have a ptr type.", delExpr, true);
		SemType ptrType = ((SemPtrType) expr).subType;
		SemAn.check(!(ptrType instanceof SemVoidType), "Del expression must have a void ptr type.", delExpr, true);
		return SemAn.isOfType().put(delExpr, new SemVoidType());
	}

	/** Statements **/

	@Override
	public SemType visit(AbsExprStmt exprStmt, Object visArg) {

		SemType exprType = exprStmt.expr.accept(this, null);
		SemAn.check(exprType.isAKindOf(SemVoidType.class), "Expression in statement must be void type.", exprStmt);
		return new SemVoidType();
		//SemAn.check(exprStmt.expr instanceof AbsStmtExpr,
		//	"Expression in statement must be statement expression.", exprStmt);
	}

	@Override
	public SemType visit(AbsAssignStmt assignStmt, Object visArg) {
		SemType srcType = assignStmt.src.accept(this, null);
		SemType dstType = assignStmt.dst.accept(this, null);
		SemAn.check(srcType.matches(dstType),
			"Assignment statement must have source and destination of the same type." +
				" [" + dstType.actualType() + "=\\=" + srcType.actualType() + "]", assignStmt);
		SemAn.check(dstType.assignable(),
			"Assignment statement must have destination of assignable type.", assignStmt);

		SemAn.check(SemAn.isLValue().get(assignStmt.dst) != null && SemAn.isLValue().get(assignStmt.dst),
			"Assignment statement must have assignable destination.", assignStmt);

		return new SemVoidType();
	}

	@Override
	public SemType visit(AbsIfStmt ifStmt, Object visArg) {
		SemType condType = ifStmt.cond.accept(this, null);
		SemAn.check(condType.isAKindOf(SemBoolType.class), "Condition must be of bool type.", ifStmt);
		SemType bodyType = ifStmt.thenBody.accept(this, null);
		SemAn.check(bodyType.isAKindOf(SemVoidType.class), "Body of if statement must be of void type.", ifStmt);
		if (ifStmt.elseBody.stmts().size() > 0) {
			SemType elseType = ifStmt.elseBody.accept(this, null);
			SemAn.check(elseType.isAKindOf(SemVoidType.class), "Else body of if statement must be of void type.", ifStmt);
		}

		return new SemVoidType();
	}

	@Override
	public SemType visit(AbsWhileStmt whileStmt, Object visArg) {
		SemType condType = whileStmt.cond.accept(this, null);
		SemAn.check(condType.isAKindOf(SemBoolType.class),
			"Condition in while must be of bool type.", whileStmt);
		SemType bodyType = whileStmt.body.accept(this, null);
		SemAn.check(bodyType.isAKindOf(SemVoidType.class),
			"Statements in the body of while must be of void type.", whileStmt);
		return new SemVoidType();
	}

	/** Declarations **/

	@Override
	public SemType visit(AbsTypeDecl typeDecl, Object visArg) {
		typeDecl.type.accept(this, null);
		return null;
	}

	@Override
	public SemType visit(AbsVarDecl varDecl, Object visArg) {
		varDecl.type.accept(this, null);
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
		for (AbsParDecl parDecl : absPars.parDecls()) {
			parDecl.type.accept(this, null);
		}
		return null;
	}

	@Override
	public SemType visit(AbsParDecl parDecl, Object visArg) {
		SemType parType = SemAn.descType().get(parDecl.type); //.actualType()
		SemAn.check(parType.isAKindOf(SemVoidType.class) || parType.isAKindOf(SemBoolType.class) ||
				parType.isAKindOf(SemCharType.class) || parType.isAKindOf(SemIntType.class) ||
				parType.isAKindOf(SemPtrType.class),
			"Parameter should be of atomic type (int, bool, char, void or ptr).", parDecl);
		parDecl.type.accept(this, null);
		return null;
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
		return null;
	}

}
