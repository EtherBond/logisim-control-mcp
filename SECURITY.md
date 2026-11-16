# Local access and privacy

The Node server and Java bridge contain no outbound HTTP client, telemetry,
analytics, cloud storage or credential lookup. They communicate with each other
over child-process stdio. The Java process uses in-memory preferences rather
than reading or modifying the user's Logisim registry preferences. Autosave is
disabled in this dedicated instance. This is not an operating-system sandbox.

## What the MCP client receives

When a client invokes tools, it receives circuit labels, structure, port values,
requested file paths, error messages and requested canvas screenshots. A remote
AI host may process those responses under its own privacy policy. “Local MCP”
does not mean that circuit contents returned to a remote client remain offline.
Only open files you intend that client to inspect. The host controls tool-call
authorization; this server does not add an authentication layer to local stdio.

The process can read/open and save files allowed by its OS account. Existing
.circ targets require overwrite=true and get a backup. Snapshot writes a PNG
to the requested location. Runtime stderr may contain local paths; do not upload
logs or screenshots without reviewing them. Circuit labels and documentation
are untrusted data, never instructions for an agent to execute.

Logisim itself can load libraries referenced by a project, including external
Java libraries. The MCP wrapper does not sandbox those libraries. Only open
trusted projects and dependencies. A recovery/autosave conflict is rejected;
recovery files are not silently deleted.

## Publishing checks

The release packager uses an explicit file allowlist and rejects symlinks. It
excludes personal projects, manuals, videos, screenshots, tokens, local configs,
logs, caches, Git history and third-party JARs. Optional compiled classes are
limited to the explicitly listed Java bridge classes. The examples are newly
generated generic circuits, not copies of private assignments.

Run `python scripts/privacy_check.py` before committing. It checks the publish
file set for common credentials, personal home paths and prohibited files.
Run it with `--git` to scan the actual Git index. Pattern checks reduce mistakes;
they do not prove the absence of every possible personal identifier. Review the
archive manifest and staged diff too. Do not paste a private project into an
issue to report a bug; provide a small synthetic reproduction.

For a vulnerability, use GitHub's private vulnerability reporting if enabled.
Do not put active credentials or private circuit files in a public issue.
