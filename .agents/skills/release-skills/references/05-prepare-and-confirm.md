### Step 7: Generate Changelog and Update Version

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

Before creating the release commit, ask user to confirm:

**Use AskUserQuestion with three questions**:

1. **Version bump** (single select):
   - Show recommended version based on Step 3 analysis
   - Options: recommended (with label), other semver options
   - Example: `1.2.3 → 1.3.0 (Recommended)`, `1.2.3 → 1.2.4`, `1.2.3 → 2.0.0`

2. **Push to remote** (single select):
   - Options: "Yes, push after commit", "No, keep local only"

3. **Publish GitHub Release** (single select):
   - Offer this only when GitHub release support is available
   - Default to "Yes, publish after tag push" when the user also chose push
   - If the user keeps the release local, do not create or edit a GitHub Release

**Example Output Before Confirmation**:
```
Commits created:
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
Ready to create release commit, annotated tag, and GitHub Release.
```
