package compiler.phases.asmgen;

import compiler.Main;
import compiler.phases.Phase;
import compiler.phases.imcgen.code.ImcStmt;
import compiler.phases.lincode.CodeFragment;
import compiler.phases.lincode.Fragment;

import java.util.LinkedList;

public class AsmGen extends Phase {

	private static final boolean addAsmComments = true;
	private static final boolean printOnAdd = false;

	private static final LinkedList<AsmInstr> instructions = new LinkedList<AsmInstr>();

	public AsmGen() {
		super("asmgen");
	}

	/**
	 * Adds a new instruction to a list of instructions.
	 *
	 * @param instr The new instruction.
	 */
	public static void add(AsmInstr instr) {
		if (printOnAdd) System.out.println(instr);
		instructions.add(instr);
	}

	public static AsmInstr removeLast() {
		return instructions.removeLast();
	}

	/**
	 * Returns the list of all instructions.
	 *
	 * @return The list of all instructions.
	 */
	public static LinkedList<AsmInstr> instructions() {
		return new LinkedList<AsmInstr>(instructions);
	}

	public void generateInstructions(LinkedList<Fragment> fragments) {
		for (Fragment fragment : fragments) {
			// process each fragment - ignore data fragment
			if (fragment instanceof CodeFragment) {
				if (addAsmComments) AsmGen.add(new AsmOPER("% ====== "+ ((CodeFragment) fragment).frame.label.name +" ======",
					null, null, null));
				for (ImcStmt stmt : ((CodeFragment) fragment).stmts()) {
					stmt.accept(new InstrEvaluator(), null);
				}
				if (addAsmComments) AsmGen.add(new AsmOPER("% ===============",null, null, null));
			}
//			else if (fragment instanceof DataFragment){
//				dataSize.put(((DataFragment) fragment).label, ((DataFragment) fragment).size);
//			}
		}
	}


	@Override
	public void close() {
		if(Main.cmdLine.get("--target-phase").equals("asmgen"))
		for (AsmInstr instructions : instructions()) {
			if(!printOnAdd) System.out.println(instructions);
			instructions.log(logger);
		}
		super.close();
	}

	public static void reset() {
		instructions.clear();
	}
}
