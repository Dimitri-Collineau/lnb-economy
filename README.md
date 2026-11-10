# LNB Economy

NeoForge 1.21.1 mod (Kotlin for Forge) for LNB's Minecraft server: LNBit balances, payments between players and
casino chips. Every account and balance lives on LNB's site; the Minecraft server is the only one that calls it, and
the mod on a player's computer only talks to the server. The contract is `docs/economy.md` in `lnb-website`.

## What it does

- **Balances.** The inventory shows the default account's balance, with a "My accounts" button to see every account
  and choose the one that pays by default. Players without a linked LNB account have no economy.
- **Commands.**
  - `/balance` (`/bal`): your accounts.
  - `/pay <player|#account> <amount> [message]`: pays from your default account. Above `pay.confirmAbove`, asks to
    confirm with a click.
  - `/payfrom <#account> <player|#account> <amount> [message]`: pays from a shared account you own or are a member of.
  - `/economy status|pending|retry <key>|casino <#account> [outstanding <value>]`: operators.
- **Casinos.** A casino is a shared account. Its counters sell chips (the player pays the casino's account) and cash
  them (the account pays the player). Chips carry their casino and only its machines and counters take them. While
  playing, nothing reaches the site: the mod counts each casino's chips in circulation, which its account must back,
  and a machine only starts a game the casino's **free balance** (balance − chips in circulation − rounds in
  progress) can cover. Machines and counters are linked to a casino with the casino link wand, by an owner or member
  of its account. With MineVegas Casino installed, its machines take only chips of their casino.
- **Reliability.** Every transfer is written to `<world>/data/lnbeconomy_journal.json` before it is sent, and sent
  again with the same idempotency key until the site answers, after a restart too.

## Configuration

The site's URL and the server's token are LNB Warden's: `api.url` and `api.token` in `config/lnbwarden-server.toml`.
LNB Economy's own settings are in `config/lnbeconomy-server.toml` (confirmation threshold, refresh times, games on or
off). Neither file is ever sent to players.

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

`TESTING.md` is the manual test plan, in game.
