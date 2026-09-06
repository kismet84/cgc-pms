### Step 5: Group Changes by Skill/Module

Analyze commits since last tag and group by affected skill/module:

1. **Identify changed files** per commit
2. **Group by skill/module**:
   - `skills/<skill-name>/*` → Group under that skill
   - Root files (CLAUDE.md, etc.) → Group as "project"
   - Multiple skills in one commit → Split into multiple groups
3. **For each group**, identify related README updates needed

**Example Grouping**:
```
baoyu-cover-image:
  - feat: add new style options
  - fix: handle transparent backgrounds
  → README updates: options table

baoyu-comic:
  - refactor: improve panel layout algorithm
  → No README updates needed

project:
  - docs: update CLAUDE.md architecture section
```

### Step 6: Prepare the Module Commit Plan

Prepare the reviewable file groups and commit messages before Step 8. This step does not authorize staging or committing. Reuse existing commits; grouping their contents for release notes never authorizes splitting or rewriting history.

For each skill/module group:

1. **Check README updates needed**:
   - Scan `README*.md` for mentions of this skill/module
   - Verify options/flags documented correctly
   - Record suggested usage-example or feature-description updates when syntax or behavior changed
   - Apply only necessary documentation updates within the user's authorized local scope. A release-notes-only request does not authorize README, version, or business-code edits

2. **Prepare exact task-owned paths**:
   - Record the files and proposed message for each necessary new commit.
   - Preserve unrelated working-tree and staged changes; do not use directory globs to collect a group.
   - Execute approved groups in Step 9 only after the corresponding commit authorization is established in Step 8. A release-notes-only request authorizes no commits.

3. **Commit message format**:
   - Use conventional commit format: `<type>(<scope>): <description>`
   - `<type>`: feat, fix, refactor, docs, perf, etc.
   - `<scope>`: skill name or "project"
   - `<description>`: Clear, meaningful description of changes

**Example Proposed Messages**:
```text
feat(baoyu-cover-image): add watercolor and minimalist styles
fix(baoyu-comic): improve panel layout for long dialogues
docs(project): update architecture documentation
```

**Common README Updates Needed**:
| Change Type | README Section to Check |
|-------------|------------------------|
| New options/flags | Options table, usage examples |
| Renamed options | Options table, usage examples |
| New features | Feature description, examples |
| Breaking changes | Migration notes, deprecation warnings |
| Restructured internals | Architecture section (if exposed to users) |
