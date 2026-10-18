# Blysis Connect for Minecraft

Connects a Paper, Folia or Velocity server to a [Blysis](https://github.com/INOVIXI/blysis) site.

The server opens every connection itself. No port is opened on the game server and the site's
database never leaves the site. Deliveries wait in a queue on the site until the player is
there, and nothing is ever delivered twice.

In game:

- the site's store, paid in credits, with gifts that can be taken back until accepted
- credits: balance, sending, rankings and staff tools
- the chest of everything bought and not yet claimed
- credit vouchers that can be handed over and redeemed once
- linking the Minecraft account to the site account, from either side
- announcements of purchases, top-ups and community goals
- PlaceholderAPI placeholders

Every menu is a native form on Bedrock through Floodgate.

The protocol is documented in the Blysis repository, in
`module-sources/game-connect/PROTOCOL.md`. Any game can speak it.

Work in progress.

MIT licensed.
