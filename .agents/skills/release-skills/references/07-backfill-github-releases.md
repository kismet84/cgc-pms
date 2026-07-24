## Backfill Existing GitHub Releases

Use this mode when the user asks to backfill historical releases or passes `--backfill-releases`.

1. Do not bump versions, edit changelogs, or create release commits.
2. List existing tags in version order and detect missing releases:
   ```bash
   git tag --sort=v:refname
   gh release view <tag>
   ```
3. For each tag without a GitHub Release:
   - Normalize the changelog lookup by stripping the configured tag prefix, e.g. `v1.2.3` -> `1.2.3`
   - Extract the matching section from `CHANGELOG.md`; fall back to the first matching changelog file
   - Skip or ask before publishing if no matching changelog section exists
   - Create the release with:
     ```bash
     gh release create <tag> --title "<tag>" --notes-file <release-notes-file> --verify-tag
     ```
4. Detect lightweight tags with `git cat-file -t <tag>` (`commit` means lightweight, `tag` means annotated).
5. Do not rewrite public lightweight tags by default. Converting an existing remote tag to an annotated tag requires explicit user confirmation because it rewrites a published reference.
