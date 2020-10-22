echo "call stop-kill.sh"

timeout -sKILL 300 sh stop.sh
if [ $? != 0 ]; then
 echo timed out
 sh kill.sh
fi
