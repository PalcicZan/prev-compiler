package compiler.phases.seman;

import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.type.*;

import java.util.HashSet;

/**
 * Tests whether types constructed by {@link TypeDefiner} make sense.
 *
 * @author sliva
 */
public class TypeTester implements AbsVisitor<Object, Object> {

	private static HashSet<AbsDecl> refDecls = new HashSet<>();
	private static final boolean onlyActualTypes = false;

	@Override
	public Object visit(AbsDecls decls, Object visArg) {
		for (AbsDecl decl : decls.decls()) {
			refDecls.clear();
			decl.accept(this, null);
			if(onlyActualTypes) SemAn.descType().put(decl.type, SemAn.descType().get(decl.type).actualType());
		}
		return null;
	}

	@Override
	public Object visit(AbsTypeDecl typeDecl, Object visArg) {
		if (SemAn.check(refDecls.add(typeDecl), "Program can not have recursive types.", typeDecl, true)
			!= null) {
			return null;
		}
		typeDecl.type.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsTypeName typeName, Object visArg) {
		SemAn.declAt().get(typeName).accept(this, null);

		return null;
	}

	@Override
	public Object visit(AbsFunDecl funDecl, Object visArg) {
		SemType returnType = SemAn.descType().get(funDecl.type);
		SemAn.check(returnType.recvable(),
			"Function can not return '" + returnType.actualType() + "' type.", funDecl.type);
		funDecl.parDecls.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsFunDef funDef, Object visArg) {
		SemType returnType = SemAn.descType().get(funDef.type);
		SemAn.check(returnType.recvable(),
			"Function can not return '" + returnType.actualType() + "' type.", funDef.type);
		funDef.parDecls.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsArrType arrType, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsAtomType atomType, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsParDecls absPars, Object visArg) {
		for (AbsParDecl parDecl : absPars.parDecls()) {
			SemType parType = SemAn.descType().get(parDecl.type);
			SemAn.check(parType.sendable(),
				"Parameter to function can not be '" + parType.actualType() + "' type.", parDecl.type);
		}
		return null;
	}

	@Override
	public Object visit(AbsPtrType ptrType, Object visArg) {
		ptrType.subType.accept(this, null);
		return null;
	}

	@Override
	public Object visit(AbsRecType recType, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsVarDecl varDecl, Object visArg) {
		return varDecl;
	}
}
