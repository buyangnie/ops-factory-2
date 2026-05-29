import json
import os
import shutil
import sys
import tempfile
import unittest
import unittest.mock as mock
from pathlib import Path

import core


class KnowledgeCliCoreTest(unittest.TestCase):
    def with_temp_root(self, run):
        temp_dir = tempfile.mkdtemp(prefix="knowledge-cli-")
        previous_root = os.environ.get("QA_CLI_ROOT_DIR")
        os.environ["QA_CLI_ROOT_DIR"] = temp_dir
        try:
            run(Path(temp_dir).resolve())
        finally:
            if previous_root is None:
                os.environ.pop("QA_CLI_ROOT_DIR", None)
            else:
                os.environ["QA_CLI_ROOT_DIR"] = previous_root
            shutil.rmtree(temp_dir, ignore_errors=True)

    def test_extract_configured_root_dir_reads_knowledge_cli_scope(self):
        content = "\n".join(
            [
                "extensions:",
                "  other:",
                "    rootDir: /tmp/wrong",
                "  knowledge-cli:",
                "    x-opsfactory:",
                "      scope:",
                "        rootDir: ../../../../knowledge-service/data/artifacts/src_1",
                "        sourceId: src_1",
            ]
        )
        self.assertEqual(
            core.extract_configured_root_dir(content),
            "../../../../knowledge-service/data/artifacts/src_1",
        )

    def test_find_files_lists_matching_files_within_root(self):
        def run(root):
            (root / "config").mkdir()
            (root / "config" / "app.yaml").write_text("name: app\n", encoding="utf-8")
            (root / "config" / "note.txt").write_text("hello\n", encoding="utf-8")
            result = json.loads(core.handle_find_files(pathPrefix="config", glob="*.yaml"))
            self.assertEqual(result["rootDir"], str(root))
            self.assertEqual(result["total"], 1)
            self.assertEqual(result["files"][0]["path"], str(root / "config" / "app.yaml"))

        self.with_temp_root(run)

    def test_find_files_rejects_unsafe_globs(self):
        def run(_root):
            with self.assertRaisesRegex(ValueError, "Invalid glob pattern"):
                core.handle_find_files(glob="../*.md")
            with self.assertRaisesRegex(ValueError, "Invalid glob pattern"):
                core.handle_find_files(glob="!*.md")

        self.with_temp_root(run)

    def test_find_files_reports_truncated_results(self):
        def run(root):
            for name in ["a.md", "b.md", "c.md"]:
                (root / name).write_text("x\n", encoding="utf-8")
            result = json.loads(core.handle_find_files(glob="*.md", limit=2))
            self.assertEqual(result["total"], 2)
            self.assertTrue(result["truncated"])

        self.with_temp_root(run)

    def test_run_command_lines_times_out_when_process_produces_no_output(self):
        with mock.patch.object(core, "COMMAND_TIMEOUT_SECONDS", 0.1):
            with self.assertRaisesRegex(TimeoutError, "timed out"):
                core.run_command_lines(sys.executable, ["-c", "import time; time.sleep(2)"], 10)

    def test_search_content_finds_text_hits(self):
        def run(root):
            (root / "logs").mkdir()
            file_path = root / "logs" / "service.log"
            file_path.write_text("INFO startup\nERROR failed to bind port\n", encoding="utf-8")
            result = json.loads(core.handle_search_content(query="failed to bind", pathPrefix="logs"))
            self.assertEqual(result["rootDir"], str(root))
            self.assertEqual(result["total"], 1)
            self.assertEqual(result["hits"][0]["path"], str(file_path))
            self.assertEqual(result["hits"][0]["line"], 2)

        self.with_temp_root(run)

    def test_search_content_handles_queries_that_start_with_dash(self):
        def run(root):
            file_path = root / "dash.md"
            file_path.write_text("- starts with dash\nnormal line\n", encoding="utf-8")
            result = json.loads(core.handle_search_content(query="- starts", glob="*.md"))
            self.assertEqual(result["total"], 1)
            self.assertEqual(result["hits"][0]["path"], str(file_path))
            self.assertEqual(result["hits"][0]["line"], 1)

        self.with_temp_root(run)

    def test_search_content_limits_hits_by_glob(self):
        def run(root):
            markdown_path = root / "knowledge.md"
            yaml_path = root / "config.yaml"
            markdown_path.write_text("用户基本信息\n", encoding="utf-8")
            yaml_path.write_text("用户基本信息\n", encoding="utf-8")
            result = json.loads(core.handle_search_content(query="用户基本信息", glob="*.md"))
            self.assertEqual(result["total"], 1)
            self.assertEqual(result["hits"][0]["path"], str(markdown_path))

        self.with_temp_root(run)

    def test_read_file_returns_numbered_content(self):
        def run(root):
            file_path = root / "run.log"
            file_path.write_text("line1\nline2\nline3\nline4\n", encoding="utf-8")
            result = json.loads(core.handle_read_file(path=str(file_path), startLine=2, endLine=3))
            self.assertEqual(result["path"], str(file_path))
            self.assertEqual(result["startLine"], 2)
            self.assertEqual(result["endLine"], 3)
            self.assertRegex(result["content"], r"2\s+line2")
            self.assertRegex(result["content"], r"3\s+line3")

        self.with_temp_root(run)

    def test_read_file_truncates_large_line_ranges(self):
        def run(root):
            file_path = root / "large.md"
            file_path.write_text("\n".join(f"line{index + 1}" for index in range(260)), encoding="utf-8")
            result = json.loads(core.handle_read_file(path=str(file_path), startLine=10, endLine=260))
            self.assertEqual(result["endLine"], 209)
            self.assertTrue(result["truncated"])
            self.assertEqual(result["truncatedReason"], "line_limit")
            self.assertEqual(result["nextStartLine"], 210)

        self.with_temp_root(run)

    def test_read_file_rejects_paths_outside_root(self):
        def run(root):
            outside_file = root.parent / "outside.txt"
            outside_file.write_text("outside\n", encoding="utf-8")
            try:
                with self.assertRaisesRegex(ValueError, "escapes configured rootDir"):
                    core.handle_read_file(path=str(outside_file))
            finally:
                outside_file.unlink(missing_ok=True)

        self.with_temp_root(run)

    def test_find_files_rejects_path_prefix_symlink_escape(self):
        def run(root):
            outside_dir = Path(tempfile.mkdtemp(prefix="knowledge-cli-outside-")).resolve()
            try:
                (outside_dir / "outside.md").write_text("outside\n", encoding="utf-8")
                (root / "link-out").symlink_to(outside_dir, target_is_directory=True)
                with self.assertRaisesRegex(ValueError, "escapes configured rootDir"):
                    core.handle_find_files(pathPrefix="link-out")
            finally:
                shutil.rmtree(outside_dir, ignore_errors=True)

        self.with_temp_root(run)


if __name__ == "__main__":
    unittest.main()
