package compiler.phases.seman;

import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.type.*;

/**
 * Declares type synonyms introduced by type declarations.
 * 
 * Methods of this visitor return {@code null} but leave their results in
 * {@link SemAn#declType()}.
 * 
 * @author sliva
 *
 */
public class TypeDeclarator implements AbsVisitor<Object, Object> {

	@Override
	public Object visit(AbsDecls decls, Object visArg) {
		for(AbsDecl decl : decls.decls()){
			decl.accept(this, null);
		}
		return null;
	}

	@Override
	public Object visit(AbsTypeDecl typeDecl, Object visArg) {
		SemType type = SemAn.descType().get(typeDecl.type);
		SemAn.declType().put(typeDecl, new SemNamedType(typeDecl));
		return null;
	}

	@Override
	public Object visit(AbsFunDecl funDecl, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsFunDef funDef, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsVarDecl varDecl, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsCompDecl compDecl, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsCompDecls compDecls, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsParDecl parDecl, Object visArg) {
		return null;
	}

	@Override
	public Object visit(AbsParDecls absPars, Object visArg) {
		return null;
	}


}
