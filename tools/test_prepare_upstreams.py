"""下载不可用或 ZIP 错配时必须阻断，防止以半套/其他版本依赖发布。"""
import importlib.util
from pathlib import Path
import tempfile
import unittest
from zipfile import ZipFile

spec = importlib.util.spec_from_file_location("prepare_upstreams", Path(__file__).with_name("prepare-upstreams.py"))
prepare = importlib.util.module_from_spec(spec)
spec.loader.exec_module(prepare)


class MavenZipTest(unittest.TestCase):
    def archive(self, root, entries):
        archive = root / "upstream.zip"
        with ZipFile(archive, "w") as output:
            for name in entries:
                output.writestr(name, "fixture")
        return archive

    def host_entries(self, version="5.0.0-beta3"):
        prefix = "asia/quanhuzeyu/uilib/minecraft-host/" + version + "/minecraft-host-" + version
        return [prefix + "-development-java8.jar", prefix + ".pom"]

    def test_standard_host_and_required_interface(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            destination = root / "maven"
            prepare.unpack(self.archive(root, self.host_entries()), destination, "minecraft", "5.0.0-beta3")
            self.assertTrue((destination / self.host_entries()[0]).is_file())
            with self.assertRaisesRegex(ValueError, "覆盖其他上游"):
                prepare.unpack(root / "upstream.zip", destination, "minecraft", "5.0.0-beta3")

    def test_reject_wrong_version_missing_interface_and_traversal(self):
        for entries in (self.host_entries("5.0.0-beta2"), self.host_entries()[:1],
                        self.host_entries() + ["../outside.jar"],
                        self.host_entries() + ["asia/quanhuzeyu/uilib/core/5.0.0-beta3/core-5.0.0-beta3.jar"]):
            with self.subTest(entries=entries), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                with self.assertRaises(ValueError):
                    prepare.unpack(self.archive(root, entries), root / "maven", "minecraft", "5.0.0-beta3")
                self.assertFalse((root / "maven").exists())


if __name__ == "__main__":
    unittest.main()
