## Configuration (.releaserc.yml)

Optional config file in project root to override defaults:

```yaml
# .releaserc.yml - Optional configuration

# Version file (auto-detected if not specified)
version:
  file: package.json
  path: $.version  # JSONPath for JSON, dotted path for TOML

# Changelog files (auto-detected if not specified)
changelog:
  files:
    - path: CHANGELOG.md
      lang: en
    - path: CHANGELOG.zh.md
      lang: zh
    - path: CHANGELOG.ja.md
      lang: ja

  # Section mapping (conventional commit type → changelog section)
  # Use null to skip a type in changelog
  sections:
    feat: Features
    fix: Fixes
    docs: Documentation
    refactor: Refactor
    perf: Performance
    test: Tests
    chore: null

# Commit message format
commit:
  message: "chore: release v{version}"

# Tag format
tag:
  prefix: v  # Results in v1.0.0
  sign: false

# Additional files to include in release commit
include:
  - README.md
  - package.json
```
