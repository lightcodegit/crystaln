#!/usr/bin/python3
"""Privileged helper for CrystalN (runs as root via systemd socket activation).

Protocol (same as the session helper in Privileges.kt): reads one line of
base64-encoded shell script per request from stdin, executes it with `sh -c`,
answers a single line "__CR_R:<exit code>:<base64 captured output>".

Access control: SO_PEERCRED on the connection is compared with the uid stored
in /etc/crystaln-helper.uid (written by the app during one-time installation),
so only that user's processes may use the helper. Everyone else is disconnected
immediately. CRYSTALN_HELPER_UID_FILE overrides the uid file path for tests.
"""
import base64
import os
import socket
import struct
import subprocess
import sys
UID_FILE = os.environ.get("CRYSTALN_HELPER_UID_FILE", "/etc/crystaln-helper.uid")
RESULT = "__CR_R:"


def allowed_uid():
    try:
        with open(UID_FILE) as f:
            return int(f.read().strip())
    except (OSError, ValueError):
        return None


def peer_uid():
    """Linux SO_PEERCRED: struct ucred { pid, uid, gid } — 3 x int32."""
    fd = os.dup(0)
    try:
        s = socket.socket(fileno=fd)
        try:
            raw = s.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED, 12)
        finally:
            s.detach()  # don't let the socket object close our stdin
            os.close(fd)
        return struct.unpack("3i", raw)[1]
    except OSError:
        return None


def run_script(script):
    try:
        proc = subprocess.run(
            ["sh", "-c", script],
            capture_output=True,
            text=True,
        )
        out = (proc.stdout + proc.stderr).strip()
        return proc.returncode, out
    except OSError as exc:
        return 126, str(exc)


def main():
    allowed = allowed_uid()
    peer = peer_uid()
    if allowed is None or peer != allowed:
        sys.stderr.write("crystaln-helper: access denied (uid=%s allowed=%s)\n" % (peer, allowed))
        return 1
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        if line == "__CR_EXIT__":
            return 0
        try:
            script = base64.b64decode(line).decode("utf-8")
        except Exception:
            sys.stdout.write(RESULT + "-1:%s\n" % base64.b64encode(b"bad request").decode())
            sys.stdout.flush()
            continue
        code, out = run_script(script)
        resp = RESULT + str(code) + ":" + base64.b64encode(out.encode("utf-8")).decode()
        sys.stdout.write(resp + "\n")
        sys.stdout.flush()
    return 0


if __name__ == "__main__":
    sys.exit(main())
