# Task 26: Dictionary Management — Decisions

## Split-pane vs tabs
**Decision**: Split-pane (left dict type list + right data table)
**Rationale**: Task requirement specified split-pane layout. More intuitive for master-detail pattern where users need to see types while browsing data.

## Auto-select first type
**Decision**: After fetching types, auto-select the first item if nothing is selected
**Rationale**: Improves UX — user lands on the page and immediately sees data without an extra click

## Show actions on hover only (type list)
**Decision**: Edit/Delete buttons on dict type items only appear on hover
**Rationale**: Keeps the left sidebar clean and reduces visual noise. Consistent with common UI patterns.

## Delete confirmation
**Decision**: Use `Modal.confirm` with danger type for both type and data deletions
**Rationale**: Consistent with `material/dictionary.vue` pattern. Prevents accidental deletions.

## Disable dictCode editing on edit
**Decision**: dictCode field is disabled when editing an existing type
**Rationale**: dictCode serves as the unique identifier/key — changing it could break references.

## Router change
**Decision**: Changed `/system` from a simple page to a redirect with `/system/dict` child
**Rationale**: Allows future system management pages (user/role/menu) to be added as siblings under `/system`
