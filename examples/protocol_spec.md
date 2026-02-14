# Hex Game Protocol Specification

## Overview

This document describes the communication protocol between the game engine and your AI agent. Your agent will receive game state through **stdin** and send moves through **stdout**. All communication is line-based text.

## Protocol Messages

### 1. INIT - Game Initialization

**Direction:** Engine → Agent  
**Format:** `INIT <COLOR> <SIZE>`  
**Example:** `INIT RED 11`

This is the first message you receive. It tells you:
- Your color (RED or BLUE)
- Board size (typically 11×11)

**What you need to do:** Store this information. RED connects top-to-bottom, BLUE connects left-to-right.

---

### 2. STATE - Board State Update

**Direction:** Engine → Agent  
**Format:** `STATE <SIZE> <CURRENT_PLAYER> <MOVES>`  
**Example:** `STATE 11 RED 0:0:R,1:1:B,2:2:R`

This message provides the current board state:
- `SIZE`: Board dimensions
- `CURRENT_PLAYER`: Whose turn it is (RED or BLUE)
- `MOVES`: Comma-separated list of existing moves in format `row:col:color`
  - `R` = RED stone
  - `B` = BLUE stone
  - Empty if no moves yet

**Example:**
```
STATE 11 RED                    # Empty board, RED's turn
STATE 11 BLUE 0:0:R,1:1:B       # 2 moves, BLUE's turn
```

---

### 3. MOVE - Move Request

**Direction:** Engine → Agent  
**Format:** `MOVE`

The engine is requesting your next move.

**What you need to do:** Calculate your move and respond with coordinates (see below).

---

### 4. Agent Move Response

**Direction:** Agent → Engine  
**Format:** `<ROW> <COL>`  
**Example:** `5 7`

When you receive a `MOVE` request, respond with your chosen coordinates.

**Accepted formats:**
- Space-separated: `5 7`
- Comma-separated: `5,7`
- With parentheses: `(5,7)`

**Important:**
- Coordinates are 0-indexed
- `row` ranges from 0 to SIZE-1
- `col` ranges from 0 to SIZE-1
- Cell must be unoccupied
- Print to stdout and flush immediately

---

### 5. RESULT - Move Result (Optional)

**Direction:** Engine → Agent  
**Format:** `RESULT <STATUS> [MESSAGE]`  
**Example:** `RESULT success`

The engine may send feedback about your move:
- `success` - Move accepted
- `cell_occupied` - Cell already has a stone
- `out_of_bounds` - Coordinates outside board
- `invalid_format` - Could not parse your move

---

### 6. END - Game Over

**Direction:** Engine → Agent  
**Format:** `END <STATUS> [WINNER]`  
**Example:** `END red_win RED`

Game has ended. Possible statuses:
- `red_win` - RED player won
- `blue_win` - BLUE player won
- `ongoing` - Game was terminated early
- `error` - An error occurred

**What you need to do:** Exit gracefully.

---

## Example Game Session

```
Engine: INIT RED 11
Engine: STATE 11 RED 
Engine: MOVE
Agent:  5 5
Engine: RESULT success

Engine: STATE 11 BLUE 5:5:R
Engine: MOVE
Agent:  5 6
Engine: RESULT success

Engine: STATE 11 RED 5:5:R,5:6:B
Engine: MOVE
Agent:  4 5
Engine: RESULT success

... game continues ...

Engine: END red_win RED
```

---

## Implementation Guidelines

### Python Example

```python
import sys

def main():
    # Read initialization
    init_line = input().strip()
    parts = init_line.split()
    color = parts[1]  # "RED" or "BLUE"
    size = int(parts[2])
    
    while True:
        # Read next message
        line = input().strip()
        
        if line.startswith("STATE"):
            # Parse board state
            parts = line.split(maxsplit=3)
            current_player = parts[2]
            moves_str = parts[3] if len(parts) > 3 else ""
            # ... parse moves if needed ...
        
        elif line == "MOVE":
            # Calculate your move
            row, col = calculate_move()
            
            # Send move (IMPORTANT: flush!)
            print(f"{row} {col}")
            sys.stdout.flush()
        
        elif line.startswith("END"):
            # Game over
            break

if __name__ == "__main__":
    main()
```

### Java Example

```java
import java.util.Scanner;

public class HexAgent {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        // Read initialization
        String initLine = scanner.nextLine();
        String[] parts = initLine.split(" ");
        String color = parts[1];  // "RED" or "BLUE"
        int size = Integer.parseInt(parts[2]);
        
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            
            if (line.startsWith("STATE")) {
                // Parse board state
                // ...
            }
            else if (line.equals("MOVE")) {
                // Calculate move
                int row = calculateRow();
                int col = calculateCol();
                
                // Send move (IMPORTANT: flush!)
                System.out.println(row + " " + col);
                System.out.flush();
            }
            else if (line.startsWith("END")) {
                break;
            }
        }
        
        scanner.close();
    }
}
```

### C++ Example

```cpp
#include <iostream>
#include <string>
#include <sstream>

int main() {
    std::string line;
    
    // Read initialization
    std::getline(std::cin, line);
    std::istringstream iss(line);
    std::string cmd, color;
    int size;
    iss >> cmd >> color >> size;
    
    while (std::getline(std::cin, line)) {
        if (line.rfind("STATE", 0) == 0) {
            // Parse board state
            // ...
        }
        else if (line == "MOVE") {
            // Calculate move
            int row = calculateRow();
            int col = calculateCol();
            
            // Send move (IMPORTANT: flush!)
            std::cout << row << " " << col << std::endl;
            std::cout.flush();
        }
        else if (line.rfind("END", 0) == 0) {
            break;
        }
    }
    
    return 0;
}
```

---

## Important Notes

### ⚠️ Critical Requirements

1. **Always flush stdout** after printing your move
   - Python: `sys.stdout.flush()`
   - Java: `System.out.flush()`
   - C++: `std::cout.flush()` or use `std::endl`

2. **Time limits:** You typically have 5 seconds per move
   - Wall clock time is enforced
   - Make sure your algorithm terminates quickly

3. **Memory limits:** Usually 512 MB
   - Don't create excessive data structures
   - Clean up resources appropriately

4. **No stderr output during gameplay**
   - stderr is reserved for debugging
   - Excessive output may cause disqualification

5. **Exit cleanly** when you receive END message
   - Don't leave zombie processes

### Board Coordinate System

```
     A B C D E  (columns 0-4)
   0 . . . . .
   1  . . . . .    <- Note the hexagonal offset
   2   . . . . .
   3    . . . . .
   4     . . . . .
 (rows)
```

- RED wins by connecting row 0 to row (SIZE-1)
- BLUE wins by connecting column 0 to column (SIZE-1)
- Each cell has 6 neighbors (except edges/corners)

### Hex Neighbor Directions

From position (r, c), your neighbors are:
- (r-1, c)   - top
- (r-1, c+1) - top-right
- (r, c+1)   - right
- (r+1, c)   - bottom
- (r+1, c-1) - bottom-left
- (r, c-1)   - left

### Testing Your Agent

Test your agent with the provided tools:
```bash
# Play against random AI
python scripts/play.py --p1 your_agent.py --p2 random

# Play against yourself
python scripts/play.py --p1 your_agent.py --p2 your_agent.py

# Run tournament
python scripts/grade.py --submissions ./submissions/ --rounds 10
```

---

## Troubleshooting

### Common Issues

**"Timeout" error:**
- Your agent takes too long to respond
- Optimize your algorithm
- Make sure you're flushing stdout

**"Invalid move" error:**
- Cell is already occupied
- Coordinates out of bounds
- Check your move validation

**"Parse error":**
- Invalid output format
- Make sure to output just `<row> <col>`
- Don't print debug messages to stdout

**Agent doesn't receive messages:**
- Input buffering issue
- Make sure stdin isn't buffered

---

## FAQ

**Q: Can I parse the STATE message to reconstruct the board?**  
A: Yes, you should maintain your own board representation based on STATE messages.

**Q: Do I get STATE before every MOVE?**  
A: Yes, you'll always receive an updated STATE before a MOVE request.

**Q: Can I store data between moves?**  
A: Yes, your process stays alive for the entire game. You can maintain internal state.

**Q: What if my agent crashes?**  
A: The game ends and you lose. Make sure your code handles all edge cases.

**Q: Can I use random numbers?**  
A: Yes, but make your decisions within the time limit.

**Q: How do I debug?**  
A: Print debug info to stderr (not stdout). Test locally with the play script.

---

## Submission Requirements

1. **File structure:**
   ```
   your_name/
   ├── main.py     (or Main.java, or main.cpp)
   ├── Makefile    (optional, for compilation)
   └── README.md   (optional, strategy explanation)
   ```

2. **Execution:**
   - Python: `python3 main.py`
   - Java: `java Main` (after compilation)
   - C++: `./main` (after compilation)

3. **Dependencies:**
   - Keep dependencies minimal
   - Standard library only (no external packages)

---

## Good Luck! 🎮

Remember:
- ✅ Flush stdout after every move
- ✅ Respond within time limit
- ✅ Validate your moves
- ✅ Test thoroughly before submission
- ✅ Handle edge cases gracefully

