echo 'call stop.sh'

get_pids() {
 ps -f -U `whoami` | grep apis-main | grep java | while read _USER_ _PID_ _OTHERS_; do
  echo $_PID_
 done
}

while true; do
 _PIDS_=`get_pids`
 if [ -z "$_PIDS_" ] ; then
  break
 fi
 echo kill $_PIDS_
 kill $_PIDS_
 sleep 1
done

echo '... done'
