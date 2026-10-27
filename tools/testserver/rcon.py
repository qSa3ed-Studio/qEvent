#!/usr/bin/env python3
"""Minimal RCON client used by the qEvent test harness.

Usage: rcon.py <port> <password> "<command>" ["<command>" ...]

Prints the server response for every command. Exits non-zero when the connection fails so
that the calling script can distinguish "no answer" from "answered with text".
"""
import socket
import struct
import sys


def pack(request_id: int, request_type: int, payload: str) -> bytes:
    body = struct.pack("<ii", request_id, request_type) + payload.encode("utf-8") + b"\x00\x00"
    return struct.pack("<i", len(body)) + body


def read_packet(sock: socket.socket) -> tuple[int, int, str]:
    raw_length = sock.recv(4)
    if len(raw_length) < 4:
        raise ConnectionError("connection closed while reading the packet length")
    length = struct.unpack("<i", raw_length)[0]
    data = b""
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk:
            raise ConnectionError("connection closed while reading the packet body")
        data += chunk
    request_id, request_type = struct.unpack("<ii", data[:8])
    return request_id, request_type, data[8:-2].decode("utf-8", errors="replace")


def main() -> int:
    if len(sys.argv) < 4:
        print(__doc__, file=sys.stderr)
        return 2
    port = int(sys.argv[1])
    password = sys.argv[2]
    commands = sys.argv[3:]

    with socket.create_connection(("127.0.0.1", port), timeout=10) as sock:
        sock.sendall(pack(1, 3, password))
        packet_id, _, _ = read_packet(sock)
        if packet_id == -1:
            print("authentication failed", file=sys.stderr)
            return 3
        for command in commands:
            sock.sendall(pack(2, 2, command))
            _, _, body = read_packet(sock)
            print(f"$ {command}\n{body}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
