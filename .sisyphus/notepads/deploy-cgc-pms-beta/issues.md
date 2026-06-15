# Issues

## Docker Build Failure (2026-06-13)

**Symptom:** Backend Docker build fails with `/.mvn: not found` and `/mvnw: not found`

**Root Cause:** `backend/.dockerignore` (lines 27-28) excludes `.mvn/` and `mvnw`, but `backend/Dockerfile` (lines 9-11) requires them for the Maven wrapper dependency resolution step.

**Fix Options:**
1. Remove `.mvn/` and `mvnw` lines from `backend/.dockerignore`
2. Modify `Dockerfile` to use `mvn` from the Maven base image directly instead of `./mvnw`

**Status:** Not fixed — documented per task constraints ("Do NOT modify docker-compose or Dockerfiles; do NOT try to fix Docker issues").
