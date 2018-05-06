package compiler.phases.imcgen;

import java.util.*;

import common.report.*;
import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.seman.*;
import compiler.phases.seman.type.*;
import compiler.phases.frames.*;
import compiler.phases.imcgen.code.*;

public class ImcExprGenerator implements AbsVisitor<ImcExpr, Stack<Frame>> {

	private long depth;

	private ImcStmtGenerator imcStmtGenerator;

	private static final HashMap<AbsBinExpr.Oper, ImcBINOP.Oper> absOperToImcOper;

	static {
		absOperToImcOper = new HashMap<>();
		absOperToImcOper.put(AbsBinExpr.Oper.ADD, ImcBINOP.Oper.ADD);
		absOperToImcOper.put(AbsBinExpr.Oper.SUB, ImcBINOP.Oper.SUB);
		absOperToImcOper.put(AbsBinExpr.Oper.AND, ImcBINOP.Oper.AND);
		absOperToImcOper.put(AbsBinExpr.Oper.IOR, ImcBINOP.Oper.IOR);
		absOperToImcOper.put(AbsBinExpr.Oper.XOR, ImcBINOP.Oper.XOR);
		absOperToImcOper.put(AbsBinExpr.Oper.DIV, ImcBINOP.Oper.DIV);
		absOperToImcOper.put(AbsBinExpr.Oper.MOD, ImcBINOP.Oper.MOD);
		absOperToImcOper.put(AbsBinExpr.Oper.MUL, ImcBINOP.Oper.MUL);
		absOperToImcOper.put(AbsBinExpr.Oper.EQU, ImcBINOP.Oper.EQU);
		absOperToImcOper.put(AbsBinExpr.Oper.NEQ, ImcBINOP.Oper.NEQ);
		absOperToImcOper.put(AbsBinExpr.Oper.LTH, ImcBINOP.Oper.LTH);
		absOperToImcOper.put(AbsBinExpr.Oper.GTH, ImcBINOP.Oper.GTH);
		absOperToImcOper.put(AbsBinExpr.Oper.LEQ, ImcBINOP.Oper.LEQ);
		absOperToImcOper.put(AbsBinExpr.Oper.GEQ, ImcBINOP.Oper.GEQ);
	}

	public ImcExprGenerator() {
		depth = 1;
		imcStmtGenerator = new ImcStmtGenerator(this);
	}

	@Override
	public ImcExpr visit(AbsArrExpr arrExpr, Stack<Frame> visArg) {
		ImcExpr imcArr = arrExpr.array.accept(this, null);
		ImcExpr imcIndex = arrExpr.index.accept(this, null);
		ImcExpr imcElemSize = new ImcCONST(SemAn.isOfType().get(arrExpr).size());
		if (imcArr instanceof ImcMEM) {
			imcArr = ((ImcMEM) imcArr).addr;
			ImcGen.exprImCode.put(arrExpr.array, imcArr);
		}
		return ImcGen.exprImCode.put(arrExpr,
			new ImcMEM(new ImcBINOP(ImcBINOP.Oper.ADD, imcArr, new ImcBINOP(ImcBINOP.Oper.MUL, imcIndex, imcElemSize))));
	}

	@Override
	public ImcExpr visit(AbsAtomExpr atomExpr, Stack<Frame> visArg) {
		switch (atomExpr.type) {
			case INT:
				Long value = Long.parseLong(atomExpr.expr);
				return ImcGen.exprImCode.put(atomExpr, new ImcCONST(value));
			case PTR:
				if (atomExpr.expr.matches("null"))
					return ImcGen.exprImCode.put(atomExpr, new ImcCONST(0));
				break;
			case BOOL:
				if (atomExpr.expr.matches("true")) {
					return ImcGen.exprImCode.put(atomExpr, new ImcCONST(1));
				} else if (atomExpr.expr.matches("false")) {
					return ImcGen.exprImCode.put(atomExpr, new ImcCONST(0));
				} else {
					Report.warning(atomExpr.location, "Invalid BOOL value!");
				}
			case CHAR:
				Long charValue = (long) atomExpr.expr.charAt(0);
				return ImcGen.exprImCode.put(atomExpr, new ImcCONST(charValue));
			case VOID:
				return ImcGen.exprImCode.put(atomExpr, new ImcCONST(0));
		}
		return null;
	}

	@Override
	public ImcExpr visit(AbsBinExpr binExpr, Stack<Frame> visArg) {
		ImcExpr fstExpr = binExpr.fstExpr.accept(this, null);
		ImcExpr sndExpr = binExpr.sndExpr.accept(this, null);
		fstExpr = ImcGen.accessValue(fstExpr);
		sndExpr = ImcGen.accessValue(sndExpr);
		ImcExpr binOp = new ImcBINOP(absOperToImcOper.get(binExpr.oper), fstExpr, sndExpr);
		return ImcGen.exprImCode.put(binExpr, binOp);
	}

	@Override
	public ImcExpr visit(AbsCastExpr castExpr, Stack<Frame> visArg) {
		//castExpr.type.accept(this, null); // ignore type
		return castExpr.expr.accept(this, null);
	}

	@Override
	public ImcExpr visit(AbsDelExpr delExpr, Stack<Frame> visArg) {
		ImcExpr imcDelExpr = delExpr.expr.accept(this, null);
		if (ImcGen.useAllocFun) {
			Label label = new Label("_free");
			Vector<ImcExpr> args = new Vector<>();
			args.add(new ImcNAME(label));
			if (ImcGen.useSLinGlFunCall) args.add(new ImcCONST(0));
			args.add(imcDelExpr);
			imcDelExpr = new ImcCALL(label, args);
			return ImcGen.exprImCode.put(delExpr, imcDelExpr);
		}
		return imcDelExpr;
	}

	@Override
	public ImcExpr visit(AbsNewExpr newExpr, Stack<Frame> visArg) {
		ImcExpr imcNewExpr = new ImcCONST(0);
		if (ImcGen.useAllocFun) {
			Long sizeToAlloc = SemAn.descType().get(newExpr.type).actualType().size();
			Vector<ImcExpr> args = new Vector<>();
			Label label = new Label("_malloc");
			args.add(new ImcNAME(label));
			if (ImcGen.useSLinGlFunCall) args.add(new ImcCONST(0));
			args.add(new ImcCONST(sizeToAlloc));
			imcNewExpr = new ImcCALL(label, args);
			return ImcGen.exprImCode.put(newExpr, imcNewExpr);
		}
		return imcNewExpr;
	}

	@Override
	public ImcExpr visit(AbsRecExpr recExpr, Stack<Frame> visArg) {
		ImcExpr imcRecExpr = recExpr.record.accept(this, null);
		if (recExpr.record instanceof AbsRecExpr) {
			// record of type record access (e.g. a.b.c) needing address
			imcRecExpr = ((ImcMEM) imcRecExpr).addr;
			ImcGen.exprImCode.put(recExpr.record, imcRecExpr);
		} else if (recExpr.record instanceof AbsVarName) {
			// absolute access get only name address without (mem(name))
			if (Frames.accesses.get((AbsVarDecl) SemAn.declAt().get(((AbsVarName) recExpr.record))) instanceof AbsAccess) {
				imcRecExpr = ((ImcMEM) imcRecExpr).addr;
				ImcGen.exprImCode.put(recExpr.record, imcRecExpr);
			}
		}
		recExpr.comp.accept(this, null);
		long compOffset = ((RelAccess) Frames.accesses.get((AbsVarDecl) SemAn.declAt().get(recExpr.comp))).offset;
		imcRecExpr = new ImcMEM(new ImcBINOP(ImcBINOP.Oper.ADD, imcRecExpr, new ImcCONST(compOffset)));
		return ImcGen.exprImCode.put(recExpr, imcRecExpr);
	}

	@Override
	public ImcExpr visit(AbsFunDef funDef, Stack<Frame> visArg) {
		long prevDepth = depth;
		depth = Frames.frames.get(funDef).depth;
		ImcExpr imcValue = funDef.value.accept(this, null);
		depth = prevDepth;
		Frame funFrame = Frames.frames.get(funDef);
		if (ImcGen.useFunLabel) imcValue = new ImcSEXPR(new ImcLABEL(funFrame.label), imcValue);
		return ImcGen.exprImCode.put(funDef.value, imcValue);
	}

	@Override
	public ImcExpr visit(AbsStmtExpr stmtExpr, Stack<Frame> visArg) {
		ImcExpr imcExpr = stmtExpr.expr.accept(this, null);
		ImcStmt imcStmt = stmtExpr.stmts.accept(imcStmtGenerator, null);
		stmtExpr.decls.accept(this, null);
		ImcExpr imcStmtExpr = new ImcSEXPR(imcStmt, imcExpr);
		return ImcGen.exprImCode.put(stmtExpr, imcStmtExpr);
	}

	@Override
	public ImcExpr visit(AbsUnExpr unExpr, Stack<Frame> visArg) {
		ImcExpr imcUnExpr = unExpr.subExpr.accept(this, null);
		switch (unExpr.oper) {
			case ADD:
				break;
			case MEM:
				if(imcUnExpr instanceof ImcMEM){
					imcUnExpr = ((ImcMEM) imcUnExpr).addr;
				}
				break;
			case SUB:
				imcUnExpr = new ImcUNOP(ImcUNOP.Oper.NEG, imcUnExpr);
				break;
			case VAL:
				imcUnExpr = new ImcMEM(imcUnExpr);
				break;
			case NOT:
				imcUnExpr = new ImcUNOP(ImcUNOP.Oper.NOT, imcUnExpr);
				break;
		}
		return ImcGen.exprImCode.put(unExpr, imcUnExpr);
	}


	@Override
	public ImcExpr visit(AbsFunName funName, Stack<Frame> visArg) {

		AbsFunDecl funDecl = (AbsFunDecl) SemAn.declAt().get(funName);
		Vector<ImcExpr> args = new Vector<>();

		if (funDecl instanceof AbsFunDef) {
			// function definition
			Frame funFrame = Frames.frames.get((AbsFunDef) funDecl);
			if (funFrame.depth == 1) {
				// global call
				if (ImcGen.useFunLabel) args.add(new ImcNAME(funFrame.label));
				if (ImcGen.useSLinGlFunCall) args.add(new ImcCONST(0));
			} else {
				// local call
				long numberOfFetches = depth - funFrame.depth;
				ImcExpr funExpr;

				if (numberOfFetches < 0) {
					// child function
					funExpr = new ImcMEM(new ImcTEMP(ImcGen.FP));
				} else {
					// parent function
					funExpr = new ImcMEM(new ImcTEMP(ImcGen.FP));
					for (long i = 0; i < numberOfFetches; i++) {
						funExpr = new ImcMEM(funExpr);
					}
				}
				args.add(funExpr);
			}

			// prepare arguments
			for (AbsExpr arg : funName.args.args()) {
				ImcExpr imcArg = arg.accept(this, null);
				imcArg = ImcGen.accessValue(imcArg);
				args.add(imcArg);
			}
			return ImcGen.exprImCode.put(funName, new ImcCALL(funFrame.label, args));
		} else {
			if (ImcGen.useCallDecl) {
				Label label = new Label(funDecl.name);
				if (ImcGen.useFunLabel) args.add(new ImcNAME(label));
				if (ImcGen.useSLinGlFunCall) args.add(new ImcCONST(0));
				// prepare arguments
				for (AbsExpr arg : funName.args.args()) {
					ImcExpr imcArg = arg.accept(this, null);
					imcArg = ImcGen.accessValue(imcArg);
					args.add(imcArg);
				}
				return ImcGen.exprImCode.put(funName, new ImcCALL(label, args));
			} else {
				return new ImcCONST(0);
			}
		}
	}

	@Override
	public ImcExpr visit(AbsDecls decls, Stack<Frame> visArg) {
		for (AbsDecl decl : decls.decls()) {
			if (decl instanceof AbsFunDef) {
				decl.accept(this, null);
			}
		}
		return null;
	}


	@Override
	public ImcExpr visit(AbsVarName varName, Stack<Frame> visArg) {
		AbsVarDecl varDecl = (AbsVarDecl) SemAn.declAt().get(varName);
		Access varAccess = Frames.accesses.get(varDecl);
		ImcExpr varExpr = null;
		if (varAccess instanceof AbsAccess) {
			Label varLabel = ((AbsAccess) varAccess).label;
			if(varDecl.type instanceof AbsArrType){
				// array has only address
				varExpr = new ImcNAME(varLabel);
			} else {
				// else pass value
				varExpr = new ImcMEM(new ImcNAME(varLabel));
			}
		} else if (varAccess instanceof RelAccess) {
			RelAccess relAccess = ((RelAccess) varAccess);
			ImcCONST imcOffset = new ImcCONST(relAccess.offset);
			long numberOfFetches = depth - relAccess.depth;

			varExpr = new ImcTEMP(ImcGen.FP);
			for (long i = 0; i < numberOfFetches; i++) {
				varExpr = new ImcMEM(varExpr);
			}

			if (varDecl.type instanceof AbsRecType) {
				// record access one fetch less
				varExpr = new ImcBINOP(ImcBINOP.Oper.ADD, varExpr, imcOffset);
			} else {
				// normal relative fetch
				varExpr = new ImcMEM(new ImcBINOP(ImcBINOP.Oper.ADD, varExpr, imcOffset));
			}

		}
		return ImcGen.exprImCode.put(varName, varExpr);
	}
}
