# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (c) 2026 Vortax contributors.
"""Fake QEMU-style VNC server for QemuAudio.h.

Usage: python3 test_qemu_audio.py <path to libvncserver checkout> <path to built libvncclient.a>
Builds qemu_audio_check.cpp against LibVNCClient, runs it against this server, and checks the bytes both ways.
"""
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time
from pathlib import Path

HERE = Path(__file__).parent
seen = {"encodings": [], "format": None, "ops": [], "ack_sent": False}
errors = []


def recv(conn, n):
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            raise EOFError
        buf += chunk
    return buf


def serve(listener):
    conn, _ = listener.accept()
    try:
        conn.sendall(b"RFB 003.008\n"); recv(conn, 12)
        conn.sendall(b"\x01\x01"); recv(conn, 1)                        # one security type: None
        conn.sendall(struct.pack(">I", 0)); recv(conn, 1)               # result OK, ClientInit
        pf = struct.pack(">BBBBHHHBBBxxx", 32, 24, 0, 1, 255, 255, 255, 16, 8, 0)
        conn.sendall(struct.pack(">HH", 64, 48) + pf + struct.pack(">I", 2) + b"vm")
        while True:
            t = recv(conn, 1)[0]
            if t == 0:
                recv(conn, 19)
            elif t == 2:
                n = struct.unpack(">xH", recv(conn, 3))[0]
                seen["encodings"] = list(struct.unpack(f">{n}i", recv(conn, 4 * n)))
            elif t == 3:
                recv(conn, 9)
                if -259 in seen["encodings"] and not seen["ack_sent"]:
                    seen["ack_sent"] = True                              # QEMU answers with an empty rect, encoding -259
                    conn.sendall(struct.pack(">BxH", 0, 1) + struct.pack(">HHHHi", 0, 0, 64, 48, -259))
            elif t == 255:
                sub, op = struct.unpack(">BH", recv(conn, 3))
                if sub != 1:
                    errors.append(f"unexpected sub-message {sub}")
                    return
                if op == 2:
                    seen["format"] = struct.unpack(">BBI", recv(conn, 6))
                seen["ops"].append(op)
                if op == 0:                                              # enabled: stream sound, one chunk split over time
                    conn.sendall(bytes([255, 1, 0, 1]))                  # begin
                    conn.sendall(bytes([255, 1, 0, 2]) + struct.pack(">I", 6) + struct.pack("<hhh", 0, 16384, -16384))
                    msg = bytes([255, 1, 0, 2]) + struct.pack(">I", 4) + struct.pack("<hh", 100, 200)
                    for i in range(0, len(msg), 3):                      # three bytes at a time
                        conn.sendall(msg[i:i + 3]); time.sleep(0.02)
                    conn.sendall(bytes([255, 1, 0, 2]) + struct.pack(">I", 0))        # empty chunk
                    conn.sendall(bytes([255, 1, 0, 2]) + struct.pack(">I", 2) + b"\x07\x00")
                    conn.sendall(bytes([255, 1, 0, 0]))                  # end
                if op == 1:
                    return
            else:
                errors.append(f"unexpected client message {t}")
                return
    except EOFError:
        pass
    finally:
        conn.close()


def main(src, lib):
    exe = Path(tempfile.mkdtemp()) / "check"
    cmd = ["g++", "-std=c++17", "-o", str(exe), str(HERE / "qemu_audio_check.cpp"), "-I", f"{src}/include", "-I", f"{src}/src/libvncclient",
           "-I", str(Path(lib).parent / "include"), lib, "-lz", "-lpthread"]
    subprocess.run(cmd, check=True)
    listener = socket.socket(); listener.bind(("127.0.0.1", 0)); listener.listen(1)
    th = threading.Thread(target=serve, args=(listener,)); th.start()
    out = subprocess.run([str(exe), "127.0.0.1", str(listener.getsockname()[1])], capture_output=True, text=True, timeout=30).stdout.splitlines()
    th.join(10)
    expect = ["start before ack: 0", "available", "start after ack: 1",
              "data 6 00 00 00 40 00 c0", "data 4 64 00 c8 00", "data 2 07 00", "stop: 1"]
    got = [l for l in out if l.startswith(("available", "start", "data", "stop"))]
    problems = list(errors)
    # the empty chunk is skipped on purpose, so exactly three data lines arrive
    if got != expect:
        problems.append(f"events {got} != {expect}")
    if seen["encodings"][-1:] != [-259] and -259 not in seen["encodings"]:
        problems.append(f"SetEncodings lacked -259: {seen['encodings']}")
    if seen["format"] != (3, 1, 22050):
        problems.append(f"format {seen['format']}")
    if seen["ops"] != [2, 0, 1]:
        problems.append(f"ops {seen['ops']}")
    print("\n".join(out))
    if problems:
        print("FAILED:", *problems, sep="\n  ")
        sys.exit(1)
    print("ALL PASSED")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
