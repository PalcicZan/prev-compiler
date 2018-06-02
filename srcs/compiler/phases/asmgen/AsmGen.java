package compiler.phases.asmgen;

import compiler.Main;
import compiler.phases.Phase;
import compiler.phases.frames.Label;
import compiler.phases.imcgen.code.ImcStmt;
import compiler.phases.lincode.CodeFragment;
import compiler.phases.lincode.Fragment;
import compiler.phases.regalloc.InstrLogger;

import java.util.*;

public class AsmGen extends Phase {

	public static final boolean addFragmentComment = true;
	private static final boolean printOnAdd = false;
	private static Fragment currFrag;

	private static final HashMap<Fragment, LinkedList<AsmInstr>> fragmentInstructions = new HashMap<>();
	private static final HashMap<Label, AsmInstr> labelInstruction = new HashMap<>();

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
		fragmentInstructions.get(currFrag).add(instr);
	}

	public static void addLabel(Label label, AsmInstr instrLabel) {
		if (currFrag != null) add(instrLabel);
		labelInstruction.put(label, instrLabel);
	}

	public static AsmInstr removeLast() {
		return fragmentInstructions.get(currFrag).removeLast();
	}

	/**
	 * Returns the list of fragments instructions.
	 *
	 * @return The list of fragments instructions.
	 */
	public static HashMap<Fragment, LinkedList<AsmInstr>> instructions() {
		return new HashMap<Fragment, LinkedList<AsmInstr>>(fragmentInstructions);
	}

	public static HashMap<Label, AsmInstr> labelInstructions() {
		return labelInstruction;
	}


	public void generateInstructions(LinkedList<Fragment> fragments) {
		String titleComment = "";
		for (Fragment fragment : fragments) {
			// process each fragment - ignore data fragment
			if (fragment instanceof CodeFragment) {
				currFrag = fragment;
				fragmentInstructions.put(currFrag, new LinkedList<>());
				if (addFragmentComment) {
					titleComment = InstrLogger.comment("Fun body [" + ((CodeFragment) fragment).frame.label.name + "]");
					AsmGen.add(new AsmOPER(titleComment, null, null, null));
				}
				for (ImcStmt stmt : ((CodeFragment) fragment).stmts()) {
					stmt.accept(new InstrEvaluator((CodeFragment) fragment), null);
				}
				if (addFragmentComment) {
					String endComment = InstrLogger.comment("");
					AsmGen.add(new AsmOPER(endComment, null, null, null));
				}
			}
		}
	}


	@Override
	public void close() {
		if (Main.cmdLine.get("--target-phase").equals("asmgen"))
			for (LinkedList<AsmInstr> instructions : new TreeMap<Fragment, LinkedList<AsmInstr>>(fragmentInstructions).values()) {
				for (AsmInstr instr : instructions) {
					if (!printOnAdd) System.out.println(instr);
					instr.log(logger);
				}
			}
		super.close();
	}

	public static void reset() {
		fragmentInstructions.clear();
		labelInstruction.clear();
		currFrag = null;
	}
}
