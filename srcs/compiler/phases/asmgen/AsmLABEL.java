package compiler.phases.asmgen;

import java.util.*;

import common.logger.Logger;
import compiler.phases.frames.*;

/**
 * An assembly label.
 * 
 * @author sliva
 *
 */
public class AsmLABEL extends AsmOPER {

	/** The label. */
	private final Label label;

	public AsmLABEL(Label label) {
		super("", null, null, null);
		this.label = label;
	}

	@Override
	public String toString() {
		return label.name;
	}

	@Override
	public String toString(HashMap<Temp, Integer> regs) {
		return label.name;
	}

	@Override
	public void log(Logger logger) {
		if (logger == null)
			return;
		logger.begElement("asmlabel");
		logger.addAttribute("label", label.name);
		logger.addAttribute("token", "");
		logger.endElement();
	}
}
