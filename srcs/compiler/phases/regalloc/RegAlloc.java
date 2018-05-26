package compiler.phases.regalloc;

import compiler.Main;
import compiler.phases.Phase;
import compiler.phases.asmgen.AsmInstr;
import compiler.phases.liveness.InterferenceGraph;

import java.util.*;

public class RegAlloc extends Phase {

	/** Flags */
	public static final boolean useOrgInstrAsComm = false;
	public static final boolean showSpilledComments = false;
	public static final boolean showRegMapping = false;
	public static final boolean showInterferenceGraph = true;
	public static final boolean useNewSpilledTemp = true;
	public static int nReg = Main.nReg;

	/** Final interference graph */
	private ArrayList<ColoredGraph> interferenceColeredGraphs;

	public RegAlloc(ArrayList<InterferenceGraph> interferenceGraphs) {
		super("regalloc");
		interferenceColeredGraphs = new ArrayList<>();
		allocateRegisters(interferenceGraphs);
	}

	private void printFragmentInterferenceGraph(InterferenceGraph graph, String fragmentName) {
		String title = "% ==== INTERFERENCE GRAPH " + ((fragmentName != null) ? "[ " + fragmentName + " ]" : "") + "====";
		System.out.println(title);
		System.out.print(graph.toString());
		System.out.println("% " + new String(new char[title.length()]).replace("\0", "="));
	}


	/** Main logic of liveness analysis */

	public void allocateRegisters(ArrayList<InterferenceGraph> interferenceGraphs) {
		for (InterferenceGraph ig : interferenceGraphs) {
			ColoredGraph icg = new ColoredGraph(ig);
			interferenceColeredGraphs.add(icg);
		}
	}

	@Override
	public void close() {
		if (Main.cmdLine.get("--target-phase").equals("regalloc")) {
			for (ColoredGraph igc : interferenceColeredGraphs) {
				for (AsmInstr instr : igc.instructions) {
					if (instr.toString().startsWith("%")) {
						System.out.println(instr.toString());
						continue;
					}
					if (igc.coloringSuccessful) {
						System.out.format("%1$-20s %2$-18s\n", instr.toString(igc.regMapping), useOrgInstrAsComm ? " % " + instr.toString() : "");
					} else
						System.out.println(instr);
				}
				if (showRegMapping) {
					System.out.println("% ===== REGISTER MAPPING =====");
					System.out.println("% " + igc.regMapping);
					System.out.println("% ============================");
				}
				if (showInterferenceGraph) printFragmentInterferenceGraph(igc, null);
			}
		}
		super.close();
	}

	public static void reset() {}
}
