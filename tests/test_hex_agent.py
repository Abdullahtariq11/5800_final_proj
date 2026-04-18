"""
Integration tests for the final Java Hex agent implementation.
"""

import shutil
import subprocess
import unittest
from pathlib import Path

from engine.board import HexBoard
from engine.constants import Color, get_timeout_for_board_size
from players.subprocess_player import SubprocessPlayer


class TestHexAgent(unittest.TestCase):
    """Checks that HexAgent stays within limits and handles core tactics."""

    ROOT = Path(__file__).resolve().parents[1]
    JAVA_SOURCE = ROOT / "examples" / "java" / "HexAgent.java"
    JAVA_CLASS_DIR = ROOT / "examples" / "java"
    JAVA_CLASS_NAME = "HexAgent"
    BOARD_SIZES = (11, 15, 19, 21)
    MEMORY_LIMIT_MB = 64.0

    @classmethod
    def setUpClass(cls):
        """Compiles the Java agent once for the integration tests."""
        if shutil.which("javac") is None or shutil.which("java") is None:
            raise unittest.SkipTest("Java runtime or compiler is not available")

        subprocess.run(
            ["javac", str(cls.JAVA_SOURCE)],
            check=True,
            cwd=cls.ROOT,
        )

    def test_red_agent_opening_move_stays_within_limits_on_supported_board_sizes(self):
        """Runs the Java agent as RED for its opening move on supported board sizes."""
        for board_size in self.BOARD_SIZES:
            with self.subTest(board_size=board_size):
                board = HexBoard(board_size)
                red_player = self._create_agent(Color.RED, board_size)
                try:
                    self.assertTrue(red_player.initialize(board_size))
                    move = red_player.get_move(board)
                    self.assertIsNotNone(move, f"RED agent failed to answer on {board_size}x{board_size}")
                    self.assertIsNone(
                        red_player.last_error_reason,
                        f"Agent hit a subprocess error on {board_size}x{board_size}: {red_player.last_error_reason}",
                    )
                    if red_player.peak_memory_mb > 0:
                        self.assertLessEqual(
                            red_player.peak_memory_mb,
                            self.MEMORY_LIMIT_MB,
                            f"Agent exceeded the memory budget on {board_size}x{board_size}",
                        )
                finally:
                    self._cleanup_player(red_player)

    def test_blue_agent_swap_response_stays_within_limits_on_supported_board_sizes(self):
        """Runs the Java agent as BLUE on the swap branch for supported board sizes."""
        for board_size in self.BOARD_SIZES:
            with self.subTest(board_size=board_size):
                board = HexBoard(board_size)
                center = board_size // 2
                board.make_move(center, center, Color.RED)
                blue_player = self._create_agent(Color.BLUE, board_size)
                try:
                    self.assertTrue(blue_player.initialize(board_size))
                    move = blue_player.get_move(board)
                    self.assertEqual(move, "swap")
                    self.assertIsNone(
                        blue_player.last_error_reason,
                        f"Agent hit a subprocess error on {board_size}x{board_size}: {blue_player.last_error_reason}",
                    )
                    if blue_player.peak_memory_mb > 0:
                        self.assertLessEqual(
                            blue_player.peak_memory_mb,
                            self.MEMORY_LIMIT_MB,
                            f"Agent exceeded the memory budget on {board_size}x{board_size}",
                        )
                finally:
                    self._cleanup_player(blue_player)

    def test_blue_agent_swaps_against_central_opening(self):
        """BLUE should use the swap rule against a strong central first move."""
        board = HexBoard(11)
        board.make_move(5, 5, Color.RED)

        move = self._get_agent_move(Color.BLUE, board)
        self.assertEqual(move, "swap")

    def test_agent_takes_immediate_winning_move(self):
        """The agent should finish a one-move win instead of searching deeper."""
        board = HexBoard(3)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 0, Color.RED)
        board.make_move(0, 1, Color.BLUE)

        move = self._get_agent_move(Color.RED, board)
        self.assertEqual(move, (2, 0))

    def test_agent_blocks_immediate_opponent_win(self):
        """The agent should block a one-move opponent win."""
        board = HexBoard(3)
        board.make_move(0, 0, Color.RED)
        board.make_move(1, 0, Color.RED)
        board.make_move(0, 1, Color.BLUE)

        move = self._get_agent_move(Color.BLUE, board)
        self.assertEqual(move, (2, 0))

    def _create_agent(self, color: Color, board_size: int) -> SubprocessPlayer:
        """Creates the Java subprocess player for a specific color."""
        return SubprocessPlayer(
            color,
            "java",
            ["-cp", str(self.JAVA_CLASS_DIR), self.JAVA_CLASS_NAME],
            timeout=get_timeout_for_board_size(board_size),
            memory_limit_mb=self.MEMORY_LIMIT_MB,
            name="HexAgent",
        )

    def _get_agent_move(self, color: Color, board: HexBoard):
        """Starts the subprocess player, queries one move, and cleans up."""
        player = self._create_agent(color, board.size)
        try:
            self.assertTrue(player.initialize(board.size))
            move = player.get_move(board)
            self.assertIsNone(player.last_error_reason)
            return move
        finally:
            self._cleanup_player(player)

    def _cleanup_player(self, player):
        """Ensures player resources are released after each test."""
        player.cleanup()

        if not isinstance(player, SubprocessPlayer):
            return

        process = player.process
        if process is None:
            return

        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=2.0)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=2.0)

        for stream_name in ("stdin", "stdout", "stderr"):
            stream = getattr(process, stream_name, None)
            if stream is not None and not stream.closed:
                stream.close()


if __name__ == "__main__":
    unittest.main()
