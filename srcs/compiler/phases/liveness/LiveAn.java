package compiler.phases.liveness;

import compiler.Main;
import compiler.phases.Phase;
import compiler.phases.asmgen.AsmGen;
import compiler.phases.asmgen.AsmInstr;
import compiler.phases.frames.Label;
import compiler.phases.frames.Temp;
import compiler.phases.lincode.CodeFragment;
import compiler.phases.lincode.Fragment;

import java.util.*;

public class LiveAn extends Phase {

	/** Flags */
	private final int includeNNodes = 1; // number of nodes that seems relevant for printout
	private final LIVEDEBUG debug = LIVEDEBUG.INFGRAPH; // debug mode
	private final boolean buildExtendedInferenceGraph = true;
	private final boolean useOptInterferenceGraph = true;

	/** Final interference graph */
	private ArrayList<InterferenceGraph> interferenceGraphs = new ArrayList<>();

	/** Pointer to instructions for each fragment */
	private HashMap<Fragment, LinkedList<AsmInstr>> fragmentsInstructions;
	private LinkedList<AsmInstr> instructions;


	/** Instructions for each fragment */
	private int fg = (AsmGen.addFragmentComment) ? 1 : 0;

	/** Number of iterations needed to analyse liveness */
	private int livenessSearchIteration;

	public LiveAn() {
		super("liveness");
		dumpTable = new ArrayList<>();
		colSize = new ArrayList<>();
	}

	enum LIVEDEBUG {LIVENESSTABLE, INSTRUCTIONS, INFGRAPH, NONE}

	/** Debug print out options */
	private ArrayList<ArrayList<String>> dumpTable;
	private ArrayList<Integer> colSize;


	/** Dump information for each instruction */
	private void dump(AsmInstr instr) {
		if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.LIVENESS) {
			System.out.println(instr.toString() + ": \n" +
				"	Defs: " + instr.defs().toString() + "\n" +
				"	Uses: " + instr.uses().toString() + "\n" +
				"	Jump: " + instr.jumps().toString() + "\n" +
				"	Pred: " + instr.pred().toString() + "\n" +
				"	Succ: " + instr.succ().toString() + "\n" +
				"	In	: " + instr.in().toString() + "\n" +
				"	Out : " + instr.out().toString());
		}
	}

	/** Remembers column for printout when doing liveness analysis */
	private void dumpRememberColumn() {
		if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.LIVENESS) {
			int n = livenessSearchIteration;
			// dump all
			colSize.add(0);
			if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.LIVENESS)
				for (int i = fg; i < instructions.size() - fg; i++) {
					AsmInstr instr = instructions.get(i);
					String msg = " | " + instr.in().toString() + " "
						+ instr.out().toString() + " "
						+ instr.defs().toString();
					dumpTable.get(i - fg).add(msg);
					colSize.set(n, Math.max(colSize.get(n), msg.length()));
				}
			n++;
			livenessSearchIteration = n;
		}
	}


	private void dumpLivenessTable() {
		if ((Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.LIVENESS) && debug == LIVEDEBUG.LIVENESSTABLE) {
			// table header
			for (int i = 0; i < dumpTable.get(0).size(); i++) {
				System.out.format("%1$-" + (colSize.get(i) + 5) + "s", (i == 0) ?
					"Instr" : "in" + i + " out" + i + " defs" + i);
			}
			System.out.println();

			// table body
			for (int i = fg; i < instructions.size() - fg; i++) {
				int n = 0;
				for (String msg : dumpTable.get(i - fg)) {
					System.out.format("%1$-" + (colSize.get(n++) + 5) + "s", msg);
				}
				System.out.println();
			}
		}
	}

	private void dumpInstructions() {
		if ((Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.LIVENESS) && debug == LIVEDEBUG.INSTRUCTIONS) {
			// dump all
			for (int i = fg; i < instructions.size() - fg; i++) {
				dump(instructions.get(i));
			}
		}
	}

	/** Print functions */

	private void printFragmentInstr(LinkedList<AsmInstr> instructions) {
		for (AsmInstr instr : instructions) {
			System.out.format("%1$-18s %2$-18s\n", instr + " ",
				(instr.in().size() > includeNNodes ? "# I:" + instr.in().toString() : "") +
					(instr.out().size() > includeNNodes ?
						(instr.in().size() > includeNNodes ? "" : "#") +
							" O:" + instr.out().toString() + " " : ""));
		}
	}


	private void printFragmentInterferenceGraph(InterferenceGraph graph, String fragmentName) {
		if (debug == LIVEDEBUG.INFGRAPH) {
			String title = "==== INTERFERENCE GRAPH [ " + fragmentName + " ] ====";
			System.out.println(title);
			System.out.print(graph.toString());
			System.out.println(new String(new char[title.length()]).replace("\0", "="));
		}
	}


	/** Main logic of liveness analysis */

	private void buildExtendedInterferenceGraph() {
		InterferenceGraph fragmentInterferenceGraph = new InterferenceGraph(instructions, useOptInterferenceGraph);
		interferenceGraphs.add(fragmentInterferenceGraph);
	}

	private void getBrachSuccessors(AsmInstr instr) {
		for (Label label : instr.jumps()) {
			AsmInstr labelInstr = AsmGen.labelInstructions().get(label);
			instr.succ().add(AsmGen.labelInstructions().get(label));
			labelInstr.pred().add(instr);
		}
	}

	private void getSuccessors(AsmInstr instr, AsmInstr directSucc) {
		getBrachSuccessors(instr);
		instr.succ().add(directSucc);
		directSucc.pred().add(instr);
	}

	public void buildInterferenceGraph(LinkedList<AsmInstr> fragmentInstructions) {
		this.instructions = fragmentInstructions;
		colSize.clear();
		dumpTable.clear();

		// get successors/predecessors
		colSize.add(0);
		int fg = (AsmGen.addFragmentComment) ? 1 : 0;
		for (int i = fg; i < instructions.size() - fg; i++) {
			AsmInstr instr = instructions.get(i);
			// skip comments
			if (!instr.toString().startsWith("%")) {
				// add successor/predecessor
				if (i < instructions.size() - 2) {
					getSuccessors(instr, instructions.get(i + 1));
				} else {
					getBrachSuccessors(instr);
				}

				dumpTable.add(new ArrayList<>());
				dumpTable.get(dumpTable.size() - 1).add(instr.toString());
				colSize.set(0, Math.max(colSize.get(0), instructions.get(i).toString().length()));
			}
		}


		Set<Temp> differenceOutDef = new HashSet<>();
		boolean changes = true;
		livenessSearchIteration = 1;
		while (changes) {
			for (int i = instructions.size() - fg - 1; i >= 0; i--) {
				AsmInstr instr = instructions.get(i);
				instr.inTmp().clear();
				instr.inTmp().addAll(instr.in());
				instr.outTmp().clear();
				instr.outTmp().addAll(instr.out());

				// reset in/out
				instr.in().clear();
				instr.out().clear();
				differenceOutDef.clear();

				// new in
				differenceOutDef.addAll(instr.outTmp());
				differenceOutDef.removeAll(instr.defs());
				instr.in().addAll(instr.uses());
				instr.in().addAll(differenceOutDef);
			}
			// add all to out
			for (int i = fg; i < instructions.size() - fg; i++) {
				AsmInstr instr = instructions.get(i);
				for (AsmInstr succ : instr.succ()) {
					instr.out().addAll(succ.in());
				}
			}

			// check if no difference in this iteration
			for (int i = fg; i < instructions.size() - fg; i++) {
				AsmInstr instr = instructions.get(i);
				if (!instr.in().equals(instr.inTmp()))
					break;
				if (!instr.out().equals(instr.outTmp()))
					break;
				// all the same
				if (i == instructions.size() - fg - 1)
					changes = false;
			}
			dumpRememberColumn();
		}
		dumpLivenessTable();
		dumpInstructions();
		if (buildExtendedInferenceGraph) buildExtendedInterferenceGraph();
	}

	public void livenessAnalysis(HashMap<Fragment, LinkedList<AsmInstr>> fragmentsInstructions) {
		this.fragmentsInstructions = fragmentsInstructions;
		for (LinkedList<AsmInstr> instructions : this.fragmentsInstructions.values()) {
			buildInterferenceGraph(instructions);
		}
	}

	@Override
	public void close() {
		if (Main.cmdLine.get("--target-phase").equals("liveness")) {
			if (fragmentsInstructions != null) {
				int i = 0;
				for (Map.Entry<Fragment, LinkedList<AsmInstr>> entry : fragmentsInstructions.entrySet()) {
					printFragmentInstr(entry.getValue());
					printFragmentInterferenceGraph(interferenceGraphs.get(i++),
						((CodeFragment) entry.getKey()).frame.label.name);
				}
			} else if (instructions != null) {
				printFragmentInstr(instructions);
				printFragmentInterferenceGraph(interferenceGraphs.get(0), "");
			}
		}
		super.close();
	}

	public static void reset() {}
}
