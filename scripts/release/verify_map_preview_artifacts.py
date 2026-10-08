#!/usr/bin/env python3
"""Report same-variant APK native payload deltas and verify ELF 16 KB alignment."""

from __future__ import annotations

import argparse
import json
import struct
import sys
import zipfile
from pathlib import Path
from typing import Any


PAGE_SIZE = 16 * 1024
PT_LOAD = 1
ANDROID_ABIS = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"}
PAGE_SIZE_REQUIRED_ABIS = {"arm64-v8a", "x86_64"}


class ArtifactError(ValueError):
    """Raised when an APK does not meet the native artifact evidence contract."""


def inspect_elf(binary: bytes, name: str) -> dict[str, Any]:
    if len(binary) < 16 or binary[:4] != b"\x7fELF":
        raise ArtifactError(f"{name} is not an ELF file")

    elf_class = binary[4]
    data_encoding = binary[5]
    if elf_class not in (1, 2) or data_encoding not in (1, 2):
        raise ArtifactError(f"{name} has an unsupported ELF class or byte order")

    endian = "<" if data_encoding == 1 else ">"
    if elf_class == 2:
        if len(binary) < 64:
            raise ArtifactError(f"{name} has a truncated ELF64 header")
        phoff = struct.unpack_from(f"{endian}Q", binary, 32)[0]
        phentsize = struct.unpack_from(f"{endian}H", binary, 54)[0]
        phnum = struct.unpack_from(f"{endian}H", binary, 56)[0]
        minimum_entry_size = 56
    else:
        if len(binary) < 52:
            raise ArtifactError(f"{name} has a truncated ELF32 header")
        phoff = struct.unpack_from(f"{endian}I", binary, 28)[0]
        phentsize = struct.unpack_from(f"{endian}H", binary, 42)[0]
        phnum = struct.unpack_from(f"{endian}H", binary, 44)[0]
        minimum_entry_size = 32

    if phentsize < minimum_entry_size or phnum == 0:
        raise ArtifactError(f"{name} has no usable ELF program headers")
    if phoff + phentsize * phnum > len(binary):
        raise ArtifactError(f"{name} has truncated ELF program headers")

    loads: list[dict[str, int]] = []
    for index in range(phnum):
        entry_offset = phoff + index * phentsize
        if elf_class == 2:
            p_type, _, p_offset, p_vaddr, _, _, _, p_align = struct.unpack_from(
                f"{endian}IIQQQQQQ", binary, entry_offset
            )
        else:
            p_type, p_offset, p_vaddr, _, _, _, _, p_align = struct.unpack_from(
                f"{endian}IIIIIIII", binary, entry_offset
            )
        if p_type == PT_LOAD:
            loads.append(
                {
                    "offset": p_offset,
                    "virtual_address": p_vaddr,
                    "alignment": p_align,
                }
            )

    if not loads:
        raise ArtifactError(f"{name} has no PT_LOAD program headers")
    return {"class_bits": 64 if elf_class == 2 else 32, "load_segments": loads}


def _zip_data_offset(archive: zipfile.ZipFile, info: zipfile.ZipInfo) -> int:
    assert archive.fp is not None
    archive.fp.seek(info.header_offset)
    local_header = archive.fp.read(30)
    if len(local_header) != 30 or local_header[:4] != b"PK\x03\x04":
        raise ArtifactError(f"{info.filename} has an invalid local ZIP header")
    name_length, extra_length = struct.unpack_from("<HH", local_header, 26)
    return info.header_offset + 30 + name_length + extra_length


def inspect_apk(path: Path) -> dict[str, Any]:
    if not path.is_file():
        raise ArtifactError(f"APK does not exist: {path}")

    per_abi: dict[str, dict[str, int]] = {}
    native_entries: list[dict[str, Any]] = []
    with zipfile.ZipFile(path) as archive:
        for info in archive.infolist():
            parts = info.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or not info.filename.endswith(".so"):
                continue
            abi = parts[1]
            if abi not in ANDROID_ABIS:
                raise ArtifactError(f"unexpected native ABI in APK: {abi}")
            totals = per_abi.setdefault(abi, {"uncompressed_bytes": 0, "compressed_bytes": 0})
            totals["uncompressed_bytes"] += info.file_size
            totals["compressed_bytes"] += info.compress_size
            data_offset = _zip_data_offset(archive, info)
            elf = inspect_elf(archive.read(info), info.filename)
            load_alignments = [segment["alignment"] for segment in elf["load_segments"]]
            compatible = all(
                segment["alignment"] >= PAGE_SIZE
                and (segment["offset"] - segment["virtual_address"]) % PAGE_SIZE == 0
                for segment in elf["load_segments"]
            )
            if abi in PAGE_SIZE_REQUIRED_ABIS and not compatible:
                raise ArtifactError(f"{info.filename} has a PT_LOAD segment incompatible with 16 KB pages")
            native_entries.append(
                {
                    "path": info.filename,
                    "abi": abi,
                    "uncompressed_bytes": info.file_size,
                    "compressed_bytes": info.compress_size,
                    "compression_method": info.compress_type,
                    "zip_data_offset": data_offset,
                    "zip_data_offset_mod_16kb": data_offset % PAGE_SIZE,
                    "elf_class_bits": elf["class_bits"],
                    "pt_load_alignments": load_alignments,
                    "pt_load_16kb_compatible": compatible,
                }
            )

    if not per_abi:
        raise ArtifactError(f"APK has no native libraries: {path}")
    return {
        "path": str(path.resolve()),
        "apk_bytes": path.stat().st_size,
        "per_abi": per_abi,
        "native_entries": native_entries,
    }


def build_report(baseline_path: Path, apk_path: Path) -> dict[str, Any]:
    baseline = inspect_apk(baseline_path)
    implementation = inspect_apk(apk_path)
    baseline_abis = set(baseline["per_abi"])
    implementation_abis = set(implementation["per_abi"])
    if baseline_abis != implementation_abis:
        raise ArtifactError(
            "baseline and implementation APKs have different ABI sets: "
            f"{sorted(baseline_abis)} != {sorted(implementation_abis)}"
        )

    abi_deltas = {}
    for abi in sorted(baseline_abis):
        before = baseline["per_abi"][abi]
        after = implementation["per_abi"][abi]
        abi_deltas[abi] = {
            "baseline_uncompressed_bytes": before["uncompressed_bytes"],
            "implementation_uncompressed_bytes": after["uncompressed_bytes"],
            "uncompressed_delta_bytes": after["uncompressed_bytes"] - before["uncompressed_bytes"],
            "baseline_compressed_bytes": before["compressed_bytes"],
            "implementation_compressed_bytes": after["compressed_bytes"],
            "compressed_delta_bytes": after["compressed_bytes"] - before["compressed_bytes"],
        }

    return {
        "schema_version": 1,
        "page_size_bytes": PAGE_SIZE,
        "baseline": baseline,
        "implementation": implementation,
        "apk_size_delta_bytes": implementation["apk_bytes"] - baseline["apk_bytes"],
        "per_abi_delta": abi_deltas,
        "zipalign_command_required": "zipalign -c -P 16 -v 4 <implementation.apk>",
        "bundletool_device_download_comparison_required": True,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-apk", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    try:
        report = build_report(args.baseline_apk, args.apk)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    except (ArtifactError, OSError, zipfile.BadZipFile, struct.error) as error:
        print(f"map preview artifact verification failed: {error}", file=sys.stderr)
        return 1

    print(
        "map preview artifact report written: "
        f"APK delta {report['apk_size_delta_bytes']} bytes; "
        f"ABIs {', '.join(report['per_abi_delta'])}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
