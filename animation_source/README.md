# Animation source

The runtime animation is procedural. Edit `HandGeometry.java` and the `HandPose` class in `client/HandClient.java` to change the hand solver, grip orientation, or fade timing. There is no copied emote or static animation file controlling contact.

`neutral_player.bbmodel` is an original, untextured Blockbench reference rig using the standard player's proportions. It is supplied for visual authoring and measurement; exporting it does not replace the runtime solver. No Minecraft skin texture is included.

The solver uses vanilla model coordinates: X points to the model's left, Y points down, and Z points backward. Arm pivots are at X ±5, Y 2 (2.5 for slim models), Z 0. The distal face center is ±1, 10, 0 relative to the standard arm pivot (±0.5 for slim). World conversion applies the player renderer's 0.9375 scale, model scale, and interpolated body yaw. The relaxed contact is the lowest intersection point of the two arms' reach spheres.
