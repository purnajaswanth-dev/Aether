# Unified Engineering Context Platform: Design System

Goal: a calm, professional, dense-but-clean engineering workspace in the spirit of Linear, GitHub and Postman. It must never look like a generic chatbot.

## Theme & Colour Tokens
- Theme: light.
- Colour tokens:
  - canvas: `#FFFFFF`
  - subtle: `#F7F8FA`
  - muted: `#EFF1F4`
  - border: `#E3E6EA`
  - border-strong: `#CDD2D9`
  - text-primary: `#1B1F24`
  - text-secondary: `#5B6470`
  - text-tertiary: `#8A93A0`
  - accent: `#4F5BD5`
  - accent-hover: `#4350C4`
  - accent-subtle: `#EEF0FD`
  - success: `#1F8A4C` (subtle `#E8F6EE`)
  - warning: `#B26A00` (subtle `#FFF4E0`)
  - danger: `#D0343B` (subtle `#FDECEC`)
  - info: `#1F6FEB` (subtle `#EAF2FE`)
- Contrast requirement: All text/background pairs must meet WCAG AA contrast.

## Typography
- Inter (UI), JetBrains Mono (code, IDs, hashes, logs).
- Base 14px. Scale 12/13/14/16/20/24/30.
- Medium weight for labels, semibold for headings.
- No more than two weights per view.

## Spacing & Shape
- 4px grid.
- Radius: 6px for controls, 8px for cards and panels.
- Borders over shadows; shadow only for popovers (`0 8px 24px rgba(16,24,40,.12)`) and subtle card lift (`0 1px 2px rgba(16,24,40,.05)`).

## Layout
- Collapsible 240px left sidebar.
- Slim top bar with breadcrumbs and a Cmd/Ctrl+K command palette.
- Content max-width 1200px.
- List-detail split panes for artifacts, decisions and tasks.
- Table rows 36px.
- Generous whitespace around groups, tight spacing inside groups.

## Motion
- Transitions only on opacity and transform.
- Hover/focus: 120ms.
- Panels/menus/dialogs: 180ms.
- Route changes: 200ms fade with 4px rise.
- Easing: `cubic-bezier(0.2, 0, 0, 1)`.
- Honour `prefers-reduced-motion` (disable transitions).
- No bouncing, no parallax, no layout-shifting animation.

## Feedback
- Skeleton loaders instead of spinners for content.
- Optimistic updates only where safe.
- Toasts bottom-right.
- Inline validation.
- Every empty state explains what it is and offers the one real next action.

## Status Indicators
- Pill badges with a small dot (Connected, Not connected, Not available, Syncing, Error, Proposed, Approved, etc.).
- Never colour alone: always text or icon too.

## Accessibility
- Full keyboard navigation.
- Visible 2px accent focus ring.
- Semantic landmarks.
- ARIA via Radix primitives.
- Minimum 32px hit targets.

## Primary Navigation Structure
- Global navigation: Projects, Tasks, Memory, Integrations, Timeline, Artifacts, Settings.
- Inside a project navigation: Overview, Context, Decisions, Tasks, Activity, Integrations, Deployment.
