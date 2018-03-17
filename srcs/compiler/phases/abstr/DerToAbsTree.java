package compiler.phases.abstr;

import java.util.*;
import common.report.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.synan.*;
import compiler.phases.synan.dertree.*;

/**
 * Transforms a derivation tree into an abstract syntax tree.
 * 
 * @author sliva
 *
 */
public class DerToAbsTree implements DerVisitor<AbsTree, AbsTree> {

	// TODO

	@Override
	public AbsTree visit(DerLeaf leaf, AbsTree visArg) {
		return null;
	}

	@Override
	public AbsTree visit(DerNode node, AbsTree visArg) {
		return null;
	}

}
