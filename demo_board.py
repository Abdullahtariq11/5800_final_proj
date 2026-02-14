#!/usr/bin/env python3
"""
Demo script to showcase the Hex board functionality.
"""

from engine.board import HexBoard
from engine.constants import Color, MoveResult


def demo_basic_game():
    """Demonstrate a simple game with RED winning."""
    print("=" * 50)
    print("DEMO: Basic Hex Game (RED wins)")
    print("=" * 50)

    board = HexBoard(5)
    print("\nInitial empty board:")
    print(board)

    # RED creates a vertical path from top to bottom
    red_moves = [(0, 2), (1, 2), (2, 2), (3, 2), (4, 2)]

    # BLUE tries to block horizontally but doesn't complete
    blue_moves = [(1, 0), (1, 1), (2, 3)]

    print("\nPlaying moves...")
    for i in range(max(len(red_moves), len(blue_moves))):
        if i < len(red_moves):
            row, col = red_moves[i]
            result = board.make_move(row, col, Color.RED)
            print(f"RED plays ({row}, {col}): {result}")

        if i < len(blue_moves):
            row, col = blue_moves[i]
            result = board.make_move(row, col, Color.BLUE)
            print(f"BLUE plays ({row}, {col}): {result}")

    print("\nFinal board:")
    print(board)

    winner = board.get_winner()
    if winner:
        print(f"\n🎉 Winner: {winner}!")
    else:
        print("\n📊 Game still in progress")

    print(f"Total moves: {board.get_move_count()}")


def demo_blue_win():
    """Demonstrate BLUE winning horizontally."""
    print("\n" + "=" * 50)
    print("DEMO: BLUE wins by connecting left to right")
    print("=" * 50)

    board = HexBoard(5)

    # BLUE creates horizontal path
    blue_moves = [(2, 0), (2, 1), (2, 2), (2, 3), (2, 4)]

    # RED tries but can't stop BLUE
    red_moves = [(0, 0), (1, 1), (3, 3)]

    print("\nPlaying moves...")
    for i in range(max(len(red_moves), len(blue_moves))):
        if i < len(blue_moves):
            row, col = blue_moves[i]
            result = board.make_move(row, col, Color.BLUE)
            print(f"BLUE plays ({row}, {col}): {result}")

        if i < len(red_moves):
            row, col = red_moves[i]
            result = board.make_move(row, col, Color.RED)
            print(f"RED plays ({row}, {col}): {result}")

    print("\nFinal board:")
    print(board)

    winner = board.get_winner()
    if winner:
        print(f"\n🎉 Winner: {winner}!")
    else:
        print("\n📊 Game still in progress")


def demo_invalid_moves():
    """Demonstrate move validation."""
    print("\n" + "=" * 50)
    print("DEMO: Move Validation")
    print("=" * 50)

    board = HexBoard(5)

    # Valid move
    result = board.make_move(2, 2, Color.RED)
    print(f"\nValid move (2, 2): {result}")

    # Try to occupy same cell
    result = board.make_move(2, 2, Color.BLUE)
    print(f"Occupied cell (2, 2): {result}")

    # Out of bounds
    result = board.make_move(10, 10, Color.RED)
    print(f"Out of bounds (10, 10): {result}")

    # Negative coordinates
    result = board.make_move(-1, 0, Color.BLUE)
    print(f"Negative coords (-1, 0): {result}")

    print("\nCurrent board:")
    print(board)


def demo_board_features():
    """Demonstrate various board features."""
    print("\n" + "=" * 50)
    print("DEMO: Board Features")
    print("=" * 50)

    board = HexBoard(5)

    # Make some moves
    board.make_move(0, 0, Color.RED)
    board.make_move(1, 1, Color.BLUE)
    board.make_move(2, 2, Color.RED)

    print("\nBoard after 3 moves:")
    print(board)

    print(f"\nMove count: {board.get_move_count()}")
    print(f"Empty cells remaining: {len(board.get_empty_cells())}")
    print(f"Board is full: {board.is_full()}")

    # Test neighbors
    neighbors = board.get_neighbors(2, 2)
    print(f"\nNeighbors of (2, 2): {neighbors}")
    print(f"Number of neighbors: {len(neighbors)}")

    # Test clone
    cloned = board.clone()
    cloned.make_move(3, 3, Color.BLUE)

    print(f"\nOriginal board moves: {board.get_move_count()}")
    print(f"Cloned board moves: {cloned.get_move_count()}")
    print("✓ Clone is independent")

    # Undo feature
    print(f"\nBefore undo - cell (2,2): {board.get_cell(2, 2)}")
    board.undo_move()
    print(f"After undo - cell (2,2): {board.get_cell(2, 2)}")
    print(f"Moves after undo: {board.get_move_count()}")


def main():
    """Run all demos."""
    print("\n" + "🎮" * 25)
    print("HEX GAME BOARD DEMONSTRATION")
    print("🎮" * 25)

    demo_basic_game()
    demo_blue_win()
    demo_invalid_moves()
    demo_board_features()

    print("\n" + "=" * 50)
    print("✅ All demos completed successfully!")
    print("=" * 50)


if __name__ == '__main__':
    main()
