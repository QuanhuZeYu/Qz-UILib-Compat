#!/usr/bin/env python3
"""仅从同版本公开 Release Maven ZIP 准备本仓依赖，不读取工作站产物。"""
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import shutil
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
UPSTREAMS = (
    ("core", "QuanhuZeYu/Qz-UILib-Core"),
    ("platform", "QuanhuZeYu/Qz-UILib-Platform"),
    ("minecraft", "QuanhuZeYu/Qz-UILib"),
)


def version():
    properties = dict(re.findall(r"^([^#\s=]+)=(.*)$", (ROOT / "gradle.properties").read_text(encoding="utf-8"), re.M))
    value = properties["uilibVersion"].strip()
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:[.-](?:alpha|beta|rc)[.-]?\d+)?", value):
        raise ValueError("uilibVersion 不是正式版本：" + value)
    return value


def unpack(archive, destination, component, selected_version):
    # 检查标准仓库布局和必需接口；上游之间不得覆盖同一路径。
    required = {
        "core": ("core:development-java8", "core-verification:"),
        "platform": ("gl-renderer:java8", "font-adapter:java8", "lwjgl2-adapter:java8", "lwjgl3-adapter:java8", "platform-verification:"),
        "minecraft": ("minecraft-host:development-java8",),
    }[component]
    prefix = "asia/quanhuzeyu/uilib/"
    with ZipFile(archive) as source:
        for entry in source.infolist():
            name = entry.filename
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name or ":" in name:
                raise ValueError("Maven ZIP 含无效路径：" + name)
            if entry.is_dir():
                continue
            if not name.startswith(prefix) or len(path.parts) not in (5, 6):
                raise ValueError("Maven ZIP 不是标准仓库布局：" + name)
            if (destination / name).exists():
                raise ValueError("Maven ZIP 覆盖其他上游的文件：" + name)
            if component == "minecraft" and path.parts[3] != "minecraft-host":
                raise ValueError("Minecraft ZIP 夹带其他模块：" + name)
            if len(path.parts) == 6 and path.parts[4] != selected_version:
                raise ValueError("Maven ZIP 含其他版本：" + name)
        names = set(source.namelist())
        for coordinate in required:
            module, classifier = coordinate.split(":")
            base = prefix + module + "/" + selected_version + "/" + module + "-" + selected_version
            jar = base + ("-" + classifier if classifier else "") + ".jar"
            if jar not in names or base + ".pom" not in names:
                raise ValueError("Maven ZIP 缺少正式接口或 POM：" + coordinate + ":" + selected_version)
        source.extractall(destination)


def main():
    selected_version = version()
    directory = ROOT / "build" / "ci"
    downloads = directory / "downloads"
    repository = directory / "maven"
    downloads.mkdir(parents=True, exist_ok=True)
    # 解包暂存完成后才替换本仓 CI Maven 目录，下载失败不能留下可误用的半套仓库。
    staging = directory / "maven.pending"
    if staging.exists():
        shutil.rmtree(staging)
    staging.mkdir()
    evidence = []
    try:
        for component, repo in UPSTREAMS:
            asset = "qz-uilib-" + component + "-" + selected_version + "-maven.zip"
            url = "https://github.com/" + repo + "/releases/download/" + quote(selected_version) + "/" + asset
            archive = downloads / asset
            print("下载 " + repo + " tag=" + selected_version + " asset=" + asset, flush=True)
            try:
                with urlopen(Request(url, headers={"User-Agent": "Qz-UILib-Compat-CI"}), timeout=120) as response, archive.open("wb") as output:
                    shutil.copyfileobj(response, output)
            except (HTTPError, URLError, TimeoutError) as error:
                raise RuntimeError("无法获取 " + url + "；请先依序发布 Core → Platform → Minecraft → Compat。" + str(error)) from error
            unpack(archive, staging, component, selected_version)
            evidence.append({"repo": repo, "tag": selected_version, "asset": asset, "url": url,
                             "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()})
        if repository.exists():
            shutil.rmtree(repository)
        staging.rename(repository)
        (directory / "upstreams.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("独立 Maven 仓库已准备：" + str(repository))
    finally:
        if staging.exists():
            shutil.rmtree(staging)


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        print("上游依赖准备失败：" + str(error), file=sys.stderr)
        sys.exit(1)
