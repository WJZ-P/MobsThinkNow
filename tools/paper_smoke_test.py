#!/usr/bin/env python3
"""Build-artifact smoke test against one pinned, checksum-verified Paper server."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import queue
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request


PAPER_VERSION = "26.1.2"
PAPER_BUILD = 74
PAPER_SHA256 = "1d70b1dab9cf4a6de615209a536f3a45a2186240253c428213ce2188ab95e5f7"
PAPER_URL = (
    "https://fill-data.papermc.io/v1/objects/"
    f"{PAPER_SHA256}/paper-{PAPER_VERSION}-{PAPER_BUILD}.jar"
)
PLUGIN_FILE_NAME = "MobsThinkNowPaper.jar"
WORLD_NAME = "mtn-smoke-world"
FLAT_GENERATOR_SETTINGS = (
    '{"layers":['
    '{"block":"minecraft:bedrock","height":1},'
    '{"block":"minecraft:dirt","height":2},'
    '{"block":"minecraft:grass_block","height":1}'
    '],"biome":"minecraft:plains","structures":{}}'
)


class SmokeFailure(RuntimeError):
    """Expected, user-facing smoke-test failure."""


def parse_args() -> argparse.Namespace:
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(
        description="Start pinned Paper, run /mtnpaper selftest, validate PASS, and stop cleanly."
    )
    parser.add_argument(
        "--plugin",
        type=Path,
        default=root / "paper" / "build" / "libs" / "mobsthinknow-paper-0.1.0-alpha.1.jar",
        help="Paper plugin JAR to deploy.",
    )
    parser.add_argument(
        "--runtime",
        type=Path,
        default=root / "build" / "paper-smoke",
        help="Isolated Paper directory (defaults below the ignored build directory).",
    )
    parser.add_argument(
        "--paper-jar",
        type=Path,
        help="Use this local Paper JAR instead of downloading the pinned object.",
    )
    parser.add_argument("--java", type=Path, help="Java executable; otherwise JAVA_HOME/PATH is used.")
    parser.add_argument("--offline", action="store_true", help="Fail instead of downloading a missing Paper JAR.")
    parser.add_argument("--keep-world", action="store_true", help="Reuse the previous isolated smoke-test world.")
    parser.add_argument("--startup-timeout", type=int, default=180)
    parser.add_argument("--selftest-timeout", type=int, default=90)
    return parser.parse_args()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_paper(path: Path) -> None:
    actual = sha256(path)
    if actual != PAPER_SHA256:
        raise SmokeFailure(
            f"Paper checksum mismatch for {path}: expected {PAPER_SHA256}, got {actual}"
        )


def download_paper(destination: Path) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + ".part")
    temporary.unlink(missing_ok=True)
    request = urllib.request.Request(PAPER_URL, headers={"User-Agent": "MobsThinkNow-Paper-Smoke/1"})
    print(f"[paper-smoke] downloading pinned Paper {PAPER_VERSION} build {PAPER_BUILD}")
    try:
        with urllib.request.urlopen(request, timeout=90) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output, length=1024 * 1024)
        verify_paper(temporary)
        temporary.replace(destination)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise


def provision_paper(args: argparse.Namespace, runtime: Path) -> Path:
    runtime.mkdir(parents=True, exist_ok=True)
    pinned = runtime / f"paper-{PAPER_VERSION}-{PAPER_BUILD}.jar"
    if args.paper_jar is not None:
        source = args.paper_jar.resolve()
        if not source.is_file():
            raise SmokeFailure(f"local Paper JAR does not exist: {source}")
        verify_paper(source)
        if source != pinned:
            shutil.copy2(source, pinned)
    elif pinned.is_file():
        verify_paper(pinned)
    elif args.offline:
        raise SmokeFailure(f"offline mode requested but pinned Paper JAR is missing: {pinned}")
    else:
        download_paper(pinned)
    return pinned


def resolve_java(explicit: Path | None) -> Path:
    if explicit is not None:
        candidate = explicit.resolve()
    elif os.environ.get("JAVA_HOME"):
        executable = "java.exe" if os.name == "nt" else "java"
        candidate = Path(os.environ["JAVA_HOME"]) / "bin" / executable
    else:
        found = shutil.which("java")
        if found is None:
            raise SmokeFailure("Java was not found; set JAVA_HOME or pass --java")
        candidate = Path(found)
    if not candidate.is_file():
        raise SmokeFailure(f"Java executable does not exist: {candidate}")
    return candidate


def reserve_local_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


def remove_previous_world(runtime: Path) -> None:
    for suffix in ("", "_nether", "_the_end"):
        shutil.rmtree(runtime / f"{WORLD_NAME}{suffix}", ignore_errors=True)


def prepare_runtime(args: argparse.Namespace, runtime: Path, paper: Path) -> Path:
    plugin = args.plugin.resolve()
    if not plugin.is_file():
        raise SmokeFailure(f"plugin JAR does not exist: {plugin}; run :paper:jar first")
    runtime.mkdir(parents=True, exist_ok=True)
    (runtime / "plugins").mkdir(exist_ok=True)
    (runtime / "tmp").mkdir(exist_ok=True)
    if not args.keep_world:
        remove_previous_world(runtime)
    plugin_data = runtime / "plugins" / "MobsThinkNowPaper"
    shutil.rmtree(plugin_data, ignore_errors=True)
    plugin_data.mkdir(parents=True, exist_ok=True)
    # Hard difficulty plus a 100% base crossbow chance makes every possible hard-mode IQ a
    # deterministic success. The runtime self-test can therefore prove that a real NATURAL
    # CreatureSpawnEvent is processed once, while normal installations retain the 18% default.
    (plugin_data / "config.yml").write_text(
        """enabled: true
skeleton:
  crossbow:
    natural-loadout:
      enabled: true
      crossbow-chance: 1.0
      firework-crossbow-chance: 1.0
""",
        encoding="utf-8",
    )
    shutil.copy2(plugin, runtime / "plugins" / PLUGIN_FILE_NAME)
    shutil.copy2(paper, runtime / "paper.jar")
    (runtime / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    port = reserve_local_port()
    properties = "\n".join(
        (
            "online-mode=false",
            "server-ip=127.0.0.1",
            f"server-port={port}",
            f"level-name={WORLD_NAME}",
            "level-seed=48763219041",
            "level-type=minecraft:flat",
            f"generator-settings={FLAT_GENERATOR_SETTINGS}",
            "generate-structures=false",
            "difficulty=hard",
            "spawn-protection=0",
            "view-distance=3",
            "simulation-distance=3",
            "max-players=1",
            "enable-query=false",
            "enable-rcon=false",
            "motd=MobsThinkNow Paper smoke test",
        )
    )
    (runtime / "server.properties").write_text(properties + "\n", encoding="utf-8")
    return runtime / "paper-smoke.log"


def send_command(process: subprocess.Popen[str], command: str) -> None:
    if process.stdin is None:
        raise SmokeFailure("Paper stdin is unavailable")
    process.stdin.write(command + "\n")
    process.stdin.flush()


def run_server(
    runtime: Path,
    java: Path,
    transcript_path: Path,
    startup_timeout: int,
    selftest_timeout: int,
) -> None:
    if startup_timeout < 30 or selftest_timeout < 30:
        raise SmokeFailure("startup and self-test timeouts must each be at least 30 seconds")
    temporary = runtime / "tmp"
    environment = os.environ.copy()
    environment["TMP"] = str(temporary)
    environment["TEMP"] = str(temporary)
    command = [
        str(java),
        f"-Djava.io.tmpdir={temporary}",
        f"-Djna.tmpdir={temporary}",
        "-Xms512M",
        "-Xmx1G",
        "-jar",
        "paper.jar",
        "--nogui",
    ]
    process = subprocess.Popen(
        command,
        cwd=runtime,
        env=environment,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )
    output: queue.Queue[str] = queue.Queue()
    transcript: list[str] = []

    def read_output() -> None:
        assert process.stdout is not None
        for line in iter(process.stdout.readline, ""):
            output.put(line.rstrip("\r\n"))

    threading.Thread(target=read_output, name="paper-smoke-output", daemon=True).start()
    enabled = False
    started = False
    passed = False
    failed_detail: str | None = None
    stop_sent = False
    deadline = time.monotonic() + startup_timeout
    try:
        while process.poll() is None:
            if time.monotonic() >= deadline:
                stage = "shutdown" if passed else ("self-test" if started else "startup")
                failed_detail = f"{stage} timed out"
                break
            try:
                line = output.get(timeout=0.1)
            except queue.Empty:
                continue
            transcript.append(line)
            print(line, flush=True)
            if "[MobsThinkNowPaper] Enabling MobsThinkNowPaper" in line:
                enabled = True
            if not started and "Done (" in line:
                started = True
                deadline = time.monotonic() + selftest_timeout
                send_command(process, "mtnpaper selftest")
            if started and "[MTN SELFTEST FAIL]" in line:
                failed_detail = line
                break
            if started and not passed and "[MTN SELFTEST PASS]" in line:
                passed = True
                send_command(process, "mtnpaper status")
                send_command(process, "stop")
                stop_sent = True
                deadline = time.monotonic() + 45
        if process.poll() is None and not stop_sent:
            try:
                send_command(process, "stop")
                stop_sent = True
            except (BrokenPipeError, OSError, SmokeFailure):
                pass
        try:
            process.wait(timeout=45)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)
            failed_detail = failed_detail or "Paper did not stop within 45 seconds"
    finally:
        if process.poll() is None:
            process.kill()
            process.wait(timeout=10)
        while True:
            try:
                line = output.get_nowait()
            except queue.Empty:
                break
            transcript.append(line)
            print(line, flush=True)
        transcript_path.write_text("\n".join(transcript) + "\n", encoding="utf-8")

    if process.returncode != 0:
        raise SmokeFailure(f"Paper exited with code {process.returncode}; transcript: {transcript_path}")
    if failed_detail is not None:
        raise SmokeFailure(f"{failed_detail}; transcript: {transcript_path}")
    if not started or not enabled or not passed:
        raise SmokeFailure(
            f"missing marker(s): started={started}, pluginEnabled={enabled}, selftestPassed={passed}; "
            f"transcript: {transcript_path}"
        )
    print(f"[paper-smoke] PASS transcript={transcript_path}")


def main() -> int:
    args = parse_args()
    runtime = args.runtime.resolve()
    try:
        java = resolve_java(args.java)
        paper = provision_paper(args, runtime)
        transcript = prepare_runtime(args, runtime, paper)
        print(f"[paper-smoke] Paper={PAPER_VERSION}-{PAPER_BUILD} sha256={PAPER_SHA256}")
        print(f"[paper-smoke] plugin={args.plugin.resolve()}")
        run_server(runtime, java, transcript, args.startup_timeout, args.selftest_timeout)
        return 0
    except (SmokeFailure, OSError, urllib.error.URLError) as error:
        print(f"[paper-smoke] FAIL {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
