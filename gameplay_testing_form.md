
  
- [x] Disable the Java patch / fail-closed config (or load without the JAR) and confirm nothing that looks like awareness or a cone is drawn.

- [x] Awareness vs Java (debug dump still useful; skip animation debug)

  

- [x] Front approach: fill to threshold, then state=2 / DETECTED accepted=true in the log at the same moment the bar goes “detected.”

- [x] Break LOS (corner, closed door): fill stops, then after the delay it falls. HUD should not freeze at the last exposed value.

- [x] Rear / side vs front: rear should stay empty or climb much slower unless you are inside close range.

- [x] After detection, step out of sight: bar can stay detected until vanilla drops the target; it must not keep gaining with no exposure.

- [x] Anchors (poses you have not checked)

  

- [x] Crawling, falling, climbing: bar stays near the body, not a tile behind or in the floor. Note anything ugly; don’t try to “fix” it in your head yet.

- [x] Camera / viewport

  

- [x] Zoom in/out and pan: bars and cone tips stay on the zombie, not drifting.

- [x] Change resolution or window size once.

- [ ] Splitscreen if you can: each viewport only shows that player’s pair state; no bleed into the other pane.
	- Cannot test yet. On hold for now

- [x] ~~Stale / misleading draws~~

  

- [x] Kill a marked zombie: bar and cone disappear that frame, not a ghost.

- [x] Walk to another floor: zombies on the old Z are gone.

- [x] Fade / isAlphaZero (fog, far, unseen): no floating bars on zombies you cannot see. Overlay cones may still punch through walls — that’s expected.

- [ ] Closed door / wall: cone should shorten on that ray (approximate). It will still draw on top of the world.
- Cones are not occluded by any objects

- [x] Input

  

- [x] Click the world through a cone/bar: context menu, loot, attack still work. If a click hits the overlay instead, that’s a fail.

- [x] Performance (debug logging off)

  

- [x] Same ~20-zombie street: note FPS with overlays on vs a moment with the mod’s display constants set to hide cones (or further away). Don’t treat 25fps-with-animation-debug as a budget.

- [x] Indoor hallway vs open lot: which one hitching — discovery (bars pop in late) vs draw (always slow).

  

- [ ] Dedicated server / ownership transfer, sandbox options UI, stealth XP, depth-correct floor cones, art/options polish.
	- 
	- Will be attempting multiplayer testing in future. Work on singleplayer is fine while balancing
	- Sandbox options still need to implemented