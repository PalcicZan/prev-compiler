package compiler.phases.frames;

/**
 * A label.
 *
 * @author sliva
 */
public class Label {

	/** The name of a label. */
	public String name;

	/** Counter of anonymous labels. */
	private static long count = 0;

	/** Creates a new anonymous label. */
	public Label() {
		this.name = "L" + count;
		count++;
	}

	/**
	 * Creates a new named label.
	 *
	 * @param name The name of a label.
	 */
	public Label(String name) {
		this.name = "_" + name;
	}

	public static void reset() {
		count = 0;
	}

	@Override
	public String toString() {
		return name;
	}
}
