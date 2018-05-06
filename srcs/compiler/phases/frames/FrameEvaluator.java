package compiler.phases.frames;

import java.util.*;

import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.*;
import compiler.phases.seman.type.*;

public class FrameEvaluator extends AbsFullVisitor<Long, Long> {

	// default size of ptr
	private static final long ptrSize = (new SemPtrType(new SemVoidType())).size();
	private static final boolean relStaticDepth = false;

	// stack frames
	private Stack<FrameAttr> stackFrames;

	// frame
	private class FrameAttr {
		long offsetPar;
		long offsetLoc;
		long locsSize;
		long argsSize;

		FrameAttr() {
			this.offsetPar = ptrSize;
			this.offsetLoc = 0;
			this.locsSize = 0;
			this.argsSize = 0;
		}
	}

	private int currDepth;
	private boolean isGlobal;
	private boolean isWrapped;

	public FrameEvaluator() {
		currDepth = 1;
		isGlobal = true;
		isWrapped = false;
		stackFrames = new Stack<>();
	}

	@Override
	public Long visit(AbsFunDef funDef, Long visArg) {
		Label funLabel;
		boolean isGlobalAcs = isGlobal;
		boolean isWrappedAcs = isWrapped;
		// depth of a function 1 if not wrapped or global
		if(!isGlobal && isWrapped) currDepth++;
		isGlobal = false;
		isWrapped = true;

		// function is on (depth - 1) and parameters/local variables on (depth)
		if(relStaticDepth) currDepth++;

		if (stackFrames.empty()) {
			funLabel = new Label(funDef.name);
		} else {
			funLabel = new Label(); // nested functions
		}

		// push new frame on stack
		stackFrames.push(new FrameAttr());
		// get fun frames locals and args
		for (AbsParDecl parDecl : funDef.parDecls.parDecls()) {
			Long size = SemAn.descType().get(parDecl.type).actualType().size();
			Frames.accesses.put(parDecl, new RelAccess(size, stackFrames.peek().offsetPar, currDepth));
			stackFrames.peek().offsetPar += size;
		}

		funDef.value.accept(this, null);

		// pop frame from stack
		FrameAttr frameAttr = stackFrames.pop();

		// function is on depth - 1 vs. parameters and local variables
		if(relStaticDepth) currDepth--;

		// create new frame
		Frame funFrame = new Frame(funLabel, currDepth, frameAttr.locsSize, frameAttr.argsSize);
		Frames.frames.put(funDef, funFrame);

		// reset global and wrapped
		isGlobal = isGlobalAcs;
		isWrapped = isWrappedAcs;
		if(!isGlobal && isWrapped) currDepth--;
		return null;
	}

	@Override
	public Long visit(AbsStmtExpr stmtExpr, Long visArg) {
		stmtExpr.decls.accept(this, null);

		// "global wrapper" stack frame
		boolean wasGlobal = isGlobal;
		if (isGlobal) {
			stackFrames.push(new FrameAttr());
			isGlobal = false;
		}

		stmtExpr.stmts.accept(this, null);
		stmtExpr.expr.accept(this, null);

		// remove "global wrapper" stack from global
		if (wasGlobal) {
			FrameAttr mainFrame = stackFrames.pop();
			isGlobal = true;
			Frames.mainFrame = new Frame(new Label(""), currDepth, mainFrame.locsSize, mainFrame.argsSize);
		}

		return null;
	}

	@Override
	public Long visit(AbsFunName funName, Long visArg) {
		funName.args.accept(this, null);

		AbsDecl funDecl = SemAn.declAt().get(funName);
		long argSize = 0;
		// look into declaration and get arguments size
		for (AbsDecl arg : ((AbsFunDecl) funDecl).parDecls.parDecls()) {
			argSize += SemAn.descType().get(arg.type).actualType().size();
		}

		long returnSize = SemAn.descType().get(funDecl.type).actualType().size();
		// ignore globally called functions
		if (!isGlobal)
			stackFrames.peek().argsSize = Math.max(stackFrames.peek().argsSize, Math.max(ptrSize + argSize, returnSize));
		return null;
	}

	/** Variables  **/

	@Override
	public Long visit(AbsVarDecl varDecl, Long visArg) {
		Long size = SemAn.descType().get(varDecl.type).actualType().size();
		varDecl.type.accept(this, null);

		if (isGlobal) {
			// global has absolute access
			Frames.accesses.put(varDecl, new AbsAccess(size, new Label(varDecl.name)));
		} else {
			// relative access
			stackFrames.peek().offsetLoc -= size;
			stackFrames.peek().locsSize += size;
			Frames.accesses.put(varDecl, new RelAccess(size, stackFrames.peek().offsetLoc, currDepth));
		}
		return null;
	}

	/** Record's components **/

	@Override
	public Long visit(AbsCompDecls compDecls, Long visArg) {
		long offset = 0;
		for (AbsDecl absDecl : compDecls.compDecls()) {
			offset += absDecl.accept(this, offset);
		}
		return null;
	}

	@Override
	public Long visit(AbsCompDecl compDecl, Long offset) {
		Long size = SemAn.descType().get(compDecl.type).actualType().size();
		Frames.accesses.put(compDecl, new RelAccess(size, offset, 0));
		return size;
	}

}
