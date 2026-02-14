import java.util.*;

/**
 * Simple random Hex agent - Java example.
 * 
 * SIMPLIFIED VERSION - Just one function!
 * 
 * Your agent receives ONE line:
 *   <SIZE> <YOUR_COLOR> <MOVES>
 *   Example: 11 RED 5:5:B,6:6:R
 * 
 * Your agent outputs ONE line:
 *   <ROW> <COL>
 *   Example: 7 7
 * 
 * That's it! No need to track state, handle errors, or manage game flow.
 */
public class RandomAgent {
    
    /**
     * Parse the board state from input line.
     * 
     * @return Object array: [size(int), myColor(String), board(Map)]
     */
    private static Object[] parseBoard(String line) {
        String[] parts = line.split(" ", 3);
        
        int size = Integer.parseInt(parts[0]);
        String myColor = parts[1];  // "RED" or "BLUE"
        
        // Parse existing moves
        Map<String, String> board = new HashMap<>();
        if (parts.length == 3 && !parts[2].isEmpty()) {
            String movesStr = parts[2];
            for (String move : movesStr.split(",")) {
                String[] moveParts = move.split(":");
                int row = Integer.parseInt(moveParts[0]);
                int col = Integer.parseInt(moveParts[1]);
                String color = moveParts[2];
                board.put(row + "," + col, color);
            }
        }
        
        return new Object[]{size, myColor, board};
    }
    
    /**
     * Get all empty cells on the board.
     */
    private static List<int[]> getEmptyCells(int size, Map<String, String> board) {
        List<int[]> empty = new ArrayList<>();
        
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                if (!board.containsKey(row + "," + col)) {
                    empty.add(new int[]{row, col});
                }
            }
        }
        
        return empty;
    }
    
    /**
     * Choose your move. This is where your AI logic goes!
     * 
     * @param size Board size
     * @param myColor Your color
     * @param board Dictionary of existing moves
     * @return Array of [row, col] for your move
     */
    private static int[] chooseMove(int size, String myColor, Map<String, String> board) {
        // Simple strategy: pick a random empty cell
        List<int[]> emptyCells = getEmptyCells(size, board);
        
        if (emptyCells.isEmpty()) {
            return new int[]{0, 0};  // Shouldn't happen
        }
        
        Random random = new Random();
        return emptyCells.get(random.nextInt(emptyCells.size()));
    }
    
    /**
     * Main function - this is all you need!
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        try {
            // Read the board state (one line)
            String line = scanner.nextLine();
            
            // Parse it
            Object[] parsed = parseBoard(line);
            int size = (int) parsed[0];
            String myColor = (String) parsed[1];
            @SuppressWarnings("unchecked")
            Map<String, String> board = (Map<String, String>) parsed[2];
            
            // Choose your move
            int[] move = chooseMove(size, myColor, board);
            
            // Output your move (don't forget to flush!)
            System.out.println(move[0] + " " + move[1]);
            System.out.flush();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            scanner.close();
        }
    }
}



