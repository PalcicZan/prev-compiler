package compiler.phases.regalloc;

import compiler.Main;
import compiler.phases.Phase;
import compiler.phases.liveness.InterferenceGraph;

import java.util.*;

public class RegAlloc extends Phase {

	/** Flags */
	public static final boolean useOrgInstrAsComm = true;
	public static final boolean showSpilledComments = false;
	public static final boolean showRegMapping = true;
	public static final boolean showInterferenceGraph = true;
	public static final boolean useNewSpilledTemp = true;
	public static int nReg = Main.nReg;

	/** Final interference graph */
	public static ArrayList<ColoredGraph> interferenceColeredGraphs;

	public RegAlloc(ArrayList<InterferenceGraph> interferenceGraphs) {
		super("regalloc");
		interferenceColeredGraphs = new ArrayList<>();
		allocateRegisters(interferenceGraphs);
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
				InstrLogger.printFragmentInstructions(igc, useOrgInstrAsComm);
				if (showRegMapping) InstrLogger.printFragmentRegMapping(igc);
				if (showInterferenceGraph) InstrLogger.printFragmentInterferenceGraph(igc, null);
			}
		}
		super.close();
	}

	public static void reset() {
		interferenceColeredGraphs = new ArrayList<>();
	}
}
