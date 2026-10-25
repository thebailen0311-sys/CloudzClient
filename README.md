# Cloud Client 1.2

Fabric 1.21.11 / Java 21.

## Build

```
gradlew build
```

Jar lands in `build/libs/cloudclient-1.2.0.jar`.

## Push an update

```
$f=Get-Content gradle.properties; $k="mod_version="; $i=0; while(-not $f[$i].StartsWith($k)){$i++}; $a=$f[$i].Substring($k.Length).Split("."); $a[2]=[int]$a[2]+1; $n=$a -join "."; $f[$i]=$k+$n; Set-Content gradle.properties $f; git add .; git commit -m "v$n"; git push; git tag "v$n"; git push origin "v$n"; Write-Host "DONE v$n"
```

## Spotify

The card reads the real Spotify Web API, so it needs a refresh token once.

1. Go to https://developer.spotify.com/dashboard, create an app.
   Redirect URI can be anything, e.g. `http://127.0.0.1:8888/callback`.
2. Open this URL in a browser (replace CLIENT_ID and the redirect):

```
https://accounts.spotify.com/authorize?client_id=CLIENT_ID&response_type=code&redirect_uri=http://127.0.0.1:8888/callback&scope=user-read-playback-state%20user-read-currently-playing
```

3. Approve, then copy the `code=` value out of the URL you land on.
4. Swap it for a refresh token (one command, replace the three values):

```
curl -X POST https://accounts.spotify.com/api/token -H "Content-Type: application/x-www-form-urlencoded" -d "grant_type=authorization_code&code=CODE&redirect_uri=http://127.0.0.1:8888/callback&client_id=CLIENT_ID&client_secret=CLIENT_SECRET"
```

5. Copy `spotify.example.json` to `.minecraft/config/cloudclient/spotify.json` and paste the three values in.
6. Enable the Spotify module in the Click GUI.

Lyrics come from lrclib.net (free, no key). Word highlighting is derived from the
line duration, so it tracks the vocal without needing word-level timing.

## What changed in 1.2

- New three-pane Click GUI: category rail, card grid, sliding settings drawer.
- Per-pixel coverage anti-aliasing on every rounded rect and gradient.
- Black-to-white surface gradients everywhere, no flat fills.
- New vector logo (gradient crescent + sparkle + orbit).
- Array list: spring reordering, width animation, glow text, gradient panels, accent bar.
- Ambience: motes, vignette, aurora band, six modes.
- ESP: coloured glow via `Entity#getTeamColorValue`.
- Spotify card with karaoke lyrics.
- Version tag in the watermark and GUI footer, GitHub update check.
