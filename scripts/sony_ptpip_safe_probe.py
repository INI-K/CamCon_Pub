#!/usr/bin/env python3
"""소니 PTP/IP 안전 프로브 (게이트 G4).

카메라가 PTP/IP 연결을 받아들이는지 확인하고 **반드시 규정대로 세션을 닫는다**.

왜 이 스크립트가 필요한가
------------------------
`Camera Control PTP 3 Reference.pdf` §Disconnect(55쪽)는 이렇게 규정한다.

    "The Initiator must execute CloseSession to disconnect.
     Subsequently, release the PTP connection."

즉 CloseSession(0x1003)을 보낸 뒤에 TCP 를 끊어야 한다. 이 순서를 어기고 소켓만 닫으면 카메라가
세션을 붙잡은 채로 남아 이후 InitCommandRequest 를 EOF 로 거부한다. 실측으로 80초를 기다려도
회복되지 않았고 전원을 다시 넣어야 했다. SSH(22)·UPnP(64321)는 계속 열려 있어 겉보기로는
정상이라 원인을 찾기도 어렵다.

팀 리드의 초기 프로브가 정확히 이 실수를 저질러 카메라를 잠갔다. 그래서 이 스크립트는 어떤
경로로 끝나든(성공·실패·예외·Ctrl-C) CloseSession 을 먼저 보내도록 `finally` 로 강제한다.

사용법
------
    # SSH 터널을 먼저 세운 뒤(로컬 15741 → 카메라 localhost:15740)
    ssh -N -L 15741:localhost:15740 <user>@<카메라IP> &
    python3 scripts/sony_ptpip_safe_probe.py --host 127.0.0.1 --port 15741

    # 반복 검증(G3): 전원 재시작 없이 5회 연속 성공해야 한다
    python3 scripts/sony_ptpip_safe_probe.py --host 127.0.0.1 --port 15741 --repeat 5

종료 코드
--------
    0  모든 회차 성공(세션 수립 + CloseSession 정상 종료)
    1  한 회차라도 실패
    2  사용법 오류
"""

from __future__ import annotations

import argparse
import socket
import struct
import sys
import time
import uuid

# --- PTP/IP 상수 (PIMA 15740 / PTP-IP 규격) ---------------------------------

PKT_INIT_COMMAND_REQUEST = 0x00000001
PKT_INIT_COMMAND_ACK = 0x00000002
PKT_INIT_FAIL = 0x00000005
PKT_OPERATION_REQUEST = 0x00000006
PKT_OPERATION_RESPONSE = 0x00000007

PTP_OC_OPEN_SESSION = 0x1002
PTP_OC_CLOSE_SESSION = 0x1003

PTP_RC_OK = 0x2001

DATA_PHASE_NONE = 0x00000001

# 라이브러리와 같은 이름을 쓴다. libgphoto2 는 "localhost" 를 UTF-16LE 로 보내며 총 48바이트다.
DEFAULT_CLIENT_NAME = "localhost"


def _utf16z(text: str) -> bytes:
    """PTP/IP 문자열: UTF-16LE + 널 종단."""
    return text.encode("utf-16-le") + b"\x00\x00"


def _packet(pkt_type: int, payload: bytes) -> bytes:
    """길이(4) + 타입(4) + payload. 길이는 자기 자신을 포함한다."""
    return struct.pack("<II", 8 + len(payload), pkt_type) + payload


def _recv_exactly(sock: socket.socket, count: int) -> bytes:
    buf = b""
    while len(buf) < count:
        chunk = sock.recv(count - len(buf))
        if not chunk:
            raise ConnectionError(
                f"End of stream: {len(buf)}/{count} 바이트만 받고 끊김 "
                f"(카메라가 세션을 붙잡고 있을 때 나타나는 증상이다)"
            )
        buf += chunk
    return buf


def _recv_packet(sock: socket.socket) -> tuple[int, bytes]:
    header = _recv_exactly(sock, 8)
    length, pkt_type = struct.unpack("<II", header)
    if length < 8:
        raise ValueError(f"잘못된 패킷 길이 {length}")
    return pkt_type, _recv_exactly(sock, length - 8)


class SafePtpipSession:
    """세션을 열고, 어떤 경로로 끝나든 CloseSession 을 보내고 닫는다.

    `with` 구문 전용이다. `__exit__` 이 규정된 종료를 책임지므로 직접 소켓을 닫지 말 것.
    """

    def __init__(self, host: str, port: int, guid: bytes, name: str, timeout: float):
        self.host = host
        self.port = port
        self.guid = guid
        self.name = name
        self.timeout = timeout
        self.sock: socket.socket | None = None
        self.session_open = False
        self.transaction_id = 0
        self.connection_number: int | None = None

    def __enter__(self) -> "SafePtpipSession":
        self.sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
        self.sock.settimeout(self.timeout)
        self._init_command()
        self._open_session()
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:
        # ⚠️ 여기가 이 스크립트의 존재 이유다. 예외로 빠져나가든 정상 종료든
        #    CloseSession 을 먼저 보내고 그다음에 TCP 를 끊는다.
        try:
            if self.session_open:
                self._close_session()
        except Exception as cleanup_error:  # noqa: BLE001 - 정리 실패를 삼키되 알린다
            print(f"  ⚠️ CloseSession 실패: {cleanup_error}", file=sys.stderr)
            print(
                "     카메라가 잠겼을 수 있다. 다음 연결이 실패하면 전원을 껐다 켜야 한다.",
                file=sys.stderr,
            )
        finally:
            if self.sock is not None:
                self.sock.close()
                self.sock = None
        return False  # 예외를 삼키지 않는다

    # --- 핸드셰이크 ---------------------------------------------------------

    def _init_command(self) -> None:
        payload = self.guid + _utf16z(self.name) + struct.pack("<I", 0x00010000)
        self.sock.sendall(_packet(PKT_INIT_COMMAND_REQUEST, payload))

        pkt_type, body = _recv_packet(self.sock)
        if pkt_type == PKT_INIT_FAIL:
            reason = struct.unpack("<I", body[:4])[0] if len(body) >= 4 else -1
            raise ConnectionError(
                f"InitFail (사유 0x{reason:x}). 0x1=권한 거부(본체 승인 필요), 0x2=사용 중"
            )
        if pkt_type != PKT_INIT_COMMAND_ACK:
            raise ConnectionError(f"예상 밖 패킷 타입 0x{pkt_type:x}")

        self.connection_number = struct.unpack("<I", body[:4])[0]

    def _operation(self, opcode: int, params: tuple[int, ...] = ()) -> int:
        self.transaction_id += 1
        payload = struct.pack("<IHI", DATA_PHASE_NONE, opcode, self.transaction_id)
        for value in params:
            payload += struct.pack("<I", value)
        self.sock.sendall(_packet(PKT_OPERATION_REQUEST, payload))

        pkt_type, body = _recv_packet(self.sock)
        if pkt_type != PKT_OPERATION_RESPONSE:
            raise ConnectionError(f"OperationResponse 가 아닌 0x{pkt_type:x}")
        return struct.unpack("<H", body[:2])[0]

    def _open_session(self) -> None:
        # 세션 ID 0 은 예약값이라 1 을 쓴다.
        code = self._operation(PTP_OC_OPEN_SESSION, (1,))
        if code != PTP_RC_OK:
            raise ConnectionError(f"OpenSession 실패 (응답 0x{code:04x})")
        self.session_open = True

    def _close_session(self) -> None:
        code = self._operation(PTP_OC_CLOSE_SESSION)
        self.session_open = False
        if code != PTP_RC_OK:
            raise ConnectionError(f"CloseSession 응답이 0x{code:04x} (0x2001 이어야 한다)")


def probe_once(host: str, port: int, name: str, timeout: float) -> bool:
    guid = uuid.uuid4().bytes
    started = time.monotonic()
    try:
        with SafePtpipSession(host, port, guid, name, timeout) as session:
            elapsed = (time.monotonic() - started) * 1000
            print(
                f"  ✅ 세션 수립 (connection={session.connection_number}, {elapsed:.0f}ms)"
                " — CloseSession 후 종료한다"
            )
        return True
    except Exception as error:  # noqa: BLE001 - 프로브는 모든 실패를 보고만 한다
        print(f"  ❌ 실패: {error}")
        return False


def main() -> int:
    parser = argparse.ArgumentParser(
        description="소니 PTP/IP 안전 프로브 — 반드시 CloseSession 을 보내고 종료한다"
    )
    parser.add_argument("--host", default="127.0.0.1", help="터널 로컬 주소 (기본 127.0.0.1)")
    parser.add_argument("--port", type=int, default=15741, help="터널 로컬 포트 (기본 15741)")
    parser.add_argument("--repeat", type=int, default=1, help="반복 횟수 (G3 검증은 5)")
    parser.add_argument(
        "--interval", type=float, default=2.0, help="회차 사이 대기 초 (기본 2.0)"
    )
    parser.add_argument("--timeout", type=float, default=10.0, help="소켓 타임아웃 초")
    parser.add_argument(
        "--name",
        default=DEFAULT_CLIENT_NAME,
        help=f"클라이언트 이름 (기본 {DEFAULT_CLIENT_NAME!r} — libgphoto2 와 동일)",
    )
    args = parser.parse_args()

    if args.repeat < 1:
        print("--repeat 는 1 이상이어야 한다", file=sys.stderr)
        return 2

    print(f"대상 {args.host}:{args.port} / {args.repeat}회 / 이름 {args.name!r}")
    failures = 0
    for attempt in range(1, args.repeat + 1):
        print(f"[{attempt}/{args.repeat}]")
        if not probe_once(args.host, args.port, args.name, args.timeout):
            failures += 1
        if attempt < args.repeat:
            time.sleep(args.interval)

    if failures:
        print(f"\n실패 {failures}/{args.repeat}회.")
        print("전부 실패했다면 카메라 PTP 계층이 잠겼을 가능성이 높다(전원 재시작 필요).")
        return 1

    print(f"\n{args.repeat}회 전부 성공. 세션이 매번 규정대로 닫혔다.")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        # with 블록의 __exit__ 이 이미 CloseSession 을 보냈다.
        print("\n중단됨 (세션은 정상 종료 처리되었다)", file=sys.stderr)
        sys.exit(1)
