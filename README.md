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
