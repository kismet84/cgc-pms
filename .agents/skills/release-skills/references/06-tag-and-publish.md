### Step 9: Create Release Commit and Annotated Tag

Execute only the actions explicitly approved in Step 8. Recheck branch, worktree ownership, staged changes, target, and authorization immediately before mutation; stop only the affected action if they no longer match the reviewed operation. In `--dry-run`, report the planned actions without executing them.

1. **Commit approved task-owned files**:
   - Use the exact paths and groups reviewed in Steps 6 and 8, including only the approved version and changelog files.
   - Inspect the staged diff before each commit. Do not include unrelated staged or working-tree changes, and do not stage directory or changelog globs.
   - Reuse existing commits; create only the approved necessary module commits and release commit.

   Approved release commit example:
   ```bash
   git commit -m "chore: release v{VERSION}"
   ```

2. **Integrate through the approved repository route when authorized**:
   - Resolve the actual source and target branches; never assume `main`.
   - Follow repository-required checks, branch push, PR, and merge requirements using the corresponding approvals. Never push directly to a protected `master/main`, bypass checks, or invoke unapproved cleanup.
   - Verify the resulting remote commit when remote integration is part of the approved release. If release is local-only, retain the local release commit and do not perform remote actions.

3. **Create the approved annotated tag on the verified release commit**:
   - Resolve the exact local release SHA or approved integrated SHA before tagging. Do not tag an unrelated current checkout or silently move an existing Tag.
   ```bash
   git tag -a v{VERSION} <verified-release-sha> -F <release-notes-file>
   ```
   If `.releaserc.yml` sets `tag.sign: true`, use `git tag -s` with the same notes file.

4. **Push only the approved Tag to the reviewed remote**:
   ```bash
   git push <approved-remote> refs/tags/v{VERSION}
   ```
   Verify the remote Tag resolves to the intended release commit. Respect `no push` and `autoPush=false`; do not use a broad `--tags` push.

**Note**: Do NOT add Co-Authored-By line. This is a release commit, not a code contribution.

### Step 10: Publish Release Artifacts and GitHub Release

Project artifact publishing and GitHub Releases are separate outputs:

1. **Project artifacts**:
   - Run `release.hooks.publish_artifact` only when that exact publication and destination were explicitly approved in Step 8; configuration does not grant authorization
   - Run once per approved prepared target and verify the outcome before retrying an uncertain publication
   - Pass the same `{release_notes_file}` used for the tag and GitHub Release
   - In dry-run mode, report the proposed hook and destination without invoking a publishing hook

2. **GitHub Release**:
   - Run only if the user explicitly approved this GitHub Release create/update action and visibility, and GitHub support is available
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
Status: verified remote publication  # or "Local-only result; remote actions not executed"
```
