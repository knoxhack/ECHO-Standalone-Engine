#!/usr/bin/env python3
"""Import selected ECHO-Modules release graphs as Engine graph-only modules.

The standalone engine only executes modules compiled against its public API.
Many current ECHO-Modules standalone jars target older runtime contracts, but
their embedded .ECHO Content Graphs are still valuable for graph-first vertical
slice expansion. This script extracts those graphs, strips executable
entrypoints, filters duplicate shared runtime nodes, and writes deterministic
resource-only module folders under modules/.
"""
from __future__ import annotations

import argparse
import json
import shutil
import zipfile
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_RELEASE_ROOT = ROOT.parent / "ECHO-Modules" / "dist" / "echo-module-release"

GRAPH_SLICE_MODULES = [
    "echocore",
    "echoplatformcore",
    "echoschemacore",
    "echovalidationcore",
    "echocontentcore",
    "echohudcore",
]

CROSS_RUNTIME_TARGETS = ["echo_native", "neoforge", "echo_runtime_standalone"]


def read_json_from_zip(archive: zipfile.ZipFile, name: str, fallback: Any = None) -> Any:
    try:
        with archive.open(name) as stream:
            return json.loads(stream.read().decode("utf-8"))
    except KeyError:
        return fallback


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=False) + "\n", encoding="utf-8")


def module_ids_in_engine() -> set[str]:
    return {
        path.name
        for path in (ROOT / "modules").iterdir()
        if path.is_dir()
    }


def find_standalone_jar(release_root: Path, module_id: str) -> Path:
    module_root = release_root / module_id
    matches = sorted(module_root.glob(f"{module_id}-*-standalone.jar"))
    if len(matches) != 1:
        raise RuntimeError(f"Expected one standalone jar for {module_id} in {module_root}, found {len(matches)}")
    return matches[0]


def dependency_id(value: Any) -> str:
    if isinstance(value, str):
        return value.strip().lower()
    if isinstance(value, dict):
        return str(value.get("id") or value.get("moduleId") or "").strip().lower()
    return ""


def optional_dependency(value: Any) -> bool:
    return isinstance(value, dict) and bool(value.get("optional"))


def normalize_requires(descriptor: dict[str, Any], available: set[str], module_id: str) -> list[Any]:
    result: list[Any] = []
    for raw in descriptor.get("requires") or descriptor.get("dependencies") or []:
        dep_id = dependency_id(raw)
        if not dep_id:
            continue
        if dep_id not in available:
            if optional_dependency(raw):
                continue
            raise RuntimeError(f"{module_id} requires {dep_id}, which is not in the Engine graph slice closure")
        result.append(raw)
    return result


def node_owner(node: dict[str, Any], module_id: str) -> str:
    owner = str(node.get("moduleId") or node.get("module") or node.get("owner") or "").strip().lower()
    if owner:
        return owner
    node_id = str(node.get("id") or node.get("nodeId") or node.get("contentId") or "")
    return module_id if node_id.startswith(f"{module_id}:") else ""


def node_id(node: dict[str, Any]) -> str:
    return str(node.get("id") or node.get("nodeId") or node.get("contentId") or "").strip()


def node_kind(node: dict[str, Any]) -> str:
    return str(node.get("kind") or node.get("type") or node.get("nodeType") or node.get("contentKind") or "").strip()


def normalize_graph(raw_graph: dict[str, Any], module_id: str) -> tuple[dict[str, Any], set[str]]:
    body = raw_graph.get("graph") if isinstance(raw_graph.get("graph"), dict) else raw_graph
    retained_nodes = []
    retained_ids: set[str] = set()
    for raw_node in body.get("nodes") or []:
        if not isinstance(raw_node, dict):
            continue
        current_node_id = node_id(raw_node)
        if not current_node_id:
            continue
        if node_owner(raw_node, module_id) != module_id:
            continue
        node = dict(raw_node)
        node.setdefault("moduleId", module_id)
        retained_nodes.append(node)
        retained_ids.add(current_node_id)

    retained_edges = []
    for raw_edge in body.get("edges") or []:
        if not isinstance(raw_edge, dict):
            continue
        source = str(raw_edge.get("source") or raw_edge.get("from") or raw_edge.get("sourceId") or "").strip()
        target = str(raw_edge.get("target") or raw_edge.get("to") or raw_edge.get("targetId") or "").strip()
        if source in retained_ids and target in retained_ids:
            retained_edges.append(raw_edge)

    return {
        "schemaVersion": raw_graph.get("schemaVersion", "echo.content_graph.v1"),
        "moduleId": module_id,
        "nodes": retained_nodes,
        "edges": retained_edges,
    }, retained_ids


def normalize_features(raw_features: Any, module_id: str, retained_ids: set[str]) -> dict[str, Any]:
    if not isinstance(raw_features, dict):
        raw_features = {}
    features = []
    for raw_feature in raw_features.get("features") or raw_features.get("items") or []:
        if not isinstance(raw_feature, dict):
            continue
        owner = str(raw_feature.get("moduleId") or raw_feature.get("module") or module_id).strip().lower()
        if owner != module_id:
            continue
        node_ids = [
            str(value)
            for value in raw_feature.get("nodeIds") or raw_feature.get("nodes") or raw_feature.get("contentIds") or []
            if str(value) in retained_ids
        ]
        if not node_ids:
            continue
        feature = dict(raw_feature)
        feature["moduleId"] = module_id
        feature["nodeIds"] = node_ids
        features.append(feature)
    return {
        "schemaVersion": raw_features.get("schemaVersion", "echo.content_feature_list.v1"),
        "moduleId": module_id,
        "features": features,
    }


def export_plan(module_id: str, target: str, graph: dict[str, Any]) -> dict[str, Any]:
    mappings = []
    for node in graph["nodes"]:
        current_id = node_id(node)
        current_kind = node_kind(node)
        mappings.append({
            "nodeId": current_id,
            "runtimeId": current_id,
            "kind": current_kind,
            "adapter": f"graph.{target}.{current_kind.lower().replace(':', '_')}",
        })
    return {
        "schemaVersion": "echo.content_graph.export_plan.v1",
        "moduleId": module_id,
        "runtimeTarget": target,
        "mappings": mappings,
    }


def normalize_descriptor(descriptor: dict[str, Any], jar_name: str, available: set[str]) -> dict[str, Any]:
    module_id = str(descriptor["id"]).strip().lower()
    return {
        "schemaVersion": "echo.module.descriptor.v1",
        "id": module_id,
        "name": descriptor.get("name") or module_id,
        "version": descriptor.get("version") or "1.0.0",
        "kind": descriptor.get("kind") or descriptor.get("role") or "graph-first-system",
        "side": "both",
        "standalone": True,
        "official": True,
        "trust": "official",
        "requires": normalize_requires(descriptor, available, module_id),
        "optional": [],
        "access": {
            "nativeClasspath": ["."],
            "permissions": [],
            "contentNamespaces": [module_id],
            "contractsOnly": True,
            "notes": "Graph-only Engine slice import; executable entrypoint intentionally stripped.",
        },
        "generatedFrom": {
            "repo": "knoxhack/ECHO-Modules",
            "artifact": jar_name,
            "mode": "graph-only-engine-slice",
        },
    }


def import_module(release_root: Path, module_id: str, available: set[str], force: bool) -> dict[str, Any]:
    jar = find_standalone_jar(release_root, module_id)
    target = ROOT / "modules" / module_id
    if target.exists():
        if not force:
            raise RuntimeError(f"{target} already exists; pass --force to overwrite generated graph slice modules")
        shutil.rmtree(target)

    resources = target / "src" / "main" / "resources"
    with zipfile.ZipFile(jar) as archive:
        descriptor = read_json_from_zip(archive, "META-INF/echo.mod.json")
        graph, retained_ids = normalize_graph(
            read_json_from_zip(archive, ".echo/content-graph/content-graph.json", {}),
            module_id,
        )
        if not graph["nodes"]:
            raise RuntimeError(f"{module_id} did not retain any module-owned graph nodes")
        features = normalize_features(
            read_json_from_zip(archive, ".echo/content-graph/features.json", {}),
            module_id,
            retained_ids,
        )
        provenance = read_json_from_zip(archive, ".echo/content-graph/provenance.json", {})

    write_json(resources / "META-INF" / "echo.mod.json", normalize_descriptor(descriptor, jar.name, available))
    graph_root = resources / ".echo" / "content-graph"
    write_json(graph_root / "content-graph.json", graph)
    write_json(graph_root / "features.json", features)
    write_json(graph_root / "provenance.json", {
        **(provenance if isinstance(provenance, dict) else {}),
        "moduleId": module_id,
        "sourceArtifact": jar.name,
        "engineImportMode": "graph-only",
    })
    write_json(graph_root / "unresolved-references.json", [])
    for target_name in CROSS_RUNTIME_TARGETS:
        write_json(
            graph_root / "export-plans" / f"{target_name}.json",
            export_plan(module_id, target_name, graph),
        )
    return {
        "moduleId": module_id,
        "artifact": jar.name,
        "nodes": len(graph["nodes"]),
        "edges": len(graph["edges"]),
        "features": len(features["features"]),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--release-root", type=Path, default=DEFAULT_RELEASE_ROOT)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    available = module_ids_in_engine() | set(GRAPH_SLICE_MODULES)
    imported = [
        import_module(args.release_root, module_id, available, args.force)
        for module_id in GRAPH_SLICE_MODULES
    ]
    print(json.dumps({
        "schemaVersion": "echo.standalone_engine.graph_slice_import.v1",
        "imported": imported,
    }, indent=2))


if __name__ == "__main__":
    main()
