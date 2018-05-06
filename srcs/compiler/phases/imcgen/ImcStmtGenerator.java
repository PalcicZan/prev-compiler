package compiler.phases.imcgen;

import java.util.*;

import compiler.phases.abstr.*;
import compiler.phases.abstr.abstree.*;
import compiler.phases.frames.*;
import compiler.phases.imcgen.code.*;

public class ImcStmtGenerator implements AbsVisitor<ImcStmt, Stack<Frame>> {

	private ImcExprGenerator imcExprGenerator;

	public ImcStmtGenerator(ImcExprGenerator ieg) {
		imcExprGenerator = ieg;
	}

	@Override
	public ImcStmt visit(AbsAssignStmt assignStmt, Stack<Frame> visArg) {
		ImcExpr imsDst = assignStmt.dst.accept(imcExprGenerator, null);
		ImcExpr imsSrc = assignStmt.src.accept(imcExprGenerator, null);
		return ImcGen.stmtImCode.put(assignStmt, new ImcMOVE(imsDst, imsSrc));
	}

	@Override
	public ImcStmt visit(AbsExprStmt exprStmt, Stack<Frame> visArg) {
		ImcExpr imcExpr = exprStmt.expr.accept(imcExprGenerator, null);
		// imcExpr = ImcGen.accessValue(imcExpr);
		return ImcGen.stmtImCode.put(exprStmt, new ImcESTMT(imcExpr));
	}

	@Override
	public ImcStmt visit(AbsIfStmt ifStmt, Stack<Frame> visArg) {

		Vector<ImcStmt> imcStmts = new Vector<>();
		ImcExpr imcCond = ifStmt.cond.accept(imcExprGenerator, null);
		// labels
		Label posLabel = new Label();
		Label negLabel = new Label();
		Label endLabel = new Label();
		ImcLABEL imcPosLabel = new ImcLABEL(posLabel);
		ImcLABEL imcNegLabel = new ImcLABEL(negLabel);
		ImcLABEL imcEndLabel = new ImcLABEL(endLabel);
		// conditionals
		imcStmts.add(new ImcCJUMP(imcCond, posLabel, negLabel));
		imcStmts.add(imcPosLabel);
		// true part
		imcStmts.add(ifStmt.thenBody.accept(this, null));
		if (ifStmt.elseBody != null) imcStmts.add(new ImcJUMP(endLabel));
		// false part
		imcStmts.add(imcNegLabel);
		if (ifStmt.elseBody != null) {
			imcStmts.add(ifStmt.elseBody.accept(this, null));
			imcStmts.add(imcEndLabel);
		}

		return ImcGen.stmtImCode.put(ifStmt, new ImcSTMTS(imcStmts));
	}

	@Override
	public ImcStmt visit(AbsStmts stmts, Stack<Frame> visArg) {
		Vector<ImcStmt> imcStmtsVec = new Vector<>();
		for (AbsStmt stmt : stmts.stmts()) {
			ImcStmt imcStmt = stmt.accept(this, null);
			if (imcStmt != null) // it should never be null
				imcStmtsVec.add(imcStmt);
		}
		return new ImcSTMTS(imcStmtsVec);
	}

	@Override
	public ImcStmt visit(AbsWhileStmt whileStmt, Stack<Frame> visArg) {
		Vector<ImcStmt> imcStmts = new Vector<>();
		ImcExpr imcCond = whileStmt.cond.accept(imcExprGenerator, null);
		// labels
		Label posLabel = new Label();
		Label negLabel = new Label();
		Label startLabel = new Label();
		ImcLABEL imcPosLabel = new ImcLABEL(posLabel);
		ImcLABEL imcNegLabel = new ImcLABEL(negLabel);
		ImcLABEL imcStartLabel = new ImcLABEL(startLabel);
		// conditionals
		imcStmts.add(imcStartLabel);
		imcStmts.add(new ImcCJUMP(imcCond, posLabel, negLabel));
		imcStmts.add(imcPosLabel);
		// true part
		imcStmts.add(whileStmt.body.accept(this, null));
		imcStmts.add(new ImcJUMP(startLabel));
		// false part
		imcStmts.add(imcNegLabel);

		return ImcGen.stmtImCode.put(whileStmt, new ImcSTMTS(imcStmts));
	}
}
