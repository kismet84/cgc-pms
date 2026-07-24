### Step 9: Create Release Commit and Annotated Tag

After user confirmation:

1. **Stage version and changelog files**:
   ```bash
   git add <version-file>
   git add CHANGELOG*.md
   ```

2. **Create release commit**:
   ```bash
   git commit -m "chore: release v{VERSION}"
   ```

3. **Create annotated tag**:
   ```bash
   git tag -a v{VERSION} -F <release-notes-file>
   ```
   If `.releaserc.yml` sets `tag.sign: true`, use `git tag -s` with the same notes file.

4. **Push if user confirmed** (Step 8):
   ```bash
   git push origin main
   git push origin v{VERSION}
   ```

**Note**: Do NOT add Co-Authored-By line. This is a release commit, not a code contribution.

### Step 10: Publish Release Artifacts and GitHub Release

Project artifact publishing and GitHub Releases are separate outputs:

1. **Project artifacts**:
   - If `release.hooks.publish_artifact` exists, run it once per prepared target
   - Pass the same `{release_notes_file}` used for the tag and GitHub Release
   - In dry-run mode, pass `{dry_run}=true` and report what would be published

2. **GitHub Release**:
   - Run only if the user confirmed remote publishing and GitHub support is available
   - Ensure the tag exists on the remote before creating the release
   - Create or update using the extracted notes:
     ```bash
     if gh release view v{VERSION} >/dev/null 2>&1; then
       gh release edit v{VERSION} --title "v{VERSION}" --notes-file <release-notes-file>
     else
       gh release create v{VERSION} --title "v{VERSION}" --notes-file <release-notes-file> --verify-tag
     fi
     ```
   - Never inline multiline release notes into shell commands

**Post-Release Output**:
```
Release v1.3.0 created.

Commits:
  1. feat(baoyu-cover-image): add watercolor and minimalist styles
  2. fix(baoyu-comic): improve panel layout for long dialogues
  3. docs(project): update architecture documentation
  4. chore: release v1.3.0

Tag: v1.3.0
Tag type: annotated
GitHub Release: published  # or "skipped/local only"
Status: Pushed to origin  # or "Local only - run git push when ready"
```
