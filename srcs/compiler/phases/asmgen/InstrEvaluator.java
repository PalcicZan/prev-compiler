package compiler.phases.asmgen;

import compiler.Main;
import compiler.phases.frames.Label;
import compiler.phases.frames.Temp;
import compiler.phases.imcgen.ImcVisitor;
import compiler.phases.imcgen.code.*;

import java.util.Vector;

public class InstrEvaluator implements ImcVisitor<Object, Object> {

	/** Options flags */
	private final String spaceOper = "";

	/** Flags */
	private boolean isConst16bit = false;
	private boolean isConst8bit = false;
	private boolean isPositiveConst = false;
	private MemType memType = MemType.NONE;

	private enum MemType {NONE, LOAD, STORE}

	private StringBuilder instrBuilder = new StringBuilder();

	public static int nRegs = 8;
	public static int FPreg = 254;

	/** Debug dump */
	private void dump(ImcInstr node, String msg) {
		if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.ASMGEN) {
			if (node != null) {
				System.err.println("[" + node.getClass().getSimpleName() + "]: " + msg);
			} else {
				System.err.println(msg);
			}
		}
	}

	/** Reset flags on each node */
	private void resetFlags() {
		isConst16bit = false;
		isConst8bit = false;
		isPositiveConst = false;
		memType = memType.NONE;
	}

	private ImcTEMP createTemp(Object temp) {
		// create new temp if not given
		if (temp instanceof ImcTEMP) {
			return (ImcTEMP) temp;
		} else {
			return new ImcTEMP(new Temp());
		}
	}

	@Override
	public Object visit(ImcBINOP binOp, Object def) {
		MemType currMemType = memType;
		// visit
		memType = MemType.LOAD;
		isConst8bit = false;
		isConst16bit = false;
		Object fstExprTemp = binOp.fstExpr.accept(this, null);
		Object sndExprTemp = binOp.sndExpr.accept(this, null);
		memType = currMemType;

		// prepare for instruction
		instrBuilder.setLength(0);
		Vector<Temp> uses = new Vector<>();
		Vector<Temp> defs = new Vector<>();

		// if predefined define
		ImcTEMP result = createTemp(def);
		defs.add(result.temp);

		// set operation
		switch (binOp.oper) {
			case SUB:
				instrBuilder.append("SUB `d0, ");
				break;
			case ADD:
				instrBuilder.append("ADD `d0, ");
				break;
			case MUL:
				instrBuilder.append("MUL `d0, ");
				break;
			case DIV:
				instrBuilder.append("DIV `d0, ");
				break;
			case AND:
				instrBuilder.append("AND `d0, ");
				break;
			case XOR:
				instrBuilder.append("XOR `d0, ");
				break;
			case IOR:
				instrBuilder.append("OR `d0, ");
				break;
			case EQU: case NEQ:
			case LEQ: case GEQ:
			case GTH: case LTH:
				instrBuilder.append("CMP `d0, ");
				break;
			case MOD:
				instrBuilder.append("DIV `d0, ");
				break;
		}
		// load value in case of mem in subexpression

		// first operand must be stored in register/name
		if (fstExprTemp instanceof ImcTEMP) {
			instrBuilder.append("`s0, ");
			uses.add(((ImcTEMP) fstExprTemp).temp);
		} else if (fstExprTemp instanceof ImcNAME) {
			instrBuilder.append(((ImcNAME) fstExprTemp).label.name).append(", ");
		}

		// second operand
		if (binOp.sndExpr instanceof ImcCONST && isConst8bit) {
			// is second operand 16-bit constant
			if (isPositiveConst) {
				// use constant value
				long value = ((ImcCONST) binOp.sndExpr).value;
				instrBuilder.append(value);
				AsmGen.removeLast();
				if(value == 0 && uses.size() == 1 && defs.size() == 1){
					AsmGen.add(new AsmMOVE(instrBuilder.toString(), uses, defs, null));
					return result;
				}
			} else if (binOp.oper == ImcBINOP.Oper.ADD && isConst8bit && memType == MemType.NONE) {
				// substitute instead of addition
				instrBuilder.replace(0, 3, "SUB");
				instrBuilder.append(Math.abs(((ImcCONST) binOp.sndExpr).value));
				AsmGen.removeLast();
			} else {
				instrBuilder.append("`s").append(uses.size());
				uses.add(((ImcTEMP) sndExprTemp).temp);
			}
		} else if (sndExprTemp instanceof ImcNAME) {
			instrBuilder.append(((ImcNAME) sndExprTemp).label);
		} else if (sndExprTemp instanceof ImcTEMP) {
			// second operand is register/temp
			instrBuilder.append("`s").append(uses.size());
			uses.add(((ImcTEMP) sndExprTemp).temp);
		}

		AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
		instrBuilder.setLength(0);

		uses = new Vector<>(defs);
		defs = new Vector<>(defs);

		// needed second instruction
		switch (binOp.oper) {
			case EQU:
				instrBuilder.append("ZSZ `d0, `s0, 1");
				AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
				break;
			case NEQ:
				instrBuilder.append("ZSNZ `d0, `s0, 1");
				AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
				break;
			case LEQ:
				instrBuilder.append("ZSNP `d0, `s0, 1");
				AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
				break;
			case GEQ:
				instrBuilder.append("ZSNN `d0, `s0, 1");
				AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
				break;
			case GTH:
				instrBuilder.append("ZSP `d0, `s0, 1");
				AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
				break;
			case LTH:
				instrBuilder.append("ZSN `d0, `s0, 1");
				AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
				break;
			case MOD:
				instrBuilder.append("SET `d0, rR");
				AsmGen.add(new AsmOPER(instrBuilder.toString(), null, defs, null));
				break;
		}

		return result;
	}

	@Override
	public Object visit(ImcCONST constant, Object def) {
		isPositiveConst = true;
		long imcConst = constant.value;
		Vector<Temp> defs = new Vector<>();

		ImcTEMP result = createTemp(def);
		defs.add(result.temp);

		isConst16bit = (Math.abs(imcConst) >> 16) == 0;
		isConst8bit = (Math.abs(imcConst) >> 8) == 0;
		isPositiveConst = imcConst >= 0;

		if (imcConst == 0) {
			AsmGen.add(new AsmOPER("SETL `d0, " + imcConst, null, defs, null));
			return result;
		}

		// set low bits
		long l = (imcConst & 0xFFFF);
		boolean isSet = false;
		if (l != 0) {
			if (imcConst < 0 && imcConst > -256) {
				AsmGen.add(new AsmOPER("NEG `d0, 0, " + Math.abs(imcConst), null, defs, null));
				return result;
			} else {
				AsmGen.add(new AsmOPER("SETL `d0, " + l, null, defs, null));
				isSet = true;
			}
		}

		// set medium low bits
		long ml = (imcConst >> 16) & 0xFFFF;
		if (ml != 0) {
			if (isSet) AsmGen.add(new AsmOPER("INCML `d0, " + ml, null, defs, null));
			else AsmGen.add(new AsmOPER("SETML `d0, " + ml, null, defs, null));
			isSet = true;
		}

		// set medium high bits
		long mh = (imcConst >> 32) & 0xFFFF;
		if (mh != 0) {
			if (isSet) AsmGen.add(new AsmOPER("INCMH `d0, " + mh, null, defs, null));
			else AsmGen.add(new AsmOPER("SETMH `d0, " + mh, null, defs, null));
			isSet = true;
		}

		// set high high bits
		long h = (imcConst >> 48) & 0xFFFF;
		if (h != 0) {
			if (isSet) AsmGen.add(new AsmOPER("INCH `d0, " + h, null, defs, null));
			else AsmGen.add(new AsmOPER("SETH `d0, " + h, null, defs, null));
		}

		// return register defined
		return result;
	}

	@Override
	public Object visit(ImcCALL call, Object visArg) {
		// set FP to 0
		long offset = 0;
		for (ImcExpr arg : call.args()) {
			memType = MemType.LOAD;
			ImcTEMP tempArg = (ImcTEMP) arg.accept(this, null);
			instrBuilder.setLength(0);
			Vector<Temp> uses = new Vector<>();
			uses.add(tempArg.temp);
			instrBuilder.append("STO `s0, $254, ").append(offset);
			AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, null, null));
			offset += 8;
		}
		instrBuilder.setLength(0);
		AsmGen.add(new AsmOPER(instrBuilder.append("PUSHJ $").append(nRegs).append(", ").append(call.label.name).toString(), null, null, null));
		return null;
	}

	@Override
	public Object visit(ImcJUMP jump, Object visArg) {
		instrBuilder.setLength(0);
		instrBuilder.append("JMP ").append(jump.label.name);
		Vector<Label> labels = new Vector<>();
		labels.add(jump.label);
		AsmGen.add(new AsmOPER(instrBuilder.toString(), null, null, labels));
		return null;
	}

	@Override
	public Object visit(ImcLABEL label, Object visArg) {
		dump(label, label.label.name);
		AsmInstr instrLabel = new AsmLABEL(label.label);
		AsmGen.addLabel(label.label, instrLabel);
		return null;
	}

	@Override
	public Object visit(ImcMEM mem, Object def) {
		// save is load and access child instruction
		MemType currIsLoad = memType;
		memType = MemType.LOAD;
		Object memExpr = mem.addr.accept(this, null);
		memType = currIsLoad;
		// reset instruction
		instrBuilder.setLength(0);

		Vector<Temp> uses = new Vector<>();
		Vector<Temp> defs = null;
		ImcTEMP result = null;
		if (memType == MemType.LOAD) {
			defs = new Vector<>();
			// general load
			// where to load
			result = createTemp(def);
			defs.add(result.temp);
			instrBuilder.append("LDO `d0, ");

		} else if (memType == MemType.STORE) {
			instrBuilder.append("STO ");

			// what to store
			if (def instanceof ImcTEMP) {
				// store temp
				uses.add(((ImcTEMP) def).temp);
				instrBuilder.append("`s0, ");
			} else if (def instanceof ImcNAME) {
				// store name
				instrBuilder.append(((ImcNAME) def).label.name).append(", ");
			}
		} else {
			System.err.println("Unknown LOAD/STORE!");
		}

		// get address
		if (mem.addr instanceof ImcNAME) {
			// direct access
			instrBuilder.append(((ImcNAME) memExpr).label.name).append(", 0");
		} else if (mem.addr instanceof ImcBINOP) {
			AsmOPER addrOper = (AsmOPER) AsmGen.removeLast();
			uses.addAll(addrOper.uses());
			String[] operands = addrOper.toString().split(" ");
			instrBuilder.append(operands[2]).append(" ");
			instrBuilder.append(operands[3]);
		} else {
			// worst case - general store
			instrBuilder.append("`s").append(uses.size()).append(", 0");
			uses.add(((ImcTEMP) memExpr).temp);
		}

		AsmGen.add(new AsmOPER(instrBuilder.toString(), uses, defs, null));
		return result;
	}

	@Override
	public Object visit(ImcMOVE move, Object visArg) {
		ImcTEMP result = null;
		memType = MemType.LOAD;

		if (move.dst instanceof ImcTEMP) {
			Vector<Temp> defs = new Vector<>();
			defs.add(((ImcTEMP) move.dst).temp);
			if (move.src instanceof ImcCONST) {
				// load constant into register
				result = (ImcTEMP) move.src.accept(this, move.dst);
			} else if (move.src instanceof ImcTEMP) {
				// move temp into temp
				Vector<Temp> uses = new Vector<>();
				uses.add(((ImcTEMP) move.src).temp);
				AsmGen.add(new AsmMOVE("SET `d0, `s0", uses, defs, null));
				result = ((ImcTEMP) move.dst);
			} else if (move.src instanceof ImcCALL) {
				// return from function
				result = (ImcTEMP) move.src.accept(this, null);
				AsmGen.add(new AsmOPER("LDO `d0, $" + FPreg + ", 0", null, defs, null));
			} else if (move.src instanceof ImcNAME) {
				AsmGen.add(new AsmOPER("LDA `d0, " + ((ImcNAME) move.src).label.name, null, defs, null));
			} else if (move.src instanceof ImcBINOP || move.src instanceof ImcUNOP || move.src instanceof ImcMEM) {
				result = (ImcTEMP) move.src.accept(this, move.dst.accept(this, move.dst));
			} else {
				// worst case, create new variable
				result = (ImcTEMP) move.src.accept(this, move.dst.accept(this, move.dst));
				Vector<Temp> uses = new Vector<>();
				uses.add(result.temp);
				AsmGen.add(new AsmMOVE("SET `d0, `s0", uses, defs, null));
			}
		} else if (move.dst instanceof ImcMEM) {
			// store const
			memType = MemType.LOAD;
			Object imcSrc = move.src.accept(this, null);
			memType = MemType.STORE;
			move.dst.accept(this, imcSrc);
		}
		return result;
	}

	@Override
	public Object visit(ImcTEMP temp, Object visArg) {
		return temp;
	}

	@Override
	public Object visit(ImcNAME name, Object visArg) {
		return name;
	}

	@Override
	public Object visit(ImcCJUMP cjump, Object visArg) {
		Vector<Temp> uses = new Vector<>();
		Vector<Label> jumps = new Vector<>();
		ImcTEMP condVar = (ImcTEMP) cjump.cond.accept(this, null);
		uses.add(condVar.temp);
		jumps.add(cjump.negLabel);
		AsmGen.add(new AsmOPER("BNZ `s0, " + cjump.negLabel.name, uses, null, jumps));
		jumps = new Vector<>();
		jumps.add(cjump.posLabel);
		AsmGen.add(new AsmOPER("JMP " + cjump.posLabel.name, null, null, jumps));
		return null;
	}

	@Override
	public Object visit(ImcUNOP unOp, Object visArg) {
		Vector<Temp> defs = new Vector<>();
		Vector<Temp> uses = new Vector<>();
		ImcTEMP result = null;
		ImcTEMP unOpExpr = (ImcTEMP) unOp.subExpr.accept(this, visArg);
		defs.add(unOpExpr.temp);
		uses.add(unOpExpr.temp);
		result = unOpExpr;
		if (unOp.oper == ImcUNOP.Oper.NEG) {
			AsmGen.add(new AsmOPER("NEG `d0, `s0", uses, defs, null));
		} else if (unOp.oper == ImcUNOP.Oper.NOT) {
			AsmGen.add(new AsmOPER("ZSZ `d0, `s0 1", uses, defs, null));
		}
		return result;
	}

	/** Should not be reachable */

	@Override
	public Object visit(ImcESTMT eStmt, Object visArg) {
		eStmt.expr.accept(this, null);
		return null;
	}

	@Override
	public Object visit(ImcSEXPR sExpr, Object visArg) {
		sExpr.stmt.accept(this, null);
		sExpr.expr.accept(this, null);
		return null;
	}

	@Override
	public Object visit(ImcSTMTS stmts, Object visArg) {
		for (ImcStmt stmt : stmts.stmts()) {
			stmt.accept(this, null);
		}
		return null;
	}
}
