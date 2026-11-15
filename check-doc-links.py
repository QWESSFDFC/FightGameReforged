#!/usr/bin/env python3
"""Check that document cross-references under the repo actually resolve.

Why this exists: the project keeps its docs in two trees (notes_for_llm/, project_analyses/)
that are referenced from each other, from the root README, and from Java comments.  Moving a
doc silently breaks references, and nothing else in the build would notice (javac and the
self-test never look at .md files).

It checks two kinds of reference:
  1. relative markdown links   `[text](../path/to/doc.md#anchor)`  -> resolved against the file's dir
  2. repo-root path strings    `notes_for_llm/.../x.md`, `project_analyses/.../x.md`
     (these appear in prose AND inside Java comments)

Usage:
    python check-doc-links.py
Exit code 0 = everything resolves; 1 = broken references (listed one per line, with line numbers).

Deliberately NOT checked (keep the tool simple; extend only if a real bug slips through):
  * `#anchor` / `#L12` fragments (only the file part is verified)
  * HTML hrefs, reference-style links `[x][y]`, image links
  * non-ASCII / percent-encoded / space-containing paths
  * directory-only targets such as `project_analyses/`

Output is English on purpose: this may be run from cmd.exe, which mangles Chinese.
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.abspath(__file__))
os.chdir(ROOT)

LINK_RE = re.compile(r"\[[^\]]*\]\(([^()\s]+)\)")
# `project_analyses/xxx/y.md` 这种"仓库根相对路径串"：前后的字符不能是路径字符，避免截到一半
PATH_RE = re.compile(
    r"(?<![A-Za-z0-9_./-])((?:notes_for_llm|project_analyses)/[A-Za-z0-9_./-]*[A-Za-z0-9_-]\.(?:md|txt))"
)
SKIP_DIRS = {"out", "build", "build-input", "build-output", ".git", "tool_for_llm",
             "errors_logs", "logs", "lib", "gradle", "screenshots", "third-party-licenses"}
LINK_SUFFIXES = (".md", ".txt")
PATH_SUFFIXES = (".md", ".txt", ".java")


def files(suffixes):
    for base, dirs, names in os.walk("."):
        dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith(".git")]
        for n in names:
            if n.endswith(suffixes):
                yield os.path.relpath(os.path.join(base, n), ".").replace("\\", "/")


def main():
    lines_cache = {}

    def lines_of(path):
        if path not in lines_cache:
            try:
                with open(path, encoding="utf-8", errors="replace") as f:
                    lines_cache[path] = f.read().split("\n")
            except OSError:
                lines_cache[path] = []
        return lines_cache[path]

    def line_of(path, needle):
        for i, line in enumerate(lines_of(path), start=1):
            if needle in line:
                return i
        return 0

    links_checked = 0
    paths_checked = 0
    broken = []

    for path in files(LINK_SUFFIXES):
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.read()
        except OSError as exc:
            broken.append("{}: unreadable ({})".format(path, exc))
            continue
        for m in LINK_RE.finditer(text):
            target = m.group(1)
            if target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            bare = target.split("#", 1)[0]
            if not bare:
                continue
            links_checked += 1
            resolved = os.path.normpath(os.path.join(os.path.dirname(path), bare))
            if not os.path.exists(resolved):
                broken.append("{}:{} link -> {} (resolves to {})".format(
                    path, line_of(path, m.group(0)), target, resolved))

    for path in files(PATH_SUFFIXES):
        try:
            with open(path, encoding="utf-8", errors="replace") as f:
                text = f.read()
        except OSError:
            continue
        for m in PATH_RE.finditer(text):
            ref = m.group(1)
            paths_checked += 1
            if not os.path.exists(ref):
                broken.append("{}:{} path -> {}".format(path, line_of(path, ref), ref))

    print("checked {} links + {} repo-root paths, broken {}".format(
        links_checked, paths_checked, len(broken)))
    for b in broken:
        print("  [BAD] " + b)
    return 1 if broken else 0


if __name__ == "__main__":
    sys.exit(main())
