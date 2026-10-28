#!/usr/bin/env python3
"""Minimal offline-mode test client for Minecraft 26.3 (protocol 777).

It sends exactly the packets a vanilla client sends for the railgun controls:
  * holding right click with an item without use action -> "use_item" every 4 ticks
  * left click into the air                               -> "punch"
and records what the server answers (action bar texts, cooldowns, chat).

Packet ids come from the vanilla data generator (reports/packets.json); they change between versions.
Requires network-compression-threshold=-1 and online-mode=false on the test server.

Usage: test_bot.py <host> <port> <name> <script>
Script lines (one per line):  wait <seconds> | look <yaw> <pitch> | hold <seconds> | punch
                              | hold_punch <seconds_before> | hold_dig <seconds_before> <x> <y> <z>
"""
import socket
import struct
import sys
import threading
import time
import uuid

PROTOCOL = 777
C_LOGIN = {"login_finished": 2, "login_compression": 3, "login_disconnect": 0}
S_LOGIN = {"hello": 0, "login_acknowledged": 3}
C_CONFIG = {"select_known_packs": 15, "finish_configuration": 3, "keep_alive": 4, "ping": 5, "disconnect": 2}
S_CONFIG = {"select_known_packs": 7, "finish_configuration": 3, "keep_alive": 4, "pong": 5}
C_PLAY = {"keep_alive": 45, "ping": 62, "player_position": 73, "disconnect": 32, "system_chat": 124,
          "cooldown": 22, "set_action_bar_text": 89, "chunk_batch_finished": 11}
S_PLAY = {"keep_alive": 28, "pong": 45, "accept_teleportation": 0, "move_player_rot": 32, "use_item": 67,
          "punch": 46, "player_loaded": 44, "chunk_batch_received": 11, "client_command": 12,
          "player_action": 41}


def varint(value):
    out = b""
    value &= 0xFFFFFFFF
    while True:
        byte = value & 0x7F
        value >>= 7
        out += struct.pack("B", byte | (0x80 if value else 0))
        if not value:
            return out


def string(text):
    data = text.encode()
    return varint(len(data)) + data


def read_varint(buf, pos):
    result = shift = 0
    while True:
        byte = buf[pos]
        pos += 1
        result |= (byte & 0x7F) << shift
        shift += 7
        if not byte & 0x80:
            return result, pos


class Bot:
    def __init__(self, host, port, name):
        self.sock = socket.create_connection((host, port))
        self.name = name
        self.lock = threading.Lock()
        self.state = "login"
        self.sequence = 0
        self.yaw = 0.0
        self.pitch = 0.0
        self.events = []
        self.loaded = threading.Event()
        self.closed = False

    def log(self, kind, text=""):
        stamp = time.strftime("%H:%M:%S")
        self.events.append((stamp, kind, text))
        print(f"[{stamp}] {kind} {text}", flush=True)

    def send(self, packet_id, payload=b""):
        body = varint(packet_id) + payload
        with self.lock:
            self.sock.sendall(varint(len(body)) + body)

    def recv_exact(self, size):
        data = b""
        while len(data) < size:
            chunk = self.sock.recv(size - len(data))
            if not chunk:
                raise ConnectionError("closed")
            data += chunk
        return data

    def read_packet(self):
        length = shift = 0
        while True:
            byte = self.recv_exact(1)[0]
            length |= (byte & 0x7F) << shift
            shift += 7
            if not byte & 0x80:
                break
        data = self.recv_exact(length)
        packet_id, pos = read_varint(data, 0)
        return packet_id, data[pos:]

    def login(self, host, port):
        self.send(0, varint(PROTOCOL) + string(host) + struct.pack(">H", port) + varint(2))
        self.send(S_LOGIN["hello"], string(self.name) + uuid.uuid3(uuid.NAMESPACE_OID, self.name).bytes)
        threading.Thread(target=self.reader, daemon=True).start()

    def reader(self):
        try:
            while True:
                packet_id, data = self.read_packet()
                getattr(self, "on_" + self.state)(packet_id, data)
        except Exception as error:  # noqa: BLE001 - report every disconnect reason
            self.closed = True
            self.log("CLOSED", repr(error))

    def on_login(self, packet_id, data):
        if packet_id == C_LOGIN["login_finished"]:
            self.state = "config"
            self.send(S_LOGIN["login_acknowledged"])
        elif packet_id == C_LOGIN["login_disconnect"]:
            self.log("KICKED", data.decode(errors="replace"))
        elif packet_id == C_LOGIN["login_compression"]:
            self.log("ERROR", "server enabled compression; set network-compression-threshold=-1")

    def on_config(self, packet_id, data):
        if packet_id == C_CONFIG["select_known_packs"]:
            self.send(S_CONFIG["select_known_packs"], data)
        elif packet_id == C_CONFIG["keep_alive"]:
            self.send(S_CONFIG["keep_alive"], data)
        elif packet_id == C_CONFIG["ping"]:
            self.send(S_CONFIG["pong"], data)
        elif packet_id == C_CONFIG["finish_configuration"]:
            self.state = "play"
            self.send(S_CONFIG["finish_configuration"])
        elif packet_id == C_CONFIG["disconnect"]:
            self.log("KICKED", data.decode(errors="replace"))

    def on_play(self, packet_id, data):
        if packet_id == C_PLAY["keep_alive"]:
            self.send(S_PLAY["keep_alive"], data)
        elif packet_id == C_PLAY["ping"]:
            self.send(S_PLAY["pong"], data)
        elif packet_id == C_PLAY["player_position"]:
            teleport, pos = read_varint(data, 0)
            x, y, z = struct.unpack(">ddd", data[pos:pos + 24])
            yaw, pitch = struct.unpack(">ff", data[pos + 48:pos + 56])
            self.send(S_PLAY["accept_teleportation"], varint(teleport) + struct.pack(">dddff", x, y, z, yaw, pitch))
            self.log("POSITION", f"{x:.1f} {y:.1f} {z:.1f}")
            if not self.loaded.is_set():
                self.send(S_PLAY["client_command"], varint(0))  # respawn if the last session ended in death
                self.send(S_PLAY["player_loaded"])
                self.loaded.set()
            self.look(self.yaw, self.pitch)
        elif packet_id == C_PLAY["chunk_batch_finished"]:
            self.send(S_PLAY["chunk_batch_received"], struct.pack(">f", 9.0))
        elif packet_id == C_PLAY["set_action_bar_text"]:
            self.log("ACTIONBAR", printable(data))
        elif packet_id == C_PLAY["system_chat"]:
            self.log("CHAT", printable(data))
        elif packet_id == C_PLAY["cooldown"]:
            self.log("COOLDOWN", printable(data))
        elif packet_id == C_PLAY["disconnect"]:
            self.log("KICKED", printable(data))

    def look(self, yaw, pitch):
        self.yaw, self.pitch = yaw, pitch
        self.send(S_PLAY["move_player_rot"], struct.pack(">ffB", yaw, pitch, 1))

    def use(self):
        self.sequence += 1
        self.send(S_PLAY["use_item"], varint(0) + varint(self.sequence) + struct.pack(">ff", self.yaw, self.pitch))

    def punch(self):
        self.send(S_PLAY["punch"])
        self.log("SENT", "punch")

    def dig(self, x, y, z):
        """Left click on a block in reach: START_DESTROY_BLOCK on its top face."""
        self.sequence += 1
        position = ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF)
        self.send(S_PLAY["player_action"], varint(0) + struct.pack(">qB", position, 1) + varint(self.sequence))
        self.log("SENT", f"start destroying {x} {y} {z}")

    def hold(self, seconds, punch_after=None, action=None):
        """Mimics Minecraft#handleKeybinds: startUseItem every 4 ticks while the use key is down."""
        start = time.time()
        punched = False
        while time.time() - start < seconds:
            self.use()
            if punch_after is not None and not punched and time.time() - start >= punch_after:
                (action or self.punch)()
                punched = True
            time.sleep(0.2)
        self.log("SENT", f"held right click for {seconds}s")


def printable(data):
    return "".join(chr(b) if 32 <= b < 127 else "." for b in data)[:200]


def main(host, port, name, script):
    bot = Bot(host, int(port), name)
    bot.login(host, int(port))
    if not bot.loaded.wait(30):
        raise SystemExit("did not reach play state")
    for line in open(script):
        parts = line.split()
        if not parts or parts[0].startswith("#"):
            continue
        command, args = parts[0], [float(a) for a in parts[1:]]
        bot.log("STEP", line.strip())
        if command == "wait":
            time.sleep(args[0])
        elif command == "look":
            bot.look(args[0], args[1])
        elif command == "hold":
            bot.hold(args[0])
        elif command == "punch":
            bot.punch()
        elif command == "hold_punch":
            bot.hold(args[0] + 0.6, punch_after=args[0])
        elif command == "hold_dig":
            x, y, z = (int(v) for v in args[1:4])
            bot.hold(args[0] + 0.6, punch_after=args[0], action=lambda: bot.dig(x, y, z))
        if bot.closed:
            break
    bot.log("DONE")


if __name__ == "__main__":
    if len(sys.argv) != 5:
        raise SystemExit(__doc__)
    main(*sys.argv[1:])
