#!/usr/bin/env python3
"""生成备用更新通道用的版本清单。

公益镜像（如 ghfast.top）只代理 GitHub 的文件路径，不代理 api.github.com，
所以时检查更新在直连失败时无法从镜像拿 Release 元数据。发版时把检查更新
需要的字段写成 JSON 作为 Release 附件一起上传，客户端即可通过

    https://<mirror>/https://github.com/<owner>/<repo>/releases/latest/download/latest.json

取到最新清单——`latest` 由 GitHub 解析为最新 Release，无需预知版本号。

依赖环境变量（由 release.yml 提供）：
    versionCode  整数版本号
    versionName  版本名（如 1.3.1.2.r244.290fc17）
    TAG_NAME     git tag
    APK_SIZE     APK 字节数

用法：
    python3 .github/scripts/generate_release_manifest.py latest.json
"""

from __future__ import annotations

import json
import os
import sys


def build_manifest() -> dict:
    version_code = int(os.environ["versionCode"])
    version_name = os.environ["versionName"]
    apk_size = int(os.environ["APK_SIZE"])

    body = ""
    body_path = "release_body.md"
    if os.path.exists(body_path):
        with open(body_path, encoding="utf-8") as handle:
            body = handle.read()

    return {
        "versionCode": version_code,
        "versionName": version_name,
        "tagName": os.environ["TAG_NAME"],
        "apkName": f"newBV_{version_code}_{version_name}_release.apk",
        "apkSize": apk_size,
        "body": body,
    }


def main() -> int:
    output_path = sys.argv[1] if len(sys.argv) > 1 else "latest.json"
    manifest = build_manifest()
    with open(output_path, "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
