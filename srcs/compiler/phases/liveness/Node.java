package compiler.phases.liveness;

import compiler.phases.frames.Temp;

import java.util.HashSet;
import java.util.Set;

public class Node implements Comparable {
	/** Flags */
	public static boolean virtualDegree = true;

	public Temp t;
	public Integer color = null;
	// edges and degree
	public Set<Node> edges;
	public int degree;

//	public Spill spill = Spill.FALSE;
//	public enum Spill {FALSE, CANDIDATE, ACTUAL}


//		public boolean equals(Node o) {
//			return t.temp == ((Node)o).t.temp && degree == ((Node) o).degree;
//		}

	@Override
	public int compareTo(Object o) {
		if (t.temp == ((Node) o).t.temp) return 0;
		int deg = degree - ((Node) o).degree;
		if (deg == 0) return (int) (t.temp - ((Node) o).t.temp);
		return deg;
	}

	public Node(Temp t) {
		this.degree = 0;
		this.t = t;
		this.edges = new HashSet<>();
	}

	public int getDegree() {
		if (virtualDegree) {
			return degree;
		} else {
			return this.edges.size();
		}
	}

	public void removeEdge(Node node) {
		if (virtualDegree && edges.contains(node) && degree > 0) {
			degree -= 1;
		} else {
			edges.remove(node);
		}
	}

	public void addEdge(Node node) {
		boolean newNode = edges.add(node);
		if (virtualDegree && newNode) {
			degree += 1;
		}
	}

	@Override
	public String toString() {
		return "T" + t.temp;
	}

	public String properties() {
		return "T" + t.temp + ": [" + color + ", " + degree + ", " + edges + "]";
	}
}
