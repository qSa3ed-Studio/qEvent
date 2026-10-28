# Security policy

## Reporting a vulnerability

Please report security issues **privately** by opening a
[GitHub security advisory](https://github.com/qSa3ed-Studio/qEvent/security/advisories/new)
on `qSa3ed-Studio/qEvent`, or by emailing the maintainer through the contact listed on the
[qSa3ed Studio profile](https://github.com/qSa3ed-Studio). Please do not open a public issue for
vulnerabilities.

We aim to acknowledge reports within 7 days.

## Design notes

- **No runtime dependencies.** The plugin JAR contains only qEvent's own classes and resources;
  everything else (Bukkit/Paper API, Adventure, Gson) is provided by the server. `mvn dependency:tree`
  shows one `provided` API dependency and one test-scoped JUnit dependency.
- **No secrets in the repository.** No API keys, tokens or credentials are needed by qEvent. The only
  outbound network calls are anonymous, read-only HTTPS requests to Mojang's public skin endpoints,
  with explicit connect/read timeouts and a custom User-Agent.
- **No unsafe downloads or execution.** The optional resource pack is only ever offered to the client
  as a URL + SHA-1 you configure yourself; qEvent never downloads or executes a pack itself.
- **Input handling.** Player-controlled strings are escaped before being inserted into MiniMessage
  templates, so an announcement message cannot inject formatting, click or hover events.
- **No NMS / reflection into server internals.** One reflective call exists (`Player#hideEntity`) and
  it is probed at startup with a graceful text-only fallback if unavailable.

## Supported versions

| Version | Supported |
|---|---|
| 1.0.3 | ✅ |
| 1.0.2 | ✅ |
| 1.0.1 | ✅ |
| 1.0.0 | ✅ |