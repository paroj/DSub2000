# This is fork of DSub2000
https://github.com/paroj/DSub2000 [https://github.com/paroj/DSub2000] (https://github.com/paroj/DSub2000).

## Remote DSub2000

Two DSub2000 devices on the same Wi-Fi network can be linked so that one device (D1) controls playback on the other (D2). D2 streams audio from its own Subsonic server; D1 acts as a remote control.

### Setup

**On D2 (the device that will play audio):**

1. Open **Settings → Playback → Remote Control**
2. Enable **Local Control Server**
3. Note the **Local Server Port** (default: 4040)

**On D1 (the device that will act as remote):**

1. Open **Settings → Playback → Remote Control**
2. Set **Remote Client Host** to D2's local IP address
3. Set **Remote Client Port** to match D2's server port (default: 4040)

### Usage

1. On D1, tap the cast icon in the top bar
2. Select **Remote DSub2000** from the list
3. Browse and queue music on D1 as usual — playback happens on D2
4. All now-playing controls on D1 work remotely:
   - Play / Pause
   - Previous / Next track
   - Seek — drag the progress bar to jump to any position
   - Volume control

D1's now-playing screen reflects D2's current playback position (updated every 2 seconds).

### Requirements and Limitations

* Both devices must be on the same local Wi-Fi network
* D2 must have a valid Subsonic server configured — it streams audio using its own server connection
* Song metadata (title, artist, album) is sent by D1 so D2's now-playing screen displays correctly even if D2 is connected to a different server

## Permissions

This is why we need the permissions we do:

* Foreground Service - For downloading your playlists to cache
* Read & Write External Storage - Store the music cache on a sdcard
* Access Fine Location - Automatic day/ night mode and different server URL in local WiFi
* Read Phone State - Auto-pause playback when a call is received