package compiler.phases.regalloc;

import compiler.Main;
import compiler.phases.asmgen.AsmInstr;
import compiler.phases.asmgen.AsmOPER;
import compiler.phases.frames.Frame;
import compiler.phases.frames.Temp;
import compiler.phases.lincode.CodeFragment;
import compiler.phases.liveness.InterferenceGraph;
import compiler.phases.liveness.LiveAn;
import compiler.phases.liveness.Node;

import java.util.*;

public class ColoredGraph extends InterferenceGraph {

	/** Temporary register mapping to physical register */
	public HashMap<Temp, Integer> regMapping;
	/** Flag denoting if coloring was successful */
	public boolean coloringSuccessful;
	/** Spilled flags */
	public static final boolean showSpilledComments = RegAlloc.showSpilledComments;
	public static final boolean useNewSpilledTemp = RegAlloc.useNewSpilledTemp;


	private final HashSet<Integer> colors;          // color is a number between 0 to (nReg - 1) inclusive

	/** List/sets for nodes */
	private Stack<Node> simplifiedNodes;            // nodes already pruned from graph - treeSet heuristic
	private Node spilledNode;                       // node that is currently process as spilled node
	private Stack<Node> spilledCandidateNodes;      // spilled candidates
	private Set<Node> spilledFromBeginningNodes;    // spilled candidates
	private Set<Node> spilledNodes;
	private Set<Temp> originalNodes;
	private Set<Temp> avoidFixingNodes;
	private Stack<Node> selectStack;

	//	private Set<Node> freezeWorklist;
	//	private Set<Node> coalescedNodes;
	//	private Set<Node> coloredNodes;¸
	//	private Set<Node> worklistMove;

	/** Counters */
	private int nIter;                              // number of iteration needed to color graph
	private int nSpilledReg;                        // number of spilled nodes/registers


	ColoredGraph(InterferenceGraph interferenceGraph) {
		super(interferenceGraph);
		nIter = 0;
		nSpilledReg = 0;
		regMapping = new HashMap<>();
		simplifiedNodes = new Stack<>();
		spilledCandidateNodes = new Stack<>();
		selectStack = new Stack<>();
		spilledNodes = new HashSet<>();
		avoidFixingNodes = new HashSet<>();
		spilledFromBeginningNodes = new HashSet<>();
		//	freezeWorklist = new HashSet<>();
		//	coalescedNodes = new HashSet<>();
		//	coloredNodes = new HashSet<>();
		//	worklistMove = new HashSet<>();

		// prepare all possible colors
		colors = new HashSet<>();
		for (int i = 0; i < RegAlloc.nReg; i++) {
			colors.add(i);
		}
		originalNodes = new HashSet<>();
		for (Node n : interferenceGraph.interferenceGraph.values())
			originalNodes.add(n.t);
		dump("Number of registers: " + RegAlloc.nReg);
		LiveAn.printInstructions(instructions);
		createGraph();
	}

	private void dump(String msg) {
		if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.REGALLOC) {
			System.out.println(msg);
		}
	}

	public void createGraph() {
		coloringSuccessful = false;
		nIter = 0;

		while (!coloringSuccessful) {

			// build if not first time
			if (nIter != 0) build();

			prepareStacks();

			// simplify and get spill candidates
			while (!simplifiedNodes.isEmpty() ||
//				!freezeWorklist.isEmpty() ||
//				!worklistMove.isEmpty() ||
				!spilledCandidateNodes.isEmpty()
				) {
				if (!simplifiedNodes.isEmpty()) simplify();
				else if (!spilledCandidateNodes.isEmpty()) spill();
			}

			// select color
			coloringSuccessful = selectColor();
			nIter++;
			// logging
			dump("Interference Graph: " + interferenceGraph.values());
			dump("Simplified nodes: " + simplifiedNodes);
			dump("Spilling candidates: " + spilledNodes);
			dump("Iteration successful [" + nIter + "]: " + coloringSuccessful);
		}

		// resize frame if necessary
		dump("Frame size: " + fragment.frame.size);
		resizeFrame();
		dump("New frame size: " + nSpilledReg + " " + fragment.frame.size);
	}

	private void prepareStacks() {
		for (Map.Entry<Temp, Node> o : interferenceGraph.entrySet()) {
			Node node = o.getValue();
			if (node.getDegree() >= RegAlloc.nReg) {
				spilledCandidateNodes.add(node);
				spilledFromBeginningNodes.add(node);
			} else
				// using heuristic (ordered by degrees)
				simplifiedNodes.add(node);
		}
	}

	private void resizeFrame() {
		long tempRegSize = nSpilledReg * 8;
		Frame resizedFrame = new Frame(fragment.frame.label, fragment.frame.depth, fragment.frame.locsSize + tempRegSize, fragment.frame.argsSize);
		fragment = new CodeFragment(resizedFrame, fragment.stmts(), fragment.FP, fragment.RV, fragment.begLabel, fragment.endLabel);
	}

	private void build() {
		rewriteProgram();
		spilledNodes.clear();
		selectStack.clear();
		spilledCandidateNodes.clear();
		spilledFromBeginningNodes.clear();

		// reset instructions
		for (AsmInstr instr : instructions) {
			instr.in().clear();
			instr.out().clear();
			instr.outTmp().clear();
			instr.inTmp().clear();
			instr.succ().clear();
			instr.pred().clear();
		}

		LiveAn.calculateInterferece(instructions);
		buildInterferenceGraph();
		if (Main.debug == Main.DEBUG.FULL || Main.debug == Main.DEBUG.REGALLOC)
			LiveAn.printInstructions(instructions);
	}

	private void simplify() {
		Node node = simplifiedNodes.pop();
		selectStack.push(node);
		for (Node n : node.edges) {
			// virtually decrease degree
			n.removeEdge(node);
			if (n.getDegree() == RegAlloc.nReg) {
				spilledCandidateNodes.remove(n);
				simplifiedNodes.add(n);
			}
		}
	}

	private void spill() {
		Node node = spilledCandidateNodes.pop();
		selectStack.push(node);
	}

	private boolean selectColor() {
		HashSet<Integer> allowedColors;
		Node node;
		boolean success = true;
		while (!selectStack.isEmpty()) {
			allowedColors = new HashSet<>(colors);
			node = selectStack.pop();
			// remove neighbours colors
			for (Node interferenceNode : node.edges) {
				if (interferenceNode.color != null) {
					allowedColors.remove(interferenceNode.color);
				}
			}
			// color if possible
			if (!allowedColors.isEmpty()) {
				node.color = allowedColors.iterator().next();
				addNode(node);
				regMapping.put(node.t, node.color);
			} else {
				spilledNodes.add(node);
				dump("Can't color " + node + " " + node.getDegree());
				success = false;
			}
		}
		dump("Colored nodes: " + regMapping);
		dump("Spilled nodes: " + spilledNodes);
		return success;
	}

	private void loadSpilledReg(ListIterator<AsmInstr> it, long offset, Temp addrTemp, Temp spilledTemp) {
		Vector<Temp> defs = new Vector<>();
		spilledTemp = spilledTemp == null ? spilledNode.t : spilledTemp;
		defs.add(addrTemp != null ? addrTemp : spilledNode.t);
		AsmOPER regAddr = new AsmOPER("SUB `d0, " + Main.FPreg + ", " + offset, null, defs, null);
		Vector<Temp> uses = new Vector<>();
		defs = new Vector<>();
		uses.add(addrTemp != null ? addrTemp : spilledNode.t);
		defs.add(spilledTemp);
		AsmOPER loadReg = new AsmOPER("LDO `d0, `s0, 0", uses, defs, null);
		it.add(regAddr);
		it.add(loadReg);
	}

	private void storeSpilledReg(ListIterator<AsmInstr> it, long offset, Temp addrTemp, Temp spilledTemp, boolean calcAddr) {
		// calculate address if necessary
		if (calcAddr) {
			Vector<Temp> defs = new Vector<>();
			defs.add(addrTemp);
			AsmOPER regAddr = new AsmOPER("SUB `d0, " + Main.FPreg + ", " + offset, null, defs, null);
			it.add(regAddr);
		}
		Vector<Temp> uses = new Vector<>();
		spilledTemp = spilledTemp == null ? spilledNode.t : spilledTemp;
		uses.add(spilledTemp);
		uses.add(addrTemp);
		it.add(new AsmOPER("STO `s0, `s1, 0", uses, null, null));
	}

	private void findBestSpilledNode(Set<Node> spilledNodes) {
		for (Node node : spilledNodes) {
			if (avoidFixingNodes.contains(node.t)) continue;
			spilledNode = (spilledNode == null || spilledNode.degree < node.degree) ? node : spilledNode;
		}
	}

	private void rewriteProgram() {
		// spilled node heuristic
		spilledNode = null;
		findBestSpilledNode(spilledNodes);
		if (spilledNode == null) {
			findBestSpilledNode(spilledFromBeginningNodes);
			if (spilledNode == null) {
				for (Node n : interferenceGraph.values())
					if (originalNodes.contains(n.t)) {
						spilledNode = n;
						break;
					}
			}
		}
		if (spilledNode == null) {
			boolean what = false;
			while (!what) {
				selectStack.clear();
				for (Node n : interferenceGraph.values()) {
					n.color = null;
					if (!spilledNodes.contains(n)) {
						selectStack.push(n);
					}
				}
				for (Node n : spilledNodes) {
					selectStack.push(n);
				}
				spilledNodes.clear();
				what = selectColor();
			}
			return;
		}

		dump("GRAPH " + interferenceGraph.values().toString());
		dump("ORG " + originalNodes.toString());
		dump(spilledNodes.toString());
		dump(avoidFixingNodes.toString());
		dump(spilledFromBeginningNodes.toString());
		dump("SPILLED: " + (spilledNode == null ? "null" : spilledNode.toString()));

		ListIterator<AsmInstr> it = instructions.listIterator();
		long spilledTempOffset = (fragment.frame.locsSize + 16) + nSpilledReg * 8;
		while (it.hasNext()) {
			AsmInstr instr = it.next();
			boolean isUsed = instr.uses().contains(spilledNode.t);
			boolean isDef = instr.defs().contains(spilledNode.t);

			if (isUsed) {
				it.previous();
				Temp addrTemp = new Temp();
				Temp newSpilledTemp = new Temp();
				avoidFixingNodes.add(addrTemp);
				avoidFixingNodes.add(newSpilledTemp);
				if (showSpilledComments) it.add(new AsmOPER("% ====== START L ======", null, null, null));
				if (useNewSpilledTemp) {
					instr.uses().replaceAll(temp -> temp == spilledNode.t ? newSpilledTemp : temp);
					loadSpilledReg(it, spilledTempOffset, addrTemp, newSpilledTemp);
				} else {
					loadSpilledReg(it, spilledTempOffset, addrTemp, null);
				}
				if (showSpilledComments) it.add(new AsmOPER("% ====== END L ======", null, null, null));
				it.next();
				if (isDef) {
					instr.defs().replaceAll(temp -> temp == spilledNode.t ? newSpilledTemp : temp);
					storeSpilledReg(it, spilledTempOffset, addrTemp, newSpilledTemp, false);
					if (showSpilledComments) it.add(new AsmOPER("% ====== END SL ======", null, null, null));
				}
			} else if (isDef) {
				if (showSpilledComments) it.add(new AsmOPER("% ====== START S ======", null, null, null));
				Temp newSpilledTemp = new Temp();
				Temp addrTemp = new Temp();
				avoidFixingNodes.add(addrTemp);
				avoidFixingNodes.add(newSpilledTemp);
				instr.defs().replaceAll(temp -> temp == spilledNode.t ? newSpilledTemp : temp);
				storeSpilledReg(it, spilledTempOffset, addrTemp, newSpilledTemp, true);
				if (showSpilledComments) it.add(new AsmOPER("% ====== END S ======", null, null, null));
			}
		}
		nSpilledReg++;
		spilledCandidateNodes.clear();
	}

	public void reset() {}

}
