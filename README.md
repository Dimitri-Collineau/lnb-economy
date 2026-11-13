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
  - `/account` (`/compte`): shared accounts. `create <handle> <name>`, `rename <handle> <name>`,
    `type <handle> team|shop|casino`, `invite <handle> <player>`, `members <handle>`, `invitations`,
    `accept|decline <invitation>`.
  - `/trade <player>`: asks to trade, from any distance (a key, unbound by default, asks the player in front of you).
  - `/hdv` (`/ah`): opens the auction house, unless `features.auctionsCommand` is off.
  - `/mailbox` (`/courrier`): collects items that didn't fit in your inventory.
  - `/economy notify on|off`: messages when someone buys from or sells to your shops, and when one of your auction
    house listings sells.
  - `/economy status|pending|retry <key>|casino <#account> [outstanding <value>]`: operators.
  - `/economy npc hdv|licence [name]`: operators place the auctioneer or the licence dealer where they stand. An
    operator in creative mode removes either by hitting it.
- **Shared accounts.** "My accounts" (or `/account`) opens a shared account, renames it, sets its type (team, shop or
  casino) and invites players. The members screen shows faces and roles, and proposes the players who are on as the
  owner types (Tab completes, Enter invites); any other username works too. An invited player gets a message with
  [Accept] and [Decline], right away or when they next join, and sees the invitation at the top of "My accounts".
  Changing roles, removing members and closing an account stay on the site.
- **Casinos.** A casino is a shared account of type casino, which takes a **casino licence**. The licence dealer, a
  croupier who has seen better nights, sells one for the price set on the site: the money is burned, and he hands
  over a signed book as a keepsake. One licence makes one account a casino, for good. A casino's counters sell chips
  (the player pays the casino's account) and cash them (the account pays the player). Chips carry their casino and
  only its machines and counters take them. While playing, nothing reaches the site: the mod counts each casino's
  chips in circulation, which its account must back, and a machine only starts a game the casino's **free balance**
  (balance − chips in circulation − rounds in progress) can cover; otherwise it says so in red, with a sound, and its
  screen closes. Machines and counters are linked to a casino with the casino link wand, by an owner or member of its
  account. With MineVegas Casino installed, its machines take only chips of their casino.
- **Poker table.** Casino hold'em for up to three players at once, against one croupier and one board. The table is
  4 x 3 blocks, craftable, and linked to a casino with the wand like a counter. Each player antes in the casino's chips
  and gets two cards; once the first three board cards are out, each calls for twice the ante or folds. The dealer
  plays from a pair of fours. The croupier behind the table shuffles, deals each card onto the felt, watches whoever
  hasn't chosen, turns his own cards, pays or collects and sweeps the table, and everyone near the table sees the same
  round. When the deal starts, a seated player's camera turns to him and the screen steps aside into a side panel. A
  round cut short by a broken table or a restart gives its stakes back. It works without MineVegas, and
  `casino.holdem` in the config switches it off. With MineVegas installed, it replaces MineVegas's poker table:
  MineVegas's recipe makes this one, MineVegas's table is in no creative tab, one a player holds turns into this one,
  and one already placed no longer opens (broken, it gives this one).
- **Shops.** A shop block either **sells** one item, in lots, and may buy it back at a lower or equal price, or only
  **buys** it: the owner picks any item from a search, a price per lot and how many the shop holds at most, and
  players bring it the item. Customers get a screen for each kind. Shops sell and buy for an account their owner or a
  member chooses. Their owners and members fill them (hoppers can fill them too, never empty them), set them up and
  break them. The site registers each shop and prices every order from its saved prices. While the site decides, the
  items are held aside in the journal entry, so a refusal or a crash gives them back.
- **Trades.** Both players offer items (copies: nothing leaves the inventories yet) and LNBits from an account, then
  press Ready; any change sets both back. After a 3 second countdown, both offers leave the inventories together and
  are held in the journal entry while the site moves the difference in money. Each player then gets the other's
  items, or their own back if the site refuses.
- **Auction house.** Players list any stack of their inventory at a fixed price, browse, buy, and take their listings
  back; a listing that can't go up says why. Listings and their items live on the site: a listed item leaves the
  game, held in the journal entry until the site has it. A purchase lands in the buyer's mailbox on the site, which
  the server claims with a key and hands over; unsold items come back the same way from "To collect". The seller is
  told when a listing sells, right away or when they next join. It opens with `/hdv`, an auction house podium
  (operators only, unbreakable outside creative) or the auctioneer NPC.
- **Mob drops.** A hostile mob killed by a player has a chance (10 %) to leave them a few LNBits (1 to 10), minted by
  the site on their personal account right away. Mobs from spawners give nothing by default.
- **Mailbox.** Items owed to a player (bought chips or items, items handed back) go straight to the inventory; what
  doesn't fit waits for `/mailbox`. Nothing is dropped on the ground.
- **Creative tab.** Every block and item of the mod is in its own "LNB Economy" tab (the auction house podium for
  operators only). Chips are not: a chip without a casino is worth nothing.
- **Reliability.** Every money move is written to `<world>/data/lnbeconomy_journal.json` before it is sent, and sent
  again with the same idempotency key until the site answers, after a restart too.

## Installing (Prism Launcher)

Create an instance with **Minecraft 1.21.1** and the **NeoForge 21.1.264 or newer** loader (tested on 21.1.265), with
Java 21. Then put these jars in the instance's `mods/` folder. The same mods go on the server, except where noted.

| Mod              | Version                          | Needed                                                                     |
|------------------|----------------------------------|----------------------------------------------------------------------------|
| LNB Economy      | this mod, from the [releases][r] | required, on the server and on the players' computers                      |
| LNB Warden       | 0.1.0 or newer                   | required, on the server and on the players' computers                      |
| Kotlin for Forge | 5.12 or newer                    | required                                                                   |
| GeckoLib         | 4.9 or newer (tested on 4.9.3)   | required: the counter's teller and the licence dealer are animated with it |
| MineVegas Casino | 1.1.1 up to, not including, 1.2  | optional: its machines then take only the chips of their casino            |

[r]: https://github.com/Dimitri-Collineau/lnb-economy/releases

The server also needs LNB Warden's `api.url` and `api.token` (see Configuration). Without a linked LNB account, a
player has no economy.

### The site

The mod needs a version of LNB's site with the economy's server routes (`docs/economy.md` in `lnb-website`). Since
0.2.0, that includes shared accounts from the game, account types, casino licences, mob drops and sale notices: an
older site refuses those (the rest keeps working). Each part also has a flag on the site (`/admin/flags`):
`economy.serverApi` and `economy.transfers` for everything, then `economy.sharedAccounts`, `economy.casinoLicenses`,
`economy.mobDrops`, `economy.shops`, `economy.trades` and `economy.auctions`. The licence price is in the site's
economy settings.

## Configuration

The site's URL and the server's token are LNB Warden's: `api.url` and `api.token` in `config/lnbwarden-server.toml`.
LNB Economy's own settings are in `config/lnbeconomy-server.toml`:

- `pay.confirmAbove`: payments above it ask for a click to confirm.
- `cache`: how often balances are read again from the site, and how long machines go on with a casino's last known
  balance while the site is down.
- `casino`: each game on or off, MineVegas's (`slots`, `roulette`, `wheel`, `plinko`, `derby`, `blackjack`, `poker`)
  and this mod's poker table (`holdem`).
- `features`: shops, trades, the auction house, and whether `/hdv` opens it anywhere.
- `mobDrops`: on or off, the chance, the smallest and largest amount, whether mobs from spawners count, and
  `flushSeconds`: 0 sends each drop at once, more adds them up and sends them that often.

Neither file is ever sent to players.

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
- **Several players at a MineVegas machine.** Duck Derby already races several players. MineVegas's blackjack seats
  one player; for poker, this mod's table seats three. Roulette with several bettors would take more mixins.
- **The auctioneer's look.** The NPC still wears the plain vanilla villager texture.

## Credits

The poker table's models and textures, its croupier (model, animations and clothes) with the card motion, and the
playing cards come from
[MineVegas Casino](https://www.curseforge.com/minecraft/mc-mods/minevegas-casino) by byMehd, under its licence (MIT
with an attribution condition), copied in `LICENSES/MineVegas-Casino.txt`. They are under
`assets/lnbeconomy/models/block/poker_table_*.json`, `textures/block/poker/`, `textures/item/poker_table.png` and
`textures/gui/cards.png`, `textures/entity/poker_cards.png` and `textures/entity/poker_dealer/`, and the code in
`src/main/java/fr/bde_teknantes/lnb/economy/poker/table/`. The croupier's base skin, and the counter's teller and the
auctioneer, use the vanilla villager texture, referenced and never copied. Everything else is this project's own.

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
