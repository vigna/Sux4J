JAR=sux4j

sourcedir=$(cd $(dirname ${BASH_ARGV[0]}) && pwd) 
count=$(\ls -1 $sourcedir/$JAR-*.jar 2>/dev/null | wc -l)

if (( count == 0 )); then
	echo "WARNING: no $JAR jar file."
elif (( count > 1 )); then
	echo "WARNING: several $JAR jar files ($(\ls -m $JAR-*.jar))"
else
	export CLASSPATH=$(ls -1 $sourcedir/$JAR-*.jar | tail -n 1):$CLASSPATH
fi

export CLASSPATH=$CLASSPATH:$(\ls -1 $sourcedir/jars/runtime/*.jar | paste -d: -s)
