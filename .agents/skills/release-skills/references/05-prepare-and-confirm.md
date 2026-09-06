### Step 7: Generate Changelog and Update Version

Prepare the complete reviewable version changes and release notes within the user's authorized local scope. For `--dry-run` or a notes-only request, show the proposed content without changing version files. Do not stage or commit during preparation.

1. **Generate multi-language changelogs** (as described in Step 4)
2. **Update version file**:
   - Read version file (JSON/TOML/text)
   - Update version number
   - Write back (preserve formatting)
3. **Create release notes file**:
   - Prefer the new version section from `CHANGELOG.md`
   - If no English/default changelog exists, use the first detected changelog
   - Extract only the exact `## {VERSION} - {YYYY-MM-DD}` section through the next `##`
   - Match both plain version and tag-prefixed headings when needed, e.g. `1.2.3` and `v1.2.3`
   - Keep breaking changes near the top; if needed, add a short highlight before other sections
   - Write notes to a UTF-8 temp file and reuse it for annotated tag messages, GitHub Releases, and `publish_artifact`
   - In normal mode, stop rather than creating an empty tag or GitHub Release when notes cannot be found

**Version Paths by File Type**:

| File | Path |
|------|------|
| package.json | `$.version` |
| pyproject.toml | `project.version` |
| Cargo.toml | `package.version` |
| marketplace.json | `$.metadata.version` |
| VERSION / version.txt | Direct content |

### Step 8: User Confirmation

Before any proposed commit, Tag, or remote mutation, present the prepared version, change summary, release-notes source, exact file groups, resolved destination, and actions for review.

Record explicit decisions for each applicable action; they may be collected together:

1. **Local release**: exact target version, proposed module/release commits, Tag name and type, and intended target commit or approved integration route.
2. **Remote branch delivery**: remote, source/target branches, push, and any required PR/merge actions. Each action must be authorized under repository rules; branch push alone does not authorize merge or cleanup.
3. **Remote Tag**: exact Tag and remote. Local Tag creation does not authorize its push.
4. **GitHub Release**: exact repository, Tag, draft/public status, and create/update action. Offer only when supported; do not infer this approval from a branch or Tag push.
5. **Project artifacts**: exact hook, destination, and intended publication. Hook configuration alone is not approval.

Reuse an explicit approval already given for this same reviewed operation; do not ask again at each later step. Ask only for missing decisions or changes to the approved target, scope, or visibility. Preserve any rule requiring fresh approval at execution time. Silence, suggested defaults, and approval of another action do not grant approval.

Use a currently available tool only if it permits approval questions; otherwise ask in ordinary conversation. Do not depend on a fixed approval-tool name. Continue independent preparation while awaiting required decisions, but do not execute dependent mutations. Respect `--dry-run`, `no push`, and `autoPush=false`.

**Example Output Before Confirmation**:
```
Proposed commits (not executed):
  1. feat(baoyu-cover-image): add watercolor and minimalist styles
  2. fix(baoyu-comic): improve panel layout for long dialogues
  3. docs(project): update architecture documentation

Changelog preview (en):
  ## 1.3.0 - 2026-01-22
  ### Features
  - Add watercolor and minimalist styles to cover-image
  ### Fixes
  - Improve panel layout for long dialogues in comic

Release notes source: CHANGELOG.md#1.3.0
Prepared for review: local commits and annotated tag.
Remote branch, Tag, GitHub Release, and artifact publication: separate decisions required.
```
