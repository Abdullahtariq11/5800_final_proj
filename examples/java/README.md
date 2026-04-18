# CS 5800 Final Project - Hex Agent - Final Code Submission

**Group Members:** 
- Abdullah Tariq
- Chendong Yu

**Main Features & Optimizations:**

Our final version incorporates a highly optimized time-bounded Monte Carlo Tree Search guided by UCB1 featuring the following features:

- **Dijkstra-Based Shortest Path & Heuristic Filtering**: Utilizes a Dijkstra-style BFS and heuristic functions to find the cheapest connection path.

- **Zero-GC Rollouts & 1D Flat Board**: Flattens the board into a 1D integer array in order to strictly adhere to the 64MB memory limit.

- **Dynamic Time-Bounding**: Tracks elapsed time via System.currentTimeMillis() and scales its internal time limits safely below the framework's strict cut-offs.

- **First-Turn Swap Rule**: Analyzes RED's opening move and invokes the swap rule to get CENTRAL position if RED opens within a strict Manhattan radius of the board's center.
---

## Table of Contents
1. [Introduction](#1-introduction)
2. [Game Rules](#2-game-rules)
3. [Quick Start](#3-quick-start)
4. [Communication Protocol](#4-communication-protocol)
5. [Testing Your Agent](#5-testing-your-agent)
6. [Constraints & Limits](#6-constraints--limits)
7. [Debugging Tips](#7-debugging-tips)
8. [Testing](#8-testing)

---

## 1. Introduction

### What is Hex?

Hex is a two-player abstract strategy game played on a rhombus-shaped hexagonal grid. The game was independently invented by Piet Hein and John Nash in the 1940s.

In this project, you'll develop an AI agent to play Hex competitively. 

**Key Features:**
- Simple rules, complex strategy
- No possibility of a draw
- Pure strategy (no luck element)
- Deep algorithmic challenges

---

## 2. Game Rules

### Board and Players

- The game is played on an **N×N hexagonal grid** (typically 11×11)
- Two players: **RED** and **BLUE**
- Players alternate turns placing their colored stones on empty cells
- RED always moves first

### Objective

Each player aims to connect their opposite sides of the board:
- **RED** must connect the **top edge** to the **bottom edge**
- **BLUE** must connect the **left edge** to the **right edge**

### Winning Condition

A player wins by creating a continuous chain of their stones connecting their two sides. The chain can wind and twist, but must be unbroken.

**Important:** Hex has a mathematical property that guarantees no draw is possible. One player must eventually win.

### Coordinates and Board Representation

**Important:** Even though Hex is played on a hexagonal grid, the coordinate system uses standard **matrix-style (row, column) indexing**.

The board uses **0-indexed coordinates**:
- Rows are numbered `0` to `N-1` (top to bottom)
- Columns are numbered `0` to `N-1` (left to right)
- Position `(0, 0)` is the top-left corner
- Position `(N-1, N-1)` is the bottom-right corner

**Think of it like a 2D array:** `board[row][col]`

**The "hexagonal" part is in the connectivity, not the coordinates:**
- Each cell at `(row, col)` has up to **6 neighbors** (not 4 or 8 like a square grid)
- The neighbors are at positions:
  - `(row-1, col)` - top
  - `(row-1, col+1)` - top-right
  - `(row, col+1)` - right
  - `(row+1, col)` - bottom
  - `(row+1, col-1)` - bottom-left
  - `(row, col-1)` - left

**Example for a 7×7 board:**
```
       0  1  2  3  4  5  6   <- columns
    0  .  .  .  .  .  .  .
     1  .  .  .  .  .  .  .
      2  .  .  .  B  .  .  .   <- B at (2,3) is row 2, col 3
       3  .  .  .  .  .  .  .
        4  .  .  .  .  .  .  .
         5  .  .  .  .  .  .  .
          6  .  .  .  .  .  .  .
          ^
        rows
```

**The 6 neighbors of B at (2,3):**
- `(1, 3)` - top: row 2-1=1, col 3
- `(1, 4)` - top-right: row 2-1=1, col 3+1=4
- `(2, 4)` - right: row 2, col 3+1=4
- `(3, 3)` - bottom: row 2+1=3, col 3
- `(3, 2)` - bottom-left: row 2+1=3, col 3-1=2
- `(2, 2)` - left: row 2, col 3-1=2



### Valid Moves

On your turn, you must place a stone on any **empty cell** by specifying its row and column.

**Invalid moves result in automatic forfeiture:**
- Placing a stone on an occupied cell
- Placing a stone out of bounds
- Improperly formatted output
- Exceeding time or memory limits

### The Swap Rule

To balance the first-player advantage, Hex includes an optional **swap rule**:

- **Only the second player (BLUE)** may invoke the swap rule, and only on their first turn (turn 2 of the game).
- Instead of placing a new stone, BLUE outputs `swap`.
- **What happens:** RED's existing stone at position `(r, c)` is removed, and a BLUE stone is placed at the mirrored position `(c, r)` (row and column are transposed).
- **Colors do not change.** Each player keeps their original color. After the swap, it is RED's turn to move.

**Why mirror?** 

The Hex board has a diagonal symmetry. A strong opening move for RED at `(r, c)` corresponds to an equally strong position for BLUE at `(c, r)`. The swap rule exploits this symmetry — if RED's first move is too strong, BLUE can claim its mirror and force RED to play a different strategy.

**Example:**
```
Turn 1: RED plays (3, 4)
Turn 2: BLUE outputs "swap"
Result: Now instead a RED stone at (3, 4), there is a BLUE stone at (4, 3), and the next turn is RED.
```

## 3. Quick Start

### System Requirements

**This framework is designed for and only supports Linux systems.**

### Prerequisites

1. **Python 3.x** (for running the framework)
   ```bash
   python3 --version
   ```

2. **tkinter** (for GUI):
   ```bash
   sudo apt-get install python3-tk
   ```

3. **Language-specific tools**:
   - **Java**: JDK 8 or higher

---

## 4. Communication Protocol

You can implement your agent in any of these languages: python3, c, c++, java. To support different languages, your agent communicates with the game framework through **stdin** and **stdout**. The protocol is simple: one line in, one line out.

### Input Format (What Your Agent Receives)

Each turn, your agent receives a **single line** describing the current board state:

```
<SIZE> <YOUR_COLOR> <MOVES>
```

**Fields:**
1. `<SIZE>`: Board size (integer, e.g., `11`)
2. `<YOUR_COLOR>`: Your color - either `RED` or `BLUE`
3. `<MOVES>`: Comma-separated list of existing moves (format: `row:col:color`)

**Examples:**

```
11 RED 
```
*Empty board, you are RED, it's your first move*

```
11 BLUE 5:5:R
```
*11×11 board, you are BLUE, RED has played at (5,5)*

```
11 RED 5:5:B,6:6:R,7:7:B
```
*You are RED, three moves have been played*

```
19 BLUE 9:9:R,10:10:B,8:9:R
```
*19×19 board, you are BLUE, three moves on the board*

### Output Format (What Your Agent Must Send)

Your agent must output a **single line** with your move:

```
<ROW> <COL>
```

or

```
swap
```

**Examples:**

```
5 7
```
*Place your stone at row 5, column 7*

```
10 3
```
*Place your stone at row 10, column 3*

```
swap
```
*Invoke the swap rule (BLUE's first turn only)*

### Critical Implementation Details

#### 1. Flush stdout After Every Output

**This is essential!** The framework reads from your stdout line by line. If you don't flush, your move will be buffered and never sent.

**Java:**
```java
System.out.println(row + " " + col);
System.out.flush();  // REQUIRED!
```

#### 2. Use a Loop to Handle Multiple Moves

Your agent will receive multiple turns in one game. Use a loop:

```python
while True:
    line = input()
    # Parse and make move
    print(f"{row} {col}")
    sys.stdout.flush()
```

#### 3. Parsing the Input

See the example agents for complete parsing code. Basic approach:

```python
parts = line.strip().split(maxsplit=2)
size = int(parts[0])
my_color = parts[1]  # "RED" or "BLUE"

# Parse moves
board = {}
if len(parts) == 3 and parts[2]:
    for move in parts[2].split(','):
        row, col, color = move.split(':')
        board[(int(row), int(col))] = color
```

### Protocol Flow

```
Framework ─────> Your Agent: "11 RED "
Your Agent ─────> Framework: "5 5"

Framework ─────> Your Agent: "11 BLUE 5:5:R"
Your Agent ─────> Framework: "6 6"

Framework ─────> Your Agent: "11 RED 5:5:R,6:6:B"
Your Agent ─────> Framework: "7 7"
```

### Common Protocol Mistakes

❌ **Forgetting to flush stdout** → Timeout  
❌ **Wrong output format** → Invalid move (forfeit)  
❌ **Printing debug info to stdout** → Protocol error (use stderr!)  
❌ **Not handling the loop** → Only plays one move  
❌ **Zero-based vs one-based indexing confusion** → Out of bounds  

---

## 5. Testing Your Agent

### GUI Mode (Recommended)

The GUI provides visual feedback and is the best way to develop and test your agent.

**Basic Command Structure:**

```bash
python3 gui_main.py --red-subprocess "COMMAND" --blue-subprocess "COMMAND" [other flags]
```

- Replace `"COMMAND"` with your agent's execution command (e.g., `"python3 my_agent.py"`)
- Use `--red-subprocess` to make RED player a subprocess agent
- Use `--blue-subprocess` to make BLUE player a subprocess agent
- If a subprocess flag is not provided, that player will be **human** (interact with the GUI by clicking)
- Add other flags like `--board-size`, `--timeout`, `--memory-limit` as needed (see details below)


**Run Command Examples:**

Before testing your agent, you may need to compile it first:

**Java:**
- First compile to create `.class` file:
```bash
javac examples/java/HexAgent.java
```
- Then run:
```bash
python3 gui_main.py --blue-subprocess "java -cp ./examples/java HexAgent"
```

**Command-Line Options:**

| Option | Description | Default |
|--------|-------------|---------|
| `--board-size N` | Set board size (3-26) | 11 |
| `--timeout SECONDS` | Time limit per move (overrides auto-selection) | Auto (based on board size) |
| `--memory-limit MB` | Memory limit in MB | 64 |
| `--red-subprocess "CMD"` | Command to run RED agent | Human (GUI) |
| `--blue-subprocess "CMD"` | Command to run BLUE agent | Human (GUI) |
| `--red-name "NAME"` | Display name for RED | "Red Player" |
| `--blue-name "NAME"` | Display name for BLUE | "Blue Player" |



### Terminal Mode (Alternative)

Terminal mode provides a text-based interface for testing.

**Usage:**

Commands are similar to GUI mode, but use `terminal_main.py` instead of `gui_main.py`:

```bash
python3 terminal_main.py \
    --blue-subprocess "python3 examples/python/HexAgent.py"
```

All flags (`--board-size`, `--timeout`, `--memory-limit`, `--red-subprocess`, `--blue-subprocess`, etc.) work the same as in GUI mode.

**Additional Terminal-Specific Flags:**

| Option | Description |
|--------|-------------|
| `--no-stats` | Hide player statistics during game |
| `--show-full-log` | Show complete event log at end of game |
| `--show-move-history` | Show complete move history at end of game |

---

## 6. Constraints & Limits

The framework automatically applies different time limits based on board size. When you run the game with a specific board size, the appropriate time limit is automatically enforced.

**Automatic Time Limits:**
| Board Size | Time limit |
| --- | --- |
11×11 | 150 ms
15×15 | 200 ms
19×19 | 250 ms
21×21 | 300 ms

**Note:** For board sizes not listed above, the framework uses a default timeout of 1.0 second.

**Memory Limit:**
All sizes: 64 MB (configurable with `--memory-limit` flag)

**Testing your agent:** Make sure your algorithm can work efficiently under different board sizes and their corresponding time/memory constraints.

---

## 7. Debugging Tips

### Using stderr for Debug Output

**Never print to stdout except for your move!** Stdout is for protocol communication only.

Use **stderr** for all debug output:

**Java:**
```java
System.err.println("DEBUG: Evaluating position " + row + "," + col);
System.err.flush();
```

## 8. Testing
 
Build `HexAgent` before running the tests:
 
```bash
javac examples/java/HexAgent.java
```

Run the `HexAgent` test suite:

```bash
python3 -m unittest tests.test_hex_agent
```

### What The Tests Cover

The `HexAgent` tests focus mainly on framework-related constraints rather than proving perfect gameplay. In particular, they check:
- time-limit compliance
- memory-limit compliance
- correct subprocess execution through the game framework

The tests cover the official project board sizes `11`, `15`, `19`, and `21` for resource-sensitive scenarios, and also include a few deterministic tactical checks:
- swap against a strong central opening
- immediate winning move
- immediate blocking move