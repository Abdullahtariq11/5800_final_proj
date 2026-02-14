#!/usr/bin/env python3
"""
Run the Hex game web server.

This script starts the Flask web application with WebSocket support.
"""

from web.app import run_app
import sys
from pathlib import Path

# Add project root to path
project_root = Path(__file__).parent
sys.path.insert(0, str(project_root))


if __name__ == '__main__':
    print("=" * 60)
    print("Hex Game Web Server")
    print("=" * 60)
    print("Starting server...")
    print("Open your browser and navigate to: http://localhost:5000")
    print("Press Ctrl+C to stop the server")
    print("=" * 60)

    run_app(host='0.0.0.0', port=5000, debug=True)
