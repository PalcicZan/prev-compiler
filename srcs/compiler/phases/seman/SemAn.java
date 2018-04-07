package compiler.phases.seman;

import common.report.Location;
import common.report.Report;
import compiler.phases.*;
import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.type.*;

import java.util.HashMap;

/**
 * Semantic analysis.
 *
 * @author sliva && zan
 */
public class SemAn extends Phase {

	private static final boolean completePhase = false;

	/** Hash map for error messages */
	static final HashMap<Class, String> declMsg;

	static {
		declMsg = new HashMap<>();
		declMsg.put(AbsVarDecl.class, "variable");
		declMsg.put(AbsFunDecl.class, "function");
		declMsg.put(AbsTypeDecl.class, "type");
		declMsg.put(AbsParDecl.class, "parameter");
		declMsg.put(AbsCompDecl.class, "component");
	}

	public static String mismatchMsg(AbsDecl decl, String shouldBeDeclMsg) {
		return "Use mismatch on '" + decl.name + "'! On [" + decl.location + "] declared as a " +
			SemAn.declMsg.get(decl.getClass()) + " but used as a " + shouldBeDeclMsg + ".";
	}

	public static void mismatch(AbsDecl decl, Location calleLoc, String shouldBeDeclMsg) {
		throw new Report.Error(calleLoc, mismatchMsg(decl, shouldBeDeclMsg));
	}

	public static SemType check(boolean cond, String msg, AbsTree node, boolean forceError) {
		if (cond) return null;
		if (forceError) {
			throw new Report.Error(node.location, msg);
		} else {
			return check(cond, msg, node);
		}
	}

	public static SemType check(boolean cond, String msg, AbsTree node) {
		if (cond) return null;
		if (completePhase) {
			Report.warning(node.location, msg);
			return new SemErrorType();
		} else {
			throw new Report.Error(node.location, msg);
		}
	}

	/** The attribute that maps the usage of a name to its declaration. */
	private static final AbsAttribute<AbsName, AbsDecl> declAt = new AbsAttribute<AbsName, AbsDecl>();

	/**
	 * The attribute that maps maps a type declaration to an internal
	 * representation of a declared type. (Declarations)
	 */
	private static final AbsAttribute<AbsTypeDecl, SemNamedType> declType = new AbsAttribute<AbsTypeDecl, SemNamedType>();
	/**
	 * The attribute that maps a type expression to an internal representation
	 * of a described type. (isType)
	 */
	private static final AbsAttribute<AbsType, SemType> descType = new AbsAttribute<AbsType, SemType>();

	/**
	 * The attribute that maps an expression to an internal representation of
	 * its type. (ofType)
	 */
	private static final AbsAttribute<AbsExpr, SemType> isOfType = new AbsAttribute<AbsExpr, SemType>();

	/** The attribute that maps a record to its symbol table. */
	private static final AbsAttribute<SemRecType, SymbTable> recSymbTable = new AbsAttribute<SemRecType, SymbTable>();

	/**
	 * The attribute that tells whether an expression can evaluate to an lvalue.
	 */
	private static final AbsAttribute<AbsExpr, Boolean> isLValue = new AbsAttribute<AbsExpr, Boolean>();

	/**
	 * Returns an attribute that maps the usage of a name to its declaration.
	 *
	 * @return The attribute that maps the usage of a name to its declaration.
	 */
	public static AbsAttribute<AbsName, AbsDecl> declAt() {
		return declAt;
	}

	/**
	 * Returns an attribute that maps maps a type declaration to an internal
	 * representation of a declared type.
	 *
	 * @return The attribute that maps maps a type declaration to an internal
	 * representation of a declared type.
	 */
	public static AbsAttribute<AbsTypeDecl, SemNamedType> declType() {
		return declType;
	}

	/**
	 * Returns an attribute that maps a type expression to an internal
	 * representation of a described type.
	 *
	 * @return The attribute that maps a type expression to an internal
	 * representation of a described type.
	 */
	public static AbsAttribute<AbsType, SemType> descType() {
		return descType;
	}

	/**
	 * Returns an attribute that maps an expression to an internal
	 * representation of its type.
	 *
	 * @return The attribute that maps an expression to an internal
	 * representation of its type.
	 */
	public static AbsAttribute<AbsExpr, SemType> isOfType() {
		return isOfType;
	}

	/**
	 * Returns an attribute that maps a record to its symbol table.
	 *
	 * @return The attribute that maps a record to its symbol table.
	 */
	public static AbsAttribute<SemRecType, SymbTable> recSymbTable() {
		return recSymbTable;
	}

	/**
	 * Returns an attribute that tells whether an expression can evaluate to an
	 * lvalue.
	 *
	 * @return The attribute that tells whether an expression can evaluate to an
	 * lvalue.
	 */
	public static AbsAttribute<AbsExpr, Boolean> isLValue() {
		return isLValue;
	}

	/**
	 * Constructs a new semantic analysis phase.
	 */
	public SemAn() {
		super("seman");
	}

	@Override
	public void close() {
		declAt.lock();
		declType.lock();
		descType.lock();
		isOfType.lock();
		recSymbTable.lock();
		Abstr.absTree().accept(new AbsLogger(logger).addSubvisitor(new SemLogger(logger)), null);
		super.close();
	}

}
