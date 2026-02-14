"""
Unit tests for engine protocol.

Tests only what the ENGINE needs:
- encode_board: sending board state to students
- decode_move: parsing student move responses
"""

from engine.constants import Color
from engine.board import HexBoard
from engine.protocol import Protocol, ProtocolError
import pytest
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))


class TestProtocolEncoding:
    """Test encoding board state for students."""

    def test_encode_board_empty(self):
        """Test encoding empty board."""
        board = HexBoard(11)
        msg = Protocol.encode_board(board, Color.RED)
        assert msg == "11 RED \n"

    def test_encode_board_with_moves(self):
        """Test encoding board with moves."""
        board = HexBoard(11)
        board.make_move(5, 5, Color.BLUE)
        board.make_move(6, 6, Color.RED)

        msg = Protocol.encode_board(board, Color.RED)
        assert msg.startswith("11 RED ")
        assert "5:5:B" in msg
        assert "6:6:R" in msg

    def test_encode_board_blue_player(self):
        """Test encoding for BLUE player."""
        board = HexBoard(5)
        board.make_move(0, 0, Color.RED)
        msg = Protocol.encode_board(board, Color.BLUE)
        assert msg.startswith("5 BLUE ")
        assert "0:0:R" in msg

    def test_encode_board_small_board(self):
        """Test with small board."""
        board = HexBoard(5)
        board.make_move(0, 0, Color.RED)
        msg = Protocol.encode_board(board, Color.BLUE)
        assert "5 BLUE" in msg
        assert "0:0:R" in msg

    def test_encode_board_large_board(self):
        """Test with larger board."""
        board = HexBoard(19)
        board.make_move(18, 18, Color.BLUE)
        msg = Protocol.encode_board(board, Color.RED)
        assert "19 RED" in msg
        assert "18:18:B" in msg

    def test_encode_board_multiple_moves(self):
        """Test encoding many moves."""
        board = HexBoard(11)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 1, Color.BLUE)
        board.make_move(2, 2, Color.RED)
        board.make_move(3, 3, Color.BLUE)

        msg = Protocol.encode_board(board, Color.RED)
        assert "0:0:R" in msg
        assert "1:1:B" in msg
        assert "2:2:R" in msg
        assert "3:3:B" in msg


class TestMoveDecoding:
    """Test parsing student move responses."""

    def test_decode_move_valid(self):
        """Test decoding valid move."""
        row, col = Protocol.decode_move("5 7")
        assert row == 5
        assert col == 7

    def test_decode_move_with_whitespace(self):
        """Test decoding move with extra whitespace."""
        row, col = Protocol.decode_move("  5   7  ")
        assert row == 5
        assert col == 7

    def test_decode_move_zero_coordinates(self):
        """Test decoding (0, 0)."""
        row, col = Protocol.decode_move("0 0")
        assert row == 0
        assert col == 0

    def test_decode_move_invalid_format(self):
        """Test invalid move format raises error."""
        with pytest.raises(ProtocolError):
            Protocol.decode_move("5")  # Only one coordinate

        with pytest.raises(ProtocolError):
            Protocol.decode_move("5 7 8")  # Three coordinates

        with pytest.raises(ProtocolError):
            Protocol.decode_move("5,7")  # Comma-separated not accepted

        with pytest.raises(ProtocolError):
            Protocol.decode_move("(5,7)")  # Parentheses not accepted

    def test_decode_move_invalid_values(self):
        """Test non-integer values raise error."""
        with pytest.raises(ProtocolError):
            Protocol.decode_move("a b")

    def test_decode_move_empty(self):
        """Test empty move raises error."""
        with pytest.raises(ProtocolError):
            Protocol.decode_move("")
