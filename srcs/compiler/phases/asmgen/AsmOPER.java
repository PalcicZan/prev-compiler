package compiler.phases.asmgen;

import java.util.*;

import common.logger.Logger;
import compiler.phases.frames.*;

/**
 * A general assembly operation.
 *
 * @author sliva
 */
public class AsmOPER extends AsmInstr {

	/** The string representation of the instruction. */
	private final String instr;

	/** The list of temporaries used by this instruction. */
	private final Vector<Temp> uses;

	/** The list of temporaries defined by this instruction. */
	private final Vector<Temp> defs;

	/** The list of labels this instruction can jump to. */
	private final Vector<Label> jumps;

	private final Set<Temp> in;
	private final Set<Temp> out;
	private final Set<Temp> inTmp;
	private final Set<Temp> outTmp;
	private final Set<AsmInstr> succ;
	private final Set<AsmInstr> pred;

	/**
	 * Constructs a new assembly instruction.
	 *
	 * @param instr The string representation of the instruction.
	 * @param uses  The list of temporaries used by this instruction.
	 * @param defs  The list of temporaries defined by this instruction.
	 * @param jumps The list of labels this instruction can jump to.
	 */
	public AsmOPER(String instr, Vector<Temp> uses, Vector<Temp> defs, Vector<Label> jumps) {
		this.instr = instr;
		this.uses = uses == null ? new Vector<Temp>() : uses;
		this.defs = defs == null ? new Vector<Temp>() : defs;
		this.jumps = jumps == null ? new Vector<Label>() : jumps;

		this.in = new HashSet<>();
		this.out = new HashSet<>();
		this.inTmp = new HashSet<>();
		this.outTmp = new HashSet<>();
		this.succ = new HashSet<>();
		this.pred = new HashSet<>();
	}

	@Override
	public Vector<Temp> uses() {
//		return new Vector<Temp>(uses);
		return uses;
	}

	@Override
	public Vector<Temp> defs() {
//		return new Vector<Temp>(defs);
		return defs;
	}

	@Override
	public Vector<Label> jumps() {
		return new Vector<Label>(jumps);
	}

	@Override
	public Set<AsmInstr> pred() {
		return pred;
	}

	@Override
	public Set<AsmInstr> succ() { return succ; }

	@Override
	public Set<Temp> in() {
		return in;
	}

	public Set<Temp> out() { return out; }

	public Set<Temp> inTmp() {
		return inTmp;
	}

	public Set<Temp> outTmp() { return outTmp; }


	@Override
	public String toString() {
		String instruction = this.instr;
		for (int i = 0; i < uses.size(); i++)
			instruction = instruction.replace("`s" + i, "T" + uses.get(i).temp);
		for (int i = 0; i < defs.size(); i++)
			instruction = instruction.replace("`d" + i, "T" + defs.get(i).temp);
		return instruction;
	}

	@Override
	public String toString(HashMap<Temp, Integer> regs) {
		String instruction = this.instr;
		for (int i = 0; i < uses.size(); i++)
			instruction = instruction.replace("`s" + i, "$" + regs.get(uses.get(i)));
		for (int i = 0; i < defs.size(); i++)
			instruction = instruction.replace("`d" + i, "$" + regs.get(defs.get(i)));
		return instruction;
	}


	@Override
	public String toString(boolean instr) {
		return this.instr;
	}

	@Override
	public void log(Logger logger) {
		if (logger == null)
			return;
		logger.begElement("asmoper");
		logger.addAttribute("label", "");
		logger.addAttribute("token", toString());
		logger.endElement();

	}
}
