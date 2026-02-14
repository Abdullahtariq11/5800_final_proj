#!/usr/bin/env python3
"""
Test script to verify example agents work with the protocol.
"""

import subprocess
import sys
import time


def test_agent(agent_cmd):
    """Test an agent with simulated protocol messages."""
    print(f"\nTesting agent: {' '.join(agent_cmd)}")
    print("=" * 60)

    # Start agent process
    process = subprocess.Popen(
        agent_cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        bufsize=1
    )

    try:
        # Send INIT
        print("Engine -> Agent: INIT RED 5")
        process.stdin.write("INIT RED 5\n")
        process.stdin.flush()

        # Send initial STATE
        print("Engine -> Agent: STATE 5 RED ")
        process.stdin.write("STATE 5 RED \n")
        process.stdin.flush()

        # Request move
        print("Engine -> Agent: MOVE")
        process.stdin.write("MOVE\n")
        process.stdin.flush()

        # Read response
        response = process.stdout.readline().strip()
        print(f"Agent -> Engine: {response}")

        # Validate response format
        parts = response.split()
        if len(parts) == 2:
            try:
                row, col = int(parts[0]), int(parts[1])
                if 0 <= row < 5 and 0 <= col < 5:
                    print("✓ Valid move format and coordinates")
                else:
                    print("✗ Coordinates out of bounds")
            except ValueError:
                print("✗ Invalid coordinate format")
        else:
            print("✗ Invalid response format")

        # Send RESULT
        print("Engine -> Agent: RESULT success")
        process.stdin.write("RESULT success\n")
        process.stdin.flush()

        # Send END
        print("Engine -> Agent: END ongoing")
        process.stdin.write("END ongoing\n")
        process.stdin.flush()

        # Wait for agent to exit
        process.wait(timeout=2)

        # Check stderr for any errors
        stderr_output = process.stderr.read()
        if stderr_output:
            print("\nAgent stderr output:")
            print(stderr_output)

        print("\n✅ Agent test completed successfully")

    except subprocess.TimeoutExpired:
        print("\n✗ Agent did not exit after END message")
        process.kill()
    except Exception as e:
        print(f"\n✗ Error during test: {e}")
        process.kill()
    finally:
        try:
            process.stdin.close()
            process.stdout.close()
            process.stderr.close()
        except:
            pass


def main():
    """Test all example agents."""
    print("=" * 60)
    print("TESTING EXAMPLE AGENTS")
    print("=" * 60)

    # Test Python agent
    test_agent(["python", "examples/python/random_agent.py"])

    print("\n" + "=" * 60)
    print("Note: Java and C++ agents need to be compiled first")
    print("  Java: javac examples/java/RandomAgent.java")
    print("  C++:  g++ -std=c++11 examples/cpp/random_agent.cpp -o examples/cpp/random_agent")
    print("=" * 60)


if __name__ == "__main__":
    main()
