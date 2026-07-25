import pathlib
import subprocess
import unittest


PACKAGE_DIR = pathlib.Path(__file__).resolve().parents[1]


class DownloadLayoutTest(unittest.TestCase):
    def test_otelcollector_is_available_to_raw_and_base_consumers(self):
        result = subprocess.run(
            [str(PACKAGE_DIR / "download.sh"), "--print-layout", "--arch", "x86_64"],
            check=True,
            capture_output=True,
            text=True,
        )
        expected = (
            "otelcol-contrib_0.156.0_linux_amd64.tar.gz -> "
            "raw/packages/otelcol-contrib_0.156.0_linux_amd64.tar.gz,"
            "base/otelcol-contrib_0.156.0_linux_amd64.tar.gz"
        )
        self.assertIn(expected, result.stdout)

    def test_base_filter_selects_only_cli_destination(self):
        result = subprocess.run(
            [
                str(PACKAGE_DIR / "download.sh"),
                "--print-layout",
                "--arch",
                "x86_64",
                "--dir",
                "base",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        line = next(line for line in result.stdout.splitlines() if line.startswith("otelcol-contrib_"))
        self.assertIn("base/otelcol-contrib_", line)
        self.assertNotIn("raw/packages/", line)


if __name__ == "__main__":
    unittest.main()
