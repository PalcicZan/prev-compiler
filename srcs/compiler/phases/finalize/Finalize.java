package compiler.phases.finalize;

import common.report.Report;
import compiler.Main;
import compiler.phases.Phase;
import compiler.phases.asmgen.AsmInstr;
import compiler.phases.asmgen.AsmLABEL;
import compiler.phases.asmgen.AsmMOVE;
import compiler.phases.asmgen.AsmOPER;
import compiler.phases.lincode.DataFragment;
import compiler.phases.lincode.Fragment;
import compiler.phases.lincode.LinCode;
import compiler.phases.regalloc.ColoredGraph;
import compiler.phases.regalloc.InstrLogger;
import compiler.phases.regalloc.RegAlloc;

import java.io.*;
import java.util.*;

public class Finalize extends Phase {

	/** Flags */
	public static final boolean addDataFragmentComment = true;
	public static final boolean addGlobalRegComment = true;
	public static final boolean addDebugRV = true;
	public static final String FPBaseAddress = "16384"; //24576


	private ArrayList<ColoredGraph> interferenceColeredGraphs;
	private static final HashSet<String> stdLibrary;

	static {
		stdLibrary = new HashSet<>();
		stdLibrary.add("_printint");
		stdLibrary.add("_printchar");
		stdLibrary.add("_println");
		stdLibrary.add("_malloc");
		stdLibrary.add("_free");
	}

	private HashSet<String> defFun;
	private HashSet<String> usedStd;
	private LinkedList<AsmInstr> localInstr;
	private StringBuilder asmBuilder;
	private BufferedWriter dstFile;
	private StringBuilder localInstrBuilder;
	private int dataSegmentOffset;

	public Finalize() {
		super("finalize");
		asmBuilder = new StringBuilder();
		localInstrBuilder = new StringBuilder();
		localInstr = new LinkedList<>();
		interferenceColeredGraphs = new ArrayList<>(RegAlloc.interferenceColeredGraphs);
		usedStd = new HashSet<>();
		defFun = new HashSet<>();
		dataSegmentOffset = 0;
		try {
			String dstFileName = compiler.Main.cmdLineArgValue("--dst-file-name");
			dstFile = new BufferedWriter(new FileWriter(dstFileName));
		} catch (IOException ___) {
			throw new Report.Error("Cannot open destination file '" + dstFile + "'.");
		}
	}

	public void run() {
		if (Main.useMmixAliases) prependAliases();
		else prependGlobalRegisters();
		prependDataFragments();
		getUserDefFun();
		prependMainWrapper();
		for (ColoredGraph cg : interferenceColeredGraphs) {
			prologue(cg);
			formatFragmentInstructions(cg);
			appendFragment(cg);
			epilogue(cg);
		}
		if (!usedStd.isEmpty()) appendStdLib();
	}

	private void getUserDefFun() {
		for (ColoredGraph cg : interferenceColeredGraphs) {
			defFun.add(cg.fragment.frame.label.name);
		}
	}

	private void prependMainWrapper() {
		localInstr.clear();
		localInstr.add(new AsmOPER(InstrLogger.comment("Main wrapper"), null, null, null));
		localInstr.add(new AsmOPER("PREFIX :", null, null, null));
		localInstr.add(new AsmOPER("LOC #100", null, null, null));
		localInstr.add(new AsmOPER("Main	PUT rG, 250", null, null, null));
		localInstr.add(new AsmOPER("SET $7, 0", null, null, null));
		localInstr.add(new AsmOPER("SETH " + Main.FP + ", " + FPBaseAddress, null, null, null));
		localInstr.add(new AsmOPER("SUB " + Main.FP + ", " + Main.FP + ", 8", null, null, null));
		localInstr.add(new AsmOPER("SET " + Main.SP + ", " + Main.FP, null, null, null));
		localInstr.add(new AsmOPER("SETH " + Main.HP + ", 16384", null, null, null));
		localInstr.add(new AsmOPER("ADD " + Main.HP + ", " + Main.HP + ", 8", null, null, null));
		localInstr.add(new AsmOPER("PUSHJ $" + Main.nReg + ", _", null, null, null));
		localInstr.add(new AsmOPER("TRAP 0, Halt, 0", null, null, null));
		localInstr.add(new AsmOPER(InstrLogger.comment(""), null, null, null));
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
	}

	private void prependAliases() {
		localInstr.clear();
		localInstr.add(new AsmOPER(InstrLogger.comment("Aliases"), null, null, null));
		localInstr.add(new AsmOPER(Main.SP + "	IS " + Main.SPReg, null, null, null));
		localInstr.add(new AsmOPER(Main.FP + "	IS " + Main.FPReg, null, null, null));
		localInstr.add(new AsmOPER(Main.HP + "	IS " + Main.HPReg, null, null, null));
		localInstr.add(new AsmOPER(InstrLogger.comment(""), null, null, null));
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
	}

	private void prologue(ColoredGraph cg) {
		localInstr.clear();
		localInstr.add(new AsmOPER(InstrLogger.comment("Prologue [" + cg.fragment.frame.label + "]"), null, null, null));
		localInstr.add(new AsmOPER(cg.fragment.frame.label + "	SETL $0, " + (cg.fragment.frame.locsSize + 8), null, null, null));
		localInstr.add(new AsmOPER("SUB $1, " + Main.SP + ", $0", null, null, null));
		localInstr.add(new AsmOPER("STO " + Main.FP + ", $1, 0", null, null, null));
		localInstr.add(new AsmOPER("SUB $1, $1, 8", null, null, null));
		localInstr.add(new AsmOPER("GET $0, rJ", null, null, null));
		localInstr.add(new AsmOPER("STO $0, $1, 0", null, null, null));
		localInstr.add(new AsmOPER("ADD " + Main.FP + ", " + Main.SP + ", 0" + "	" + InstrLogger.comment("FP set to SP"), null, null, null));
		localInstr.add(new AsmOPER("SUB " + Main.SP + ", " + Main.SP + ", " + cg.fragment.frame.size + "	" + InstrLogger.comment("Move SP"), null, null, null));
		localInstr.add(new AsmOPER(InstrLogger.comment(""), null, null, null));
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
	}

	private void epilogue(ColoredGraph cg) {
		String rvReg = "$" + cg.regMapping.get(cg.fragment.RV);
		localInstr.clear();
		localInstr.add(new AsmOPER(InstrLogger.comment("Epilogue [" + cg.fragment.frame.label + "]"), null, null, null));
		localInstr.add(new AsmOPER(cg.fragment.endLabel.name + "	STO " + rvReg + ", " + Main.FP + ", 0", null, null, null));
		if (addDebugRV) localInstr.add(new AsmOPER("SET $255, " + rvReg, null, null, null));
		localInstr.add(new AsmOPER("SETL $0, " + (cg.fragment.frame.locsSize + 8), null, null, null));
		localInstr.add(new AsmOPER("SUB $1, " + Main.FP + ", $0", null, null, null));
		localInstr.add(new AsmOPER("LDO " + Main.FP + ", $1, 0", null, null, null));
		localInstr.add(new AsmOPER("SETL $0, " + cg.fragment.frame.size, null, null, null));
		localInstr.add(new AsmOPER("ADD " + Main.SP + ", " + Main.SP + ", $0", null, null, null));
		localInstr.add(new AsmOPER("SUB $1, $1, 8", null, null, null));
		localInstr.add(new AsmOPER("LDO $0, $1, 0", null, null, null));
		localInstr.add(new AsmOPER("PUT rJ, $0", null, null, null));
		localInstr.add(new AsmOPER("POP 0,0", null, null, null));
		localInstr.add(new AsmOPER(InstrLogger.comment(""), null, null, null));
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
	}

	private void formatFragmentInstructions(ColoredGraph cg) {
		LinkedList<AsmInstr> instrs = cg.instructions;
		HashMap<AsmInstr, AsmLABEL> instructionLabel = new HashMap<>();
		// move labels to next instructions
		AsmInstr prevInstr = instrs.peekFirst();
		for (int i = 1; i < instrs.size(); i++) {
			localInstrBuilder.setLength(0);
			AsmInstr curr = instrs.get(i);

			// skip comments
			if (curr.toString().startsWith("%"))
				continue;

			// link functions to ones in standard library
			if (curr instanceof AsmOPER && curr.toString().startsWith("PUSHJ")) {
				String funName = curr.toString().split(",")[1].replaceAll(" ", "");
				if (!defFun.contains(funName)) {
					if (stdLibrary.contains(funName)) {
						usedStd.add(funName);
					} else {
						Report.warning("Definition of function [" + funName + "] is missing or cannot be linked.");
					}
				}
			}

			// place labels into next line and remove unnecessary jumps
			if (curr instanceof AsmLABEL && curr.toString().equals(cg.fragment.endLabel.name)) {
				instrs.remove(i);
				curr = prevInstr;
				i--;
			} else if (curr instanceof AsmLABEL && prevInstr instanceof AsmOPER &&
				prevInstr.jumps().size() > 0 && prevInstr.jumps().firstElement().name.equals(curr.toString())
				&& !instructionLabel.containsKey(prevInstr)) {
				instrs.remove(i - 1);
				i--;
			} else if (prevInstr instanceof AsmLABEL) {
				// move labels
				localInstrBuilder.append(prevInstr.toString()).append("	");
				if (curr instanceof AsmMOVE) {
					instrs.set(i, new AsmMOVE(localInstrBuilder.append(((AsmMOVE) curr).getInstr()).toString(), curr.uses(), curr.defs(), curr.jumps()));
				} else if (curr instanceof AsmOPER) {
					instrs.set(i, new AsmOPER(localInstrBuilder.append(((AsmOPER) curr).getInstr()).toString(), curr.uses(), curr.defs(), curr.jumps()));
				}
				curr = instrs.get(i);
				instructionLabel.put(curr, (AsmLABEL) prevInstr);
				instrs.remove(prevInstr);
				i--;
			}
			prevInstr = curr;
		}
	}

	private void prependGlobalRegisters() {
		localInstr.clear();
		if (addGlobalRegComment) localInstr.add(new AsmOPER(InstrLogger.comment("Global registers"),
			null, null, null));

		localInstr.add(new AsmOPER(String.format("%s", "SP GREG"), null, null, null));
		localInstr.add(new AsmOPER(String.format("%s", "FP GREG"), null, null, null));
		localInstr.add(new AsmOPER(String.format("%s", "HP GREG"), null, null, null));
		if (addGlobalRegComment) localInstr.add(new AsmOPER(InstrLogger.comment(""),
			null, null, null));
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
	}

	private void prependDataFragments() {
		localInstr.clear();
		if (addDataFragmentComment) localInstr.add(new AsmOPER(InstrLogger.comment("Data Segment"),
			null, null, null));
		localInstr.add(new AsmOPER("LOC Data_Segment", null, null, null));
		localInstr.add(new AsmOPER("GREG @", null, null, null));
		int offset = 0;
		for (Fragment f : LinCode.fragments()) {
			localInstrBuilder.setLength(0);
			if (f instanceof DataFragment) {
				DataFragment df = ((DataFragment) f);
				localInstrBuilder.append(df.label).append("	OCTA ");
				for (int i = 0; i < df.size / 8; i++) {
					localInstrBuilder.append(i == 0 ? "0" : ",0");
					offset += 8;
				}
				localInstr.add(new AsmOPER(localInstrBuilder.toString(), null, null, null));
			}
		}
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
		dataSegmentOffset = asmBuilder.length();
		localInstr.clear();
		if (addDataFragmentComment) localInstr.add(new AsmOPER(InstrLogger.comment(""),
			null, null, null));
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
	}

	private void appendStdLib() {
		localInstr.clear();
		localInstr.add(new AsmOPER("Buf BYTE", null, null, null));
		localInstr.add(new AsmOPER("NewLn BYTE #a,0", null, null, null));
		localInstr.add(new AsmOPER("Blanks BYTE \" \",0", null, null, null));
		asmBuilder.insert(dataSegmentOffset, InstrLogger.instructions(localInstr, null));
		localInstr.clear();
		for (String stdFun : usedStd) {
			switch (stdFun) {
				case "_printint":
					localInstr.add(new AsmOPER("_printint LDA $255, Buf", null, null, null));
					localInstr.add(new AsmOPER("ADD $255, $255, 255", null, null, null));
					localInstr.add(new AsmOPER("SET $0, 0", null, null, null));
					localInstr.add(new AsmOPER("STB $0, $255, 1", null, null, null));
					localInstr.add(new AsmOPER("LDO $0, " + Main.SP + ", 8", null, null, null));
					localInstr.add(new AsmOPER("ZSP $1, $0, 1", null, null, null));
					localInstr.add(new AsmOPER("BNZ $1, read", null, null, null));
					localInstr.add(new AsmOPER("SETL $1, 45", null, null, null));
					localInstr.add(new AsmOPER("STB $1, $255", null, null, null));
					localInstr.add(new AsmOPER("SET $1, $255", null, null, null));
					localInstr.add(new AsmOPER("TRAP 0,Fputs,StdOut", null, null, null));
					localInstr.add(new AsmOPER("SET $255, $1", null, null, null));
					localInstr.add(new AsmOPER("NEG $0, $0", null, null, null));
					localInstr.add(new AsmOPER("read	DIV $0, $0, 10", null, null, null));
					localInstr.add(new AsmOPER("GET $1, rR", null, null, null));
					localInstr.add(new AsmOPER("INCL $1, '0'", null, null, null));
					localInstr.add(new AsmOPER("STBU $1, $255, 0", null, null, null));
					localInstr.add(new AsmOPER("SUB $255, $255, 1", null, null, null));
					localInstr.add(new AsmOPER("BNZ $0, read", null, null, null));
					localInstr.add(new AsmOPER("ADD $255, $255, 1", null, null, null));
					localInstr.add(new AsmOPER("LDO $0, $255", null, null, null));
					localInstr.add(new AsmOPER("TRAP 0,Fputs,StdOut", null, null, null));
					break;
				case "_println":
					localInstr.add(new AsmOPER("_println	LDA $255, NewLn", null, null, null));
					localInstr.add(new AsmOPER("TRAP 0,Fputs,StdOut", null, null, null));
					break;
				case "_printchar":
					localInstr.add(new AsmOPER("_printchar LDO $0, " + Main.SP + ", 8", null, null, null));
					localInstr.add(new AsmOPER("STB $0, Buf", null, null, null));
					localInstr.add(new AsmOPER("LDA $255, Buf", null, null, null));
					localInstr.add(new AsmOPER("TRAP 0,Fputs,StdOut", null, null, null));
					break;
				case "_malloc":
					localInstr.add(new AsmOPER("_malloc LDO $0, " + Main.SP + ", 8", null, null, null));
					localInstr.add(new AsmOPER("STO " + Main.HP + ", " + Main.SP + ", 8", null, null, null));
					localInstr.add(new AsmOPER("ADD " + Main.HP + ", " + Main.HP + ", $0", null, null, null));
					break;
				case "_free":
					localInstr.add(new AsmOPER("_free POP 0,0", null, null, null));
					asmBuilder.append(InstrLogger.instructions(localInstr, null));
					return;
			}
			localInstr.add(new AsmOPER("POP 0,0", null, null, null));
		}
		asmBuilder.append(InstrLogger.instructions(localInstr, null));
	}

	private void appendFragment(ColoredGraph cg) {
		asmBuilder.append(InstrLogger.fragmentInstructions(cg, false));
	}

	@Override
	public void close() {
		try {
			dstFile.write(asmBuilder.toString());
			dstFile.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		super.close();
	}
}
