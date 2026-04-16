import java.util.*;

/**
 * Hex agent that mixes fast heuristics with bounded random rollouts.
 */
public class MyAgentAttemptThree {
    private static final int EMPTY = 0;
    private static final int RED = 1;
    private static final int BLUE = 2;
    private static final int INF = 1_000_000;
    private static final Random RNG = new Random();

    private static final int[][] DIRS = {
        {-1, 0}, {-1, 1}, {0, 1}, {1, 0}, {1, -1}, {0, -1}
    };
    private static final int[][] BRIDGE_OFFSETS = {
        {-1, -1}, {-1, 2}, {-2, 1}, {1, 1}, {2, -1}, {1, -2}
    };

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
     * Returns the move budget for the current board size.
     */
    private static long getTimeLimitMs(int size) {
        if (size <= 11) return 85;
        if (size <= 15) return 120;
        if (size <= 19) return 165;
        if (size <= 21) return 200;
        return 300;
    }

    /**
     * Runs the stdin/stdout loop required by the game engine.
     */
    public static void main(String[] args) {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.trim().split("\\s+", 3);
                int size = Integer.parseInt(parts[0]);
                int myColor = parts[1].equals("RED") ? RED : BLUE;

                int[] board = new int[size * size];
                int moveCount = 0;
                int firstMoveIndex = -1;

                if (parts.length == 3 && !parts[2].isEmpty()) {
                    String[] moves = parts[2].split(",");
                    for (String move : moves) {
                        String[] moveParts = move.split(":");
                        int row = Integer.parseInt(moveParts[0]);
                        int col = Integer.parseInt(moveParts[1]);
                        int idx = row * size + col;
                        board[idx] = moveParts[2].equals("R") ? RED : BLUE;
                        if (moveCount == 0) {
                            firstMoveIndex = idx;
                        }
                        moveCount++;
                    }
                }

                if (myColor == BLUE && moveCount == 1 && shouldSwap(size, firstMoveIndex)) {
                    System.out.println("swap");
                    System.out.flush();
                    continue;
                }

                int[] move = chooseMove(size, myColor, board, moveCount);
                System.out.println(move[0] + " " + move[1]);
                System.out.flush();
            }

            reader.close();
        } catch (Exception e) {
            System.err.println("[ERROR] Exception occurred: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }

    /**
     * Decides whether BLUE should use the swap rule.
     */
    private static boolean shouldSwap(int size, int firstMoveIndex) {
        if (firstMoveIndex < 0) {
            return false;
        }
        int row = firstMoveIndex / size;
        int col = firstMoveIndex % size;
        int center = size / 2;
        int dist = Math.abs(row - center) + Math.abs(col - center);
        return dist <= Math.max(2, size / 3);
    }

    /**
     * Selects a move using tactical checks, heuristics, and rollouts.
     */
    private static int[] chooseMove(int size, int myColor, int[] board, int moveCount) {
        long deadline = System.nanoTime() + getTimeLimitMs(size) * 1_000_000L;
        int[] empty = new int[board.length];
        int emptyCount = 0;

        for (int i = 0; i < board.length; i++) {
            if (board[i] == EMPTY) {
                empty[emptyCount++] = i;
            }
        }

        if (emptyCount == board.length) {
            return new int[] {size / 2, size / 2};
        }
        if (emptyCount == 1) {
            return new int[] {empty[0] / size, empty[0] % size};
        }
        if (moveCount <= 2) {
            int openingMove = chooseHeuristicMove(size, myColor, board, empty, emptyCount);
            return new int[] {openingMove / size, openingMove % size};
        }

        int[] workBoard = new int[board.length];
        int[] queue = new int[board.length];
        boolean[] visited = new boolean[board.length];

        int opponent = myColor == RED ? BLUE : RED;
        if (emptyCount <= Math.max(40, size * 3)) {
            int winningMove = findImmediateMove(size, myColor, empty, emptyCount, board, workBoard, queue, visited);
            if (winningMove >= 0) {
                return new int[] {winningMove / size, winningMove % size};
            }

            int blockingMove = findImmediateMove(size, opponent, empty, emptyCount, board, workBoard, queue, visited);
            if (blockingMove >= 0) {
                return new int[] {blockingMove / size, blockingMove % size};
            }
        }

        PathInfo myPath = findShortestPath(size, myColor, board);
        PathInfo oppPath = findShortestPath(size, opponent, board);

        double[] scores = new double[emptyCount];
        Integer[] order = new Integer[emptyCount];
        for (int i = 0; i < emptyCount; i++) {
            int idx = empty[i];
            scores[i] = scoreMove(size, myColor, board, idx, myPath, oppPath);
            order[i] = i;
        }

        Arrays.sort(order, (a, b) -> Double.compare(scores[b], scores[a]));

        int candidateCount = Math.min(emptyCount, Math.max(8, Math.min(18, size + 2)));
        int[] candidates = new int[candidateCount];
        for (int i = 0; i < candidateCount; i++) {
            candidates[i] = empty[order[i]];
        }

        int[] wins = new int[candidateCount];
        int[] visits = new int[candidateCount];
        int bestMove = candidates[0];
        double bestValue = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < candidateCount; i++) {
            double value = scores[order[i]];
            if (value > bestValue) {
                bestValue = value;
                bestMove = candidates[i];
            }
        }

        if (emptyCount >= (board.length * 2) / 3) {
            return new int[] {bestMove / size, bestMove % size};
        }

        int[] rolloutMoves = new int[emptyCount];
        int[] rolloutBoard = new int[board.length];

        while (System.nanoTime() < deadline) {
            for (int i = 0; i < candidateCount; i++) {
                if (System.nanoTime() >= deadline) {
                    break;
                }

                int move = candidates[i];
                System.arraycopy(board, 0, rolloutBoard, 0, board.length);
                rolloutBoard[move] = myColor;
                int winner = simulateRandomGame(size, rolloutBoard, myColor == RED ? BLUE : RED, rolloutMoves, queue, visited);
                if (winner == myColor) {
                    wins[i]++;
                }
                visits[i]++;
            }
        }

        double bestRate = -1.0;
        for (int i = 0; i < candidateCount; i++) {
            if (visits[i] == 0) {
                continue;
            }
            double rate = (double) wins[i] / visits[i];
            double blended = rate + scores[order[i]] * 0.002;
            if (blended > bestRate) {
                bestRate = blended;
                bestMove = candidates[i];
            }
        }

        return new int[] {bestMove / size, bestMove % size};
    }

    /**
     * Picks the best move using only the static board evaluation.
     */
    private static int chooseHeuristicMove(int size, int myColor, int[] board, int[] empty, int emptyCount) {
        PathInfo myPath = findShortestPath(size, myColor, board);
        PathInfo oppPath = findShortestPath(size, myColor == RED ? BLUE : RED, board);
        int bestMove = empty[0];
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int i = 0; i < emptyCount; i++) {
            int idx = empty[i];
            double score = scoreMove(size, myColor, board, idx, myPath, oppPath);
            if (score > bestScore) {
                bestScore = score;
                bestMove = idx;
            }
        }

        return bestMove;
    }

    /**
     * Finds a move that wins immediately for the given color.
     */
    private static int findImmediateMove(int size, int color, int[] empty, int emptyCount, int[] board,
                                         int[] workBoard, int[] queue, boolean[] visited) {
        for (int i = 0; i < emptyCount; i++) {
            int move = empty[i];
            System.arraycopy(board, 0, workBoard, 0, board.length);
            workBoard[move] = color;
            if (checkWin(size, color, workBoard, queue, visited)) {
                return move;
            }
        }
        return -1;
    }

    /**
     * Plays out a random completion and returns the winner.
     */
    private static int simulateRandomGame(int size, int[] board, int currentTurn, int[] rolloutMoves,
                                          int[] queue, boolean[] visited) {
        int count = 0;
        for (int i = 0; i < board.length; i++) {
            if (board[i] == EMPTY) {
                rolloutMoves[count++] = i;
            }
        }

        for (int i = count - 1; i > 0; i--) {
            int j = RNG.nextInt(i + 1);
            int tmp = rolloutMoves[i];
            rolloutMoves[i] = rolloutMoves[j];
            rolloutMoves[j] = tmp;
        }

        int turn = currentTurn;
        for (int i = 0; i < count; i++) {
            board[rolloutMoves[i]] = turn;
            turn = turn == RED ? BLUE : RED;
        }

        return checkWin(size, RED, board, queue, visited) ? RED : BLUE;
    }

    /**
     * Checks whether the given color has a completed connection.
     */
    private static boolean checkWin(int size, int color, int[] board, int[] queue, boolean[] visited) {
        Arrays.fill(visited, false);
        int head = 0;
        int tail = 0;

        if (color == RED) {
            for (int col = 0; col < size; col++) {
                int idx = col;
                if (board[idx] == RED) {
                    visited[idx] = true;
                    queue[tail++] = idx;
                }
            }
        } else {
            for (int row = 0; row < size; row++) {
                int idx = row * size;
                if (board[idx] == BLUE) {
                    visited[idx] = true;
                    queue[tail++] = idx;
                }
            }
        }

        while (head < tail) {
            int idx = queue[head++];
            int row = idx / size;
            int col = idx % size;

            if ((color == RED && row == size - 1) || (color == BLUE && col == size - 1)) {
                return true;
            }

            for (int[] d : DIRS) {
                int nr = row + d[0];
                int nc = col + d[1];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size) {
                    continue;
                }
                int next = nr * size + nc;
                if (!visited[next] && board[next] == color) {
                    visited[next] = true;
                    queue[tail++] = next;
                }
            }
        }

        return false;
    }

    /**
     * Estimates the cheapest connection path for one color.
     */
    private static PathInfo findShortestPath(int size, int color, int[] board) {
        int n = board.length;
        int[] dist = new int[n];
        int[] parent = new int[n];
        boolean[] used = new boolean[n];
        Arrays.fill(dist, INF);
        Arrays.fill(parent, -1);

        for (int i = 0; i < size; i++) {
            int idx = color == RED ? i : i * size;
            int cost = cellCost(color, board[idx]);
            if (cost < INF) {
                dist[idx] = cost;
            }
        }

        int bestTarget = -1;
        for (int step = 0; step < n; step++) {
            int current = -1;
            int best = INF;
            for (int i = 0; i < n; i++) {
                if (!used[i] && dist[i] < best) {
                    best = dist[i];
                    current = i;
                }
            }
            if (current < 0 || best == INF) {
                break;
            }

            used[current] = true;
            int row = current / size;
            int col = current % size;
            if ((color == RED && row == size - 1) || (color == BLUE && col == size - 1)) {
                bestTarget = current;
                break;
            }

            for (int[] d : DIRS) {
                int nr = row + d[0];
                int nc = col + d[1];
                if (nr < 0 || nr >= size || nc < 0 || nc >= size) {
                    continue;
                }
                int next = nr * size + nc;
                int cost = cellCost(color, board[next]);
                if (cost >= INF) {
                    continue;
                }
                int alt = dist[current] + cost;
                if (alt < dist[next]) {
                    dist[next] = alt;
                    parent[next] = current;
                }
            }
        }

        if (bestTarget < 0) {
            return new PathInfo(INF, new int[0], 0);
        }

        int[] empties = new int[n];
        int emptyCount = 0;
        int cur = bestTarget;
        while (cur >= 0) {
            if (board[cur] == EMPTY) {
                empties[emptyCount++] = cur;
            }
            cur = parent[cur];
        }

        return new PathInfo(dist[bestTarget], empties, emptyCount);
    }

    /**
     * Returns the pathfinding cost of a single board cell.
     */
    private static int cellCost(int color, int cell) {
        if (cell == color) {
            return 0;
        }
        if (cell == EMPTY) {
            return 1;
        }
        return INF;
    }

    /**
     * Scores a candidate move using local and path-based features.
     */
    private static double scoreMove(int size, int myColor, int[] board, int idx, PathInfo myPath, PathInfo oppPath) {
        int row = idx / size;
        int col = idx % size;
        int center = size / 2;
        int opponent = myColor == RED ? BLUE : RED;

        int friendly = 0;
        int enemy = 0;
        int bridges = 0;

        for (int[] d : DIRS) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr < 0 || nr >= size || nc < 0 || nc >= size) {
                continue;
            }
            int cell = board[nr * size + nc];
            if (cell == myColor) {
                friendly++;
            } else if (cell == opponent) {
                enemy++;
            }
        }

        for (int[] offset : BRIDGE_OFFSETS) {
            int nr = row + offset[0];
            int nc = col + offset[1];
            if (nr >= 0 && nr < size && nc >= 0 && nc < size && board[nr * size + nc] == myColor) {
                bridges++;
            }
        }

        double score = 2.5 * (size - (Math.abs(row - center) + Math.abs(col - center)));
        score += 14.0 * friendly + 5.0 * enemy + 6.0 * bridges;
        if (myColor == RED) {
            score += 3.0 * (size - Math.abs(col - center));
        } else {
            score += 3.0 * (size - Math.abs(row - center));
        }
        if (friendly >= 2) {
            score += 18.0;
        }
        if (friendly >= 3) {
            score += 12.0;
        }
        if (enemy >= 2) {
            score += 10.0;
        }
        if (friendly == 0 && enemy == 0) {
            score -= 20.0;
        }

        for (int i = 0; i < myPath.emptyCount; i++) {
            if (myPath.empties[i] == idx) {
                score += 80.0 - 7.0 * myPath.distance;
                break;
            }
        }

        for (int i = 0; i < oppPath.emptyCount; i++) {
            if (oppPath.empties[i] == idx) {
                score += 50.0 - 5.0 * oppPath.distance;
                break;
            }
        }

        return score;
    }
}
