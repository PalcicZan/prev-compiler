package compiler.phases.synan;

/**
 * CFG nonterminals.
 * 
 * @author sliva
 *
 */
public enum Nont {

	Source,

	Expr, ExprMulDiv, ExprXorOr, ExprAnd, ExprCompare, ExprAddSub, ExprUnary, ExprAccess,

	Term,
	Type,
	Identifiers, IdentifiersExtension,
	Access,
	Assign,
	Args, Arg, ArgExtension,
	Stmt, StmtExtension,
	Decl, DeclExtension,

	Where, Else
}
