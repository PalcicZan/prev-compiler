package compiler.phases.liveness;

import compiler.Main;
import compiler.phases.asmgen.AsmInstr;
import compiler.phases.asmgen.AsmMOVE;
import compiler.phases.frames.Temp;

import java.util.*;

public class InterferenceGraph {

	private HashMap<Temp, Set<Temp>> interferenceGraph;
	private HashMap<Temp, Set<Temp>> redundantEdges;

	/** Dump information for each instruction */
	private void dump(AsmInstr instr, String msg) {
		if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.LIVENESS) {
			System.out.println(instr != null ? instr.toString() + ": " + msg : msg);
		}
	}

	InterferenceGraph(LinkedList<AsmInstr> instructions, boolean useOptInterferenceGraph) {
		interferenceGraph = new HashMap<>();
		redundantEdges = new HashMap<>();

		for (AsmInstr instr : instructions) {

			for (int i = 0; i < instr.defs().size(); i++) {
				addNode(instr.defs().get(i));
			}
			dump(null, "=== New instruction ===");
			findRedundantEdges(instr, instr.out());
			addEdges(instr, instr.in());
			addEdges(instr, instr.out());
		}
	}

	@Override
	public String toString() {
		StringBuilder intfrGraph = new StringBuilder();
		for (Map.Entry<Temp, Set<Temp>> entry : interferenceGraph.entrySet()) {
			intfrGraph.append(entry.getKey()).append(": ").append(((HashSet) entry.getValue()).toString()).append("\n");
		}
		return intfrGraph.toString();
	}

	private void addNode(Temp node) {
		if (!interferenceGraph.containsKey(node)) {
			interferenceGraph.put(node, new HashSet<>());
		}
	}

	private void addEdge(Temp curr, Temp neighbour) {
		if (curr == neighbour) return;
		// edge not allowed
		if (redundantEdges.containsKey(curr) &&
			redundantEdges.get(curr).contains(neighbour)) {
			return;
		}

		dump(null, "EDGE " + curr + "--" + neighbour);
		interferenceGraph.get(curr).add(neighbour);
		interferenceGraph.get(neighbour).add(curr);
	}

	private void markRedundantEdge(Temp curr, Temp neighbour) {
		if (!redundantEdges.containsKey(curr)) {
			redundantEdges.put(curr, new HashSet<>());
		}
		if (!redundantEdges.containsKey(neighbour)) {
			redundantEdges.put(neighbour, new HashSet<>());
		}
		if (curr == neighbour) return;

		dump(null, "MARK REDUNDANT EDGE " + curr + "-/-" + neighbour);
		redundantEdges.get(curr).add(neighbour);
		redundantEdges.get(neighbour).add(curr);
	}

	private void removeEdge(HashMap<Temp, Set<Temp>> graph, Temp curr, Temp neighbour) {
		if (!graph.containsKey(curr)) {
			return;
		}
		if (!graph.containsKey(neighbour)) {
			return;
		}
		if (curr == neighbour) return;

		dump(null, "REMOVE " + curr + "--" + neighbour);
		graph.get(curr).remove(neighbour);
		graph.get(neighbour).remove(curr);
	}

	private void addEdges(AsmInstr instr, Set<Temp> instrTemp) {
		Object[] temp = instrTemp.toArray();
		for (int i = 0; i < temp.length; i++) {
			for (int j = i; j < temp.length; j++) {
				Temp t1 = (Temp) temp[i];
				Temp t2 = (Temp) temp[j];
				addNode(t1);
				addNode(t2);
				addEdge(t1, t2);
			}
		}
	}

	private void findRedundantEdges(AsmInstr instr, Set<Temp> instrTemp) {
		dump(instr, instrTemp.toString());
		// add all defs as nodes
		boolean isDef = instr.defs().size() > 0;
		boolean usedDef = false;
		Temp def = null;
		if (isDef) {
			def = instr.defs().get(0);
			usedDef = instrTemp.contains(def);
		}
		for (Temp t1 : instrTemp) {
			addNode(t1);
			if (isDef) {
				if (instr instanceof AsmMOVE) {
					if (t1 != instr.uses().get(0)) {
						if (usedDef) {
							addEdge(t1, def);
						}
					} else {
						markRedundantEdge(t1, def);
					}
				} else {
					removeEdge(redundantEdges, t1, def);
				}
			}
		}
	}

}
