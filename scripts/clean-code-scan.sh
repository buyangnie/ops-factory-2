#!/bin/sh
# clean-code-scan.sh — Git pre-commit hook for clean code scanning
# Scans staged changes against project clean code rules via Claude Code CLI.
#
# Usage:
#   As pre-commit hook (automatic):  .git/hooks/pre-commit
#   Manual scan:                      bash scripts/clean-code-scan.sh
#   Install hook:                     bash scripts/clean-code-scan.sh --install

set -e

# --- Install mode ---
if [ "$1" = "--install" ]; then
    HOOK_DIR="$(git rev-parse --git-dir)/hooks"
    HOOK_FILE="$HOOK_DIR/pre-commit"
    SCRIPT_PATH="$(cd "$(dirname "$0")" && pwd)/$(basename "$0")"

    # Check if another pre-commit hook already exists
    if [ -f "$HOOK_FILE" ]; then
        # Check if it's already our hook
        if grep -q "clean-code-scan" "$HOOK_FILE" 2>/dev/null; then
            echo "clean-code-scan: pre-commit hook already installed, updating."
        else
            echo "clean-code-scan: existing pre-commit hook found at $HOOK_FILE"
            echo "  Appending clean-code-scan invocation."
            echo "" >> "$HOOK_FILE"
            echo "# --- clean-code-scan ---" >> "$HOOK_FILE"
            echo "bash \"$(pwd)/scripts/clean-code-scan.sh\"" >> "$HOOK_FILE"
            echo "Hook appended successfully."
            exit 0
        fi
    fi

    # Create new hook or overwrite our previous hook
    cp "$SCRIPT_PATH" "$HOOK_FILE"
    chmod +x "$HOOK_FILE" 2>/dev/null || true
    echo "clean-code-scan: pre-commit hook installed to $HOOK_FILE"
    exit 0
fi

# --- Scan mode ---

echo "clean-code-scan: checking staged files..."

# Check if claude CLI is available
if ! command -v claude >/dev/null 2>&1; then
    echo "clean-code-scan: claude CLI not found, skipping scan."
    echo "  Install Claude Code CLI or run 'claude login' to enable scanning."
    exit 0
fi

# Get staged files (added, copied, modified, renamed — skip deleted)
STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACMR 2>/dev/null || true)

if [ -z "$STAGED_FILES" ]; then
    echo "clean-code-scan: no staged files found, skipping."
    exit 0
fi

# Supported file extensions and build files
SUPPORTED_RE='\.\(js\|jsx\|mjs\|ts\|tsx\|java\|py\)$\|/\(pom\.xml\|build\.gradle\|tsconfig\.json\)$\|webpack\.config\.\|vite\.config\.\|Makefile$\|CMakeLists\.txt'

# Filter to only supported file types
RELEVANT=$(echo "$STAGED_FILES" | grep -E "$SUPPORTED_RE" 2>/dev/null || true)

if [ -z "$RELEVANT" ]; then
    echo "clean-code-scan: no supported file types in staging area, skipping."
    exit 0
fi

# Collect the diff for relevant files only
DIFF=$(git diff --cached -- $RELEVANT 2>/dev/null || true)

if [ -z "$DIFF" ]; then
    echo "clean-code-scan: empty diff for relevant files, skipping."
    exit 0
fi

# Check diff size — warn if very large but still proceed
DIFF_LINES=$(echo "$DIFF" | wc -l)
if [ "$DIFF_LINES" -gt 3000 ]; then
    echo "clean-code-scan: large diff detected ($DIFF_LINES lines), scanning first 3000 lines."
    DIFF=$(echo "$DIFF" | head -3000)
fi

# Build file list
FILE_LIST=""
for f in $RELEVANT; do
    FILE_LIST="$FILE_LIST
- $f"
done

# Determine which rule files are needed
RULE_HINT=""
JS_FILES=$(echo "$RELEVANT" | grep -E '\.(js|jsx|mjs)$' 2>/dev/null || true)
TS_FILES=$(echo "$RELEVANT" | grep -E '\.(ts|tsx)$' 2>/dev/null || true)
JAVA_FILES=$(echo "$RELEVANT" | grep -E '\.java$' 2>/dev/null || true)
PY_FILES=$(echo "$RELEVANT" | grep -E '\.py$' 2>/dev/null || true)
BUILD_FILES=$(echo "$RELEVANT" | grep -E '(pom\.xml|build\.gradle|tsconfig\.json|webpack\.config\.|vite\.config\.|Makefile|CMakeLists\.txt)' 2>/dev/null || true)

if [ -n "$JS_FILES" ]; then
    RULE_HINT="$RULE_HINT
- JavaScript: docs/cleanCode/运营商服务与软件-javascript-3-代码审查规则.md"
fi
if [ -n "$TS_FILES" ]; then
    RULE_HINT="$RULE_HINT
- TypeScript: docs/cleanCode/typescript-minimal-review-rules.md"
fi
if [ -n "$JAVA_FILES" ]; then
    RULE_HINT="$RULE_HINT
- Java: docs/cleanCode/运营商服务与软件-java-最佳实践代码审查规则.md"
fi
if [ -n "$PY_FILES" ]; then
    RULE_HINT="$RULE_HINT
- Python: docs/cleanCode/python-minimal-v1-review-rules.md"
fi
if [ -n "$BUILD_FILES" ]; then
    RULE_HINT="$RULE_HINT
- Build: docs/cleanCode/编译选项-v3-代码审查规则.md"
fi

echo "clean-code-scan: invoking Claude for analysis..."
echo "  Files: $(echo "$RELEVANT" | wc -l | tr -d ' ') relevant"
echo "  Diff: $DIFF_LINES lines"

# Run Claude Code in non-interactive print mode
# Timeout after 120 seconds — on timeout, pass silently
RESULT=""
if command -v timeout >/dev/null 2>&1; then
    RESULT=$(timeout 120 claude --print \
        --allowedTools "Read" \
        -p "You are a clean code scanner following the /scan skill defined in skills/clean-code-scan/SKILL.md.

Files changed:
$FILE_LIST

Relevant rule files:$RULE_HINT

Diff content:
\`\`\`diff
$DIFF
\`\`\`

Instructions:
1. Read the relevant rule files listed above from the project root.
2. Start with the 规则索引 (rule index table) to identify applicable rules.
3. Check the diff against each applicable rule.
4. Only report violations with CLEAR code evidence in the diff.
5. For each violation include: file path, line number, rule name and ID, severity level, evidence code, and fix suggestion.
6. Group output by severity: 致命 and 严重 issues first (BLOCKING), then 一般, 提示, 信息 (non-blocking).
7. End your output with EXACTLY one of these lines on its own:
   SCAN_RESULT: PASS
   SCAN_RESULT: BLOCK
   Use BLOCK if and only if any 致命 or 严重 issues were found.
8. Output the report in Markdown format following the skill's output format template.
9. If no issues found, output the clean template with SCAN_RESULT: PASS." 2>&1) || {
        EXIT_CODE=$?
        if [ $EXIT_CODE -eq 124 ]; then
            echo "clean-code-scan: scan timed out (120s), passing without blocking."
            exit 0
        fi
        echo "clean-code-scan: Claude invocation failed (exit $EXIT_CODE)."
        echo "  To bypass: git commit --no-verify"
        echo "$RESULT"
        exit 0
    }
else
    # No timeout command available (some Windows environments)
    RESULT=$(claude --print \
        --allowedTools "Read" \
        -p "You are a clean code scanner following the /scan skill defined in skills/clean-code-scan/SKILL.md.

Files changed:
$FILE_LIST

Relevant rule files:$RULE_HINT

Diff content:
\`\`\`diff
$DIFF
\`\`\`

Instructions:
1. Read the relevant rule files listed above from the project root.
2. Start with the 规则索引 (rule index table) to identify applicable rules.
3. Check the diff against each applicable rule.
4. Only report violations with CLEAR code evidence in the diff.
5. For each violation include: file path, line number, rule name and ID, severity level, evidence code, and fix suggestion.
6. Group output by severity: 致命 and 严重 issues first (BLOCKING), then 一般, 提示, 信息 (non-blocking).
7. End your output with EXACTLY one of these lines on its own:
   SCAN_RESULT: PASS
   SCAN_RESULT: BLOCK
   Use BLOCK if and only if any 致命 or 严重 issues were found.
8. Output the report in Markdown format following the skill's output format template.
9. If no issues found, output the clean template with SCAN_RESULT: PASS." 2>&1) || {
        echo "clean-code-scan: Claude invocation failed."
        echo "  To bypass: git commit --no-verify"
        echo "$RESULT"
        exit 0
    }
fi

# Display the scan report
echo ""
echo "$RESULT"

# Parse the exit decision
if echo "$RESULT" | grep -q "SCAN_RESULT: BLOCK"; then
    echo ""
    echo "ERROR: Clean code scan found blocking issues (严重 or 致命)."
    echo "Fix the issues listed above, then re-commit."
    echo "To bypass: git commit --no-verify (not recommended)"
    exit 1
fi

# PASS or no issues
echo ""
echo "clean-code-scan: scan passed."
exit 0
