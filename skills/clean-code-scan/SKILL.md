---
name: clean-code-scan
description: Scan code changes against the project's clean code review rules in docs/cleanCode/. Detects violations in staged or specified files, reports findings by severity level, and blocks on Fatal (致命) and Severe (严重) issues with fix suggestions. Use this skill whenever the user asks to check code quality, scan for violations, review code before committing, find security issues in their changes, or mentions clean code, code review rules, 整洁代码, or 代码审查. Also use when the user says /scan, asks to run the pre-commit scan manually, or wants to verify their changes pass the company code standards before submitting a PR.
---

# Clean Code Scan

Automated scanning against the project's company-mandated clean code review rules. The rules cover JavaScript, TypeScript, Java, Python, and build configurations — over 700 rules total across five rule files.

The skill's job is to load the right rules for the right files, find real violations backed by code evidence, and report them clearly so the developer can fix the critical ones before committing.

## When to Use

- `/scan` — scan staged git changes
- `/scan --all` — scan all tracked files
- `/scan --files <paths>` — scan specific files
- Pre-commit hook (`scripts/clean-code-scan.sh`) — automatic on `git commit`

## Language-to-Rule Mapping

The rule files are large (the Java file alone has 429 rules). Loading all of them for every scan would waste context and produce noise. Instead, only load rule sets that match the staged file types:

| File Pattern | Rule File |
|---|---|
| `.js`, `.jsx`, `.mjs` | `docs/cleanCode/运营商服务与软件-javascript-3-代码审查规则.md` |
| `.ts`, `.tsx` | `docs/cleanCode/typescript-minimal-review-rules.md` |
| `.java` | `docs/cleanCode/运营商服务与软件-java-最佳实践代码审查规则.md` |
| `.py` | `docs/cleanCode/python-minimal-v1-review-rules.md` |
| `pom.xml`, `build.gradle`, `tsconfig.json`, `webpack.config.*`, `vite.config.*`, `Makefile`, `CMakeLists.txt` | `docs/cleanCode/编译选项-v3-代码审查规则.md` |

## Scanning Workflow

### Step 1: Gather the changes to scan

Run `git diff --cached` for staged changes. For `--all`, use all tracked files. For `--files`, diff against HEAD for just those paths.

Group the files by language so you know which rule sets to load.

If there are no files matching the supported extensions above, report a clean pass immediately — there's nothing to scan.

### Step 2: Load rules efficiently

For each language present, read the corresponding rule file. But don't read the whole file at once — it's too large. Instead:

1. Read the `## 规则索引` (rule index table) first. This is a compact table with columns: 序号, 规则, 级别, 语言, 工具规则, 类型. From this you can see all rule names, severity levels, and types at a glance.
2. Identify rules that are relevant to the diff content — match rule topics (security, error handling, naming, etc.) against what you see in the code changes.
3. Only then read the individual rule details (审查要点, 修复建议, examples) for rules that could plausibly match.

This two-pass approach keeps the context lean. The Java rules file alone has 429 rules — reading every detail for every rule would fill the entire context window.

### Step 3: Analyze the diff against loaded rules

For each diff hunk, check it against the rules you loaded. The key principle here is **evidence-based reporting**: only flag a violation if you can point to specific lines in the diff that clearly match the rule's review points or error examples.

Don't flag a rule just because the rule name sounds like it might be relevant. For instance, a rule called "禁止使用 eval()" should only fire if you actually see `eval(` in the diff — not because the file happens to be JavaScript.

**Security severity override:** When a security-related rule is classified as 一般 or 提示 in the rule file but the code pattern clearly introduces a high-risk vulnerability (e.g., `eval()` on user input, SQL injection, deserialization of untrusted data), elevate the reported severity to 严重 in your output and note that you've escalated it. The rule file severity reflects a general classification, but the actual risk depends on context. A `debugger` statement in production code is 一般; `eval(userInput)` is a severe vulnerability regardless of what the rule file says.

### Step 4: Build findings with fix guidance

For each violation you find, capture:

- **File and line range** — where in the diff
- **Rule name and ID** — from the rule file
- **Severity** — one of: 致命, 严重, 一般, 提示, 信息
- **Evidence** — the actual code snippet that triggers the violation
- **Fix suggestion** — from the rule's 修复建议 section
- **Correct example** — from the rule's 正确示例 section, when available

The fix suggestion and correct example are the most valuable parts for the developer — they turn a "you have a problem" into "here's how to fix it."

### Step 5: Check for beyond-rule-set observations

After completing the rule-based scan, do one more pass over the diff looking for code quality issues that the rule set doesn't explicitly cover. The rule sets are finite — they don't catch everything. Common patterns the rules may miss:

- **TypeScript/JavaScript**: excessive `any` usage when proper types exist, unused imports or interfaces, array index as React `key`, missing error boundaries, silent `catch` blocks with no user feedback
- **Python**: weak hashing algorithms (MD5, SHA1) for passwords, missing type hints on public functions, hardcoded file paths, use of deprecated APIs
- **Java**: empty catch blocks, raw type usage, missing null checks on public API boundaries
- **All languages**: obvious dead code, commented-out code blocks, TODO/FIXME that should be tracked as issues, inconsistent error handling within the same module

Report these as **Observations** (non-blocking, 提示 level) in a separate section. This way the developer is still informed without inflating the rule-based violation count.

### Step 6: Generate the report

Produce the report using the output format below. The report has three sections: blocking issues (致命 + 严重) at the top, then non-blocking rule violations, then observations (beyond-rule-set findings). End with a summary table and a single result marker line.

## Severity and Blocking Rules

The severity levels come from the company rule files:

| Level | Chinese | Blocks Commit | What to include |
|---|---|---|---|
| Fatal | 致命 | YES | Detailed fix suggestion + correct example |
| Severe | 严重 | YES | Detailed fix suggestion + correct example |
| Normal | 一般 | No | Brief fix suggestion |
| Warning | 提示 | No | Brief suggestion |
| Info | 信息 | No | Note for reference |

致命 and 严重 issues mean the code has a real problem (security vulnerability, crash risk, data corruption potential) that should not ship. They block the commit to prevent merging known-bad code. The fix suggestion and correct example are mandatory for these — the developer needs a clear path to resolution.

一般, 提示, and 信息 issues are style, best-practice, or informational. They're reported so the developer is aware, but they don't block the workflow.

## Output Format

When issues are found, use this template:

```
╔══════════════════════════════════════════════════════════╗
║           Clean Code Scan Results                        ║
╠══════════════════════════════════════════════════════════╣
║ Rule sets loaded: <rule file names>                      ║
║ Files scanned: <count>                                   ║
╚══════════════════════════════════════════════════════════╝

## BLOCKING Issues (致命 + 严重) — must fix before commit

### [<severity>] <rule ID> <rule title>
- **File:** <file path>:<line>
- **Rule:** <tool rule ID>
- **Evidence:** `<code snippet>`
- **Fix:** <fix suggestion>
- **Correct example:** `<correct code>`

---

## Non-blocking Issues

### [<severity>] <rule ID> <rule title>
- **File:** <file path>:<line>
- **Rule:** <tool rule ID>
- **Evidence:** `<code snippet>`
- **Fix:** <fix suggestion>

---

## Observations (beyond rule set)

Code quality concerns not covered by the loaded rule set, reported for awareness:

### [<file>:<line>] <issue title>
- **Evidence:** `<code snippet>`
- **Suggestion:** <improvement suggestion>

---

## Summary

| Severity | Count | Blocks commit |
|----------|-------|---------------|
| 致命       | <n>   | YES           |
| 严重       | <n>   | YES           |
| 一般       | <n>   | No            |
| 提示       | <n>   | No            |
| 信息       | <n>   | No            |
| 观察项     | <n>   | No            |

SCAN_RESULT: BLOCK
```

When no issues are found:

```
╔══════════════════════════════════════════════════════════╗
║           Clean Code Scan Results                        ║
╠══════════════════════════════════════════════════════════╣
║ Rule sets loaded: <rule file names>                      ║
║ Files scanned: <count>                                   ║
║ Issues found: 0                                          ║
╚══════════════════════════════════════════════════════════╝

SCAN_RESULT: PASS
```

The `SCAN_RESULT: PASS` or `SCAN_RESULT: BLOCK` line at the end is consumed by the pre-commit hook script to decide the exit code. Keep it on its own line with no trailing content.

The `SCAN_RESULT: PASS` or `SCAN_RESULT: BLOCK` line at the end is consumed by the pre-commit hook script to decide the exit code. Keep it on its own line with no trailing content.

## Constraints

These constraints come from the project's `docs/cleanCode/README.md` and reflect how the company expects these rules to be applied:

1. **Scope to staged languages.** Loading unrelated rule sets wastes time and produces false confidence in "no issues found" when the relevant rules were never checked.
2. **Evidence required.** A violation without code evidence is speculation. Don't flag based on rule names alone.
3. **Low-severity rules still count.** 通用规范建议类, 代码坏味道类, 提示, and 信息 rules are company-mandated — report them even though they don't block.
4. **Build rules only for build changes.** The 编译选项 rule file only applies when build scripts, compiler flags, or delivery configurations change.
5. **Repo style wins over rule formatting.** If a formatting rule conflicts with the repo's established conventions, follow the repo.
6. **Security rules need context.** Security rules are high priority but still require judgment about data sources, trust boundaries, and runtime environment. A hardcoded secret in a test fixture is different from one in production code.
7. **Escalate security severity when warranted.** The rule file assigns a default severity, but if the actual code context makes the issue more dangerous (e.g., `eval()` on untrusted input vs. on a hardcoded string), report it at the severity that reflects the real risk. Add a note: "(severity escalated from 一般 per security context)".

## Pre-commit Hook

The hook at `scripts/clean-code-scan.sh` invokes `claude --print` with this skill's workflow. It gracefully degrades: if Claude CLI is unavailable, the API times out, or an error occurs, it warns but doesn't block the commit.

Install: `bash scripts/clean-code-scan.sh --install`
Bypass: `git commit --no-verify` (not recommended)
