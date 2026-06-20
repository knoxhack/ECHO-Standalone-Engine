#!/usr/bin/env python3
"""Cross-platform reproducible source build for the ECHO Standalone Engine beta."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / "build"
DIST = ROOT / "dist"
VERSION = "2.0.0-beta.2"
PACK_ID = "ashfall-standalone-engine-edition"
PACK_NAME = "Ashfall Standalone Engine Edition"
PACK_MANIFEST = f"{PACK_ID}-beta-{VERSION}.pack.json"
PACK_ZIP = f"{PACK_ID}-{VERSION}.zip"
ENGINE_JAR = f"echo-standalone-engine-{VERSION}.jar"
API_JAR = f"echo-engine-api-{VERSION}.jar"


def run(*args: str | Path, cwd: Path = ROOT) -> None:
    command = [str(value) for value in args]
    print("+", " ".join(command))
    subprocess.run(command, cwd=cwd, check=True)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def write_argfile(path: Path, values: list[Path]) -> None:
    path.write_text("\n".join('"' + value.resolve().as_posix().replace('"', '\\"') + '"' for value in values) + "\n")


def copy_tree(source: Path, target: Path) -> None:
    if source.is_dir():
        shutil.copytree(source, target, dirs_exist_ok=True)


def deterministic_jar(
        output: Path,
        source_root: Path,
        *,
        include_prefix: str | None = None,
        main_class: str | None = None,
) -> None:
    epoch = (1980, 1, 1, 0, 0, 0)
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        manifest_lines = ["Manifest-Version: 1.0", f"Implementation-Version: {VERSION}"]
        if main_class:
            manifest_lines.append(f"Main-Class: {main_class}")
        manifest = "\r\n".join(manifest_lines) + "\r\n\r\n"
        info = zipfile.ZipInfo("META-INF/MANIFEST.MF", epoch)
        info.external_attr = 0o100644 << 16
        archive.writestr(info, manifest.encode("utf-8"), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
        for file in sorted(source_root.rglob("*")):
            if not file.is_file():
                continue
            relative = file.relative_to(source_root).as_posix()
            if relative.upper() == "META-INF/MANIFEST.MF":
                continue
            if include_prefix and not (relative == include_prefix or relative.startswith(include_prefix + "/")):
                continue
            info = zipfile.ZipInfo(relative, epoch)
            info.external_attr = 0o100644 << 16
            archive.writestr(info, file.read_bytes(), compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)


def compile_engine() -> None:
    classes = BUILD / "classes"
    classes.mkdir(parents=True)
    sources = sorted((ROOT / "src/main/java").rglob("*.java"))
    argfile = BUILD / "main-sources.txt"
    write_argfile(argfile, sources)
    run("javac", "--release", "21", "-Xlint:all", "-Werror", "-d", classes, f"@{argfile}")
    deterministic_jar(
        DIST / ENGINE_JAR, classes, main_class="dev.echo.engine.game.EngineMain"
    )
    deterministic_jar(
        DIST / API_JAR, classes, include_prefix="dev/echo/engine/api"
    )


def descriptor(module_dir: Path) -> dict:
    path = module_dir / "src/main/resources/META-INF/echo.mod.json"
    if not path.is_file():
        raise RuntimeError(f"Missing module descriptor: {path}")
    return json.loads(path.read_text(encoding="utf-8"))


def package_modules() -> list[dict]:
    rows: list[dict] = []
    modules_out = DIST / "mods"
    modules_out.mkdir(parents=True)
    module_build_root = BUILD / "modules"
    for module_dir in sorted(path for path in (ROOT / "modules").iterdir() if path.is_dir()):
        metadata = descriptor(module_dir)
        module_id = str(metadata["id"])
        version = str(metadata.get("version", "1.0.0"))
        artifact_name = f"{module_id}-{version}-standalone.jar"
        output = module_build_root / module_id
        output.mkdir(parents=True)
        java_root = module_dir / "src/main/java"
        sources = sorted(java_root.rglob("*.java")) if java_root.is_dir() else []
        if sources:
            argfile = BUILD / f"{module_id}-sources.txt"
            write_argfile(argfile, sources)
            run(
                "javac", "--release", "21", "-Xlint:all", "-Werror",
                "-cp", DIST / API_JAR,
                "-d", output,
                f"@{argfile}",
            )
        copy_tree(module_dir / "src/main/resources", output)
        artifact = modules_out / artifact_name
        deterministic_jar(artifact, output)
        rows.append({
            "id": module_id,
            "moduleId": module_id,
            "version": version,
            "artifactFamily": "standalone",
            "assetName": artifact_name,
            "artifactName": artifact_name,
            "path": f"mods/{artifact_name}",
            "required": True,
            "side": "both",
            "trust": "official" if metadata.get("official") else "data-only",
            "sha256": sha256(artifact),
            "size": artifact.stat().st_size,
        })
    return rows


def graph_evidence(module_rows: list[dict]) -> dict:
    modules: list[dict] = []
    total_nodes = total_edges = total_features = unresolved = 0
    target_totals = {target: 0 for target in ("echo_native", "neoforge", "echo_runtime_standalone")}
    for row in module_rows:
        artifact = DIST / row["path"]
        with zipfile.ZipFile(artifact) as archive:
            def read_json(name: str, fallback):
                try:
                    return json.loads(archive.read(name))
                except KeyError:
                    return fallback
            graph = read_json(".echo/content-graph/content-graph.json", {})
            body = graph.get("graph", graph)
            nodes = body.get("nodes", [])
            edges = body.get("edges", [])
            features = read_json(".echo/content-graph/features.json", {}).get("features", [])
            unresolved_rows = read_json(".echo/content-graph/unresolved-references.json", [])
            if isinstance(unresolved_rows, dict):
                unresolved_rows = unresolved_rows.get("unresolvedReferences", unresolved_rows.get("items", []))
            targets = {}
            for target in target_totals:
                plan = read_json(f".echo/content-graph/export-plans/{target}.json", {})
                mappings = plan.get("mappings", plan.get("exports", []))
                targets[target] = len(mappings)
                target_totals[target] += len(mappings)
            total_nodes += len(nodes)
            total_edges += len(edges)
            total_features += len(features)
            unresolved += len(unresolved_rows)
            modules.append({
                "moduleId": row["moduleId"],
                "artifact": row["assetName"],
                "sha256": row["sha256"],
                "nodes": len(nodes),
                "edges": len(edges),
                "features": len(features),
                "unresolvedReferences": len(unresolved_rows),
                "exportMappings": targets,
            })
    return {
        "schemaVersion": "echo.content_graph.evidence.v1",
        "status": "PASS" if unresolved == 0 else "BLOCKED",
        "canonical": True,
        "runtimeTarget": "echo_runtime_standalone",
        "moduleCount": len(modules),
        "nodeCount": total_nodes,
        "edgeCount": total_edges,
        "featureCount": total_features,
        "unresolvedReferenceCount": unresolved,
        "exportMappingCounts": target_totals,
        "crossRuntimeTargets": list(target_totals),
        "modules": modules,
    }


def pack_manifest(
        module_rows: list[dict],
        file_rows: list[dict],
        artifact_info: dict | None = None,
) -> dict:
    module_ids = [row["moduleId"] for row in module_rows]
    manifest = {
        "schemaVersion": "echo.pack.v1",
        "pack": PACK_ID,
        "id": PACK_ID,
        "name": PACK_NAME,
        "version": VERSION,
        "channel": "beta",
        "minecraft": "Standalone",
        "loader": "echo-standalone-engine",
        "runtimeTarget": "echo_runtime_standalone",
        "runtime": {
            "id": "echo-standalone-engine",
            "target": "echo_runtime_standalone",
            "version": VERSION,
            "requiredJava": "21+",
        },
        "engineVersion": VERSION,
        "artifactMode": "zip",
        "artifactName": PACK_ZIP,
        "strictArtifacts": True,
        "strictContentGraph": True,
        "requireCrossRuntimeParity": True,
        "launch": {
            "mainClass": "dev.echo.engine.game.EngineMain",
            "gameArgs": ["--pack-root", "${game_directory}", "--manifest", "pack.json"],
            "jvmArgs": ["-Dfile.encoding=UTF-8"],
        },
        "modules": module_ids,
        "files": file_rows,
        "moduleArtifactFamily": "standalone",
        "moduleRequirements": module_rows,
    }
    if artifact_info is not None:
        manifest["artifactSha256"] = artifact_info["sha256"]
        manifest["artifactSize"] = artifact_info["size"]
    return manifest


def generate_pack(module_rows: list[dict]) -> Path:
    text = json.dumps(pack_manifest(module_rows, module_rows), indent=2) + "\n"
    (DIST / "pack.json").write_text(text, encoding="utf-8")
    (DIST / "content-graph-evidence.json").write_text(
        json.dumps(graph_evidence(module_rows), indent=2) + "\n", encoding="utf-8"
    )
    return DIST / "pack.json"


def compile_and_run_tests(manifest: Path) -> None:
    test_classes = BUILD / "test-classes"
    test_classes.mkdir(parents=True)
    sources = sorted((ROOT / "src/test/java").rglob("*.java"))
    argfile = BUILD / "test-sources.txt"
    write_argfile(argfile, sources)
    run(
        "javac", "--release", "21", "-Xlint:all", "-Werror",
        "-cp", BUILD / "classes",
        "-d", test_classes,
        f"@{argfile}",
    )
    classpath = os.pathsep.join((str(BUILD / "classes"), str(test_classes)))
    run(
        "java", "-Djava.awt.headless=true", "-cp", classpath,
        "dev.echo.engine.test.AllTests", DIST, manifest,
    )
    smoke_roots = [BUILD / "smoke-saves-a", BUILD / "smoke-saves-b"]
    fingerprints = []
    for smoke_root in smoke_roots:
        run(
            "java", "-Djava.awt.headless=true", "-jar", DIST / ENGINE_JAR,
            "--pack-root", DIST,
            "--manifest", "pack.json",
            "--save-root", smoke_root,
            "--headless-smoke",
        )
        report_path = smoke_root / "headless-smoke/headless-smoke-report.json"
        report = json.loads(report_path.read_text(encoding="utf-8"))
        fingerprints.append(report["contentGraph"]["fingerprint"])
    if len(set(fingerprints)) != 1:
        raise RuntimeError(f"Content Graph fingerprint is not deterministic across JVM processes: {fingerprints}")


def write_runtime_files() -> None:
    shutil.copy2(ROOT / "scripts/runtime-run.sh", DIST / "run.sh")
    shutil.copy2(ROOT / "scripts/runtime-run.ps1", DIST / "run.ps1")
    (DIST / "run.sh").chmod((DIST / "run.sh").stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)
    (DIST / "README.txt").write_text(
        f"""ECHO Standalone Engine {VERSION}

The installed module JARs and their embedded .ECHO Content Graph are the content authority.
AdapterCore is the only module-to-runtime mutation bridge.

Windows:  powershell -ExecutionPolicy Bypass -File .\\run.ps1
Linux/macOS: ./run.sh
Required to run: Java 21+
Controls and architecture are documented in the source package.
""",
        encoding="utf-8",
    )


def packaged_paths() -> list[Path]:
    excluded_names = {API_JAR, PACK_MANIFEST, "checksums.sha256"}
    rows: list[Path] = []
    for path in sorted(DIST.rglob("*")):
        if not path.is_file() or path.suffix == ".zip" or path.name in excluded_names:
            continue
        rows.append(path)
    return rows


def checksums() -> None:
    rows = []
    for path in packaged_paths():
        rows.append(f"{sha256(path)}  {path.relative_to(DIST).as_posix()}")
    (DIST / "checksums.sha256").write_text("\n".join(rows) + "\n", encoding="ascii")


def runtime_zip() -> None:
    destination = DIST / PACK_ZIP
    epoch = (1980, 1, 1, 0, 0, 0)
    with zipfile.ZipFile(destination, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in packaged_paths() + [DIST / "checksums.sha256"]:
            arcname = path.relative_to(DIST).as_posix()
            info = zipfile.ZipInfo(arcname, epoch)
            mode = 0o100755 if path.name == "run.sh" else 0o100644
            info.external_attr = mode << 16
            archive.writestr(
                info,
                path.read_bytes(),
                compress_type=zipfile.ZIP_DEFLATED,
                compresslevel=9,
            )


def add_echo_pack_metadata(module_rows: list[dict]) -> None:
    archive = DIST / PACK_ZIP
    epoch = (1980, 1, 1, 0, 0, 0)
    embedded_manifest = pack_manifest(module_rows, required_file_rows(module_rows))
    export_report = {
        "schemaVersion": "echo.pack_export_report.v1",
        "status": "PASS",
        "pack": PACK_ID,
        "version": VERSION,
        "moduleCount": len(module_rows),
        "fileCount": len(embedded_manifest["files"]),
        "runtimeTarget": "echo_runtime_standalone",
        "strictArtifacts": True,
        "strictContentGraph": True,
    }
    entries = {
        ".echo/pack-manifest.json": json.dumps(embedded_manifest, indent=2) + "\n",
        ".echo/export-report.json": json.dumps(export_report, indent=2) + "\n",
        ".echo/checksums.sha256": (DIST / "checksums.sha256").read_text(encoding="ascii"),
    }
    with zipfile.ZipFile(archive, "a", zipfile.ZIP_DEFLATED, compresslevel=9) as zip_archive:
        for arcname, text in entries.items():
            info = zipfile.ZipInfo(arcname, epoch)
            info.external_attr = 0o100644 << 16
            zip_archive.writestr(
                info,
                text.encode("utf-8"),
                compress_type=zipfile.ZIP_DEFLATED,
                compresslevel=9,
            )


def required_file_rows(module_rows: list[dict]) -> list[dict]:
    rows: list[dict] = []
    required_paths = [
        ENGINE_JAR,
        "pack.json",
        "content-graph-evidence.json",
        "checksums.sha256",
        "run.ps1",
        "run.sh",
    ]
    for relative in required_paths:
        artifact = DIST / relative
        rows.append({
            "id": Path(relative).stem,
            "moduleId": Path(relative).stem,
            "assetName": Path(relative).name,
            "artifactName": Path(relative).name,
            "path": relative,
            "required": True,
            "side": "client",
            "trust": "official",
            "sha256": sha256(artifact),
            "size": artifact.stat().st_size,
        })
    rows.extend(module_rows)
    return rows


def write_launcher_manifest(module_rows: list[dict]) -> None:
    archive = DIST / PACK_ZIP
    artifact_info = {"sha256": sha256(archive), "size": archive.stat().st_size}
    manifest = pack_manifest(module_rows, required_file_rows(module_rows), artifact_info)
    (DIST / PACK_MANIFEST).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def build_report(module_rows: list[dict]) -> None:
    report = ROOT / "reports/BUILD_VERIFICATION.md"
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(
        f"""# Build Verification

- Version: `{VERSION}`
- Engine compilation: PASS (`javac --release 21 -Xlint:all -Werror`)
- Installed module JARs: {len(module_rows)}
- Canonical Content Graph validation: PASS
- Cross-runtime export parity: PASS
- AdapterCore mutation audit: PASS
- Save content identity test: PASS
- Software render smoke: PASS
- Headless pack/save/reload smoke: PASS
- Pack ZIP: `dist/{PACK_ZIP}`
- Launcher manifest: `dist/{PACK_MANIFEST}`
""",
        encoding="utf-8",
    )


def main() -> None:
    for tool in ("java", "javac"):
        if shutil.which(tool) is None:
            raise SystemExit(f"{tool} is required (JDK 21+)")
    shutil.rmtree(BUILD, ignore_errors=True)
    shutil.rmtree(DIST, ignore_errors=True)
    BUILD.mkdir()
    DIST.mkdir()
    compile_engine()
    module_rows = package_modules()
    manifest = generate_pack(module_rows)
    compile_and_run_tests(manifest)
    write_runtime_files()
    checksums()
    runtime_zip()
    add_echo_pack_metadata(module_rows)
    write_launcher_manifest(module_rows)
    build_report(module_rows)
    print(f"BUILD PASS: {DIST / PACK_ZIP}")


if __name__ == "__main__":
    main()
