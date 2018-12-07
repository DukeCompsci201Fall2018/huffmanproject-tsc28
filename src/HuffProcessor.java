import java.util.*;
/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		in.reset();
		writeCompressedBits(codings,in,out);
		out.close();
	}

	private int[] readForCounts(BitInputStream in) {
		TreeMap <Integer, Integer> map = new TreeMap<>();
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				break;
			}
			if (! map.containsKey(val)){
				map.put(val, 0);
			}
			map.put(val, map.get(val)+1);
		}
		int[] ret = new int[ALPH_SIZE];
		for (int i : map.keySet()) {
			ret[i] = map.get(i);
		}	
		ret[256] = 1;
		return ret;
	}
	
	private HuffNode makeTreeFromCounts(int[] counts) {
		PriorityQueue<HuffNode> pq = new PriorityQueue<>();
		for (int j = 0; j < counts.length; j ++) {
			if (counts[j] > 0) {
				pq.add(new HuffNode(j, counts[j], null, null));
			}
		}
		while (pq.size() > 1) {
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			HuffNode t = new HuffNode(-1, 
					left.myWeight+right.myWeight, left,right);
			pq.add(t);
		}
		HuffNode root = pq.remove();
		return root;
	}

	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE+1];
		codingHelper(root,"",encodings);
		return encodings;
	}
	
	private void codingHelper(HuffNode node, String path, String[] encodings) {
		if (node.myRight == null && node.myLeft == null) {
			encodings[node.myValue] = path;
			return;
		}
		codingHelper(node.myLeft, path+"0", encodings);
		codingHelper(node.myRight, path+"1", encodings);
	}

	private void writeHeader(HuffNode node, BitOutputStream out) {
		if (node == null) {
			return;
		}
		if (node.myLeft == null && node.myRight == null) {
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD+1, node.myValue);
		}
		else {
			out.writeBits(1, 0);
			writeHeader(node.myLeft, out);
			writeHeader(node.myRight, out);
		}
	}

	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		while (true) {
			int val = in.readBits(BITS_PER_WORD);
			if (val == -1) {
				String code = codings[256];
				out.writeBits(code.length(), Integer.parseInt(code,2));
				break;
			}
			String code = codings[val];
			out.writeBits(code.length(), Integer.parseInt(code,2));
		}
	}
	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){

		int val = in.readBits(BITS_PER_INT);
		if (val != HUFF_TREE || val == -1) {
			throw new HuffException("Illegal header starts with " + val);
		}
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();
	}
	
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		if (bit == -1) {
			throw new HuffException ("Illegal header starts with "+bit);
		}
		if (bit == 0) { // interior node
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0,0,left,right);
		}
		else {	// leaf node reached
			int value = in.readBits(BITS_PER_WORD + 1);
			return new HuffNode(value,0,null,null);
		}
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException ("bad input, no PSEUDO_EOF");
			}
			else {
				if (bits == 0) {
					current = current.myLeft;
				}
				else {
					current = current.myRight;
				}
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) {
						break;
					}
					else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}	
				}
			}
		}
	}
	
}