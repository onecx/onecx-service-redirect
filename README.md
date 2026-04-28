# onecx-service-redirect

A Quarkus-based redirect service that rewrites legacy or external URLs to new target URLs.
Redirects are performed **client-side in the browser** using JavaScript, which is the key design decision that makes this service work — it allows redirect rules to access the full URL including the **fragment** (`#...`), which is never sent to the backend by the browser.

---

## How it works

```
Browser                          onecx-service-redirect
  │                                       │
  │  GET /old-app?param=1#/some/path      │
  │──────────────────────────────────────>│
  │                                       │  1. Match request path (without fragment)
  │                                       │     against server-side rule group keys
  │                                       │  2. Serialize matching client rules to JSON
  │  200 OK (HTML page with JS)           │
  │<──────────────────────────────────────│
  │                                       │
  │  Browser executes JS:                 │
  │    window.location.href includes #    │
  │    Try each rule in order (0, 1, ...)  │
  │    First regex match wins             │
  │    Apply replace-pattern substitution │
  │    window.location.href = newUrl      │
  │                                       │
  │  GET /new-app/some/path?param=1       │
  │──────────────────────────────────────>│ (target server)
```

### Why client-side?

The HTTP protocol strips the fragment (`#...`) before sending a request to the server. The server never sees `#/task/123`. The browser, however, has access to `window.location.href` which includes the full URL with the fragment. By returning a small HTML page with JavaScript, the redirect logic runs in the browser where the complete URL is available.

---

## Configuration

Rules are defined in `application.properties` (or any Quarkus config source) under the prefix `onecx.redirect.url-rewrite-rules`.

The configuration has **two levels**:

```
onecx.redirect.url-rewrite-rules."<server-side-key>"."<index>".pattern=<regex>
onecx.redirect.url-rewrite-rules."<server-side-key>"."<index>".replace-pattern=<template>
```

| Part | Description |
|---|---|
| `<server-side-key>` | A Java regex matched against the incoming request URL **on the server** (without fragment). Selects which group of client rules to send to the browser. |
| `<index>` | A numeric index (`"0"`, `"1"`, `"2"`, ...) defining the **priority order** in which rules are tried by the browser. Lower = higher priority. |
| `pattern` | A JavaScript regex matched against `window.location.href` **in the browser** (includes fragment). Use named capture groups `(?<name>...)` to extract values. |
| `replace-pattern` | The target URL template. Reference captured groups with `($name)`. |

### Optional settings

| Property | Description |
|---|---|
| `onecx.redirect.host-forward-rules."<ruleId>".host-pattern` | Regex matched against incoming host. |
| `onecx.redirect.host-forward-rules."<ruleId>".proxy-host` | Target proxy host for matching host-pattern. |
| `onecx.redirect.host-forward-rules."<ruleId>".proxy-protocol` | Optional target protocol (`http` or `https`). |
| `onecx.redirect.rules.mode` | Rule evaluation mode: `combined` (default) or `separate`. |
| `onecx.redirect.rules.redirect-wait-seconds` | Wait time in seconds used by `redirectWaitTemplate.html` (default `10`). |
| `onecx.redirect.bundled-redirect-template-name` | Name of a bundled classpath template in `src/main/resources/templates` (for example `redirectWaitTemplate.html`). |
| `onecx.redirect.custom-redirect-template-path` | Path to a custom HTML template used when a rule group matches. The template receives `{rules}` and optional `{hostForwardRule}`. |
| `onecx.redirect.custom-fallback-template-path` | Path to a custom HTML template used when no rule group matches. The template receives a `{reqPath}` variable. |

---

## Using Redirect Wait Template

If you want a countdown page before redirecting, use `redirectWaitTemplate.html` and configure `onecx.redirect.rules.redirect-wait-seconds`.

Template resolution priority:

- `onecx.redirect.custom-redirect-template-path` (filesystem path) - highest priority
- `onecx.redirect.bundled-redirect-template-name` (classpath template in jar)
- default injected `redirectTemplate.html`

### Option 1: application.properties

```ini
# Enable wait redirect template from filesystem
onecx.redirect.custom-redirect-template-path=/deployments/config/redirectWaitTemplate.html

# Wait time used by redirectWaitTemplate.html
onecx.redirect.rules.redirect-wait-seconds=10

# Optional: host/path rule mode
onecx.redirect.rules.mode=combined
```

To use the bundled template from the JAR instead of a filesystem file:

```ini
onecx.redirect.bundled-redirect-template-name=redirectWaitTemplate.html
onecx.redirect.rules.redirect-wait-seconds=10
onecx.redirect.rules.mode=combined
```

Notes:

- `custom-redirect-template-path` must point to a file on the container filesystem.
- The default value for `onecx.redirect.rules.redirect-wait-seconds` is `10`.

### Option 2: Helm (values.yaml)

For this chart, configure runtime properties via `app.config.values` and mount the wait template file via `app.data.import`.

```yaml
app:
  config:
    enabled: true
    values: |
      onecx.redirect.custom-redirect-template-path=/deployments/config/redirectWaitTemplate.html
      onecx.redirect.rules.redirect-wait-seconds=10
      onecx.redirect.rules.mode=combined

  data:
    import:
      enabled: true
      # The mounted file path used above in custom-redirect-template-path
      mountPath: /deployments/config/redirectWaitTemplate.html
      subPath: redirectWaitTemplate.html
      values: |
        <!-- Paste the exact content of src/main/resources/templates/redirectWaitTemplate.html -->
        <!DOCTYPE html>
        <html lang="en">
        ...
        </html>
```

Environment-variable style is also possible in Helm (`app.env`), for example:

```yaml
app:
  env:
    ONECX_REDIRECT_RULES_REDIRECT_WAIT_SECONDS: "10"
    ONECX_REDIRECT_RULES_MODE: "combined"
    ONECX_REDIRECT_CUSTOM_REDIRECT_TEMPLATE_PATH: "/deployments/config/redirectWaitTemplate.html"
```

  This env example uses a filesystem template, so the file must be mounted (or baked into the image).

If you want to use the bundled wait template from the JAR via Helm (no file mount):

```yaml
app:
  env:
    ONECX_REDIRECT_BUNDLED_REDIRECT_TEMPLATE_NAME: "redirectWaitTemplate.html"
    ONECX_REDIRECT_RULES_REDIRECT_WAIT_SECONDS: "10"
    ONECX_REDIRECT_RULES_MODE: "combined"
```

When using `ONECX_REDIRECT_BUNDLED_REDIRECT_TEMPLATE_NAME`, no file mount is needed because the template is loaded from the application JAR classpath.

---

## Writing rules

### Capturing query parameters

Query parameters are part of the URL that the browser sees. Use a named capture group in the `pattern` to extract them.

> **Escaping `?`:** Two levels of escaping are needed. First, `.properties` files require `\\` to produce a single `\`. Second, the value is embedded into a JSON string in the browser payload, which also requires `\\` to produce a single `\`. So to get a regex `\?` (escaped question mark) in the browser, you need `\\\\?` in the properties file: `\\\\` → Java string `\\` → JSON string `\` → JS regex `\?`.

**Example** — capture `u-id` query parameter:

```ini
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".pattern=my-app\\\\?u-id=(?<uid>.+)
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".replace-pattern=/ui/new-app?u-id=($uid)
```

| Input URL | Output URL |
|---|---|
| `http://host/my-app?u-id=42` | `http://host/ui/new-app?u-id=42` |

---

### Capturing fragments

The fragment (`#...`) is included in `window.location.href` and can be matched directly in the `pattern`.

**Example** — capture a fragment path segment:

```ini
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".pattern=my-app#/details/(?<id>.+)
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".replace-pattern=/ui/new-app/details/($id)
```

| Input URL | Output URL |
|---|---|
| `http://host/my-app#/details/123` | `http://host/ui/new-app/details/123` |

---

### Capturing both query parameters and fragments

**Important:** When a pattern captures a query parameter that appears before a `#` fragment, use `[^#]+` (not `.+`) for that capture group. `.+` is greedy and will consume past the `#`, causing the wrong rule to match.

```ini
# ✅ Correct — [^#]+ stops at the fragment boundary
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".pattern=my-app\\\\?u-id=(?<uid>[^#]+)#/task/(?<taskId>.+)
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".replace-pattern=/ui/new-app/task/($taskId)?u-id=($uid)

# ❌ Wrong — .+ will greedily consume '20000#/task/99' as the uid group
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".pattern=my-app\\\\?u-id=(?<uid>.+)#/task/(?<taskId>.+)
```

| Input URL | Output URL |
|---|---|
| `http://host/my-app?u-id=20000#/task/99` | `http://host/ui/new-app/task/99?u-id=20000` |

---

### Multiple rules for the same app (multiple fragments)

This is the main use case for multiple client-side rules. The server can only see the URL **without** the fragment, so both `?u-id=20000#/task/99` and `?u-id=20000#/testorder/55` and `?u-id=20000` (no fragment) arrive at the server as identical requests. The browser, however, has the full URL and can distinguish between them.

Define a **rule group** under one server-side key and add one client-side rule per variant, ordered from **most specific to least specific**:

```ini
# Rule group: my-app
# Index "0" — most specific: URL has a #/task/ fragment
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".pattern=my-app\\\\?u-id=(?<uid>[^#]+)#/task/(?<taskId>.+)
onecx.redirect.url-rewrite-rules.".*/my-app.*"."0".replace-pattern=/ui/new-app/task/($taskId)?u-id=($uid)

# Index "1" — less specific: URL has a #/testorder/ fragment
onecx.redirect.url-rewrite-rules.".*/my-app.*"."1".pattern=my-app\\\\?u-id=(?<uid>[^#]+)#/testorder/(?<orderId>.+)
onecx.redirect.url-rewrite-rules.".*/my-app.*"."1".replace-pattern=/ui/new-app/testorder/($orderId)?u-id=($uid)

# Index "2" — least specific: URL has no fragment (catch-all for this group)
onecx.redirect.url-rewrite-rules.".*/my-app.*"."2".pattern=my-app\\\\?u-id=(?<uid>.+)
onecx.redirect.url-rewrite-rules.".*/my-app.*"."2".replace-pattern=/ui/new-app/overview?u-id=($uid)
```

| Input URL | Matched rule | Output URL |
|---|---|---|
| `http://host/my-app?u-id=20000#/task/99` | `"0"` | `http://host/ui/new-app/task/99?u-id=20000` |
| `http://host/my-app?u-id=20000#/testorder/55` | `"1"` | `http://host/ui/new-app/testorder/55?u-id=20000` |
| `http://host/my-app?u-id=20000` | `"2"` | `http://host/ui/new-app/overview?u-id=20000` |

The browser tries rule `"0"` first. If the regex does not match (e.g. no `#/task/` in the URL), it moves on to `"1"`, then `"2"`. The first match wins.

> **Rule:** Always put the most specific pattern (one that requires a fragment) at a lower index number than the less specific ones. The catch-all (no fragment) must always be last.

---

### Multiple independent app groups

Each server-side key is an independent group. When a request comes in, the server picks the **most specific matching key** (longest key after stripping `.*` wildcards) and sends only that group's rules to the browser.

```ini
onecx.redirect.url-rewrite-rules.".*/app-a.*"."0".pattern=app-a#/page/(?<page>.+)
onecx.redirect.url-rewrite-rules.".*/app-a.*"."0".replace-pattern=/ui/app-a/($page)

onecx.redirect.url-rewrite-rules.".*/app-b.*"."0".pattern=app-b\\\\?id=(?<id>.+)
onecx.redirect.url-rewrite-rules.".*/app-b.*"."0".replace-pattern=/ui/app-b/details/($id)
```

A request to `/app-a/...` only ever receives the `app-a` rules. `app-b` rules are never sent.

---

## Fallback behaviour

If no server-side key matches the incoming URL, the fallback template is returned, showing a `502 - Bad Gateway` page with the requested path. This can be customised via `onecx.redirect.custom-fallback-template-path`.

---

## Escaping reference

| Character to match | In `.properties` file | Explanation |
|---|---|---|
| Literal `?` | `\\\\?` | `\\\\` → Java `\\` → JSON `\` → JS regex `\?` (escaped question mark) |
| Literal `.` | `\\\\.` | Same principle |
| Named group stopping before `#` | `(?<name>[^#]+)` | Use character class instead of greedy `.+` |
