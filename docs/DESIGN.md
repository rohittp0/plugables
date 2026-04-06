# Plugables Docs — Design System

This document defines the visual and structural standards for all documentation
pages in this directory. New pages must follow these conventions.

---

## Aesthetic Direction

**"Warm Technical Manuscript"** — the feel of a well-typeset technical reference book.
Clean, precise, and readable. The design prioritises legibility and structure over
visual novelty, but uses distinctive typography to create a memorable character.

---

## Typography

All fonts are loaded from Google Fonts via a single `@import` at the top of `style.css`.

| Role | Font | Weights | Notes |
|------|------|---------|-------|
| Display (H1, H2) | Fraunces | 300, 400, 600, 700 | Variable optical-size serif. Use italic for stylistic emphasis in hero titles. |
| Body | Nunito Sans | 400, 500, 600 | Clean humanist sans. Variable optical size. |
| Code | JetBrains Mono | 400, 500 | All `<code>`, `<pre>`, and monospace labels. |

### Type Scale

Defined with `clamp()` for fluid scaling. Do not add fixed `font-size` overrides
outside of the scale — use the appropriate heading level instead.

| Tag | Size |
|-----|------|
| `h1` | `clamp(2.25rem, 5vw, 3.5rem)`, weight 700 |
| Hero `h1` | `clamp(3rem, 8vw, 5.5rem)`, weight 700 |
| `h2` | `clamp(1.5rem, 3vw, 2rem)`, weight 600 |
| `h3` | `1.25rem`, weight 600, body font |
| `h4` | `0.75rem`, uppercase, letter-spaced — use for section labels/eyebrows only |
| Body `p` | `1rem` / 16px base |
| Small label | `0.6875rem` (11px), uppercase, `letter-spacing: 0.1em` |

---

## Colour Palette

Defined as CSS custom properties in `:root` inside `style.css`. Always use the
variables — never hardcode hex values in page-specific CSS.

```
--bg:            #fafaf8   Warm off-white — page background
--bg-alt:        #f2f0eb   Slightly darker — card surfaces, table headers
--bg-code:       #18181b   Near-black — code block background
--bg-callout:    #fff7f0   Light orange tint — note/warning callouts
--bg-tip:        #f0fdf4   Light green tint — tip/success callouts

--text:          #18181b   Primary text (zinc-900)
--text-2:        #52525b   Secondary text (zinc-600)
--text-3:        #a1a1aa   Tertiary / placeholder (zinc-400)
--text-code:     #e4e0d5   Text inside code blocks

--accent:        #d94f23   Burnt orange-red — primary accent, links, highlights
--accent-hover:  #b83d17   Darker accent for hover states
--accent-bg:     #fff1ea   Very light orange — badge backgrounds

--border:        #e4e4e7   Default border (zinc-200)
--border-strong: #c4c4c8   Emphasized border (zinc-300)

--green:         #15803d   Success/tip accent
--green-bg:      #f0fdf4   Tip callout background
```

---

## Layout

### Navigation bar
- `position: sticky`, `height: 64px` (`--nav-height`)
- Frosted glass effect: `backdrop-filter: blur(12px)` + semi-transparent background
- Left: brand logo (Fraunces, 1.125rem, accent dot before brand name)
- Right: page links + GitHub link

### Home page — `.home-layout`
- Max width: `1120px` (`--page-max`), centered, `padding: 0 2rem`
- Hero section followed by sections with `margin-bottom: 4rem`

### Plugin/doc pages — `.page-layout`
- CSS Grid: `220px sidebar | 1fr content`
- Gap: `4rem`
- Max width: `1120px`
- Sidebar is `position: sticky`, top `= nav-height + 2rem`
- Below 768px: sidebar is hidden, single-column layout

### Content max width
- `p` elements: `max-width: 68ch`
- Code blocks, tables, diagrams: full content column width
- Page lead paragraphs: `max-width: 62ch`

---

## Components

### Headings in doc sections
Use `<section id="section-id" class="doc-section">` for every major section.
This enables TOC anchor links and applies `scroll-margin-top` to account for
the sticky nav.

```html
<section id="installation" class="doc-section">
  <h2>Installation</h2>
  <!-- content -->
</section>
```

### Code blocks
Always wrap `<pre>` in `.code-wrap` and add a `.code-lang` label:

```html
<div class="code-wrap">
  <span class="code-lang">kotlin</span>
  <pre><code><!-- code here --></code></pre>
</div>
```

**Syntax spans** — apply manually to key tokens for readability:

| Class | Use for | Colour |
|-------|---------|--------|
| `.k` | Keywords (`val`, `fun`, `class`, `import`) | Red-pink |
| `.fn` | Function/property names at definition site | Purple |
| `.ty` | Type names | Blue |
| `.st` | String literals | Light blue |
| `.cm` | Comments | Muted grey, italic |
| `.nm` | Number/boolean literals | Orange |
| `.op` | Operators, braces, punctuation | Light grey |
| `.yk` | YAML keys | Green |
| `.yv` | YAML values | Light blue |
| `.yc` | YAML comments | Muted grey, italic |

Use spans sparingly — only on the most semantically meaningful tokens. Avoid
over-colouring. When in doubt, leave it unstyled (`--text-code` default).

### Callouts
Two styles: default (orange, for notes/warnings) and `tip` (green).

```html
<div class="callout">
  <p class="callout-title">Note</p>
  <p>Content here.</p>
</div>

<div class="callout tip">
  <p class="callout-title">Tip</p>
  <p>Content here.</p>
</div>
```

### Tables
Always wrap tables in `.table-wrap` for overflow scroll on mobile:

```html
<div class="table-wrap">
  <table>
    <thead><tr><th>Col</th></tr></thead>
    <tbody><tr><td>Value</td></tr></tbody>
  </table>
</div>
```

### Badges
Three variants: `badge-version`, `badge-platform`, `badge-kotlin`.

```html
<span class="badge badge-version">v1.0.0</span>
<span class="badge badge-platform">Android</span>
<span class="badge badge-kotlin">Kotlin</span>
```

### Feature grid
Use for 3–6 short feature descriptions. Responsive grid, auto-fit.

```html
<div class="feature-grid">
  <div class="feature-item">
    <strong>Feature name</strong>
    <p>Short description.</p>
  </div>
</div>
```

### Pipeline diagram
Use to show a build step sequence. Horizontal by default, stacks vertically on mobile.

```html
<div class="pipeline">
  <div class="pipeline-step"><strong>Step 1</strong><span>subtitle</span></div>
  <span class="pipeline-arrow">→</span>
  <div class="pipeline-step"><strong>Step 2</strong><span>subtitle</span></div>
</div>
```

---

## Page Structure

Every page must follow this skeleton:

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <!-- meta charset, viewport, title, description -->
  <link rel="stylesheet" href="style.css" />
</head>
<body>
  <nav class="site-nav"><!-- nav --></nav>

  <!-- Home: <div class="home-layout"> -->
  <!-- Plugin page: <div class="page-layout"> with sidebar + main.content -->

  <footer class="site-footer"><!-- footer --></footer>
</body>
</html>
```

### Sidebar TOC
Every plugin/doc page must include a sidebar TOC with `<nav class="toc">`.
Links must target `#section-id` anchors matching the page's `doc-section` ids.
Use `.toc-h3` class for subsection links (adds left indent).

---

## Writing Style

- **Lead with the benefit**, not the mechanism. "Eliminate stub maintenance" not "generates a file".
- Keep `<p>` elements short — max 3 sentences. Use lists for enumerations of 3+.
- Code examples must reflect the actual API — never pseudocode in live docs.
- Every table row with required fields should be accurate; mark optional fields explicitly.
- KDoc-style descriptions in YAML should be professional but direct — a phrase, not a sentence fragment.

---

## Adding a New Plugin Page

1. Copy `typed-events.html` as a starting template.
2. Update `<title>`, meta description, breadcrumb, and `<h1>`.
3. Populate the TOC sidebar with the page's actual section IDs.
4. Fill in sections: Overview, Installation, Configuration, Example, (optional) Advanced.
5. Use the same `doc-section` + anchor pattern for all sections.
6. Add the plugin to the `plugin-grid` in `index.html`.
7. Link to the new page from the nav in all existing pages.
