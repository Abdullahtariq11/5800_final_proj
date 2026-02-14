#!/usr/bin/env python3
"""
Demo script to showcase the protocol functionality.
"""

from engine.protocol import Protocol, ProtocolHelper, ProtocolError
from engine.board import HexBoard
from engine.constants import Color, GameStatus, MoveResult


def demo_basic_messages():
    """Demonstrate basic protocol messages."""
    print("=" * 60)
    print("DEMO: Basic Protocol Messages")
    print("=" * 60)

    # INIT message
    print("\n1. INIT Message:")
    msg = Protocol.encode_init(Color.RED, 11)
    print(f"   Encoded: {msg.strip()}")

    # MOVE request
    print("\n2. MOVE Request:")
    msg = Protocol.encode_move_request()
    print(f"   Encoded: {msg.strip()}")

    # RESULT message
    print("\n3. RESULT Message:")
    msg = Protocol.encode_result(MoveResult.SUCCESS)
    print(f"   Success: {msg.strip()}")
    msg = Protocol.encode_result(
        MoveResult.CELL_OCCUPIED, "Cell already taken")
    print(f"   Error: {msg.strip()}")

    # END message
    print("\n4. END Message:")
    msg = Protocol.encode_end(GameStatus.RED_WIN, Color.RED)
    print(f"   Encoded: {msg.strip()}")


def demo_state_encoding():
    """Demonstrate STATE message with board."""
    print("\n" + "=" * 60)
    print("DEMO: STATE Message Encoding")
    print("=" * 60)

    # Empty board
    board = HexBoard(5)
    msg = Protocol.encode_state(board, Color.RED)
    print("\nEmpty board STATE:")
    print(f"  {msg.strip()}")

    # Board with moves
    board.make_move(0, 0, Color.RED)
    board.make_move(1, 1, Color.BLUE)
    board.make_move(2, 2, Color.RED)

    print("\nBoard with moves:")
    print(board)

    msg = Protocol.encode_state(board, Color.BLUE)
    print(f"\nSTATE message:")
    print(f"  {msg.strip()}")


def demo_move_parsing():
    """Demonstrate move parsing."""
    print("\n" + "=" * 60)
    print("DEMO: Move Parsing")
    print("=" * 60)

    test_moves = [
        "5 7",
        "3,4",
        "(2,6)",
        "  1 , 2  ",
    ]

    print("\nParsing various move formats:")
    for move_str in test_moves:
        try:
            row, col = Protocol.decode_move(move_str)
            print(f"  '{move_str}' -> ({row}, {col}) ✓")
        except ProtocolError as e:
            print(f"  '{move_str}' -> ERROR: {e}")

    # Invalid moves
    print("\nInvalid moves (should fail):")
    invalid_moves = ["5", "abc def", "", "1 2 3"]
    for move_str in invalid_moves:
        try:
            row, col = Protocol.decode_move(move_str)
            print(f"  '{move_str}' -> ({row}, {col}) ✗ Should have failed!")
        except ProtocolError as e:
            print(f"  '{move_str}' -> Correctly rejected ✓")


def demo_roundtrip():
    """Demonstrate encode/decode roundtrip."""
    print("\n" + "=" * 60)
    print("DEMO: Encode/Decode Round-Trip")
    print("=" * 60)

    # Create original board
    print("\nOriginal board:")
    original = HexBoard(7)
    original.make_move(0, 0, Color.RED)
    original.make_move(1, 1, Color.BLUE)
    original.make_move(2, 2, Color.RED)
    original.make_move(3, 3, Color.BLUE)
    print(original)

    # Encode to protocol
    msg = Protocol.encode_state(original, Color.RED)
    print(f"\nEncoded STATE:")
    print(f"  {msg.strip()}")

    # Decode back
    reconstructed, player = ProtocolHelper.board_from_state(msg.strip())
    print(f"\nReconstructed board (current player: {player.name}):")
    print(reconstructed)

    # Verify
    print("\nVerification:")
    print(f"  Size match: {reconstructed.size == original.size} ✓")
    print(
        f"  Move count: {reconstructed.get_move_count()} == {original.get_move_count()} ✓")
    print(f"  (0,0) RED: {reconstructed.get_cell(0, 0) == Color.RED} ✓")
    print(f"  (1,1) BLUE: {reconstructed.get_cell(1, 1) == Color.BLUE} ✓")


def demo_game_session():
    """Demonstrate a complete game session protocol."""
    print("\n" + "=" * 60)
    print("DEMO: Complete Game Session Protocol")
    print("=" * 60)

    print("\nSimulated game between RED and BLUE:")
    print("-" * 60)

    # Initialize
    board = HexBoard(5)

    # Game starts - RED's turn
    print("\n[Engine -> RED] " + Protocol.encode_init(Color.RED, 5).strip())
    print("[Engine -> RED] " + Protocol.encode_state(board, Color.RED).strip())
    print("[Engine -> RED] " + Protocol.encode_move_request().strip())
    print("[RED -> Engine] 2 2")

    # RED moves
    board.make_move(2, 2, Color.RED)
    print("[Engine -> RED] " + Protocol.encode_result(MoveResult.SUCCESS).strip())

    # BLUE's turn
    print("\n[Engine -> BLUE] " + Protocol.encode_init(Color.BLUE, 5).strip())
    print("[Engine -> BLUE] " + Protocol.encode_state(board, Color.BLUE).strip())
    print("[Engine -> BLUE] " + Protocol.encode_move_request().strip())
    print("[BLUE -> Engine] 2 3")

    # BLUE moves
    board.make_move(2, 3, Color.BLUE)
    print("[Engine -> BLUE] " + Protocol.encode_result(MoveResult.SUCCESS).strip())

    # Continue game...
    print("\n... game continues ...")

    # Simulate RED winning
    for row in range(5):
        if board.is_empty(row, 2):
            board.make_move(row, 2, Color.RED)

    winner = board.get_winner()
    print(f"\n[Engine -> ALL] " +
          Protocol.encode_end(GameStatus.RED_WIN, winner).strip())

    print("\nFinal board:")
    print(board)


def demo_error_handling():
    """Demonstrate error handling."""
    print("\n" + "=" * 60)
    print("DEMO: Error Handling")
    print("=" * 60)

    board = HexBoard(5)
    board.make_move(2, 2, Color.RED)

    print("\nAttempting invalid moves:")

    # Try to move on occupied cell
    print("\n1. Move on occupied cell (2, 2):")
    print("[Player -> Engine] 2 2")
    result = board.make_move(2, 2, Color.BLUE)
    msg = Protocol.encode_result(result, "Cell already occupied by RED")
    print(f"[Engine -> Player] {msg.strip()}")

    # Out of bounds
    print("\n2. Out of bounds move (10, 10):")
    print("[Player -> Engine] 10 10")
    result = board.make_move(10, 10, Color.BLUE)
    msg = Protocol.encode_result(result, "Position outside board")
    print(f"[Engine -> Player] {msg.strip()}")

    # Invalid format
    print("\n3. Invalid move format:")
    print("[Player -> Engine] abc def")
    try:
        Protocol.decode_move("abc def")
    except ProtocolError as e:
        print(f"[Engine] Parse error: {e}")


def main():
    """Run all protocol demos."""
    print("\n" + "🔌" * 30)
    print("HEX GAME PROTOCOL DEMONSTRATION")
    print("🔌" * 30)

    demo_basic_messages()
    demo_state_encoding()
    demo_move_parsing()
    demo_roundtrip()
    demo_game_session()
    demo_error_handling()

    print("\n" + "=" * 60)
    print("✅ All protocol demos completed successfully!")
    print("=" * 60)


if __name__ == '__main__':
    main()
