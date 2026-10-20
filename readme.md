# Retro Craft
A Voxel based game that uses no libraries that has an 80's theme.
This means that No libraries or frameworks like openGL or Unity were used [Up to v0.5 where I added netty for networking]

[![Youtube video of Retro Craft](https://img.youtube.com/vi/m63pDKehByg/0.jpg)](https://www.youtube.com/watch?v=m63pDKehByg)

[Youtube video of Retro Craft](https://www.youtube.com/watch?v=m63pDKehByg)

## How to install
Just go to releases and download the jar
[OR CLICK HERE TO DOWNLOAD THE JAR](https://github.com/HeathLoganCampbell/Retro-Craft/releases/download/v0.4.5/MincraftClassic.jar)
## How to play
```
W = forwards
S = backwards
A = left
D = right
left mouse button = break block
right mouse button = place block
right arrow key = changed block placed 
```

## GPU renderer (experimental)

The world renderer can also run on the GPU. It is the same raycaster, ported to
a fragment shader: the 64x64x64 block array becomes a 256 KB `R8UI` 3D texture,
`World.render()` becomes `shaders/raycast.frag`, and the result is still drawn
at 318x200 and nearest-upscaled, so the look is unchanged.

The CPU renderer is untouched and remains the reference implementation; this is
an optional backend, not a replacement.

```bash
./run-gpu.sh                 # play, natural terrain
./run-gpu.sh --flat          # the old flat world
./run-gpu.sh --selftest 120  # render offscreen, write gpu-frame.png, print timings
```

Controls are the same as the CPU client. `Esc` quits, `F2` toggles the debug
window title, left/right arrows cycle the block type (and turn, matching the
old key handling).

Requires LWJGL 3 and a GPU with OpenGL 3.3+; on macOS the script passes
`-XstartOnFirstThread`. Measured on an M1 Pro through Metal-backed OpenGL 4.1:
**0.36-0.52 ms/frame** at 318x200 (~2,000-2,700 fps serialised), with the
simulation at 0.14 ms/tick. See [ADR 0016](docs/adr/0016-gpu-raycast-backend.md).

Not ported yet: player sprites (the depth attachment is ready), the debug
overlay, and networking.

## Development

### Things to add
* Entities
* Multiplayer support
* Fix mouse issue for windows
* make ticks more consistent
* Break up the Minecraft class so it's more understandable
* Title screen
* Chunkize the world
* Make particles
* Generate trees
* Make it so you swim in lava
* Make it so Bigger Y = higher up

## Versions

### v0.1.5
[It's just a bad version of minecraft, example](https://gyazo.com/d123022c618a0574954187d86f35e100)

### v0.2.0
[Example](https://gyazo.com/3a32f4017e7bda57560eb9ce96a4ce16)

### v0.2.1
![House](./readme-resources/house.png)
* Added the ability to change different block types
* Fix grass spawning throughout all dirt blocks

Issues still around
* windows doesn't set the location of the mouse correctly

### v0.3.0
![TNT](./readme-resources/tnt.png)
* Add TNT that explodes in a chain
* Add glass
* String rendering engine
* added cross hair

### v0.4.0
![Water](./readme-resources/water.png)
* Water that flows
* lava that flows

### v0.4.5
![waterGenerator](./readme-resources/waterGenerator.png)
* water is now naturally generated in the land
* there is a preview of what block you'll place

### Renamed to Retro Craft
Because it sounded better

### v0.5.0 [2/11/2022]
![add-sprites-and-multi-player](./readme-resources/add-sprites-and-multi-player.PNG)
* Added sprites
* Add Multiplayer abilities
* remove mouse control
* Move project to use Maven
