# Security Policy

## Supported Versions

Only the latest release on `master` is actively maintained. Older versions do not receive security patches.

| Version | Supported |
|---------|-----------|
| Latest  | Yes       |
| Older   | No        |

---

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Report privately by e-mail:

```
admin@dev1lroot.com
```

Include in your report:

- A clear description of the vulnerability and its potential impact.
- Steps to reproduce (server/client setup, mod version, Java version).
- Any proof-of-concept code or screenshots if applicable.

You will receive an acknowledgement within **72 hours**. If the issue is confirmed, a fix will be prioritised and a patch release will be made. You will be credited in the release notes unless you prefer otherwise.

---

## Scope

Security reports are relevant when they involve:

- Remote code execution via malformed packets or save data.
- Arbitrary file read/write on the server or client through mod functionality.
- Authentication / permission bypass (e.g. server-side command execution without ops).
- Crashes triggered by untrusted input that could be weaponised for denial of service.

Reports about **gameplay exploits** (item duplication, unintended crafting paths, etc.) are welcome as regular bug reports via the issue tracker, not as security disclosures.
