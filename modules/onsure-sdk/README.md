# ONSure Java SDK candidate

`ONSureClient` is a small versioned candidate API for the existing loopback-only Local API. It accepts only explicit `http://localhost`, `127.0.0.1`, or `::1` endpoints on non-privileged ports, never persists or exposes the bearer token, applies bounded timeouts, and returns nonfinal JSON responses.

This module is part of the modular compatibility build only. It does not replace the canonical standalone build, publish an artifact, rename `io.onsure`, or grant release authority.
