import java.util.*;

/**
 * Hex agent that combines fast tactical checks with heuristic filtering and
 * time-bounded Monte Carlo simulation. The board is stored as a 1D integer
 * array for low allocation overhead, strong candidate moves are ranked with
 * positional and path-based heuristics, and final move selection is refined by
 * UCB1-guided rollouts.
 */
public class MyAgentAttemptFour {
    private static final int EMPTY = 0, RED = 1, BLUE = 2;
    private static final int INF = 1_000_000;
    private static final Random RNG = new Random();

    private static final int[][] DIRS = {
        {-1, 0}, {-1, 1}, {0, 1}, {1, 0}, {1, -1}, {0, -1}
    };
    private static final int[][] BRIDGE_OFFSETS = {
        {-1, -1}, {-1, 2}, {-2, 1}, {1, 1}, {2, -1}, {1, -2}
    };
    private static final int[] BFS_DR = {-1, 1, 0, 0, -1, 1};
    private static final int[] BFS_DC = {0, 0, -1, 1, 1, -1};

    private static final class PathInfo {
        final int distance;
        final int[] empties;
        final int emptyCount;

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
        if (firstMoveIndex < 0) return false;
        int row = firstMoveIndex / size;
        int col = firstMoveIndex % size;
        int center = size / 2;
        int dist = Math.abs(row - center) + Math.abs(col - center);
        return dist <= Math.max(2, size / 3);
    }

    /**
     * Checks whether a candidate cell is adjacent to any existing stone.
     * This is used during candidate filtering so the rollout budget focuses on
     * locally relevant moves rather than isolated cells early in the game.
     */
    private static boolean hasOccupiedNeighbor(int size, int[] board, int idx) {
        int row = idx / size, col = idx % size;
        for (int[] d : DIRS) {
            int nr = row + d[0], nc = col + d[1];
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
        if (cell == color) return 0;
        if (cell == EMPTY) return 1;
        return INF;
    }

    /**
     * Checks whether a board index reaches the goal edge for the given color.
     * RED connects top to bottom, while BLUE connects left to right.
     */
    private static boolean isTargetEdge(int size, int color, int idx) {
        int row = idx / size, col = idx % size;
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
        int[] dist = new int[n];
        int[] parent = new int[n];
        boolean[] used = new boolean[n];
        Arrays.fill(dist, INF);
        Arrays.fill(parent, -1);

        for (int i = 0; i < size; i++) {
            int idx = (color == RED) ? i : i * size;
            int cost = cellCost(color, board[idx]);
            if (cost < INF) dist[idx] = cost;
        }

        int bestTarget = -1;
        for (int iter = 0; iter < n; iter++) {
            int u = -1;
            int best = INF;
            for (int i = 0; i < n; i++) {
                if (!used[i] && dist[i] < best) {
                    best = dist[i];
                    u = i;
                }
            }

            if (u < 0 || best == INF) break;
            used[u] = true;

            if (isTargetEdge(size, color, u)) {
                bestTarget = u;
                break;
            }

            int row = u / size, col = u % size;
            for (int[] d : DIRS) {
                int nr = row + d[0], nc = col + d[1];
                if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                    int v = nr * size + nc;
                    int step = cellCost(color, board[v]);
                    if (step >= INF) continue;
                    int alt = dist[u] + step;
                    if (alt < dist[v]) {
                        dist[v] = alt;
                        parent[v] = u;
                    }
                }
            }
        }

        if (bestTarget < 0) return new PathInfo(INF, new int[0], 0);

        int[] pathEmpties = new int[n];
        int emptyCount = 0;
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

        for (int[] d : DIRS) {
            int nr = row + d[0], nc = col + d[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size) {
                int cell = board[nr * size + nc];
                if (cell == myColor) friendly++;
                else if (cell == opp) enemy++;
            }
        }

        for (int[] b : BRIDGE_OFFSETS) {
            int nr = row + b[0], nc = col + b[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr * size + nc] == myColor)
                bridges++;
        }

        double score = 2.5 * (size - (Math.abs(row - center) + Math.abs(col - center)));
        score += 15.0 * friendly + 5.0 * enemy + 7.0 * bridges;
        if (myColor == RED) score += 3.0 * (size - Math.abs(col - center));
        else                score += 3.0 * (size - Math.abs(row - center));
        if (friendly >= 2) score += 20.0;
        if (friendly >= 3) score += 15.0;
        if (enemy   >= 2) score += 10.0;
        if (friendly == 0 && enemy == 0) score -= 25.0;

        for (int i = 0; i < myPath.emptyCount; i++) {
            if (myPath.empties[i] == idx) {
                score += 90.0 - 8.0 * myPath.distance;
                score += Math.max(0.0, 30.0 - 6.0 * myPath.emptyCount);
                break;
            }
        }

        for (int i = 0; i < oppPath.emptyCount; i++) {
            if (oppPath.empties[i] == idx) {
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
        for (int i = 0; i < numEmpty; i++) {
            int move = empty[i];
            System.arraycopy(board, 0, simBoard, 0, board.length);
            simBoard[move] = color;
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
        long startTime = System.currentTimeMillis();
        long timeLimit = getTimeLimitMs(size);

        int[] empty = new int[board.length];
        int numEmpty = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] == EMPTY) empty[numEmpty++] = i;
        }

        if (numEmpty == board.length) return new int[]{size / 2, size / 2};
        if (numEmpty == 1) return new int[]{empty[0] / size, empty[0] % size};

        int[] simBoard = new int[board.length];
        int[] queue    = new int[board.length];
        boolean[] visited = new boolean[board.length];

        int win = findWinOrBlock(size, myColor, empty, numEmpty, board, simBoard, queue, visited);
        if (win >= 0) return new int[]{win / size, win % size};

        int opp = (myColor == RED) ? BLUE : RED;
        int block = findWinOrBlock(size, opp, empty, numEmpty, board, simBoard, queue, visited);
        if (block >= 0) return new int[]{block / size, block % size};

        PathInfo myPath = findShortestPath(size, myColor, board);
        PathInfo oppPath = findShortestPath(size, opp, board);

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

        int occupied = board.length - numEmpty;
        int[] candidates = new int[numEmpty];
        double[] scores = new double[numEmpty];
        int candidatesCount = 0;

        for (int i = 0; i < numEmpty; i++) {
            int idx = empty[i];
            if (occupied > 4 && !hasOccupiedNeighbor(size, board, idx) && !isNearCenter(size, idx))
                continue;
            candidates[candidatesCount] = idx;
            scores[candidatesCount] = scoreMove(size, myColor, board, idx, myPath, oppPath);
            candidatesCount++;
        }

        if (candidatesCount == 0) {
            for (int i = 0; i < numEmpty; i++) {
                candidates[i] = empty[i];
                scores[i] = scoreMove(size, myColor, board, empty[i], myPath, oppPath);
            }
            candidatesCount = numEmpty;
        }

        int topK = Math.min(candidatesCount, Math.max(12, Math.min(30, size + 6)));
        for (int i = 0; i < topK; i++) {
            int best = i;
            for (int j = i + 1; j < candidatesCount; j++) {
                if (scores[j] > scores[best]) best = j;
            }
            double ts = scores[i];  scores[i]  = scores[best];  scores[best]  = ts;
            int    ti = candidates[i];   candidates[i]   = candidates[best];   candidates[best]   = ti;
        }

        int[] wins   = new int[topK];
        int[] trials = new int[topK];
        int[] shuffleBox = new int[numEmpty];
        final double C = 1.414;

        for (int i = 0; i < topK; i++) {
            for (int j = 0; j < 2; j++) {
                if (simulate(size, myColor, board, empty, numEmpty, candidates[i],
                             simBoard, shuffleBox, queue, visited))
                    wins[i]++;
                trials[i]++;
            }
        }
        int total = 2 * topK;

        while (System.currentTimeMillis() - startTime < timeLimit) {
            double logN = Math.log(total);
            int pick = 0;
            double bestUCB = -1.0;
            for (int i = 0; i < topK; i++) {
                double ucb = (double) wins[i] / trials[i] + C * Math.sqrt(logN / trials[i]);
                if (ucb > bestUCB) { bestUCB = ucb; pick = i; }
            }
            if (simulate(size, myColor, board, empty, numEmpty, candidates[pick],
                         simBoard, shuffleBox, queue, visited))
                wins[pick]++;
            trials[pick]++;
            total++;
        }

        int best = 0;
        double maxRate = -1.0;
        for (int i = 0; i < topK; i++) {
            double rate = (double) wins[i] / trials[i];
            if (rate > maxRate) { maxRate = rate; best = i; }
        }

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
        System.arraycopy(board, 0, simBoard, 0, board.length);
        simBoard[firstMove] = myColor;

        int count = 0;
        for (int i = 0; i < numEmpty; i++) {
            if (empty[i] != firstMove) shuffleBox[count++] = empty[i];
        }

        for (int i = count - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int tmp = shuffleBox[i]; shuffleBox[i] = shuffleBox[j]; shuffleBox[j] = tmp;
        }

        int turn = (myColor == RED) ? BLUE : RED;
        for (int i = 0; i < count; i++) {
            simBoard[shuffleBox[i]] = turn;
            turn = (turn == RED) ? BLUE : RED;
        }

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
        int head = 0, tail = 0;
        Arrays.fill(visited, false);

        for (int i = 0; i < size; i++) {
            int idx = (color == RED) ? i : i * size;
            if (board[idx] == color) {
                visited[idx] = true;
                queue[tail++] = idx;
            }
        }

        while (head < tail) {
            int curr = queue[head++];
            int r = curr / size, c = curr % size;
            if ((color == RED && r == size - 1) || (color == BLUE && c == size - 1)) return true;
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
        return false;
    }

    /**
     * JVM Warmup Routine:
     * Forces the Java JIT compiler to pre-compile the highly-frequent
     * 'simulate' and 'fastCheckWin' methods into optimized machine code
     * to eliminate the compilation delay that causes the BLUE player
     * to time out on its first turn.
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

        // Execute dummy simulations to trigger JIT Compiler optimizations
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
        warmupJVM();
        try (Scanner sc = new Scanner(System.in)) {
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                if (line.isEmpty()) continue;

                String[] parts = line.trim().split("\\s+", 3);
                int size = Integer.parseInt(parts[0]);
                int myColor = parts[1].equals("RED") ? RED : BLUE;

                int[] board = new int[size * size];
                int movesCount = 0;
                int firstMoveIndex = -1;
                if (parts.length == 3 && !parts[2].isEmpty()) {
                    for (String move : parts[2].split(",")) {
                        String[] m = move.split(":");
                        int row = Integer.parseInt(m[0]);
                        int col = Integer.parseInt(m[1]);
                        int idx = row * size + col;
                        board[idx] = m[2].equals("R") ? RED : BLUE;
                        if (movesCount == 0) firstMoveIndex = idx;
                        movesCount++;
                    }
                }

                if (myColor == BLUE && movesCount == 1 && shouldSwap(size, firstMoveIndex)) {
                    System.out.println("swap");
                    System.out.flush();
                    continue;
                }

                int[] move = chooseMove(size, myColor, board);
                System.out.println(move[0] + " " + move[1]);
                System.out.flush();
            }
        }
    }
}
