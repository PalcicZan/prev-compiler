package compiler.phases.synan;

/**
 * CFG nonterminals.
 * 
 * @author sliva
 *
 */
public enum Nont {

	Source,

	Expr,
	Expr1,
	Term,
	Type,
	Access,
	Identifiers,
	IdentifiersExtention,
	Args, Arg, ArgExtension,

	Stmt,
	StmtExtension,
	Assign,

	Decl,
	DeclExtension,

	Where,
	Else,
	Unary,
}
