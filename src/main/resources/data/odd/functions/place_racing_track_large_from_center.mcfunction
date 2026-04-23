# Run this while standing roughly at the CENTER of where you want the track.
# Your current Y level will be used as the track base height.
# Command: /function odd:place_racing_track_large_from_center

# North-west quarter
forceload add ~-121 ~-144 ~-1 ~-1
place template odd:racing_track_large_nw ~-121 ~ ~-144
forceload remove ~-121 ~-144 ~-1 ~-1

# North-east quarter
forceload add ~ ~-144 ~120 ~-1
place template odd:racing_track_large_ne ~ ~ ~-144
forceload remove ~ ~-144 ~120 ~-1

# South-west quarter
forceload add ~-121 ~ ~-1 ~143
place template odd:racing_track_large_sw ~-121 ~ ~
forceload remove ~-121 ~ ~-1 ~143

# South-east quarter
forceload add ~ ~ ~120 ~143
place template odd:racing_track_large_se ~ ~ ~
forceload remove ~ ~ ~120 ~143
