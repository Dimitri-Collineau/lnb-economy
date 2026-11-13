# Manual test plan

What the unit and integration tests can't cover: the game itself, with a client. Run it before each release.

## Setup

### The site

1. In `lnb-website` (branch `feat/economy-ingame-accounts` until it and the branches under it are merged into `main`):
   `docker compose up -d`, then `pnpm dev --host ::1` (without `--host`, Nuxt 4.6.4 fails to start).
2. In `.env`, set `WARDEN_SERVER_SECRET` to any long random string.
3. In the admin, check that the `economy` flags are on (they are unless an admin turned one off).
4. Without Microsoft sign-in, `MICROSOFT_CLIENT_ID` and `MICROSOFT_CLIENT_SECRET` can hold any placeholder: the server
   routes don't use them, but the site refuses to start when they are empty.
5. Make two LNB accounts, A and B, each with a linked Minecraft account, a starting balance, and a shared account
   `#test-casino` owned by A with B as a **viewer**. Without Microsoft sign-in, straight in the database (replace the
   UUIDs with the offline-mode ones, `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`; the tenant is Epitech's):

   ```sql
   insert into economy_settings (id, starting_balance) values (1, 5000)
     on conflict (id) do update set starting_balance = 5000;
   insert into "user" (id, name, email, email_verified, tenant_id) values
     ('dev-a', 'Player A', 'dev-a@example.invalid', true, '901cb4ca-b862-4029-9306-e5cd0f6d9f86'),
     ('dev-b', 'Player B', 'dev-b@example.invalid', true, '901cb4ca-b862-4029-9306-e5cd0f6d9f86');
   insert into minecraft_account (uuid, user_id, username) values ('<uuid A>', 'dev-a', '<name A>'), ('<uuid B>', 'dev-b', '<name B>');
   with acc as (insert into economy_account (kind, handle, name, type) values ('shared', 'test-casino', 'Test Casino', 'casino') returning id)
   insert into economy_member (account_id, user_id, role)
     select id, 'dev-a', 'owner' from acc union all select id, 'dev-b', 'viewer' from acc;
   ```

   The starting balance is minted when the server first looks a player up. Then fund the casino with a real transfer,
   for instance `/pay #test-casino 3000` as A.

### The Minecraft server

`./gradlew runServer` from this repository. In `run/config/lnbwarden-server.toml`:

```toml
[api]
	url = "http://[::1]:3000"
	token = "<WARDEN_SERVER_SECRET>"
[auth]
	mode = "OFF"
```

`pnpm dev` only listens on `[::1]`, and Java tries `127.0.0.1` for `localhost`, so write the IPv6 address (the same
goes for `LNB_API_URL` in the integration tests). `OFF` turns off LNB Warden's authentication only: LNB Economy still
calls the site with this URL and token, and players don't need to link through Warden to join.

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
| 3.5 | A: counter, buy 1,733 from the personal account | The screen closes; "You bought … in chips from Test Casino." (its name, not its `#`). Chips 1000, 500, 100 ×2, 25, 5, 1 ×3 of `#test-casino` (tooltip). The casino's balance rose by 1,733 on the site. `/economy casino #test-casino`: chips in circulation 1733 |
| 3.6 | Buy from `#test-casino` itself | Not offered in the account list |
| 3.7 | Change with the largest chip at 25 | Same total in chips of 25 and less; chips in circulation unchanged |
| 3.8 | Cash them all | Chips gone, personal balance back, chips in circulation 0 |
| 3.8b | Empty the casino's account on the site, wait for the refresh, cash chips | "Cashing failed, your chips are given back: Test Casino doesn't have enough money right now."; the chips come back |
| 3.8c | Stand in front of the counter and walk around it | The teller plays its animations and never turns its head toward you |
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
| 4.4b | Wheel: bet one chip of 100 until it lands on ×0.25 | 25 in chips comes back, every time |
| 4.5 | Bet 125 | "The largest bet at Test Casino is 100 LNBits." in red above the hotbar, a low note, and the machine's screen closes; the chips are back in the inventory |
| 4.6 | Lower the casino's balance on the site so its free balance is under the best payout, wait for the refresh | "Test Casino can't cover that bet right now.", in red with the note, the screen closes, before any chip is taken |
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

## 6. Shops

A is an owner of `#test-casino`, B a player with LNBits. Give A 64 diamonds and B 32.

| # | Do | Expect |
|---|----|--------|
| 6.1 | Craft a shop (glass panes, an emerald, a chest, planks) and place it as A | It crafts and places |
| 6.2 | B: right click it | "This shop isn't set up yet." |
| 6.3 | B: break it | Refused: only its owners and members break it |
| 6.4 | A: right click, click the empty slot with a diamond | The diamond stays in A's hand; the slot shows one diamond |
| 6.5 | Lots of 16, price 40, buy back 50, Save | Refused: the buy-back price can't be above the price |
| 6.6 | Buy back 25, account `#test-casino`, Save | "Shop saved."; status Open. The shop is in the site admin's Shops (once the page is built) |
| 6.7 | Put 48 diamonds and 1 dirt in the stock | The dirt doesn't go in |
| 6.8 | Put a hopper with diamonds on top, and one under the shop | The top hopper fills the stock; the bottom one takes nothing |
| 6.9 | B: right click, 2 lots from the personal account, Buy | 32 diamonds for B, 80 to `#test-casino`; A gets "B bought 32 × Diamond at your shop for 80". The screen shows the new stock |
| 6.10 | B: sell 1 lot back | 16 diamonds back in the stock, 25 to B |
| 6.11 | B: sell when A's stock is at the buy-back limit | "The shop has no room for that many." |
| 6.12 | A: change the price to 45 while B's screen is open, then B buys | "The price changed…"; nothing moves |
| 6.13 | Stop the site, B buys | The diamonds leave the stock; "LNB's site is slow to answer…". Start the site: B gets them, once |
| 6.14 | Same, but the site refuses (B's account frozen in the admin) | The diamonds go back to the stock |
| 6.15 | B: fill the inventory, then buy | What doesn't fit waits; "… wait in your mailbox"; `/mailbox` after making room gives it |
| 6.16 | Turn the shop off in the site admin, B opens it | "This shop is closed." A saving it again: refused |
| 6.17 | A: `/economy notify off`, B buys | No message for A |
| 6.18 | A: break the shop | The stock drops; the shop is off on the site |
| 6.19 | Push it with a piston, move it with a Create contraption | It doesn't move |
| 6.20 | Turn `features.shops` off in `lnbeconomy-server.toml`, restart | Right click: "Shops are switched off on this server." |
| 6.21 | A: place a second shop, choose "Buys" at the top | The selling price goes away; "Price paid per lot", "Buys until it holds" and "Search an item…" show |
| 6.22 | Search an item…, type "diam", click the diamond | Back on the settings with a diamond in the item slot. Clicking the slot with an item still works |
| 6.23 | Lots of 8, price 30, until 64, account `#test-casino`, Save | "Shop saved." |
| 6.24 | B: right click it | "Shop: buying": what it pays per lot, how many lots it still takes, how many B carries, one button "Sell" |
| 6.25 | B: sell 2 lots | 16 diamonds leave B for the stock, 60 from `#test-casino` to B; A gets "B sold 16 × Diamond to your shop for 60" |
| 6.26 | B: sell until the shop holds 64 | Then "The shop has no room for that many."; A takes the diamonds out of the stock and it buys again |
| 6.27 | Empty `#test-casino` on the site, B sells | "The order didn't go through: There isn't enough money on that account."; B's diamonds come back |
| 6.28 | A: switch that shop back to "Sells" while B's screen is open, save, then B orders | B's screen changes to the selling one on the next refresh; nothing is bought at a stale price |

## 7. Trades

A and B are both online with LNBits, A with 10 diamonds, B with 5 emeralds.

| # | Do | Expect |
|---|----|--------|
| 7.1 | A: `/trade B` from far away (another dimension too) | B gets "A wants to trade with you. [Accept] [Decline]" |
| 7.2 | B: Decline | A: "B declined your trade." |
| 7.3 | Bind the trade key in the controls; A: look at B within 5 blocks, press it | Same request. Further than 5 blocks: nothing |
| 7.4 | B: Accept | Both get the trade screen, each with the other's name |
| 7.5 | A: click an offer slot with the diamonds, right click another with one diamond | Copies show in A's offer and on B's screen; A still holds the 10 diamonds |
| 7.6 | A: Ready, then B changes their offer | Both back to not ready |
| 7.7 | A gives 100 LNBits, B gives 30, both Ready | Countdown 3, 2, 1, then: A has the emeralds, B the diamonds; A's account down 70, B's up 70, one Trade line in each history |
| 7.8 | Offer 10 diamonds, throw one away during the countdown | "A no longer has everything they offered": nothing moves |
| 7.9 | Equal amounts on both sides | Items swap; no transaction on the site |
| 7.10 | Stop the site, trade with money | Items leave both inventories; "LNB's site is slow to answer…". Start the site: each gets the other's items, once |
| 7.11 | A gives more than A's balance | Refused by the site: each gets their own items back |
| 7.12 | A closes the screen, or logs out, during a trade | B: "The trade is off." Nothing moved |
| 7.13 | A: `/trade A`, and between two Minecraft accounts of the same LNB account | Refused |
| 7.14 | Turn `features.trades` off, restart, `/trade B` | "Trades are switched off on this server." |

## 8. Auction house

A and B online, A with 16 diamonds in hand. In the site admin's settings, the tax is 5 %.

| # | Do | Expect |
|---|----|--------|
| 8.1 | A: `/hdv` | The auction house opens on Browse |
| 8.2 | A: My listings, 16, price 100, List it | The diamonds leave A's hand; "Listed: 16 × Diamond for 100." The listing shows in My listings and in Browse |
| 8.3 | Stop the site, list another stack, start it again | The stack leaves the hand; once the site answers, it's listed once |
| 8.4 | Same, but the site refuses (A banned on the site) | The stack comes back |
| 8.5 | B: search "diam", sort Cheapest each, hover the listing | Price, price per item, seller A, time left |
| 8.6 | B: select it, Buy | B gets the 16 diamonds; B −100, A +95; one Auction house line each; 5 burned (admin Overview, by reason) |
| 8.7 | B: buy it again from a stale screen | "That listing is no longer up." |
| 8.8 | A: list, then take it back | "Listing taken back."; the stack comes back to A |
| 8.9 | A: list, set its end in the past in the database, open Browse | Gone; A's To collect shows it as "Didn't sell in time"; Collect everything gives it back |
| 8.10 | B: buy with a full inventory | The item waits in the local mailbox; `/mailbox` gives it |
| 8.11 | List more than the per-player limit | "You have the most listings you can at once." |
| 8.12 | Operator: place an auction house block; survival player tries to break it | Unbreakable; right click opens the auction house |
| 8.13 | Operator: `/economy npc hdv Bob` | An auctioneer named Bob; right click opens; it can't be hurt or pushed; hit in creative: gone |
| 8.14 | `features.auctionsCommand = false`, restart, `/hdv` | "Go to an auction house to use it."; the block and the NPC still open it |
| 8.15 | Turn the site flag `economy.auctions` off | Browsing and buying refused; taking back and collecting still work |
| 8.16 | A lists, B buys while A is online | A gets "B bought your 16 × Diamond at the auction house: +95. (tax: 5)" in gold, with a sound |
| 8.17 | A lists, logs out, B buys, A joins | A gets the same message on joining, once: joining again says nothing |
| 8.18 | A: `/economy notify off`, then B buys a listing of A | No message; and none later after `/economy notify on` |
| 8.19 | A: List it with the price empty, then with a count above the stack | "Not listed: the price is from 1 to …" and "Not listed: you asked to list … but that stack only holds …", in red with a low note; the items stay |

## 9. Shared accounts from the game

A and B online. The site flag `economy.sharedAccounts` is on.

| # | Do | Expect |
|---|----|--------|
| 9.1 | A: My accounts, "+ New", type a name | The handle follows the name (`Casino Royal !` gives `casino-royal`) until A edits it; Team is chosen; Casino is locked with "Needs a casino licence" |
| 9.2 | A: Open the account | "Shared account … opened. You own it."; back on My accounts, the new account is there; the site's bank page shows it |
| 9.3 | A: `/account create ab x` and a handle already taken | A red message for each (bad handle, "That # is already taken."), with a low note |
| 9.4 | A: "..." on the account, change the name, Save | "… is now called …" in green; My accounts and B's screens show the new name |
| 9.5 | A: Members, type the start of B's name | B is proposed with their face and "online"; Tab completes, Enter invites |
| 9.6 | B, right away | A gold chat message "A invites you to …" with [Accept] [Decline], a sound, and the invitation at the top of My accounts |
| 9.7 | B: Accept (chat or the + button) | "You joined …"; the account is in B's list as Member; A's Members screen lists B after reopening |
| 9.8 | A: invite a linked player who is away, then that player joins | They get the invitation message on joining |
| 9.9 | A: invite, then the x beside the pending invitation | "Invitation taken back."; the invited player's My accounts no longer shows it |
| 9.10 | B (member): "..." on the account | Name and type are locked ("Only the account's owners…"); Members opens, without the invitation box |
| 9.11 | `/compte invitations`, `/account members <handle>`, `/account rename`, `/account type` | The same answers in the chat |
| 9.12 | Turn `economy.sharedAccounts` off on the site, create an account | "The economy is switched off for now." |

## 10. Casino licences

An operator, and A with 100,000 LNBits or more (give them with an adjustment in the site admin). The licence price is in
the site admin's economy settings.

| # | Do | Expect |
|---|----|--------|
| 10.1 | Operator: `/economy npc licence Marcel` | A dishevelled dealer named Marcel (visor askew, undone bow tie, a bottle held by its neck in his right fist), swaying, head hanging; every ten seconds he drinks from the bottle; he doesn't turn toward players; can't be hurt or pushed; hit in creative: gone |
| 10.2 | A: right click him | He starts awake and waves; his offer, the price, "Yours: 0 free of 0", the account that pays |
| 10.3 | A, with too little on the chosen account | "That account doesn't hold enough.", Buy greyed |
| 10.4 | A: Buy, then Confirm | "Casino licence bought for …", a level-up sound, a signed book "Casino Licence" naming A; A −price; the site shows a burn with the reason casino licence; "Yours: 1 free of 1" |
| 10.5 | A: My accounts, "+ New", Casino, Open | The account opens as a casino; free licences back to 0; Casino is locked again for a second account |
| 10.6 | A: "..." on the casino | Team and Shop are locked: "A casino stays a casino." |
| 10.7 | A: link wand on a counter | Only casinos are proposed; with none, the message says to buy a licence |
| 10.8 | In the database, set `#test-casino`'s type to `team`, wait for the refresh (or `/economy casino #test-casino`), play a linked machine | Refused in red: "… isn't a casino: its account has no casino licence." |
| 10.9 | Stop the site, buy a licence, start it again | "…isn't answering yet"; then bought once, one book |

## 11. Mob drops

`mobDrops` in `config/lnbeconomy-server.toml`: set `chance = 1.0` for the test. The site flag `economy.mobDrops` is on.

| # | Do | Expect |
|---|----|--------|
| 11.1 | A: kill a zombie | "+N from the mob" in gold above the hotbar, N from `min` to `max` |
| 11.2 | Open the inventory right after | A's personal balance already went up by N; one mint on the site, reason mob drop |
| 11.2b | `flushSeconds = 30`, restart, kill several zombies | The balance moves once, up to 30 seconds later, by the total; one mint for the batch |
| 11.3 | Kill a cow; kill a zombie from a spawner | Nothing (with `fromSpawners = false`) |
| 11.4 | With `flushSeconds = 30`: kill a zombie, log out before the batch is sent | The drop is sent at once; the balance is right on the next login |
| 11.5 | `enabled = false`, or the site flag off | Nothing drops; with the flag off, the server stops rolling for ten minutes after the refusal |
| 11.6 | A guest (no linked account) kills a zombie | Nothing |

## 12. Poker table

A, B and a third player C online, each with chips of `#test-casino` bought at a counter; `#test-casino` holds enough
to cover the antes (102 times each ante).

| # | Do | Expect |
|---|----|--------|
| 12.1 | Craft a poker table (gold ingot, green carpet, gold ingot; three dark oak planks; one plank under the middle) and place it | A 4 x 3 table, MineVegas's, facing the player; it needs the whole floor free |
| 12.2 | Right click it | "This machine isn't linked to a casino." |
| 12.3 | A: wand on any block of the table, link to `#test-casino`, largest bet 100 | Linked; the link screen says "poker table" |
| 12.4 | A: right click, ante 10, Sit on the middle seat | 10 in chips leave A's inventory (change comes back in smaller chips); A's face and name on the seat; "Cards in 10 s" |
| 12.5 | B: right click before the countdown ends, ante 20, Sit | B sits beside A; both screens show both seats |
| 12.6 | C: ante 5, Sit on the last seat | The cards come within 2 seconds: a full table doesn't wait |
| 12.6b | Look at the table | A croupier behind it shuffles, then deals the cards one by one onto the felt, toward each seat; the seated players' cameras turn to him and their screen becomes a side panel without a dark veil |
| 12.7 | Everyone looks | Two cards each, face up for all; the dealer's two face down; three board cards; "Call or fold: 25 s"; the croupier watches the seat that hasn't chosen |
| 12.8 | A: Call (20). B: Fold. C: let the time run out | A's stake reads 30; B "Folded"; at the end C is folded too |
| 12.9 | The showdown | The croupier deals the last two cards, turns his two, then pays or collects; five board cards, the dealer's cards and hand; each seat's hand and result; chips paid; a chat line each ("you win", "the dealer doesn't play", "you folded") |
| 12.10 | After about 4 seconds | The croupier sweeps the cards away; the seats empty; "Waiting for an ante" |
| 12.11 | Ante 125 | "The largest bet at Test Casino is 100 LNBits.", in red with a low note |
| 12.12 | Lower `#test-casino` on the site under 102 times the ante, wait for the refresh, ante | "Test Casino can't cover that bet right now."; no chip taken |
| 12.13 | A: Call with fewer chips than twice the ante | "Call" is greyed; A can only fold |
| 12.14 | A sits, a bystander opens the table | They see the round and can't act; walking 12 blocks away stops the updates |
| 12.15 | A seated, closes the screen, walks off, comes back and opens it | A is still seated and can still call or fold |
| 12.16 | Mid-round, break any block of the table | The whole table breaks, one item drops; "The poker round was interrupted: your … in chips are given back."; `/economy casino #test-casino`: chips in circulation as before the antes |
| 12.17 | Mid-round, stop the server and start it again | Same message and chips for each seated player when the table's chunk loads |
| 12.18 | `/economy casino #test-casino` during a round | "held for rounds" shows 102 times the antes until the showdown |
| 12.19 | `casino.holdem = false` in the config, restart, ante | "This game is switched off on this server." |

## 13. Without MineVegas

Remove MineVegas from the server and the client: the game starts, payments, counters and the poker table work, the
log has no mixin error.
