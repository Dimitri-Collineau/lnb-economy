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
  - `/account` (`/compte`) `create <handle> <name>`, `rename <handle> <name>`, `type <handle> team|shop|casino`,
    `invite <handle> <player>`, `members <handle>`, `invitations`, `accept|decline <invitation>`: shared accounts.
  - `/economy notify on|off`: messages when someone buys from or sells to your shops, and when one of your auction
    house listings sells.
  - `/economy status|pending|retry <key>|casino <#account> [outstanding <value>]|npc hdv|licence [name]`: operators.
- **Shared accounts.** "My accounts" (and `/account`) opens a shared account, renames it, sets its type (team, shop
  or casino) and invites players, who answer from the chat or from "My accounts" at once or when they next join. The
  members screen proposes the players who are on as the owner types. Roles, removals and closing stay on the site.
- **Casinos.** A casino is a shared account of type casino, which takes a **casino licence**: the licence dealer (an
  NPC placed with `/economy npc licence [name]`) sells one for the price set on the site, burned, and hands over a
  signed book. One licence makes one account a casino, for good. Its counters sell chips (the player pays the casino's account) and cash
  them (the account pays the player). Chips carry their casino and only its machines and counters take them. While
  playing, nothing reaches the site: the mod counts each casino's chips in circulation, which its account must back,
  and a machine only starts a game the casino's **free balance** (balance − chips in circulation − rounds in
  progress) can cover. Machines and counters are linked to a casino with the casino link wand, by an owner or member
  of its account. With MineVegas Casino installed, its machines take only chips of their casino.
- **Poker table.** The mod's own table, craftable and 4 x 3 blocks, linked to a casino with the wand like a counter:
  casino hold'em for up to three players at once against one dealer and one board. Each player antes in the casino's
  chips and gets two cards; once the first three board cards are out, each calls for twice the ante or folds. The
  dealer plays from a pair of fours. Everyone at the table, and anyone watching, sees the same round. A round cut
  short by a broken table or a restart gives its stakes back. It works without MineVegas, and can be switched off
  with `casino.holdem` in the config.
- **Shops.** A shop block sells one item, in lots, for an account its owner or a member chooses, and may buy it back
  at a lower or equal price. Or it only **buys**: the owner picks any item from a search, a price per lot and how many
  the shop holds at most, and players bring it the item; each kind has its own screen for customers. Its owners and members fill it (hoppers can fill it too, never empty it), set it up and
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
- **Mob drops.** A hostile mob killed by a player has a chance (10 %) to leave them a few LNBits (1 to 10), minted by
  the site on their personal account right away. Chance, amounts, whether spawner mobs count, and sending the drops
  in batches instead (`mobDrops.flushSeconds`) are in the config.
- **Mailbox.** Items owed to a player (bought chips or items, items handed back) go straight to the inventory; what
  doesn't fit waits for `/mailbox`. Nothing is dropped on the ground.
- **Reliability.** Every money move is written to `<world>/data/lnbeconomy_journal.json` before it is sent, and sent
  again with the same idempotency key until the site answers, after a restart too.

## Installing (Prism Launcher)

Create an instance with **Minecraft 1.21.1** and the **NeoForge 21.1.264 or newer** loader (tested on 21.1.265), with
Java 21. Then put these jars in the instance's `mods/` folder. The same mods go on the server, except where noted.

| Mod                    | Version                          | Needed                                                        |
|------------------------|----------------------------------|---------------------------------------------------------------|
| LNB Economy            | this mod, from the [releases][r] | required, on the server and on the players' computers         |
| LNB Warden             | 0.1.0 or newer                   | required, on the server and on the players' computers         |
| Kotlin for Forge       | 5.12 or newer                    | required                                                      |
| GeckoLib               | 4.9 or newer (tested on 4.9.3)   | required: the counter's teller is animated with it            |
| MineVegas Casino       | 1.1.1 up to, not including, 1.2  | optional: its machines then take only the chips of their casino |

[r]: https://github.com/Dimitri-Collineau/lnb-economy/releases

The server also needs LNB Warden's `api.url` and `api.token` (see Configuration). Without a linked LNB account, a
player has no economy.

## Configuration

The site's URL and the server's token are LNB Warden's: `api.url` and `api.token` in `config/lnbwarden-server.toml`.
LNB Economy's own settings are in `config/lnbeconomy-server.toml` (confirmation threshold, refresh times, games on or
off, switches for shops, trades, the auction house and its `/hdv` command, and mob drops: on or off, chance, smallest
and largest amount, spawner mobs, and whether they are sent one by one or in batches). Neither file is ever sent to players.

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

## Not done yet

- **Roles and closing in game.** Changing a member's role, removing a member and closing a shared account are done on
  the site.
- **The poker table in the world.** Cards and stakes show in the table's screen only: nothing is drawn on the table
  itself, and no dealer stands behind it.
- **Several players at a MineVegas machine.** Duck Derby already races several players. MineVegas's own poker and
  blackjack seat one player: for poker, use this mod's table. Roulette with several bettors would take more mixins.
- **The auctioneer's look.** The NPC still wears the plain vanilla villager texture. The plan is a clothing overlay on
  top of it, like the counter's teller: dark suit, waistcoat, crimson bow tie, gold monocle. A gavel in its hand would
  need GeckoLib.

## Credits

The poker table's models and textures, and the playing cards of its screen, come from
[MineVegas Casino](https://www.curseforge.com/minecraft/mc-mods/minevegas-casino) by byMehd, under its licence (MIT
with an attribution condition), copied in `LICENSES/MineVegas-Casino.txt`. They are under
`assets/lnbeconomy/models/block/poker_table_*.json`, `textures/block/poker/`, `textures/item/poker_table.png` and
`textures/gui/cards.png`. Everything else is this project's own.

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
