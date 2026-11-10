# Manual test plan

What the unit and integration tests can't cover: the game itself, with a client. Run it before each release.

## Setup

### The site

1. In `lnb-website` (branch `feat/economy`): `docker compose up -d`, then `pnpm dev`.
2. In `.env`, set `WARDEN_SERVER_SECRET` to any long random string.
3. In the admin, turn on the `economy`, `economy.serverApi`, `economy.transfers` and `economy.sharedAccounts` flags.
4. Make two LNB accounts, A and B, each with a linked Minecraft account (offline mode: the UUID is
   `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`). Give each a few thousand LNBits with an admin adjustment.
5. With A, open a shared account `#test-casino` on `/minecraft/bank` and give it 10,000 LNBits (pay it from the site
   admin with an adjustment). Invite B as a **viewer**.

### The Minecraft server

`./gradlew runServer` from this repository. In `run/config/lnbwarden-server.toml`:

```toml
[api]
	url = "http://localhost:3000"
	token = "<WARDEN_SERVER_SECRET>"
[auth]
	mode = "OPTIONAL"
```

`run/server.properties` needs `online-mode=false`. Restart after editing.

### The client

The Prism instance `1.21.10` (Minecraft 1.21.1, NeoForge 21.1.265) with, in `minecraft/mods/`:
`kotlinforforge-5.12.0-all.jar`, `lnbwarden-*.jar`, `lnbeconomy-*.jar`, `minevegas-casino-neoforge-1.21.1-1.1.1.jar`.
**Not** `casinobank`: it wraps the same MineVegas calls. Join `localhost` as A, and from a second client as B when a
test needs two players.

## 1. Accounts

| # | Do | Expect |
|---|----|--------|
| 1.1 | Join as A, open the inventory | `Ⓛ` and A's default account balance above the inventory, top right, with "My accounts" on its right. Hovering the amount names the account |
| 1.2 | Open the recipe book | The balance and the button follow the inventory |
| 1.3 | Switch to creative, open the inventory | No balance is shown there |
| 1.4 | "My accounts" | Personal first, then `#test-casino` with its role and balance, ★ on the default |
| 1.5 | Click `#test-casino` | "Paying by default: #test-casino." in chat, the ★ moves, the inventory shows its balance. The site's `/minecraft/bank` agrees |
| 1.6 | Click the personal account | Back to personal |
| 1.7 | Join as B, open "My accounts" | `#test-casino` is greyed: B is a viewer |
| 1.8 | Join as a username linked to nothing | No balance in the inventory; `/balance` says to link an LNB account |
| 1.9 | Switch the game to French | Amounts read `1 250`, texts are in French |

## 2. Payments

| # | Do | Expect |
|---|----|--------|
| 2.1 | A: `/pay B 50 thanks` | A: "You paid 50 LNBits to B from your personal account." B: "A paid you 50 LNBits: thanks". Both balances update at once |
| 2.2 | A: `/pay B 5000` | A clickable [Confirm]; clicking pays; clicking again after 30 s says nothing to confirm |
| 2.3 | A: `/pay #test-casino 10` | Paid; B (a viewer of the account, online) is told |
| 2.4 | A: `/payfrom #test-casino B 10` | Paid from the casino account |
| 2.5 | B: `/payfrom #test-casino A 10` | Refused: B can't spend from it |
| 2.6 | A: `/pay B 99999999` | "There isn't enough money on that account." |
| 2.7 | `/pay B 0`, `/pay B 1.5`, `/pay test-casino 5`, `/pay #A 5` | The matching usage error, nothing sent |
| 2.8 | `/pay ` then Tab; `/pay #` then Tab; `/payfrom ` then Tab | Online players and known handles; for `/payfrom`, only accounts A can spend from |
| 2.9 | A: `/pay Nobody 5` (a name linked to nothing) | "Nobody has no LNB account with the economy." |
| 2.10 | `/balance` | Every account with its balance, the default marked |

## 3. Counter

| # | Do | Expect |
|---|----|--------|
| 3.1 | Craft a counter (3 gold ingots, dark oak planks, a chest) and a link wand (redstone, gold ingot, stick) | Both craft |
| 3.2 | Right click the counter | "This counter isn't linked to a casino yet." |
| 3.3 | A: wand on the counter, choose `#test-casino`, Link | "Linked to #test-casino." |
| 3.4 | B: wand on the counter | The screen says only its owners and members can change the link; no Link button |
| 3.5 | A: counter, buy 1,733 from the personal account | Chips 1000, 500, 100 ×2, 25, 5, 1 ×3 of `#test-casino` (tooltip). The casino's balance rose by 1,733 on the site. `/economy casino #test-casino`: chips in circulation 1733 |
| 3.6 | Buy from `#test-casino` itself | Not offered in the account list |
| 3.7 | Change with the largest chip at 25 | Same total in chips of 25 and less; chips in circulation unchanged |
| 3.8 | Cash them all | Chips gone, personal balance back, chips in circulation 0 |
| 3.9 | Freeze `#test-casino` in the site admin, wait 5 minutes (or `/economy casino #test-casino`), buy | Refused: the casino is frozen. Unfreeze |
| 3.10 | Stop the site, buy | "LNB's site isn't answering yet…"; `/economy pending` lists it. Start the site: the chips arrive, once |
| 3.11 | Same, but A leaves before the site comes back, then joins again | The chips are given on join |
| 3.12 | Same, but restart the Minecraft server while the site is down | After the restart, the transfer is sent again with the same key and the chips come once |

## 4. Machines

For each game (slots, roulette, wheel, Plinko, duck derby solo and group, blackjack with and without a double, poker with
and without a call):

| # | Do | Expect |
|---|----|--------|
| 4.1 | Before linking, open the machine and try to put a chip in | The slot refuses it |
| 4.2 | Wand on the machine as A, link to `#test-casino` with a largest bet of 100 | Linked |
| 4.3 | Try a diamond, a gold ingot, another casino's chip | Refused by the slot, on the client too (no flicker back) |
| 4.4 | Bet `#test-casino` chips worth 100 and play | The game runs; payouts come out in the same chips. `/economy casino #test-casino`: chips in circulation go down by the stake and up by the payout |
| 4.5 | Bet 125 | "The largest bet here is 100 LNBits." |
| 4.6 | Lower the casino's balance on the site so its free balance is under the best payout, wait for the refresh | "#test-casino can't cover that bet right now." before any chip is taken |
| 4.7 | Blackjack/poker: start a round, then `/economy casino` | "held for rounds" shows the reserved amount until the result |
| 4.8 | Open the casino settings screen (Croupier's Hand), add diamonds as a bet and an exchange rule | Diamonds are still refused; payouts stay in chips |
| 4.9 | Break a linked machine and place it again | It is unlinked |

## 5. Lost chips

| # | Do | Expect |
|---|----|--------|
| 5.1 | Throw a 100 chip in lava | Chips in circulation down by 100 |
| 5.2 | Drop a chip and wait 5 minutes | Down when it despawns |
| 5.3 | Drop a chip, pick it up | Unchanged |
| 5.4 | `/economy casino #test-casino outstanding 0` as an operator | Set, and logged |

## 6. Without MineVegas

Remove MineVegas from the server and the client: the game starts, payments and counters work, the log has no mixin
error.
