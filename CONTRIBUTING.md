# Contributing to Xelvra

Thank you for your interest in improving Xelvra.

Xelvra is an Android VPN client under active development.
Contributions are welcome through GitHub Issues and
Pull Requests.

## Reporting Bugs

Use the GitHub Bug Report form.

Include:
- Xelvra version.
- Android version and device model.
- Affected protocol, if applicable.
- Steps to reproduce.
- Expected and actual behavior.
- Sanitized logs when available.

Never publish VPN credentials, private keys, profile
URIs, access tokens, or sensitive diagnostic data.

## Proposing Changes

1. Search existing Issues and Pull Requests.
2. Open an Issue for substantial changes.
3. Fork the repository.
4. Create a dedicated branch.
5. Implement a focused change.
6. Test the affected functionality.
7. Open a Pull Request against `main`.
8. Respond to maintainer review feedback.

## Code Standards

- Follow existing Kotlin conventions.
- Keep changes focused and reviewable.
- Avoid unrelated formatting changes.
- Explain changes to VPN lifecycle and routing behavior.
- Include relevant tests when practical.
- Never commit credentials or signing keys.
- Preserve compatibility with non-rooted Android devices.

## Review and Merge Policy

The project maintainer is `@MasFha`.

All external contributions must be submitted through
Pull Requests.

A Pull Request is not considered accepted until
reviewed and approved by the maintainer.

Contributors must not assume that opening a Pull Request
grants permission to merge changes.

Repository branch protection must be configured
separately to enforce technical merge restrictions.

## Third-Party Code

Do not submit third-party source or binary dependencies
without identifying their origin, version, and license.

Contributions must comply with applicable third-party
license obligations.

## Security

Do not disclose suspected security vulnerabilities
through public Issues.

Follow SECURITY.md for private reporting guidance.
