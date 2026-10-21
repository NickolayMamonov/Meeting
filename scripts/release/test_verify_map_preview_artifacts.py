import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from verify_map_preview_artifacts import ArtifactError, build_report, inspect_elf


def elf64_with_load_alignment(alignment: int) -> bytes:
    data = bytearray(120)
    data[:4] = b"\x7fELF"
    data[4] = 2
    data[5] = 1
    data[6] = 1
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<H", data, 54, 56)
    struct.pack_into("<H", data, 56, 1)
    struct.pack_into("<IIQQQQQQ", data, 64, 1, 5, 0, 0x4000, 0, 8, 8, alignment)
    return bytes(data)


def write_apk(path: Path, *, arm64_alignment: int, arm64_data_size: int = 120) -> None:
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("lib/arm64-v8a/libpreview.so", elf64_with_load_alignment(arm64_alignment))
        archive.writestr("lib/armeabi-v7a/libpreview.so", elf64_with_load_alignment(0x1000))
        archive.writestr("lib/x86/libpreview.so", elf64_with_load_alignment(0x1000))
        archive.writestr("lib/x86_64/libpreview.so", elf64_with_load_alignment(0x4000))
        if arm64_data_size > 120:
            archive.writestr("assets/payload.bin", bytes(arm64_data_size - 120))


class VerifyMapPreviewArtifactsTest(unittest.TestCase):
    def test_reports_same_abi_set_apk_and_native_size_deltas(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            baseline = directory / "baseline.apk"
            implementation = directory / "implementation.apk"
            write_apk(baseline, arm64_alignment=0x4000)
            write_apk(implementation, arm64_alignment=0x4000, arm64_data_size=240)

            report = build_report(baseline, implementation)

            self.assertEqual(
                {"arm64-v8a", "armeabi-v7a", "x86", "x86_64"},
                set(report["per_abi_delta"]),
            )
            self.assertEqual(0, report["per_abi_delta"]["arm64-v8a"]["uncompressed_delta_bytes"])
            self.assertGreater(report["apk_size_delta_bytes"], 0)
            self.assertTrue(all(entry["pt_load_16kb_compatible"] for entry in report["implementation"]["native_entries"] if entry["abi"] in {"arm64-v8a", "x86_64"}))
            self.assertTrue(all(entry["zip_data_offset_mod_16kb"] >= 0 for entry in report["implementation"]["native_entries"]))

    def test_rejects_arm64_load_segment_with_small_alignment(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            baseline = directory / "baseline.apk"
            implementation = directory / "implementation.apk"
            write_apk(baseline, arm64_alignment=0x4000)
            write_apk(implementation, arm64_alignment=0x1000)

            with self.assertRaisesRegex(ArtifactError, "incompatible with 16 KB"):
                build_report(baseline, implementation)

    def test_rejects_malformed_elf(self):
        with self.assertRaisesRegex(ArtifactError, "not an ELF"):
            inspect_elf(b"not an elf", "libbroken.so")


if __name__ == "__main__":
    unittest.main()
