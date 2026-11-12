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
  - `/trade <player>`: asks to trade, from any distance (a key, unbound by default, asks the player in front of you).
  - `/hdv` (`/ah`): opens the auction house, unless `features.auctionsCommand` is off.
  - `/mailbox` (`/courrier`): collects items that didn't fit in your inventory.
  - `/economy notify on|off`: messages when someone buys from or sells to your shops.
  - `/economy status|pending|retry <key>|casino <#account> [outstanding <value>]|npc hdv [name]`: operators.
- **Casinos.** A casino is a shared account. Its counters sell chips (the player pays the casino's account) and cash
  them (the account pays the player). Chips carry their casino and only its machines and counters take them. While
  playing, nothing reaches the site: the mod counts each casino's chips in circulation, which its account must back,
  and a machine only starts a game the casino's **free balance** (balance − chips in circulation − rounds in
  progress) can cover. Machines and counters are linked to a casino with the casino link wand, by an owner or member
  of its account. With MineVegas Casino installed, its machines take only chips of their casino.
- **Shops.** A shop block sells one item, in lots, for an account its owner or a member chooses, and may buy it back
  at a lower or equal price. Its owners and members fill it (hoppers can fill it too, never empty it), set it up and
  break it; everyone else buys. The site registers each shop and prices every order from its saved prices. While
  the site decides, the items are held aside in the journal entry, so a refusal or a crash gives them back.
- **Trades.** Both players offer items (copies: nothing leaves the inventories yet) and LNBits from an account, then
  press Ready; any change sets both back. After a 3 second countdown, both offers leave the inventories together and
  are held in the journal entry while the site moves the difference in money. Each player then gets the other's
  items, or their own back if the site refuses.
- **Auction house.** Players list the stack in their hand at a fixed price, browse, buy, and take their listings
  back. Listings and their items live on the site: a listed item leaves the game, held in the journal entry until
  the site has it. A purchase lands in the buyer's mailbox on the site, which the server claims with a key and hands
  over; unsold items come back the same way from "To collect". It opens with `/hdv`, an auction house block (in the
  operators' creative tab, unbreakable outside creative) or the auctioneer NPC (`/economy npc hdv [name]`, removed
  by an operator hitting it in creative).
- **Mailbox.** Items owed to a player (bought chips or items, items handed back) go straight to the inventory; what
  doesn't fit waits for `/mailbox`. Nothing is dropped on the ground.
- **Reliability.** Every money move is written to `<world>/data/lnbeconomy_journal.json` before it is sent, and sent
  again with the same idempotency key until the site answers, after a restart too.

## Configuration

The site's URL and the server's token are LNB Warden's: `api.url` and `api.token` in `config/lnbwarden-server.toml`.
LNB Economy's own settings are in `config/lnbeconomy-server.toml` (confirmation threshold, refresh times, games on or
off, and switches for shops, trades, the auction house and its `/hdv` command). Neither file is ever sent to players.

## Building

LNB Warden and MineVegas Casino are not in any Maven repository and are never committed. Put their jars in `libs/`:

- `libs/lnbwarden-*.jar`: build it from `lnb-warden` with `./gradlew jar`;
- `libs/minevegas-*.jar`: MineVegas Casino 1.1.1 for NeoForge 1.21.1.

Then `./gradlew build` (Java 21).

The CI builds LNB Warden from its public repository and downloads MineVegas Casino from the `MINEVEGAS_JAR_URL`
secret, a direct link to its jar. Without that secret, it skips the build.

## Releases

Push a tag `vX.Y.Z` and the CI builds the mod as version `X.Y.Z`, then publishes `lnbeconomy-X.Y.Z.jar` as a GitHub
release. Players download that jar instead of building the mod. The release holds only this mod: LNB Warden, MineVegas
Casino, Kotlin for Forge and GeckoLib are installed separately. A tag fails the CI when `MINEVEGAS_JAR_URL` is not set.

## License

AGPL-3.0-or-later, like LNB Warden and the site.

## Tests

`./gradlew test` runs the unit tests, which need neither Minecraft nor the site.

`./gradlew integrationTest` calls a running site. Start it from `lnb-website` (`docker compose up -d`, then
`pnpm dev`) with `WARDEN_SERVER_SECRET` set in its `.env` and the `economy`, `economy.serverApi` and
`economy.transfers` flags on, then set:

| Variable          | Value                                                                       |
| ----------------- | --------------------------------------------------------------------------- |
| `LNB_API_URL`     | `http://[::1]:3000` (`pnpm dev` only listens on IPv6)                       |
| `LNB_API_TOKEN`   | the site's `WARDEN_SERVER_SECRET`                                           |
| `LNB_TEST_PLAYER` | UUID of a linked Minecraft account (offline mode: `OfflinePlayer:<name>`)    |
| `LNB_TEST_CASINO` | handle of a shared account that player owns or is a member of              |
| `LNB_TEST_PARTNER` | optional: UUID of another player's linked Minecraft account, for trades     |

The tests move 1 LNBit to that account and back. Without these variables, they are skipped.

`TESTING.md` is the manual test plan, in game.
