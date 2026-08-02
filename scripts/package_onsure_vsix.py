#!/usr/bin/env python3
"""Build and canonicalize the ONSure VSIX for byte-reproducible local packaging."""

from __future__ import annotations

import hashlib
import json
import os
import pathlib
import subprocess
import xml.etree.ElementTree as ET
import zipfile


ROOT = pathlib.Path(__file__).resolve().parents[1]
EXTENSION = ROOT / "vscode-extension"
FIXED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)
CONTENT_TYPES = "[Content_Types].xml"
CONTENT_TYPES_NAMESPACE = "http://schemas.openxmlformats.org/package/2006/content-types"


def content_digest(entries: list[tuple[str, bytes]]) -> str:
    digest = hashlib.sha256()
    for name, data in sorted(entries):
        encoded = name.encode("utf-8")
        digest.update(len(encoded).to_bytes(8, "big"))
        digest.update(encoded)
        digest.update(len(data).to_bytes(8, "big"))
        digest.update(hashlib.sha256(data).digest())
    return digest.hexdigest()


def normalize_entries(entries: list[tuple[str, bytes]]) -> list[tuple[str, bytes]]:
    normalized: list[tuple[str, bytes]] = []
    for name, data in entries:
        if name != CONTENT_TYPES:
            normalized.append((name, data))
            continue
        source = ET.fromstring(data)
        expected_tag = f"{{{CONTENT_TYPES_NAMESPACE}}}Types"
        if source.tag != expected_tag:
            raise ValueError("VSIX_CONTENT_TYPES_CONTRACT_INVALID")
        ET.register_namespace("", CONTENT_TYPES_NAMESPACE)
        target = ET.Element(expected_tag)
        children = sorted(
            source,
            key=lambda child: (
                child.tag,
                child.attrib.get("Extension", ""),
                child.attrib.get("PartName", ""),
                child.attrib.get("ContentType", ""),
            ),
        )
        for child in children:
            ET.SubElement(target, child.tag, dict(sorted(child.attrib.items())))
        normalized.append((
            name,
            b'<?xml version="1.0" encoding="utf-8"?>\n'
            + ET.tostring(target, encoding="utf-8", short_empty_elements=True)
            + b"\n",
        ))
    return normalized


def canonicalize_vsix(package_file: pathlib.Path) -> dict[str, object]:
    package = package_file.resolve()
    if not package.is_file() or package.suffix != ".vsix":
        raise ValueError("VSIX_PACKAGE_FILE_INVALID")
    with zipfile.ZipFile(package) as source:
        entries = normalize_entries([
            (info.filename, source.read(info.filename)) for info in source.infolist()
        ])
    names = [name for name, _ in entries]
    if len(names) != len(set(names)):
        raise ValueError("VSIX_DUPLICATE_ENTRY")
    temporary = package.with_suffix(package.suffix + ".deterministic.tmp")
    try:
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_STORED) as output:
            for name, data in sorted(entries):
                info = zipfile.ZipInfo(name, FIXED_ZIP_TIME)
                info.create_system = 3
                info.compress_type = zipfile.ZIP_STORED
                mode = 0o40755 if name.endswith("/") else 0o100644
                info.external_attr = mode << 16
                output.writestr(info, data)
        os.replace(temporary, package)
    finally:
        temporary.unlink(missing_ok=True)
    raw = package.read_bytes()
    return {
        "contract": "ONSURE_DETERMINISTIC_VSIX_PACKAGE_V1",
        "path": str(package),
        "entry_count": len(entries),
        "content_sha256": content_digest(entries),
        "package_sha256": hashlib.sha256(raw).hexdigest(),
        "size_bytes": len(raw),
        "zip_timestamp": "1980-01-01T00:00:00Z",
        "compression": "STORED",
        "final_claim_allowed": False,
    }


def main() -> int:
    package = json.loads((EXTENSION / "package.json").read_text(encoding="utf-8"))
    output = EXTENSION / f"{package['name']}-{package['version']}.vsix"
    subprocess.run(
        ["npx", "@vscode/vsce", "package", "--no-dependencies"],
        cwd=EXTENSION,
        check=True,
    )
    report = canonicalize_vsix(output)
    print(json.dumps(report, indent=2, sort_keys=True))
    print("ONSURE_DETERMINISTIC_VSIX_PACKAGE_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
