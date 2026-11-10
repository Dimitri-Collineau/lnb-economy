# LNB Economy

NeoForge 1.21.1 mod (Kotlin for Forge) for LNB's Minecraft server: LNBit balances, payments between players and
casino chips. Every account and balance lives on LNB's site; the Minecraft server is the only one that calls it, and
the mod on a player's computer only talks to the server. The contract is `docs/economy.md` in `lnb-website`.

## Building

LNB Warden and MineVegas Casino are not in any Maven repository and are never committed. Put their jars in `libs/`:

- `libs/lnbwarden-*.jar`: build it from `lnb-warden` with `./gradlew jar`;
- `libs/minevegas-*.jar`: MineVegas Casino 1.1.1 for NeoForge 1.21.1.

Then `./gradlew build` (Java 21). The CI downloads both jars from the `WARDEN_JAR_URL` and `MINEVEGAS_JAR_URL` secrets
and skips the build when they are not set.

## License

AGPL-3.0-or-later, like LNB Warden and the site.

## Tests

`./gradlew test` runs the unit tests, which need neither Minecraft nor the site.

`./gradlew integrationTest` calls a running site. Start it from `lnb-website` (`docker compose up -d`, then
`pnpm dev`) with `WARDEN_SERVER_SECRET` set in its `.env` and the `economy`, `economy.serverApi` and
`economy.transfers` flags on, then set:

| Variable          | Value                                                                       |
| ----------------- | --------------------------------------------------------------------------- |
| `LNB_API_URL`     | `http://localhost:3000`                                                     |
| `LNB_API_TOKEN`   | the site's `WARDEN_SERVER_SECRET`                                           |
| `LNB_TEST_PLAYER` | UUID of a linked Minecraft account (offline mode: `OfflinePlayer:<name>`)    |
| `LNB_TEST_CASINO` | handle of a shared account that player owns or is a member of              |

The tests move 1 LNBit to that account and back. Without these variables, they are skipped.
