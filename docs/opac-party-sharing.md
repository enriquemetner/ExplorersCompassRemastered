# Optional OPAC party sharing

`progression.opacPartySharing = true` in `config/explorerscompass/server/settings.toml` enables sharing when Open
Parties and Claims is installed. Restart the server after changing the setting.
OPAC is an optional compile-only dependency; it is not bundled with the compass.
For local testing, install OPAC Fabric 1.20.1 (tested with 0.33.0) and its Forge
Config API Port dependency (tested with 8.0.3) in the test server and clients.

Each OPAC party shares the combined saved personal unlocks of its current
members, including offline members. Joining grants access to those structures.
Leaving, removal, or disbanding takes away access from that party. A structure
remains available if the player earned it personally or a current party member
still supplies it. Invitations and alliances grant no access.

Personal unlocks from rules, FTB rewards, admin commands, and compass transfers
can contribute to the party. Creative access and borrowed party access are
temporary. Missing and blacklisted structures are excluded from the shared list.

Admin lock and unlock commands change the named player's personal unlocks.
Party gains can show the party unlock toast to online players who have obtained
a compass. Gains and losses refresh an open compass, while **Newly Unlocked**
continues to show personal progress. A target that becomes locked can no longer
be searched or used for compass teleport.
