# DSub2000

Connect to your Subsonic server and listen to your music wherever you go. Songs
are cached for playback to save on your mobile bandwidth and to make them
available when you have no connection at all.

Subsonic is a cross-platform FOSS media server that's capable of indexing very
large media collections. The server can transcode if necessary so that the app
can play files that your device may not normally support.

## Features

* Drag and drop songs to rearrange your playlist on the Now Playing tab
* Pause playback when other apps request audio focus (navigation, etc)
* Lockscreen controls
* Gapless Playback
* Highly customizable UI
* User defined Cache Size, Network Timeout, and Buffer Length

## Updating Icons
Media Icons are double standard size.  On https://romannurik.github.io/AndroidAssetStudio/icons-actionbar.html you can manually change this via the following js commands:
```
PARAM_RESOURCES.iconSize = {w: 64, h: 64}
PARAM_RESOURCES['targetRect-clipart'] = { x: 12, y: 12, w: 40, h: 40 }
form.fields_[0].params_.maxFinalSize = {w: 512, h: 512}
```
