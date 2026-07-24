### Step 1: Detect Project Configuration

1. Check for `.releaserc.yml` (optional config override)
   - If present, inspect whether it defines release hooks
2. Auto-detect version file by scanning (priority order):
   - `package.json` (Node.js)
   - `pyproject.toml` (Python)
   - `Cargo.toml` (Rust)
   - `marketplace.json` or `.claude-plugin/marketplace.json` (Claude Plugin)
   - `VERSION` or `version.txt` (Generic)
3. Scan for changelog files using glob patterns:
   - `CHANGELOG*.md`
   - `HISTORY*.md`
   - `CHANGES*.md`
4. Identify language of each changelog by filename suffix
5. Detect GitHub release support:
   - Check whether `origin` points to GitHub
   - Check whether `gh` is installed and authenticated
   - Check existing releases with `gh release list --limit 5` when available
6. Display detected configuration

**Project Hook Contract**:

If `.releaserc.yml` defines `release.hooks`, keep the release workflow generic and delegate project-specific packaging/publishing to those hooks.

Supported hooks:

| Hook | Purpose | Expected Responsibility |
|------|---------|-------------------------|
| `prepare_artifact` | Make one target releasable | Validate the target is self-contained, sync/embed local dependencies, optionally stage extra files |
| `publish_artifact` | Publish one releasable target | Upload the prepared target (or a staged directory if the project uses one), attach version/changelog/tags |

Supported placeholders:

| Placeholder | Meaning |
|-------------|---------|
| `{project_root}` | Absolute path to repository root |
| `{target}` | Absolute path to the module/skill being released |
| `{artifact_dir}` | Absolute path to a temporary staging directory for this target, when the project uses one |
| `{version}` | Version selected by the release workflow |
| `{dry_run}` | `true` or `false` |
| `{release_notes_file}` | Absolute path to a UTF-8 file containing release notes/changelog text |

Execution rules:
- Keep the skill generic: do not hardcode registry/package-manager/project layout details into this SKILL.
- If `prepare_artifact` exists, run it once per target before publish-related checks that need the final releasable target state.
- Write release notes to a temp file and pass that file path to `publish_artifact`; do not inline multiline changelog text into shell commands.
- If hooks are absent, fall back to the default project-agnostic release workflow.

**Language Detection Rules**:

Changelog files follow the pattern `CHANGELOG_{LANG}.md` or `CHANGELOG.{lang}.md`, where `{lang}` / `{LANG}` is a language or region code.

| Pattern | Example | Language |
|---------|---------|----------|
| No suffix | `CHANGELOG.md` | en (default) |
| `_{LANG}` (uppercase) | `CHANGELOG_CN.md`, `CHANGELOG_JP.md` | Corresponding language |
| `.{lang}` (lowercase) | `CHANGELOG.zh.md`, `CHANGELOG.ja.md` | Corresponding language |
| `.{lang-region}` | `CHANGELOG.zh-CN.md` | Corresponding region variant |

Common language codes: `zh` (Chinese), `ja` (Japanese), `ko` (Korean), `de` (German), `fr` (French), `es` (Spanish).

**Output Example**:
```
Project detected:
  Version file: package.json (1.2.3)
  Changelogs:
    - CHANGELOG.md (en)
    - CHANGELOG.zh.md (zh)
    - CHANGELOG.ja.md (ja)
```
