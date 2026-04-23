# Run this while standing at the NORTH-WEST corner of where you want the track.
# Your current Y level will be used as the track base height.
# Command: /function odd:place_racing_track_large_from_nw

# North-west quarter
forceload add ~ ~ ~120 ~143
place template odd:racing_track_large_nw ~ ~ ~
forceload remove ~ ~ ~120 ~143

# North-east quarter
forceload add ~121 ~ ~241 ~143
place template odd:racing_track_large_ne ~121 ~ ~
forceload remove ~121 ~ ~241 ~143

# South-west quarter
forceload add ~ ~144 ~120 ~287
place template odd:racing_track_large_sw ~ ~ ~144
forceload remove ~ ~144 ~120 ~287

# South-east quarter
forceload add ~121 ~144 ~241 ~287
place template odd:racing_track_large_se ~121 ~ ~144
forceload remove ~121 ~144 ~241 ~287
