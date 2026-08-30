#!/usr/bin/env python3
"""Generates a QR code for the Pulse tool's setup screen - entirely offline.

Run this yourself, locally. It never sends your API key anywhere; it just prompts for it and
writes a PNG to disk. Requires: pip install "qrcode[pil]"
"""

import getpass
import sys

try:
    import qrcode
except ImportError:
    print('Missing dependency. Run: pip install "qrcode[pil]"', file=sys.stderr)
    sys.exit(1)

DEFAULT_OUTPUT = "pulse-api-key.png"


def main() -> None:
    api_key = getpass.getpass("intervals.icu API Key (hidden): ").strip()

    if not api_key:
        print("An API key is required.", file=sys.stderr)
        sys.exit(1)

    output_path = input(f"Save QR code to [{DEFAULT_OUTPUT}]: ").strip() or DEFAULT_OUTPUT
    qrcode.make(api_key).save(output_path)
    print(f"Saved {output_path}. Scan it with the Pulse tool, then delete the file -")
    print("it's a plaintext copy of your API key.")


if __name__ == "__main__":
    main()
