#!/usr/bin/env python3
"""Public API compatibility gate for filegdb4j.

Dumps the public API of the given jars in a normalized, comparable form and
compares it against a stored baseline. Normalization removes differences that
are irrelevant for source/binary compatibility (for example the implicit
``extends java.lang.Record`` superclass, which disappears when records are
converted to plain final classes for Java 8).

Usage:
    api.py dump <output-dir> <jar>...
    api.py check <baseline-dir> <jar>...

Only *removed* or *changed* public API entries are reported as errors; new
members are allowed (additive changes).
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
import zipfile

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
JAVAP = os.environ.get("JAVAP", "javap")

MODULE_JARS = (
    ("geometry", "filegdb4j-geometry"),
    ("core", "filegdb4j-core"),
    ("jts", "filegdb4j-jts"),
)


def module_name(jar: str) -> str:
    base = os.path.basename(jar)
    for module, _ in MODULE_JARS:
        if base.startswith("filegdb4j-" + module):
            return module
    return os.path.splitext(base)[0]


def class_names(jar: str) -> list[str]:
    names = []
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            if not entry.endswith(".class") or entry.endswith("module-info.class"):
                continue
            names.append(entry[: -len(".class")].replace("/", "."))
    return sorted(names)


def normalize_line(line: str) -> str:
    line = line.strip()
    line = re.sub(r"\s+", " ", line)
    line = line.replace(" extends java.lang.Record", "")
    return line


def dump_jar(jar: str) -> list[str]:
    lines: list[str] = []
    for name in class_names(jar):
        result = subprocess.run(
            [JAVAP, "-public", "-classpath", jar, name],
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            continue
        declaration = None
        members: list[str] = []
        for raw in result.stdout.splitlines():
            line = raw.strip()
            if not line or line.startswith("Compiled from"):
                continue
            if line.endswith("{"):
                declaration = normalize_line(line[:-1])
                continue
            if line == "}":
                continue
            if declaration is not None:
                members.append(normalize_line(line))
        if declaration is None:
            continue
        lines.append("class " + declaration)
        for member in sorted(members):
            lines.append("  " + member)
    return lines


def compare(baseline: dict[str, list[str]], current: dict[str, list[str]]) -> list[str]:
    problems: list[str] = []
    for module, expected in baseline.items():
        actual = current.get(module, [])
        expected_set = set(expected)
        actual_set = set(actual)
        if not actual:
            problems.append(f"{module}: no API dumped (missing jar?)")
            continue
        for entry in sorted(expected_set - actual_set):
            problems.append(f"{module}: removed or changed: {entry}")
    return problems


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2
    command, directory = argv[1], argv[2]
    jars = argv[3:]
    if not jars:
        print("no jars given", file=sys.stderr)
        return 2

    current = {module_name(jar): dump_jar(jar) for jar in jars}

    if command == "dump":
        os.makedirs(directory, exist_ok=True)
        for module, lines in current.items():
            path = os.path.join(directory, module + ".txt")
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("\n".join(lines) + "\n")
            print(f"wrote {path} ({len(lines)} entries)")
        return 0

    if command == "check":
        baseline: dict[str, list[str]] = {}
        for module, _ in MODULE_JARS:
            path = os.path.join(directory, module + ".txt")
            if not os.path.isfile(path):
                print(f"missing baseline {path}", file=sys.stderr)
                return 2
            with open(path, encoding="utf-8") as handle:
                baseline[module] = [line.rstrip("\n") for line in handle if line.strip()]
        problems = compare(baseline, current)
        print(f"baseline: {directory}")
        print(f"jars: {', '.join(os.path.basename(jar) for jar in jars)}")
        if problems:
            print("API compatibility check FAILED:")
            for problem in problems:
                print("  " + problem)
            return 1
        print("API compatibility check OK (no removed or changed public API entries)")
        return 0

    print(f"unknown command: {command}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
