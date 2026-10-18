# Blysis Connect for Minecraft

Connects a Paper, Folia or Velocity server to a Blysis site.

The server opens every connection itself. No port is opened on the game server and the site's
database never leaves the site. Deliveries wait in a queue on the site until the player is
there, and nothing is ever delivered twice.

## What it does

- **Deliveries.** What a member buys on the site runs on the right server, when the player is
  there if the product says so. A command the operator's blocklist names is refused, never run.
- **The store in game.** Shelves and products as the site marks them visible in game, a purchase
  window with quantity, recipient and the product's own questions, paid in credits.
- **Gifts.** A purchase for another member waits in their chest until they accept it. The buyer
  can take it back, with a full refund, until then. A member can turn gifts off.
- **The chest.** Everything bought and not yet claimed, taken with one click.
- **Credits.** Balance, sending to another member, rankings, and staff tools.
- **Vouchers.** Credits turned into an item that pays once, to whoever uses it.
- **Linking.** `/link` gives a code to confirm on the site; a code made on the site is typed after
  `/verify`.
- **Announcements.** Purchases, top-ups and a reached community goal, as lists of effects the
  operator writes.
- **PlaceholderAPI.** Balance, link state, rankings, latest top-ups, the community goal and the
  connection.

Every window is a native form for a Bedrock player through Floodgate. Every window, word, command
and effect is a file the operator can rewrite. English and Turkish ship; a language is added by
adding a `messages_<language>.conf`.

## Installing on Paper or Folia

1. On the site, install the Game Connect module and make a token under its servers page.
2. Put `BlysisConnect-Paper-<version>.jar` in `plugins/` and start the server once.
3. In `plugins/BlysisConnect/config.conf`, write the site's address and the token:

   ```hocon
   site {
     address = "https://your-site.example"
     token   = "gc_..."
   }
   ```

4. Run `/blysis reload`. The console says `Connected to the site.`

`/blysis doctor` says how the connection, the journal, the windows and the placeholders are.

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/store` | The store | `blysis.store` |
| `/chest`, `/chest sent` | What waits for you; the gifts you sent | `blysis.chest` |
| `/credits` | Your balance | `blysis.credits` |
| `/credits send <member> <amount>` | Send credits | `blysis.credits.send` |
| `/credits top [balance\|topups] [day\|month\|year\|all]` | Rankings | `blysis.credits.top` |
| `/credits add\|remove\|set\|see <member> [amount]` | Staff | `blysis.admin.credits` |
| `/voucher create <amount>`, `/voucher redeem` | Vouchers | `blysis.voucher.create`, `blysis.voucher.redeem` |
| `/voucher give <player> <amount>` | A voucher nobody paid for | `blysis.admin.voucher` |
| `/link`, `/verify <code>` | Link this account to the site | `blysis.link` |
| `/blysis reload\|version\|doctor\|lang` | Operator | `blysis.admin.*` |

Every command and every word under it can be renamed, aliased or turned off in `commands.conf`.
Staff with `blysis.admin.notify` read once that the site stopped answering, and once that it is
back.

## Files

| File | What it holds |
|---|---|
| `config.conf` | The site, delivery limits and the blocklist, the voucher item, fallback icons, announcements |
| `commands.conf` | Command names, aliases, words per language, on and off |
| `menus/*.conf` | Every window: slots, items, sounds, what a click does |
| `messages/messages_<language>.conf` | Every word a player reads |
| `text-input.conf` | How a player types a quantity, a name or an answer: dialog, chat, anvil or sign |
| `../uxmTheme/theme.conf` | The colours, shared by every plugin of the family |

## Placeholders

| Placeholder | Value |
|---|---|
| `%blysis_balance%` | The player's credits |
| `%blysis_linked%`, `%blysis_receiving_gifts%` | `true` or `false` |
| `%blysis_connection%` | `connected`, `connecting`, `unreachable`, `stopped` or `offline` |
| `%blysis_top_<kind>_<period>_<place>_name%` | A ranking: kind `balance`, `topups` or `latest`; period `day`, `month`, `year` or `all`; place 1 to 10 |
| `%blysis_top_<kind>_<period>_<place>_amount%` | The same place's amount |
| `%blysis_goal_title%`, `_raised`, `_goal`, `_percent`, `_active` | The community goal |

Every value is read from memory, so a scoreboard never waits on the site.

## When the site is away

Deliveries wait on the site and run when it is back. Players see their last known balance and the
store as it last was, with a note. Anything that moves credits or claims something is refused
with a message until the site answers again.

## Building

Java 25 and the Gradle wrapper:

```bash
./gradlew build
```

The Paper and Folia jar is `connect-paper/build/libs/BlysisConnect-Paper-<version>.jar`, the
Velocity jar `connect-velocity/build/libs/BlysisConnect-Velocity-<version>.jar`. Each runs on
its own; no other plugin is needed.

The protocol is documented in the Blysis repository, in
`module-sources/game-connect/PROTOCOL.md`. Any game can speak it.

MIT licensed.
