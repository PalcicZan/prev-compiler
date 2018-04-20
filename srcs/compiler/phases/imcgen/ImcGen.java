package compiler.phases.imcgen;

import compiler.phases.*;
import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.*;
import compiler.phases.frames.*;
import compiler.phases.imcgen.code.*;

/**
 * Intermediate code generation.
 *
 * @author sliva
 */
public class ImcGen extends Phase {

	public static final boolean useAllocFun = true;
	public static final boolean useSLinGlFunCall = true;
	public static final boolean useFunLabel = true;
	public static final boolean useCallDecl = true;


	/** Intermediate code of expressions. */
	public static final AbsAttribute<AbsExpr, ImcExpr> exprImCode = new AbsAttribute<AbsExpr, ImcExpr>();

	/** Intermediate code of statements. */
	public static final AbsAttribute<AbsStmt, ImcStmt> stmtImCode = new AbsAttribute<AbsStmt, ImcStmt>();

	public static final Temp FP = new Temp();

	/**
	 * Constructs a new phase for computing frames and accesses.
	 */
	public ImcGen() {
		super("imcgen");
	}

	public static ImcExpr accessValue(ImcExpr varName) {
		if (varName instanceof ImcNAME) {
			return new ImcMEM(varName);
		}
		return varName;
	}

	@Override
	public void close() {
		exprImCode.lock();
		stmtImCode.lock();
		Abstr.absTree().accept(new AbsLogger(logger).addSubvisitor(new SemLogger(logger))
			.addSubvisitor(new FrmLogger(logger)).addSubvisitor(new ImcGenLogger(logger)), null);
		super.close();
	}

}
