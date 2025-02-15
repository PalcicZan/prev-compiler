package compiler.phases.asmgen;

import java.util.*;

import common.logger.Loggable;
import compiler.phases.frames.*;

/**
 * An assembly instruction (operation or label).
 *
 * @author sliva
 */
public abstract class AsmInstr implements Loggable {

	/**
	 * list of temporaries used by this instruction.
	 *
	 * @return The list of temporaries used by this instruction.
	 */
	public abstract Vector<Temp> uses();

	/**
	 * Returns the list of temporaries defined by this instruction.
	 *
	 * @return The list of temporaries defined by this instruction.
	 */
	public abstract Vector<Temp> defs();

	/**
	 * Returns the list of labels this instruction can jump to.
	 *
	 * @returnThe list of labels this instruction can jump to.
	 */
	public abstract Vector<Label> jumps();


	public abstract Set<Temp> in();

	public abstract Set<Temp> out();

	public abstract Set<Temp> inTmp();

	public abstract Set<Temp> outTmp();

	public abstract Set<AsmInstr> succ();

	public abstract Set<AsmInstr> pred();

	/**
	 * Returns a string representing this instruction with temporaries.
	 *
	 * @return A string representing this instruction with temporaries.
	 */
	public abstract String toString();

	/**
	 * Returns a string representing this instruction with registers.
	 *
	 * @param regs A mapping of temporaries to registers.
	 * @return A a string representing this instruction with registers.
	 */
	public abstract String toString(HashMap<Temp, Integer> regs);

	public abstract String toString(boolean instr);


}
