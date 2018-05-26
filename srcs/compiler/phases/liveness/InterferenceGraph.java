package compiler.phases.liveness;

import compiler.Main;
import compiler.phases.asmgen.AsmInstr;
import compiler.phases.asmgen.AsmMOVE;
import compiler.phases.frames.Temp;
import compiler.phases.lincode.CodeFragment;

import java.util.*;

public class InterferenceGraph {

	/** Graph and instructions */
	public HashMap<Temp, Node> interferenceGraph;
	public LinkedList<AsmInstr> instructions;
	public CodeFragment fragment;

	/** Redundant edges */
	private HashMap<Temp, Set<Temp>> redundantEdges;


	/** Dump information for each instruction */
	protected void dump(AsmInstr instr, String msg) {
		if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.LIVENESS) {
			System.out.println(instr != null ? instr.toString() + ": " + msg : msg);
		}
	}

	protected InterferenceGraph(CodeFragment codeFragment, LinkedList<AsmInstr> instrs) {
		fragment = codeFragment;
		instructions = new LinkedList<>(instrs);
		buildInterferenceGraph();
	}

	protected InterferenceGraph(InterferenceGraph interfGraph) {
		interferenceGraph = interfGraph.interferenceGraph;
		instructions = interfGraph.instructions;
		fragment = interfGraph.fragment;
		redundantEdges = null;
	}

	public void buildInterferenceGraph() {
		interferenceGraph = new HashMap<>();
		redundantEdges = new HashMap<>();


		for (AsmInstr instr : instructions) {
			for (int i = 0; i < instr.defs().size(); i++) {
				addNode(instr.defs().get(i));
			}
			dump(null, "=== New instruction ===");
			findRedundantEdges(instr, instr.out());
			addEdges(instr.in());
			addEdges(instr.out());
		}
	}

	@Override
	public String toString() {
		StringBuilder intfrGraph = new StringBuilder();
		for (Map.Entry<Temp, Node> entry : interferenceGraph.entrySet()) {
			intfrGraph.append("% ").append(entry.getValue().toString()).append(": ").append(entry.getValue().edges).append("\n");
		}
		return intfrGraph.toString();
	}

	private void addNode(Temp temp) {
		if (!interferenceGraph.containsKey(temp)) {
			interferenceGraph.put(temp, new Node(temp));
		}
	}

	protected void addNode(Node node) {
		if (!interferenceGraph.containsKey(node.t)) {
			interferenceGraph.put(node.t, node);
		}
	}

	protected void removeNode(Node node, Iterator it) {
		if (interferenceGraph.containsKey(node.t)) {
			for (Node neigbour : node.edges) {
				neigbour.removeEdge(node);
			}
			if (it == null) interferenceGraph.remove(node.t);
			else it.remove();
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

		Node currNode = interferenceGraph.get(curr);
		Node neigbourNode = interferenceGraph.get(neighbour);

		currNode.addEdge(neigbourNode);
		neigbourNode.addEdge(currNode);
//		neigbourNode.edges.add(currNode);
	}

	private void addEdges(Set<Temp> instrTemp) {
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
