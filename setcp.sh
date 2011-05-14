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

export CLASSPATH=$CLASSPATH:/usr/share/java/dsiutils.jar
export CLASSPATH=$CLASSPATH:/usr/share/java/fastutil.jar
export CLASSPATH=$CLASSPATH:/usr/share/java/jsap.jar
export CLASSPATH=$CLASSPATH:/usr/share/java/junit4.jar
export CLASSPATH=$CLASSPATH:/usr/share/java/log4j.jar
# Indirect dependencies from dsiutils.
export CLASSPATH=$CLASSPATH:/usr/share/java/jakarta-commons-collections.jar
export CLASSPATH=$CLASSPATH:/usr/share/java/jakarta-commons-configuration.jar
export CLASSPATH=$CLASSPATH:/usr/share/java/jakarta-commons-io.jar
export CLASSPATH=$CLASSPATH:/usr/share/java/jakarta-commons-lang.jar
# This is necessary for unit tests (to process the output)
export CLASSPATH=$CLASSPATH:/usr/share/java/xalan-j2-serializer.jar
