# Performance

## How the mod saves frames

A HUD is normally drawn again in every frame, even when nothing on it changed. With many cards on screen, that time adds up. SkyBlock Companion works its HUD out 20 times a second, draws it into one picture and shows that picture in the frames in between. A full HUD then costs about as much as drawing one picture.

The helpers work the same way. They look only at what they need, so the empty armor stands SkyBlock places everywhere are skipped.

## The performance options

Both are in the settings under HUD, Appearance and layout, and both are on by default:

- **HUD as a picture** draws the HUD into a picture 20 times a second and shows that picture in between.
- **Faster HUD** works the HUD out 20 times a second instead of in every frame.

With them on, numbers on the HUD can change up to 50 ms later. Turn one off if a card ever looks wrong or stuck.

## Benchmarks

Recorded with `/sbc perf record` for one minute each, standing in one spot.

- **PC:** RTX 4060 Ti, i7-8700K, 32 GB RAM, Windows 11
- **Game:** 1080p fullscreen, render distance 14, no shaders, Sodium and a few other performance mods. Each report lists all of them.

| Location | Setup | Average FPS | 1% low | 0.1% low | SBC time per frame | Report |
| :--- | :--- | ---: | ---: | ---: | ---: | :--- |
| Private Island | SBC, performance options on | 548 | 184 | 105 | 0.20 ms | [Report](performance/record-20261109-104506.txt) |
| Private Island | SBC, performance options off | 296 | 132 | 96 | 1.04 ms | [Report](performance/record-20261109-105005.txt) |
| Hub bank, 47 players | SBC, performance options on | 421 | 150 | 93 | 0.26 ms | [Report](performance/record-20261109-105120.txt) |

With the options on, the mod took 0.20 to 0.26 ms of a frame. With them off it took about 1 ms, and the average FPS on the Private Island was about half.

In the middle of the Hub, the same modpack without SBC runs at about 280 FPS, and popular SkyBlock modpacks at 70 to 100. Those two were read from the FPS counter, not recorded.

Each report is the full output of `/sbc perf record`: the settings, the mods, how long every part of a frame took and where the player stood each second.

## Measure it yourself

Run `/sbc perf record` in game and play as usual. It records 60 seconds, or any length from 5 to 600 seconds with `/sbc perf record <seconds>`, and `/sbc perf stop` ends it early. The report shows the average FPS, the 1% low and how long each part of the mod took, and is saved in `config/sbcompanion/perf`.

To see it live, turn on **Performance overlay** in the same settings: the milliseconds of every HUD element and feature, at the top of the screen.
