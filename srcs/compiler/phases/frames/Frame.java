package compiler.phases.frames;

import common.logger.*;
import compiler.phases.seman.type.*;

/**
 * A frame.
 *
 * @author sliva
 */
public class Frame implements Loggable {

	/** The function's entry label. */
	public final Label label;

	/** The function's static depth (>0). */
	public final int depth;

	/** The size of the frame. */
	public long size;

	/** The size of the block of local variables within a frame. */
	public final long locsSize;

	/** The size of the block of arguments within a frame. */
	public final long argsSize;

	/**
	 * Constructs a new frame with no temporary variables and no saved
	 * registers.
	 *
	 * @param label     The function's entry label.
	 * @param depth    The function's static depth (>0).
	 * @param locsSize The size of the block of local variables within a frame.¸
	 * @param argsSize The size of the block of arguments within a frame.
	 */
	public Frame(Label label, int depth, long locsSize, long argsSize) {
		this.label = label;
		this.depth = depth;
		this.locsSize = locsSize;
		this.argsSize = argsSize;
		this.size = this.locsSize + this.argsSize + 2 * (new SemPtrType(new SemVoidType())).size();
		// old FP + return addr
	}

	@Override
	public void log(Logger logger) {
		if (logger == null)
			return;
		logger.begElement("frame");
		logger.addAttribute("label", label.name);
		logger.addAttribute("depth", Integer.toString(depth));
		logger.addAttribute("locssize", Long.toString(locsSize));
		logger.addAttribute("argssize", Long.toString(argsSize));
		logger.addAttribute("size", Long.toString(size));
		logger.endElement();
	}

}
