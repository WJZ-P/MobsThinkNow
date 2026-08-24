#!/usr/bin/env python3
"""Build-artifact smoke test against one pinned, checksum-verified Paper server."""

from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import queue
import re
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
        description="Start pinned Paper, verify disable/enable reloads, run selftest, and stop cleanly."
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
        default=root / ".gradle" / "paper-smoke",
        help="Isolated Paper directory (defaults to the persistent ignored Gradle cache).",
    )
    parser.add_argument(
        "--paper-jar",
        type=Path,
        help="Use this local Paper JAR instead of downloading the pinned object.",
    )
    parser.add_argument("--java", type=Path, help="Java executable; otherwise JAVA_HOME/PATH is used.")
    parser.add_argument(
        "--jfr-output",
        type=Path,
        help="Record the Paper process with JFR profile settings and verify the resulting file.",
    )
    parser.add_argument("--offline", action="store_true", help="Fail instead of downloading a missing Paper JAR.")
    parser.add_argument("--keep-world", action="store_true", help="Reuse the previous isolated smoke-test world.")
    parser.add_argument("--startup-timeout", type=int, default=180)
    parser.add_argument("--selftest-timeout", type=int, default=90)
    parser.add_argument(
        "--selftest-runs",
        type=int,
        default=1,
        help="Run the combat self-test repeatedly in the same Paper process (1-100).",
    )
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


def validate_runtime(runtime: Path) -> None:
    root = Path(__file__).resolve().parents[1]
    filesystem_root = Path(runtime.anchor).resolve()
    forbidden = {filesystem_root, root.resolve(), Path.home().resolve()}
    if runtime in forbidden:
        raise SmokeFailure(f"refusing unsafe smoke runtime directory: {runtime}")


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
    root = Path(__file__).resolve().parents[1]
    default_runtime = (root / ".gradle" / "paper-smoke").resolve()
    legacy_runtime = root / "build" / "paper-smoke"
    if runtime == default_runtime and not runtime.exists() and legacy_runtime.is_dir():
        runtime.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(legacy_runtime), runtime)
        print(f"[paper-smoke] migrated runtime cache from {legacy_runtime}")
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


def write_smoke_config(runtime: Path, enabled: bool) -> None:
    plugin_data = runtime / "plugins" / "MobsThinkNowPaper"
    plugin_data.mkdir(parents=True, exist_ok=True)
    # Hard difficulty plus a 100% base crossbow chance makes every possible hard-mode IQ a
    # deterministic success. The runtime self-test can therefore prove that a real NATURAL
    # CreatureSpawnEvent is processed once, while normal installations retain the 18% default.
    (plugin_data / "config.yml").write_text(
        f"""enabled: {str(enabled).lower()}
skeleton:
  crossbow:
    natural-loadout:
      enabled: true
      crossbow-chance: 1.0
      firework-crossbow-chance: 1.0
""",
        encoding="utf-8",
    )


def write_malformed_smoke_config(runtime: Path) -> None:
    (runtime / "plugins" / "MobsThinkNowPaper" / "config.yml").write_text(
        "enabled: true\nspider: [unterminated\n",
        encoding="utf-8",
    )


def write_duplicate_smoke_config(runtime: Path) -> None:
    (runtime / "plugins" / "MobsThinkNowPaper" / "config.yml").write_text(
        "enabled: true\nenabled: false\n",
        encoding="utf-8",
    )


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
    write_smoke_config(runtime, True)
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
    selftest_runs: int,
    jfr_output: Path | None,
) -> None:
    if startup_timeout < 30 or selftest_timeout < 30:
        raise SmokeFailure("startup and self-test timeouts must each be at least 30 seconds")
    if selftest_runs < 1 or selftest_runs > 100:
        raise SmokeFailure("self-test runs must be between 1 and 100")
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
    ]
    if jfr_output is not None:
        command.append(
            f"-XX:StartFlightRecording=filename={jfr_output},settings=profile,dumponexit=true"
        )
    command.extend(("-jar", "paper.jar", "--nogui"))
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
    stage = "startup"
    invalid_reload_verified = False
    duplicate_reload_verified = False
    disabled_reload_verified = False
    enabled_reload_verified = False
    reloaded = False
    started = False
    passed = False
    selftests_passed = 0
    cleanup_status_checks = 0
    failed_detail: str | None = None
    stop_sent = False
    deadline = time.monotonic() + startup_timeout
    try:
        while process.poll() is None:
            if time.monotonic() >= deadline:
                failed_detail = f"{stage} timed out"
                break
            try:
                line = output.get(timeout=0.1)
            except queue.Empty:
                continue
            transcript.append(line)
            print(line, flush=True)
            startup_configuration_failure = stage == "startup" and any(marker in line for marker in (
                "Cannot load configuration from stream",
                "InvalidConfigurationException",
            ))
            if startup_configuration_failure or "Error occurred while enabling MobsThinkNowPaper" in line:
                failed_detail = f"fatal plugin configuration/startup log: {line}"
                break
            if "[MobsThinkNowPaper] Enabling MobsThinkNowPaper" in line:
                enabled = True
            if stage == "startup" and "Done (" in line:
                write_malformed_smoke_config(runtime)
                stage = "invalid-reload"
                deadline = time.monotonic() + selftest_timeout
                send_command(process, "mtnpaper reload")
            elif stage == "invalid-reload" and "configuration reload failed;" in line:
                stage = "invalid-status"
                send_command(process, "mtnpaper status")
            elif stage == "invalid-status" and "Mobs Think Now Paper | enabled=true" in line:
                expected = (
                    "projectileSensorRunning=true",
                    "webTrapSchedulerRunning=true",
                    "fireworkSchedulerRunning=true",
                    "squadSchedulerRunning=true",
                )
                missing = [marker for marker in expected if marker not in line]
                if missing:
                    failed_detail = "invalid reload changed runtime state: " + ", ".join(missing)
                    break
                invalid_reload_verified = True
                write_duplicate_smoke_config(runtime)
                stage = "duplicate-reload"
                deadline = time.monotonic() + selftest_timeout
                send_command(process, "mtnpaper reload")
            elif stage == "duplicate-reload" and "configuration reload failed;" in line:
                stage = "duplicate-status"
                send_command(process, "mtnpaper status")
            elif stage == "duplicate-status" and "Mobs Think Now Paper | enabled=true" in line:
                expected = (
                    "projectileSensorRunning=true",
                    "webTrapSchedulerRunning=true",
                    "fireworkSchedulerRunning=true",
                    "squadSchedulerRunning=true",
                )
                missing = [marker for marker in expected if marker not in line]
                if missing:
                    failed_detail = "duplicate-key reload changed runtime state: " + ", ".join(missing)
                    break
                duplicate_reload_verified = True
                write_smoke_config(runtime, False)
                stage = "disable-reload"
                deadline = time.monotonic() + selftest_timeout
                send_command(process, "mtnpaper reload")
            elif stage == "disable-reload" and "configuration reloaded." in line:
                stage = "disable-status"
                send_command(process, "mtnpaper status")
            elif stage == "disable-status" and "Mobs Think Now Paper | enabled=false" in line:
                expected = (
                    "projectileSensorRunning=false",
                    "webTrapSchedulerRunning=false",
                    "fireworkSchedulerRunning=false",
                    "squadSchedulerRunning=false",
                )
                missing = [marker for marker in expected if marker not in line]
                if missing:
                    failed_detail = "disabled reload left scheduler(s) active: " + ", ".join(missing)
                    break
                disabled_reload_verified = True
                write_smoke_config(runtime, True)
                stage = "enable-reload"
                deadline = time.monotonic() + selftest_timeout
                send_command(process, "mtnpaper reload")
            elif stage == "enable-reload" and "configuration reloaded." in line:
                stage = "enable-status"
                send_command(process, "mtnpaper status")
            elif stage == "enable-status" and "Mobs Think Now Paper | enabled=true" in line:
                expected = (
                    "projectileSensorRunning=true",
                    "webTrapSchedulerRunning=true",
                    "fireworkSchedulerRunning=true",
                    "squadSchedulerRunning=true",
                )
                missing = [marker for marker in expected if marker not in line]
                if missing:
                    failed_detail = "enabled reload did not restart scheduler(s): " + ", ".join(missing)
                    break
                enabled_reload_verified = True
                reloaded = True
                started = True
                stage = "self-test"
                deadline = time.monotonic() + selftest_timeout
                send_command(process, "mtnpaper selftest")
            if stage == "self-test" and "[MTN SELFTEST FAIL]" in line:
                failed_detail = line
                break
            if stage == "self-test" and "[MTN SELFTEST PASS]" in line:
                selftests_passed += 1
                cleanup_status_checks = 0
                stage = "self-test-cleanup"
                deadline = time.monotonic() + selftest_timeout
                send_command(process, "mtnpaper status")
            elif stage == "self-test-cleanup" and "Mobs Think Now Paper | enabled=true" in line:
                cleanup_markers = (
                    "loadedSupportedMobs=0",
                    "cachedIntelligence=0",
                    "trackedProjectiles=0",
                    "activeFireworkBolts=0",
                    "activeCreeperFeints=0",
                    "coolingCreeperFeints=0",
                    "activeBlastReservations=0",
                    "activePounceReservations=0",
                    "activeWebTraps=0",
                    "activeWebTrapOwners=0",
                    "activeSquads=0",
                    "squadMembers=0",
                    "pendingDamageMemories=0",
                    "pendingShieldSignals=0",
                    "disabledShieldGuards=0",
                )
                missing = [marker for marker in cleanup_markers if marker not in line]
                for metric in ("intelligencePersistentReads", "intelligenceCacheHits",
                               "directiveComputations", "directiveCacheHits",
                               "geometryComputations", "geometryCacheHits"):
                    match = re.search(rf"(?:^|, ){metric}=(\d+)(?:,|$)", line)
                    if match is None or int(match.group(1)) <= 0:
                        missing.append(metric + ">0")
                if missing:
                    cleanup_status_checks += 1
                    if cleanup_status_checks >= 20:
                        failed_detail = (
                            f"self-test {selftests_passed} left runtime state active after "
                            f"{cleanup_status_checks} checks: " + ", ".join(missing)
                        )
                        break
                    send_command(process, "mtnpaper status")
                    continue
                if selftests_passed < selftest_runs:
                    stage = "self-test"
                    deadline = time.monotonic() + selftest_timeout
                    send_command(process, "mtnpaper selftest")
                else:
                    passed = True
                    send_command(process, "stop")
                    stop_sent = True
                    stage = "shutdown"
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
    if not started or not enabled or not invalid_reload_verified or not duplicate_reload_verified or not disabled_reload_verified or not enabled_reload_verified or not reloaded or not passed:
        raise SmokeFailure(
            f"missing marker(s): started={started}, pluginEnabled={enabled}, "
            f"invalidReload={invalid_reload_verified}, duplicateReload={duplicate_reload_verified}, "
            f"disabledReload={disabled_reload_verified}, "
            f"enabledReload={enabled_reload_verified}, reloaded={reloaded}, "
            f"selftests={selftests_passed}/{selftest_runs}, selftestPassed={passed}; "
            f"transcript: {transcript_path}"
        )
    if jfr_output is not None:
        if not jfr_output.is_file() or jfr_output.stat().st_size == 0:
            raise SmokeFailure(f"JFR recording was not created: {jfr_output}")
        print(f"[paper-smoke] JFR={jfr_output} bytes={jfr_output.stat().st_size}")
    print(f"[paper-smoke] PASS transcript={transcript_path}")


def main() -> int:
    args = parse_args()
    runtime = args.runtime.resolve()
    try:
        validate_runtime(runtime)
        if args.startup_timeout < 30 or args.selftest_timeout < 30:
            raise SmokeFailure("startup and self-test timeouts must each be at least 30 seconds")
        if args.selftest_runs < 1 or args.selftest_runs > 100:
            raise SmokeFailure("self-test runs must be between 1 and 100")
        java = resolve_java(args.java)
        jfr_output = args.jfr_output.resolve() if args.jfr_output is not None else None
        if jfr_output is not None:
            if "," in str(jfr_output):
                raise SmokeFailure("JFR output path must not contain a comma")
            jfr_output.parent.mkdir(parents=True, exist_ok=True)
            jfr_output.unlink(missing_ok=True)
        paper = provision_paper(args, runtime)
        transcript = prepare_runtime(args, runtime, paper)
        print(f"[paper-smoke] Paper={PAPER_VERSION}-{PAPER_BUILD} sha256={PAPER_SHA256}")
        print(f"[paper-smoke] plugin={args.plugin.resolve()}")
        run_server(
            runtime,
            java,
            transcript,
            args.startup_timeout,
            args.selftest_timeout,
            args.selftest_runs,
            jfr_output,
        )
        return 0
    except (SmokeFailure, OSError, urllib.error.URLError) as error:
        print(f"[paper-smoke] FAIL {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
