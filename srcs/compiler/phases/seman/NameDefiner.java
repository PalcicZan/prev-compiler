package compiler.phases.seman;

import common.report.*;
import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;

/**
 * A visitor that traverses (a part of) the AST and stores all declarations
 * encountered into the symbol table. It is meant to be called from another
 * visitor, namely {@link NameChecker}.
 *
 * @author sliva
 */
public class NameDefiner implements AbsVisitor<Object, Object> {

	/** The symbol table. */
	private final SymbTable symbTable;

	/**
	 * Constructs a new name checker using the specified symbol table.
	 *
	 * @param symbTable The symbol table.
	 */
	public NameDefiner(SymbTable symbTable) {
		this.symbTable = symbTable;
	}

	@Override
	public Object visit(AbsParDecls absPars, Object visArg) {
		for (AbsParDecl parDecl : absPars.parDecls()) {
			parDecl.accept(this, null);
		}
		return null;
	}

	@Override
	public Object visit(AbsDecls decls, Object visArg) {
		for (AbsDecl absDecl : decls.decls()) {
			absDecl.accept(this, null);
		}
		return null;
	}

	@Override
	public Object visit(AbsParDecl parDecl, Object visArg) {
		try {
			symbTable.ins(parDecl.name, parDecl);
		} catch (SymbTable.CannotInsNameException e) {
			throw new Report.Error(parDecl.location, "Name of parameter '" + parDecl.name +
				"' already exists " + e.msg + " in this scope.");
		}
		return null;
	}

	@Override
	public Object visit(AbsFunDecl funDecl, Object visArg) {
		try {
			symbTable.ins(funDecl.name, funDecl);
		} catch (SymbTable.CannotInsNameException e) {
			throw new Report.Error(funDecl.location, "Function name '" + funDecl.name +
				"' already exists " + e.msg + " in this scope.");
		}
		return null;
	}

	@Override
	public Object visit(AbsFunDef funDef, Object visArg) {
		try {
			symbTable.ins(funDef.name, funDef);
		} catch (SymbTable.CannotInsNameException e) {
			throw new Report.Error(funDef.location, "Function name '" + funDef.name +
				"' already exists " + e.msg + " in this scope.");
		}
		return null;
	}

	@Override
	public Object visit(AbsVarDecl varDecl, Object visArg) {
		try {
			symbTable.ins(varDecl.name, varDecl);
		} catch (SymbTable.CannotInsNameException e) {
			throw new Report.Error(varDecl.location, "Variable name '" + varDecl.name +
				"' already exists " + e.msg + " in this scope.");
		}
		return null;
	}

	@Override
	public Object visit(AbsTypeDecl typeDecl, Object visArg) {
		try {
			symbTable.ins(typeDecl.name, typeDecl);
		} catch (SymbTable.CannotInsNameException e) {
			throw new Report.Error(typeDecl.location, "Type name '" + typeDecl.name +
				"' already exists " + e.msg + " in this scope.");
		}
		return null;
	}

}
