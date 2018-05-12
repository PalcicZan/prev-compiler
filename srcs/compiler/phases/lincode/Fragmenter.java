package compiler.phases.lincode;

import java.util.*;

import compiler.Main;
import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.frames.*;
import compiler.phases.imcgen.*;
import compiler.phases.imcgen.code.*;

public class Fragmenter extends AbsFullVisitor<Object, Object> {

	/** Flags **/
	private static final boolean useBinopTemp = true;
	private static final boolean useShortArrAcc = true;
	private static final boolean useTempCall = true;
	private static final boolean useOptTraces = true;

	private boolean mainFun = true;
	private Vector<ImcStmt> fragmentStmts = new Vector<>();

	private void dump(AbsTree node, String msg) {
		if (Main.debug == Main.DEBUG.FULL) {
			if (node != null) {
				System.err.println("[" + node.location + ", " + node.getClass().getSimpleName() + "]: " + msg);
			} else {
				System.err.println(msg);
			}
		}
	}

	private Vector<Vector<ImcStmt>> getBlocks(Vector<ImcStmt> stmts) {
		Vector<Vector<ImcStmt>> blocks = new Vector<>();
		boolean finished = true;
		boolean started = false;
		for (ImcStmt stmt : stmts) {
			if (!started) {
				blocks.add(new Vector<>());
				started = true;
				finished = false;
				if (!(stmt instanceof ImcLABEL)) {
					blocks.lastElement().add(new ImcLABEL(new Label()));
					continue;
				}
			} else if (stmt instanceof ImcLABEL) {
				if (!finished) {
					blocks.lastElement().add(new ImcJUMP(((ImcLABEL) stmt).label));
				}
				blocks.add(new Vector<>());
				started = true;
				finished = false;
			} else if (stmt instanceof ImcCJUMP || stmt instanceof ImcJUMP) {
				finished = true;
				started = false;
			}
			blocks.lastElement().add(stmt);
		}
		if(Main.debug == Main.DEBUG.FULL){
			for (Vector block : blocks) {
				dump(null, Integer.toString(block.size()));
				dump(null, ((ImcLABEL) block.firstElement()).label.name);
				dump(null, block.lastElement().getClass().getSimpleName());
			}
		}
		return blocks;
	}

	private Vector<ImcStmt> optTraces(Vector<ImcStmt> stmts) {
		LinkedList<Vector<ImcStmt>> blocks = new LinkedList<>(getBlocks(stmts));
		HashMap<Label, Vector<ImcStmt>> labelBlocks = new HashMap<>();
		for(Vector<ImcStmt> block:blocks){
			labelBlocks.put(((ImcLABEL)block.firstElement()).label, block);
		}
		HashSet<Vector<ImcStmt>> marked = new HashSet<>();
		Vector<ImcStmt> newStmts = new Vector<>();
		while (blocks.size() > 0) {
			Vector<ImcStmt> currBlock = blocks.removeFirst();
			Vector<ImcStmt> trace = new Vector<>();
			while(!marked.contains(currBlock)){
				marked.add(currBlock);
				trace.addAll(currBlock);
				ImcStmt jump = currBlock.lastElement();
				if(jump instanceof ImcJUMP){
					Vector<ImcStmt> succ = labelBlocks.get(((ImcJUMP)jump).label);
					if(!marked.contains(succ)) {
						currBlock = succ;
					}
				} else if(jump instanceof ImcCJUMP){
					Vector<ImcStmt> succ = labelBlocks.get(((ImcCJUMP)jump).negLabel);
					if(!marked.contains(succ)) {
						currBlock = succ;
						continue;
					}
					succ = labelBlocks.get(((ImcCJUMP)jump).posLabel);
					if(!marked.contains(succ)) {
						currBlock = succ;
					}
				}

			}
			newStmts.addAll(trace);
		}
		return newStmts;
	}


	@Override
	public Object visit(AbsStmts stmts, Object visArg) {
		for (AbsStmt absStmt : stmts.stmts()) {
			absStmt.accept(this, stmts);
		}
		return null;
	}

	@Override
	public Object visit(AbsWhileStmt whileStmt, Object visArg) {
		ImcSTMTS imcStmts = (ImcSTMTS) ImcGen.stmtImCode.get(whileStmt);
		Vector<ImcStmt> imcWhileStmts = imcStmts.stmts();
		ImcCJUMP whileCond = ((ImcCJUMP) imcWhileStmts.get(1));
		fragmentStmts.add(new ImcJUMP(((ImcLABEL) imcWhileStmts.get(0)).label)); // test label
		fragmentStmts.add(imcWhileStmts.get(2)); // body label
		whileStmt.body.accept(this, null);
		fragmentStmts.add(imcWhileStmts.get(0));
		fragmentStmts.add(new ImcCJUMP((ImcExpr) whileStmt.cond.accept(this, null),
			whileCond.posLabel, whileCond.negLabel));
		fragmentStmts.add(imcWhileStmts.get(5));

		return null;
	}

	@Override
	public Object visit(AbsIfStmt ifStmt, Object visArg) {
		ImcSTMTS imcStmts = (ImcSTMTS) ImcGen.stmtImCode.get(ifStmt);
		Vector<ImcStmt> imcIfStmts = imcStmts.stmts();
		ImcCJUMP imcCond = ((ImcCJUMP) imcIfStmts.get(0));
		fragmentStmts.add(new ImcCJUMP((ImcExpr) ifStmt.cond.accept(this, null), imcCond.posLabel, imcCond.negLabel));
		fragmentStmts.add(imcIfStmts.get(4));
		ifStmt.elseBody.accept(this, null);
		fragmentStmts.add(imcIfStmts.get(3));
		fragmentStmts.add(imcIfStmts.get(1));
		ifStmt.thenBody.accept(this, null);
		fragmentStmts.add(new ImcJUMP(((ImcLABEL) imcIfStmts.get(6)).label));
		fragmentStmts.add(imcIfStmts.get(6));

		return null;
	}

	@Override
	public Object visit(AbsAssignStmt assignStmt, Object visArg) {
		dump(assignStmt, "=== Assign statement ===");
		dump(assignStmt, assignStmt.dst.getClass().getSimpleName());

		ImcExpr dstExpr;
		if (assignStmt.dst instanceof AbsArrExpr || assignStmt.dst instanceof AbsRecExpr) {
			dstExpr = new ImcMEM((ImcExpr) assignStmt.dst.accept(this, assignStmt));
		} else {
			dstExpr = (ImcExpr) assignStmt.dst.accept(this, null);
		}
		ImcExpr srcExpr = (ImcExpr) assignStmt.src.accept(this, null);
		fragmentStmts.add(new ImcMOVE(dstExpr, srcExpr));

		return null;
	}

	@Override
	public Object visit(AbsArrExpr arrExpr, Object visArg) {
		dump(arrExpr, "Array expression.");
		ImcTEMP result = new ImcTEMP(new Temp());

		// array address
		ImcExpr arrAddr = (ImcExpr) arrExpr.array.accept(this, null);
		dump(arrExpr, (arrAddr == null) ? "null" : arrAddr.toString());

		// array index
		ImcExpr arrIndex = (ImcExpr) arrExpr.index.accept(this, null);
		dump(arrExpr, (arrIndex == null) ? "null" : arrIndex.toString());

		ImcExpr imcArrExpr = ImcGen.exprImCode.get(arrExpr);
		ImcBINOP imcInxExpr = (ImcBINOP) ((ImcBINOP) ((ImcMEM) imcArrExpr).addr).sndExpr;
		ImcTEMP indexTemp = new ImcTEMP(new Temp());
		ImcStmt indexResult = new ImcMOVE(indexTemp, new ImcBINOP(ImcBINOP.Oper.MUL, arrIndex, imcInxExpr.sndExpr));

		fragmentStmts.add(indexResult);
		ImcExpr imcFinalArrExpr;
		if (visArg instanceof AbsAssignStmt) {
			// get address
			imcFinalArrExpr = new ImcBINOP(ImcBINOP.Oper.ADD, arrAddr, indexTemp);
		} else {
			// get value
			imcFinalArrExpr = new ImcMEM(new ImcBINOP(ImcBINOP.Oper.ADD, arrAddr, indexTemp));
		}

		if (useShortArrAcc) {
			return imcFinalArrExpr;
		} else {
			fragmentStmts.add(new ImcMOVE(result, imcFinalArrExpr));
			return result;
		}
	}

	@Override
	public Object visit(AbsRecExpr recExpr, Object visArg) {
		ImcTEMP result = new ImcTEMP(new Temp());
		ImcExpr imcRecExpr = ImcGen.exprImCode.get(recExpr);
		if (imcRecExpr instanceof ImcMEM) {
			imcRecExpr = ((ImcBINOP) ((ImcMEM) imcRecExpr).addr).sndExpr;
		} else {
			imcRecExpr = ((ImcBINOP) imcRecExpr).sndExpr;
		}
		ImcExpr recAddr = new ImcBINOP(ImcBINOP.Oper.ADD,
			(ImcExpr) recExpr.record.accept(this, recExpr),
			imcRecExpr);

		if (visArg instanceof AbsAssignStmt || visArg instanceof AbsRecExpr) {
			// return address
			fragmentStmts.add(new ImcMOVE(result, recAddr));
		} else {
			// return value
			fragmentStmts.add(new ImcMOVE(result, new ImcMEM(recAddr)));
		}
		return result;
	}

	@Override
	public Object visit(AbsBinExpr binExpr, Object visArg) {
		dump(binExpr, "=== Binary expression ===");
		ImcTEMP result = new ImcTEMP(new Temp());
		ImcExpr t1 = (ImcExpr) binExpr.fstExpr.accept(this, null);
		if (useBinopTemp && !(t1 instanceof ImcTEMP)) {
			ImcTEMP t1Temp = new ImcTEMP(new Temp());
			fragmentStmts.add(new ImcMOVE(t1Temp, t1));
			t1 = t1Temp;
		}
		ImcExpr t2 = (ImcExpr) binExpr.sndExpr.accept(this, null);
		if (useBinopTemp && !(t2 instanceof ImcTEMP)) {
			ImcTEMP t2Temp = new ImcTEMP(new Temp());
			fragmentStmts.add(new ImcMOVE(t2Temp, t2));
			t2 = t2Temp;
		}
		ImcBINOP.Oper oper = ((ImcBINOP) ImcGen.exprImCode.get(binExpr)).oper;
		fragmentStmts.add(new ImcMOVE(result, new ImcBINOP(oper, t1, t2)));
		return result;
	}

	@Override
	public Object visit(AbsFunName funName, Object visArg) {
		ImcCALL imcCallFun = (ImcCALL) ImcGen.exprImCode.get(funName);
		Vector<ImcExpr> imcExprsArgs = new Vector<>();
		imcExprsArgs.add(new ImcCONST(0));
		for (AbsExpr arg : funName.args.args()) {
			imcExprsArgs.add((ImcExpr) arg.accept(this, null));
		}
		ImcCALL newImcCallFun = new ImcCALL(imcCallFun.label, imcExprsArgs);
		if (useTempCall) {
			ImcTEMP result = new ImcTEMP(new Temp());
			fragmentStmts.add(new ImcMOVE(result, newImcCallFun));
			return result;
		}
		return newImcCallFun;
	}

	@Override
	public Object visit(AbsVarName varName, Object visArg) {
		return ImcGen.exprImCode.get(varName);
	}

	@Override
	public Object visit(AbsAtomExpr atomExpr, Object visArg) {
		ImcExpr result = new ImcTEMP(new Temp());
		if (visArg instanceof AbsStmts) {
			fragmentStmts.add(new ImcMOVE(result, ImcGen.exprImCode.get(atomExpr)));
			return result;
		}
		result = ImcGen.exprImCode.get(atomExpr);
		return result;
	}

	@Override
	public Object visit(AbsUnExpr unExpr, Object visArg) {
		dump(unExpr, "=== Unary expression ===");
		// get operator & change sub expression if necessary
		ImcExpr imcUnExpr = ImcGen.exprImCode.get(unExpr);
		ImcExpr result;
		dump(unExpr, imcUnExpr.toString());
		if (imcUnExpr instanceof ImcUNOP) {
			result = new ImcUNOP(((ImcUNOP) imcUnExpr).oper, (ImcExpr) unExpr.subExpr.accept(this, null));
		} else {
			result = imcUnExpr;
		}
		return result;
	}

	@Override
	public Object visit(AbsStmtExpr stmtExpr, Object visArg) {
		dump(stmtExpr, "Statement expression.");
		Temp RV = new Temp();
		ImcTEMP result = new ImcTEMP(RV);

		if (mainFun) {
			dump(stmtExpr, "Main function.");
			Label begLabel = new Label();
			Label endLabel = new Label();
			// create main frame - can't AbsFunDef because it's locked
			Frame mainFrame = Frames.mainFrame;

			// create main fragment
			mainFun = false;
			Vector<ImcStmt> imcStmts = new Vector<>();
			CodeFragment mainFrgm = new CodeFragment(mainFrame, imcStmts, ImcGen.FP, RV, begLabel, endLabel);
			LinCode.add(mainFrgm);

			// walk through decl, stmts and expression
			stmtExpr.decls.accept(this, null);
			stmtExpr.stmts.accept(this, null);
			ImcStmt stmt = new ImcMOVE(result, (ImcExpr) stmtExpr.expr.accept(this, stmtExpr));

			// update fragments statements
			imcStmts.add(new ImcLABEL(begLabel));
			imcStmts.addAll(fragmentStmts);
			imcStmts.add(stmt);
			imcStmts.add(new ImcLABEL(endLabel));
			if(useOptTraces) imcStmts = optTraces(imcStmts);
			mainFrgm.stmts().addAll(imcStmts);
			return result;
		} else {
			dump(stmtExpr, "Statement expression inside function.");
			stmtExpr.decls.accept(this, null);
			stmtExpr.stmts.accept(this, null);
			ImcStmt expr = new ImcMOVE(result, (ImcExpr) stmtExpr.expr.accept(this, stmtExpr));
			fragmentStmts.add(expr);
		}

		return result;
	}


	@Override
	public Object visit(AbsFunDef funDef, Object fragments) {
		dump(funDef, "=== Fun definition ===");
		Frame frame = Frames.frames.get(funDef);
		Temp RV = new Temp();
		Label begLabel = new Label();
		Label endLabel = new Label();
		Vector<ImcStmt> tempStmts = new Vector<ImcStmt>(fragmentStmts);
		fragmentStmts.clear();
		ImcStmt stmt = new ImcMOVE(new ImcTEMP(RV), (ImcExpr) funDef.value.accept(this, null));
		{
			Vector<ImcStmt> canStmts = new Vector<ImcStmt>();
			canStmts.add(new ImcLABEL(begLabel));
			canStmts.addAll(fragmentStmts);
			canStmts.add(stmt);
			canStmts.add(new ImcLABEL(endLabel));
			if(useOptTraces) canStmts = optTraces(canStmts);
			CodeFragment fragment = new CodeFragment(frame, canStmts, ImcGen.FP, RV, begLabel, endLabel);
			LinCode.add(fragment);
		}

		fragmentStmts.clear();
		fragmentStmts.addAll(tempStmts);
		return null;
	}

	@Override
	public Object visit(AbsVarDecl varDecl, Object visArg) {
		Access access = Frames.accesses.get(varDecl);
		if (access instanceof AbsAccess) {
			AbsAccess absAccess = (AbsAccess) access;
			DataFragment fragment = new DataFragment(absAccess.label, absAccess.size);
			LinCode.add(fragment);
		}
		return null;
	}
}
