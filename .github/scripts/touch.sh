# Runs on the device: one continuous finger drag with real touch events (press, hold, glide, rest, lift).
# Usage: sh touch.sh DEVICE MAXX MAXY SCREENW SCREENH x1 y1 x2 y2 HOLD_SECONDS REST_SECONDS
dev=$1; mx=$2; my=$3; sw=$4; sh=$5; x1=$6; y1=$7; x2=$8; y2=$9; hold=${10}; rest=${11}
px() { echo $(( $1 * mx / sw )); }
py() { echo $(( $1 * my / sh )); }
at() { sendevent $dev 3 53 $(px $1); sendevent $dev 3 54 $(py $2); sendevent $dev 0 0 0; }
sendevent $dev 3 47 0; sendevent $dev 3 57 7; sendevent $dev 1 330 1; at $x1 $y1
sleep $hold
at $((x1 + 8)) $((y1 + 8)); sleep 0.8
i=1
while [ $i -le 24 ]; do at $((x1 + (x2 - x1) * i / 24)) $((y1 + (y2 - y1) * i / 24)); sleep 0.04; i=$((i + 1)); done
sleep $rest
sendevent $dev 3 57 -1; sendevent $dev 1 330 0; sendevent $dev 0 0 0
