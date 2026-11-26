#!/usr/bin/env python3
"""Inventory ICEfaces partial submits and possible EL dependencies in Facelets."""

import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path


EL = re.compile(r"[#\$]\{\s*([A-Za-z_$][\w$]*)\s*\.")
EDITABLE = {"inputText", "inputTextarea", "inputSecret", "inputHidden", "inputFile",
            "inputRichText", "selectOneMenu", "selectManyListbox", "selectOneRadio",
            "selectBooleanCheckbox", "selectManyCheckbox", "selectInputDate", "selectInputText"}


def name(tag):
    return tag.rsplit("}", 1)[-1]


def beans(element):
    return {match for value in element.attrib.values() for match in EL.findall(value)}


def analyze(path):
    root = ET.parse(path).getroot()
    elements = list(root.iter())
    for source in elements:
        if source.get("partialSubmit", "").lower() != "true":
            continue
        source_beans = beans(source)
        candidates = []
        for target in elements:
            shared = source_beans & beans(target)
            if target is source or not shared or not isinstance(target.tag, str):
                continue
            component = name(target.tag)
            candidates.append({"component": component, "id": target.get("id"),
                               "shared_beans": sorted(shared),
                               "editable": component in EDITABLE,
                               "rendered_conditional": "rendered" in target.attrib})
        yield {"file": str(path), "component": name(source.tag), "id": source.get("id"),
               "bindings": sorted(source_beans), "candidates": candidates,
               "review": "Shared bean only suggests a relationship; verify setters, listeners, services, naming containers and conditional rendering."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", nargs="?", type=Path, default=Path("src/main/webapp"))
    args = parser.parse_args()
    files = sorted(args.path.rglob("*.xhtml")) if args.path.is_dir() else [args.path]
    results, errors = [], []
    for path in files:
        try:
            results.extend(analyze(path))
        except ET.ParseError as exc:
            errors.append({"file": str(path), "error": str(exc)})
    print(json.dumps({"partial_submits": results, "parse_errors": errors}, ensure_ascii=False, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
