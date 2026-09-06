#!/usr/bin/env python3
"""Generates a QR code for the Agenda tool's Add Calendar screen - entirely offline.

Run this yourself, locally. It never sends your calendar URL anywhere; it just prompts for
it and writes a PNG to disk. Requires: pip install "qrcode[pil]"
"""

import sys

try:
    import qrcode
except ImportError:
    print('Missing dependency. Run: pip install "qrcode[pil]"', file=sys.stderr)
    sys.exit(1)

DEFAULT_OUTPUT = "agenda-calendar-url.png"


def main() -> None:
    ics_url = input("ICS calendar URL: ").strip()

    if not ics_url:
        print("A URL is required.", file=sys.stderr)
        sys.exit(1)

    output_path = input(f"Save QR code to [{DEFAULT_OUTPUT}]: ").strip() or DEFAULT_OUTPUT
    qrcode.make(ics_url).save(output_path)
    print(f"Saved {output_path}. Scan it with the Agenda tool's camera icon, then delete the")
    print("file - it's a plaintext copy of your calendar link (whoever has it can read your")
    print("calendar, same as any other subscribe URL).")


if __name__ == "__main__":
    main()
