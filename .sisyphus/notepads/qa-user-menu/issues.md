# Issues Found

## Critical
- None

## Minor
1. **Double-click duplicate submissions**: Both Profile and Settings pages allow rapid double-clicks to trigger duplicate API calls. Buttons should be disabled during loading state.
2. **Stale Vite route cache**: Routes for profile/settings/help were not registered on first dev server load. Required dev server restart to pick up new routes.

## Observations
- Sidebar now shows Profile/Settings/Help as permanent menu items (duplicate with dropdown)
- Page title briefly shows incorrect value before router.afterEach update
- Backend API calls fail with 500 when backend is not running (expected)
