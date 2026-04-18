import java.util.*;

/**
 * ============================================================================
 * CS 5800 Final Project: Final Code Submission
 * Group Members: Abdullah Tariq, Chendong Yu
 * ============================================================================
 * HexAgent: A time-bounded Monte Carlo Tree Search agent.

 * Architecture & Optimizations:

 * - 1D Flat Board: The board is stored as a 1D integer array for low memory allocation overhead and improved cache locality.

 * - Zero-GC Rollouts: All memory structures are pre-allocated outside the simulation hot-loop.

 * - Heuristic Filtering: Strong candidate moves are ranked using criteria such as
 * connectivity and Dijkstra-based shortest-path heuristics before UCB1 rollouts.
 */
public class HexAgent {
    // --------------------
    // Game State Constants
    // --------------------
    private static final int EMPTY = 0, RED = 1, BLUE = 2;
    // Infinity placeholder for Dijkstra's shortest-path calculation
    private static final int INF = 1_000_000;
    // Shared random number generator for Monte Carlo Tree Search Rollouts
    private static final Random RNG = new Random();

    // -------------------------------
    // Hexagonal Grid Movement Vectors
    // -------------------------------

    // 6 immediate adjacent neighbors for a cell in a Hex grid
    // Directions: Top, Top-Right, Right, Bottom, Bottom-Left, Left
    private static final int[][] DIRS = {
        {-1, 0}, {-1, 1}, {0, 1}, {1, 0}, {1, -1}, {0, -1}
    };
    // Tactical "bridge" offsets
    private static final int[][] BRIDGE_OFFSETS = {
        {-1, -1}, {-1, 2}, {-2, 1}, {1, 1}, {2, -1}, {1, -2}
    };
    // Unrolled 1D directional arrays optimized for the high-frequency BFS win-check
    private static final int[] BFS_DR = {-1, 1, 0, 0, -1, 1};
    private static final int[] BFS_DC = {0, 0, -1, 1, 1, -1};

    /**
     * An immutable data container that holds the results of a shortest-path query.
     * It records the total distance to the target edge and explicitly tracks the
     * exact empty cells required to complete that path.
     */
    private static final class PathInfo {
        final int distance;  // The total computed cost to reach the target edge
        final int[] empties;  // The array of the not played board indices that make up the path
        final int emptyCount;  // The exact number of empty cells on this path

        PathInfo(int distance, int[] empties, int emptyCount) {
            this.distance = distance;
            this.empties = empties;
            this.emptyCount = emptyCount;
        }
    }

    /**
     * Returns the per-move time budget used by the search.
     * The values stay below the official limits so parsing, output, and JVM
     * overhead do not cause accidental time forfeits on larger boards.
     */
    private static long getTimeLimitMs(int size) {
        if (size <= 11) return 120;
        if (size <= 15) return 165;
        if (size <= 19) return 210;
        if (size <= 21) return 255;
        return 800;
    }

    /**
     * Decides whether BLUE should invoke the swap rule after RED's first move.
     * The rule is intentionally simple: if the first move lands near the board
     * center, it is treated as a strong opening worth mirroring instead of
     * playing from a weaker second-player position.
     */
    private static boolean shouldSwap(int size, int firstMoveIndex) {
        // Safety check for empty board or parsing errors
        if (firstMoveIndex < 0) return false;

        // Decode the 1D index into 2D matrix coordinates
        int row = firstMoveIndex / size;
        int col = firstMoveIndex % size;
        int center = size / 2;

        // Calculate the Manhattan distance from the geometric center
        int dist = Math.abs(row - center) + Math.abs(col - center);

        // Swap if the move falls strictly within a dynamic central radius
        return dist <= Math.max(2, size / 3);
    }

    /**
     * Checks whether a candidate cell is adjacent to any existing stone.
     * This is used during candidate filtering so the rollout budget focuses on
     * locally relevant moves rather than isolated cells early in the game.
     */
    private static boolean hasOccupiedNeighbor(int size, int[] board, int idx) {
        // Decode the flat 1D index back into 2D row/col coordinates
        int row = idx / size, col = idx % size;

        // Iterate through all 6 valid hexagonal directions
        for (int[] d : DIRS) {
            int nr = row + d[0], nc = col + d[1];

            // Check boundary limits and verify if the neighbor cell contains any stone
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr * size + nc] != EMPTY)
                return true;
        }
        return false;
    }

    /**
     * Checks whether a cell lies within a Manhattan-style radius of center.
     * Center cells remain eligible even when they are not adjacent to stones,
     * which helps preserve useful opening and early-mid game structure.
     */
    private static boolean isNearCenter(int size, int idx) {
        int center = size / 2;
        return Math.abs(idx / size - center) + Math.abs(idx % size - center) <= 2;
    }

    /**
     * Returns the pathfinding cost for a color when evaluating connection paths.
     * Friendly stones cost 0, empty cells cost 1, and enemy stones are treated
     * as blocked by assigning an effectively infinite cost.
     */
    private static int cellCost(int color, int cell) {
        if (cell == color) return 0;  // No additional moves needed
        if (cell == EMPTY) return 1;  // Costs 1 move to place a stone on an empty call
        return INF;
    }

    /**
     * Checks whether a board index reaches the goal edge for the given color.
     * RED connects top to bottom, while BLUE connects left to right.
     */
    private static boolean isTargetEdge(int size, int color, int idx) {
        // Extract 2D coordinates to evaluate positional alignment
        int row = idx / size, col = idx % size;

        // RED seeks the absolute bottom row; BLUE seeks the absolute rightmost column
        return (color == RED) ? (row == size - 1) : (col == size - 1);
    }

    /**
     * Computes the cheapest connection path for one player using a Dijkstra-style
     * search over the hex grid. The returned structure records both the total
     * path cost and the empty cells on that path, which are later used to boost
     * moves that either advance our own connection or disrupt the opponent's.
     */
    private static PathInfo findShortestPath(int size, int color, int[] board) {
        int n = board.length;

        // Zero-GC structure allocations for Dijkstra: distances, path reconstruction, and visited flags
        int[] dist = new int[n];
        int[] parent = new int[n];
        boolean[] used = new boolean[n];
        Arrays.fill(dist, INF);
        Arrays.fill(parent, -1);

        // Initialize the starting edge (Top row for RED, Left column for BLUE)
        for (int i = 0; i < size; i++) {
            int idx = (color == RED) ? i : i * size;
            int cost = cellCost(color, board[idx]);
            if (cost < INF) dist[idx] = cost;
        }

        int bestTarget = -1;

        // Main Dijkstra loop to explore the cheapest path across the board
        for (int iter = 0; iter < n; iter++) {
            int u = -1;
            int best = INF;

            // Extract the unvisited node with the minimum accumulated distance
            for (int i = 0; i < n; i++) {
                if (!used[i] && dist[i] < best) {
                    best = dist[i];
                    u = i;
                }
            }

            // Terminate if no reachable nodes remain
            if (u < 0 || best == INF) break;
            used[u] = true;

            // Stop early if the shortest path successfully reaches the opposite edge
            if (isTargetEdge(size, color, u)) {
                bestTarget = u;
                break;
            }

            // Edge relaxation: evaluate all valid hexagonal neighbors of the current node
            int row = u / size, col = u % size;
            for (int[] d : DIRS) {
                int nr = row + d[0], nc = col + d[1];
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    int v = nr * size + nc;
                    int step = cellCost(color, board[v]);
                    if (step >= INF) continue; // Skip blocked paths (enemy stones)

                    // Update the path if a cheaper route is discovered
                    int alt = dist[u] + step;
                    if (alt < dist[v]) {
                        dist[v] = alt;
                        parent[v] = u;
                    }
                }
            }
        }

        // Return infinite cost if the path is completely blocked
        if (bestTarget < 0) return new PathInfo(INF, new int[0], 0);

        int[] pathEmpties = new int[n];
        int emptyCount = 0;

        // Backtrack from the target node using the parent array to collect all critical empty cells
        for (int curr = bestTarget; curr >= 0; curr = parent[curr]) {
            if (board[curr] == EMPTY) pathEmpties[emptyCount++] = curr;
        }

        return new PathInfo(dist[bestTarget], pathEmpties, emptyCount);
    }

    /**
     * Assigns a static heuristic score to a candidate move before simulation.
     * The score combines center preference, friendly connectivity, enemy
     * pressure, bridge patterns, corridor alignment, local clustering, and
     * shortest-path overlap for both players. This stage does not pick the
     * final move by itself; it narrows the search to promising candidates.
     */
    private static double scoreMove(int size, int myColor, int[] board, int idx,
                                    PathInfo myPath, PathInfo oppPath) {
        int row = idx / size, col = idx % size;
        int center = size / 2;
        int opp = (myColor == RED) ? BLUE : RED;

        int friendly = 0, enemy = 0, bridges = 0;

        // Extract local topological features: count adjacent friendly and enemy stones
        for (int[] d : DIRS) {
            int nr = row + d[0], nc = col + d[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                int cell = board[nr * size + nc];
                if (cell == myColor) friendly++;
                else if (cell == opp) enemy++;
            }
        }

        // Detect bridge patterns (strong 2-step virtual connections that are hard to sever)
        for (int[] b : BRIDGE_OFFSETS) {
            int nr = row + b[0], nc = col + b[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr * size + nc] == myColor)
                bridges++;
        }

        // Base score: favor the geometric center (Manhattan distance decay)
        double score = 2.5 * (size - (Math.abs(row - center) + Math.abs(col - center)));

        // Linear weights for local features: heavily favor expanding our own groups
        score += 15.0 * friendly + 5.0 * enemy + 7.0 * bridges;

        // Corridor alignment: RED prefers the central column, BLUE prefers the central row
        if (myColor == RED) score += 3.0 * (size - Math.abs(col - center));
        else                score += 3.0 * (size - Math.abs(row - center));

        // Non-linear synergy bonuses for solid clustering, and penalties for isolated moves
        if (friendly >= 2) score += 20.0;
        if (friendly >= 3) score += 15.0;
        if (enemy   >= 2) score += 10.0; // Encourages blocking enemy clusters locally
        if (friendly == 0 && enemy == 0) score -= 25.0; // Discourage playing in empty areas early game

        // Offensive Pathfinding Boost: massive score spike if the move advances our shortest path
        for (int i = 0; i < myPath.emptyCount; i++) {
            if (myPath.empties[i] == idx) {
                // The closer we are to connecting (lower distance/emptyCount), the higher the urgency
                score += 90.0 - 8.0 * myPath.distance;
                score += Math.max(0.0, 30.0 - 6.0 * myPath.emptyCount);
                break;
            }
        }

        // Defensive Disruption Boost: significant score spike if the move blocks the opponent's shortest path
        for (int i = 0; i < oppPath.emptyCount; i++) {
            if (oppPath.empties[i] == idx) {
                // Prioritize blocking if the opponent is very close to winning
                score += 55.0 - 5.0 * oppPath.distance;
                score += Math.max(0.0, 20.0 - 4.0 * oppPath.emptyCount);
                break;
            }
        }

        return score;
    }

    /**
     * Tests each empty cell as a one-ply tactical move for the given color.
     * It is used both to finish immediately winning positions and to block an
     * opponent move that would otherwise win on the next turn.
     */
    private static int findWinOrBlock(int size, int color, int[] empty, int numEmpty,
                                      int[] board, int[] simBoard, int[] queue, boolean[] visited) {
        // Iterate through all currently available valid moves
        for (int i = 0; i < numEmpty; i++) {
            int move = empty[i];

            // Zero-GC array copy into the pre-allocated simulation board
            System.arraycopy(board, 0, simBoard, 0, board.length);

            // Simulate the tactical 1-ply placement
            simBoard[move] = color;

            // Execute the optimized BFS check
            if (fastCheckWin(size, color, simBoard, queue, visited)) return move;
        }
        return -1;
    }

    /**
     * Selects a move with a staged decision pipeline.
     * The method first handles trivial board states, then checks immediate
     * wins and forced blocks, then uses shortest-path information to play
     * direct connection moves in sharp late-game positions. If no tactical move
     * is forced, it filters and ranks candidates heuristically, keeps only the
     * strongest subset, and spends the remaining time on UCB1-guided Monte
     * Carlo rollouts to choose the move with the best observed win rate.
     */
    private static int[] chooseMove(int size, int myColor, int[] board) {
        // 1. TIMING & INITIALIZATION
        long startTime = System.currentTimeMillis();
        long timeLimit = getTimeLimitMs(size);

        // Collect all currently empty cells on the board
        int[] empty = new int[board.length];
        int numEmpty = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] == EMPTY) empty[numEmpty++] = i;
        }

        // 2. TRIVIAL STATES
        // Always seize the exact geometric center on the very first turn
        if (numEmpty == board.length) return new int[]{size / 2, size / 2};
        // Auto-play if only one spot remains
        if (numEmpty == 1) return new int[]{empty[0] / size, empty[0] % size};

        // 3. ZERO-GC ALLOCATIONS
        // Pre-allocate simulation arrays once to survive thousands of loops without GC pauses
        int[] simBoard = new int[board.length];
        int[] queue    = new int[board.length];
        boolean[] visited = new boolean[board.length];

        // 4. TACTICAL 1-PLY CHECKS
        int win = findWinOrBlock(size, myColor, empty, numEmpty, board, simBoard, queue, visited);
        if (win >= 0) return new int[]{win / size, win % size};
        int opp = (myColor == RED) ? BLUE : RED;
        int block = findWinOrBlock(size, opp, empty, numEmpty, board, simBoard, queue, visited);
        if (block >= 0) return new int[]{block / size, block % size};

        // 5. SHORTEST-PATH ENDGAME SHORTCUT
        PathInfo myPath = findShortestPath(size, myColor, board);
        PathInfo oppPath = findShortestPath(size, opp, board);

        // If we are extremely close to connecting (1 to 3 moves away), bypass Monte Carlo Tree Search
        // and strictly play on the critical path using heuristic scoring.
        if (myPath.emptyCount > 0 && myPath.emptyCount <= 3) {
            int bestPathMove = myPath.empties[0];
            double bestPathScore = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < myPath.emptyCount; i++) {
                int idx = myPath.empties[i];
                double pathScore = scoreMove(size, myColor, board, idx, myPath, oppPath);
                if (pathScore > bestPathScore) {
                    bestPathScore = pathScore;
                    bestPathMove = idx;
                }
            }
            return new int[]{bestPathMove / size, bestPathMove % size};
        }

        // 6. CANDIDATE FILTERING (PRUNING)
        int occupied = board.length - numEmpty;
        int[] candidates = new int[numEmpty];
        double[] scores = new double[numEmpty];
        int candidatesCount = 0;

        // Prune isolated moves early-game to focus computation on locally relevant skirmishes
        for (int i = 0; i < numEmpty; i++) {
            int idx = empty[i];
            if (occupied > 4 && !hasOccupiedNeighbor(size, board, idx) && !isNearCenter(size, idx))
                continue; // Skip mathematically weak, isolated cells
            candidates[candidatesCount] = idx;
            scores[candidatesCount] = scoreMove(size, myColor, board, idx, myPath, oppPath);
            candidatesCount++;
        }

        // Fallback: If filtering was too aggressive (e.g., highly scattered board), evaluate all empty cells
        if (candidatesCount == 0) {
            for (int i = 0; i < numEmpty; i++) {
                candidates[i] = empty[i];
                scores[i] = scoreMove(size, myColor, board, empty[i], myPath, oppPath);
            }
            candidatesCount = numEmpty;
        }

        // 7. TOP-K SELECTION
        // Dynamically scale the search width based on board size (keep between 12 and 30 candidates)
        int topK = Math.min(candidatesCount, Math.max(12, Math.min(30, size + 6)));
        for (int i = 0; i < topK; i++) {
            int best = i;
            for (int j = i + 1; j < candidatesCount; j++) {
                if (scores[j] > scores[best]) best = j;
            }
            // Swap highest score to the front
            double ts = scores[i];  scores[i]  = scores[best];  scores[best]  = ts;
            int    ti = candidates[i];   candidates[i]   = candidates[best];   candidates[best]   = ti;
        }

        // 8. UCB1-GUIDED MCTS ROLLOUTS
        int[] wins   = new int[topK];
        int[] trials = new int[topK];
        int[] shuffleBox = new int[numEmpty];
        final double C = 1.414; // Standard exploration constant (sqrt(2))

        // Seed phase: Guarantee at least 2 rollouts per candidate to establish a statistical baseline
        for (int i = 0; i < topK; i++) {
            for (int j = 0; j < 2; j++) {
                if (simulate(size, myColor, board, empty, numEmpty, candidates[i],
                    simBoard, shuffleBox, queue, visited))
                    wins[i]++;
                trials[i]++;
            }
        }
        int total = 2 * topK;

        // Hot loop: Run continuous simulations until the strict time limit is reached
        while (System.currentTimeMillis() - startTime < timeLimit) {
            double logN = Math.log(total);
            int pick = 0;
            double bestUCB = -1.0;

            // Select the next node to explore using the Upper Confidence Bound (UCB1) formula
            // Balances exploitation (current win rate) and exploration (infrequently tested moves)
            for (int i = 0; i < topK; i++) {
                double ucb = (double) wins[i] / trials[i] + C * Math.sqrt(logN / trials[i]);
                if (ucb > bestUCB) { bestUCB = ucb; pick = i; }
            }

            // Simulate randomly to the end and do backpropagation to the result
            if (simulate(size, myColor, board, empty, numEmpty, candidates[pick],
                simBoard, shuffleBox, queue, visited))
                wins[pick]++;
            trials[pick]++;
            total++;
        }

        // 9. FINAL DECISION
        // Select the candidate with the highest empirical win rate (Pure Exploitation)
        int best = 0;
        double maxRate = -1.0;
        for (int i = 0; i < topK; i++) {
            double rate = (double) wins[i] / trials[i];
            if (rate > maxRate) { maxRate = rate; best = i; }
        }

        // Convert the flat 1D index back to a 2D [row, col] format expected by the framework
        return new int[]{candidates[best] / size, candidates[best] % size};
    }

    /**
     * Runs a single random rollout after committing to a candidate first move.
     * The method copies the board, applies the candidate, shuffles the
     * remaining empty cells with Fisher-Yates, alternates colors through the
     * completion, and then checks whether the original player ends up winning.
     */
    private static boolean simulate(int size, int myColor, int[] board,
                                    int[] empty, int numEmpty, int firstMove,
                                    int[] simBoard, int[] shuffleBox,
                                    int[] queue, boolean[] visited) {

        // Fast, native memory copy to clone the current board state into the scratchpad
        System.arraycopy(board, 0, simBoard, 0, board.length);

        // Commit to the candidate move
        simBoard[firstMove] = myColor;

        // Populate the shuffle box with all remaining empty cells, excluding the move just played
        int count = 0;
        for (int i = 0; i < numEmpty; i++) {
            if (empty[i] != firstMove) shuffleBox[count++] = empty[i];
        }

        // Perform an in-place Fisher-Yates shuffle (O(N) time complexity).
        // This generates a perfectly unbiased random sequence of future moves for the rollout.
        for (int i = count - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int tmp = shuffleBox[i];
            shuffleBox[i] = shuffleBox[j];
            shuffleBox[j] = tmp;
        }

        // Fast-forward to the end of the game by alternating turns randomly.
        // Since Hex cannot end in a draw, we completely fill the board to reach a terminal state.
        int turn = (myColor == RED) ? BLUE : RED;
        for (int i = 0; i < count; i++) {
            simBoard[shuffleBox[i]] = turn;
            turn = (turn == RED) ? BLUE : RED;
        }

        // Evaluate the randomized terminal board state to see if our candidate move led to a victory
        return fastCheckWin(size, myColor, simBoard, queue, visited);
    }

    /**
     * Performs a fast BFS-based win check on the flat board representation.
     * The search starts from the player's home edge, expands through connected
     * stones of the same color, and succeeds as soon as it reaches the
     * opposite edge.
     */
    private static boolean fastCheckWin(int size, int color, int[] board,
                                        int[] queue, boolean[] visited) {
        // Implement a custom array-based queue using two pointers (head/tail).
        // This avoids the massive object allocation overhead of java.util.LinkedList.
        int head = 0, tail = 0;
        Arrays.fill(visited, false);

        // Enqueue all starting nodes:
        // RED starts from the absolute top row (0 to size-1)
        // BLUE starts from the absolute left column (multiples of size)
        for (int i = 0; i < size; i++) {
            int idx = (color == RED) ? i : i * size;
            if (board[idx] == color) {
                visited[idx] = true;
                queue[tail++] = idx;
            }
        }

        // Standard Breadth-First Search (BFS) traversal
        while (head < tail) {
            int curr = queue[head++];
            int r = curr / size, c = curr % size;

            // Check for victory condition:
            // RED reached the bottom row OR BLUE reached the rightmost column
            if ((color == RED && r == size - 1) || (color == BLUE && c == size - 1)) {
                return true;
            }

            // Expand to all adjacent neighbors of the same color
            for (int i = 0; i < 6; i++) {
                int nr = r + BFS_DR[i], nc = c + BFS_DC[i];
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    int nIdx = nr * size + nc;
                    if (!visited[nIdx] && board[nIdx] == color) {
                        visited[nIdx] = true;
                        queue[tail++] = nIdx;
                    }
                }
            }
        }

        // Queue is empty and opposite edge was never reached
        return false;
    }

    /**
     * JVM Warmup Routine:
     * Forces the Java JIT (Just-In-Time) compiler to pre-compile the highly-frequent
     * 'simulate' and 'fastCheckWin' methods into optimized machine code.
     * This eliminates the severe C1/C2 compilation latency that causes the
     * BLUE player to forfeit on its first turn due to strict timeouts.
     */
    private static void warmupJVM() {
        int size = 11;
        int[] board = new int[size * size];
        int numEmpty = size * size;
        int[] empty = new int[numEmpty];

        for (int i = 0; i < numEmpty; i++) {
            empty[i] = i;
        }

        int[] simBoard = new int[board.length];
        int[] shuffleBox = new int[board.length];
        int[] queue = new int[board.length];
        boolean[] visited = new boolean[board.length];

        // Execute 2000 dummy simulations before the game engine starts its timer.
        // 2000 iterations safely surpass the standard JVM compilation threshold,
        // ensuring the MCTS hot-loops run at native C++ equivalent speeds.
        for (int i = 0; i < 2000; i++) {
            simulate(size, RED, board, empty, numEmpty, empty[0],
                simBoard, shuffleBox, queue, visited);
        }
    }

    /**
     * Runs the stdin/stdout game loop expected by the framework.
     * Each input line is parsed into a flat board representation, the opening
     * move is checked against the swap heuristic when the agent is BLUE, and
     * otherwise the main search routine is asked for the best move to print.
     */
    public static void main(String[] args) {
        // Trigger JVM JIT optimizations before acknowledging any framework commands
        warmupJVM();

        try (Scanner sc = new Scanner(System.in)) {
            // Block and wait for the framework to pipe in the next board state
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.isEmpty()) continue;

                // Parse input protocol: <SIZE> <YOUR_COLOR> <MOVES>
                String[] parts = line.trim().split("\\s+", 3);
                int size = Integer.parseInt(parts[0]);
                int myColor = parts[1].equals("RED") ? RED : BLUE;

                int[] board = new int[size * size];
                int movesCount = 0;
                int firstMoveIndex = -1;

                // Reconstruct the 1D board state from the comma-separated move history
                if (parts.length == 3 && !parts[2].isEmpty()) {
                    for (String move : parts[2].split(",")) {
                        String[] m = move.split(":");
                        int row = Integer.parseInt(m[0]);
                        int col = Integer.parseInt(m[1]);
                        int idx = row * size + col;

                        board[idx] = m[2].equals("R") ? RED : BLUE;

                        // Keep track of RED's opening move to evaluate the Swap Rule
                        if (movesCount == 0) firstMoveIndex = idx;
                        movesCount++;
                    }
                }

                // If playing as BLUE on turn 2, decide whether to steal RED's opening
                if (myColor == BLUE && movesCount == 1 && shouldSwap(size, firstMoveIndex)) {
                    System.out.println("swap");
                    System.out.flush(); // Critical: Forces output to the framework pipeline
                    continue;
                }

                // Execute the core MCTS algorithm to find the optimal move
                int[] move = chooseMove(size, myColor, board);

                // Output format expected by framework: "<ROW> <COL>"
                System.out.println(move[0] + " " + move[1]);
                System.out.flush(); // Critical: Avoids I/O buffering timeouts
            }
        }
    }
}
