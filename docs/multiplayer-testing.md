# Local multiplayer testing

Use three terminals in the project directory. Run the server first, then each client:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runMultiplayerServer --offline
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runPlayerOne --offline
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew runPlayerTwo --offline
```

Before starting the server, read the Minecraft EULA linked in
`run-multiplayer/server/eula.txt` and set `eula=true` there if you agree.
The preparation task has already created this file. On a fresh checkout, run
`./gradlew prepareMultiplayerTest` first (using Java 17).

The clients automatically join `127.0.0.1:25565` with separate player identities.
The server accepts offline development identities and binds only to this computer.
If the port is already in use, stop the other local server first.

The isolated directories are:

- `run-multiplayer/server`: world, server configuration, quest definitions and logs
- `run-multiplayer/player1`: Player1's client settings and logs
- `run-multiplayer/player2`: Player2's client settings and logs

On preparation, existing development configuration and mod jars from `run/` are
copied into these folders only where a destination file is absent. Mods marked
client-only in their Fabric metadata are omitted from the server. Later changes
to existing files in `run/` are not copied over: edit the multiplayer folders
directly. Remove unwanted mods from both the source and destination folders.

Use the server terminal for administrative commands (without a leading slash):

```text
op Player1
```

Player1 can then edit FTB quests and Player2 can test ordinary player behavior. Use `stop` and restart the server to apply edits to the compass’s unlocking requirements.
Use separate FTB teams when testing independent quest progress. Disconnect one
client to test offline updates. Type `stop` in the server terminal to save and quit.

All launch tasks use Java 17. Start Gradle itself with Java 17 too, as shown above.
Avoid compiling or rebuilding while these games are running: they share compiled
mod classes, although their game data is separate.
