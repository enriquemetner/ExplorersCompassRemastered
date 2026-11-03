# Optional OPAC party sharing

`unlocking.opacPartySharing = true` in `common.toml` enables sharing when Open
Parties and Claims is installed. Restart the server after changing the setting.
OPAC is an optional compile-only dependency; it is not bundled with the compass.
For local testing, install OPAC Fabric 1.20.1 (tested with 0.33.0) and its Forge
Config API Port dependency (tested with 8.0.3) in the test server and clients.

Members of the same OPAC party permanently receive one another's saved unlocked
structures, including progress earned before joining and offline members' data.
Leaving, disbanding, or disabling integration does not remove imported progress.
Invitations and allied parties do not share anything. Membership changes are
checked once per second and sharing is also synchronized before opening the UI.
New saved unlock additions propagate on the next server tick.

All saved unlock sources participate, including FTB reward claims, admin grants,
and compass-transfer imports. Visits, advancements, and quest progress are not
copied. Creative's temporary search bypass never becomes a saved or shared unlock;
real visits and claims still behave normally in Creative.

The original player gets the usual toast. Other members get the same count title
with “Unlocked by a party member” beneath it. Offline shared additions are queued
and consolidated on login. Players who have never obtained a compass receive no
toast until their existing first-compass notification, which includes saved shared
structures. Repeated imports do not count as new additions.

With sharing enabled, a lock command targeting a party member affects every current
member, including offline members. Clear-visit commands still affect only their
named player. Reloads preserve the existing relock setting: full relocking discards
stale party copies before merging new earned progress, and restored IDs do not
produce duplicate toasts. Missing and common-blacklisted structures are excluded
from imports; client-hidden structures remain eligible.

Shared additions refresh an open compass without a rules-change Info popup or
changing its active view, filters, search text, selection, or scroll position.
