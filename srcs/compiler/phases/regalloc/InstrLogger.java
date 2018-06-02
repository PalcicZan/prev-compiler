package compiler.phases.regalloc;

import compiler.phases.asmgen.AsmInstr;
import compiler.phases.frames.Temp;
import compiler.phases.liveness.InterferenceGraph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class InstrLogger {

	private static StringBuilder stringBuilder = new StringBuilder();
	public static final boolean withoutSpaces = true;
	public static final boolean usePadding = true;

	private static HashSet<String> withoutPadding;

	static {
		withoutPadding = new HashSet<>();
		withoutPadding.add("FP");
		withoutPadding.add("HP");
		withoutPadding.add("Main");
		withoutPadding.add("SP");
		withoutPadding.add("%");
		withoutPadding.add("Buf");
		withoutPadding.add("NewLn");
		withoutPadding.add("Blanks");
		withoutPadding.add("read");
	}

	public static String comment(String msg) {
		stringBuilder.setLength(0);
		if (!msg.equals("")) {
			msg = " " + msg + " ";
		}
		stringBuilder.append("% =====").append(msg)
			.append((msg.length() < 22) ? new String(new char[22 - msg.length()]).replace("\0", "=") : "");
		return stringBuilder.toString();
	}

	private static void format(String instrStr) {
		if (withoutSpaces) instrStr = instrStr.replaceAll(", ", ",");
		String first = instrStr.split("\\s")[0];
		String pad = instrStr.matches("^([L][0-9]+?|[_].+?).*") || !usePadding ||
			withoutPadding.contains(first)
			? "" : "	";
		stringBuilder.append(String.format("%s%s\n", pad, instrStr));
	}

	public static String fragmentInterferenceGraph(InterferenceGraph graph, String fragmentName) {
		stringBuilder.setLength(0);
		String title = "% ==== INTERFERENCE GRAPH " + ((fragmentName != null) ? "[ " + fragmentName + " ]" : "") + "====";
		stringBuilder.append(title).append("\n");
		stringBuilder.append(graph.toString());
		stringBuilder.append("% ").append(new String(new char[title.length()]).replace("\0", "=")).append("\n");
		return stringBuilder.toString();
	}

	public static String fragmentRegMapping(ColoredGraph graph) {
		stringBuilder.setLength(0);
		stringBuilder.append("% ===== REGISTER MAPPING =====").append("\n");
		stringBuilder.append("% ").append(graph.regMapping).append("\n");
		stringBuilder.append("% ============================").append("\n");
		return stringBuilder.toString();
	}

	public static String fragmentInstructions(ColoredGraph graph, boolean useOrgInstrAsComm) {
		stringBuilder.setLength(0);
		for (AsmInstr instr : graph.instructions) {
			String instrStr;
			if (instr.toString().startsWith("%") || !graph.coloringSuccessful) {
				instrStr = instr.toString();
				format(instrStr);
			} else {
				instrStr = instr.toString(graph.regMapping);
				format(instrStr);
			}
		}
		return stringBuilder.toString();
	}

	public static String instructions(LinkedList<AsmInstr> instrs, HashMap<Temp, Integer> regMapping) {
		stringBuilder.setLength(0);
		for (AsmInstr instr : instrs) {
			String instrStr;
			instrStr = regMapping != null ? instr.toString(regMapping) : instr.toString();
			format(instrStr);
		}
		return stringBuilder.toString();
	}

	public static void printFragmentInterferenceGraph(InterferenceGraph graph, String fragmentName) {
		System.out.print(fragmentInterferenceGraph(graph, fragmentName));
	}

	public static void printFragmentRegMapping(ColoredGraph graph) {
		System.out.print(fragmentRegMapping(graph));
	}

	public static void printFragmentInstructions(ColoredGraph igc, boolean useOrgInstrAsComm) {
		System.out.print(fragmentInstructions(igc, useOrgInstrAsComm));
	}
}
