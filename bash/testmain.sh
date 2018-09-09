# Tests main methods

#!/bin/bash -e

KEYS=$(mktemp)
FUNCTION=$(mktemp)
VALUES=$(mktemp)

LANG="en_US.UTF-8" cat >$KEYS <<EOF
à
b
©
d
è
EOF

java bsh.Interpreter <<EOF
f = new DataOutputStream(new FastBufferedOutputStream(new FileOutputStream("$VALUES")));
for(i=0; i<5; i++) f.writeLong((i+2)%3);
f.close();
EOF

cp $VALUES dummy

for input in "--iso" "--utf-32" "--byte-array" ""; do
	# Check (compressed) functions

	for class in GOV3Function GOV4Function GV3CompressedFunction GV4CompressedFunction; do
		java -ea it.unimi.dsi.sux4j.mph.$class $input $FUNCTION $KEYS

		if [[ $input == --byte-array ]]; then
			java -ea it.unimi.dsi.sux4j.test.ByteArrayFunctionSpeedTest -c $FUNCTION $KEYS
		else
			java -ea it.unimi.dsi.sux4j.test.FunctionSpeedTest -c $FUNCTION $KEYS
		fi

		java -ea it.unimi.dsi.sux4j.mph.$class $input $FUNCTION -v $VALUES $KEYS

		if [[ $input == --byte-array ]]; then
			java bsh.Interpreter <<EOF
				m = load("$FUNCTION");
				keys = new it.unimi.dsi.big.io.FileLinesByteArrayCollection("$KEYS").iterator();
				for(i=0; i<5; i++) if (m.getLong(keys.next()) != (i+2)%3) System.exit(1);
EOF
		else
			java bsh.Interpreter <<EOF
				m = load("$FUNCTION");
				keys = new FileLinesCollection("$KEYS", "UTF-8").iterator();
				for(i=0; i<5; i++) if (m.getLong(keys.next()) != (i+2)%3) System.exit(1);
EOF
		fi
	done

	# Check MPHFs

	java -ea it.unimi.dsi.sux4j.mph.GOVMinimalPerfectHashFunction $input $FUNCTION $KEYS

	if [[ $input == --byte-array ]]; then
		java bsh.Interpreter <<EOF
			m = load("$FUNCTION");
			keys = new it.unimi.dsi.big.io.FileLinesByteArrayCollection("$KEYS").iterator();
			used = new boolean[5];
			for(i=0; i<5; i++) if (!(used[m.getLong(keys.next())] ^= true)) System.exit(1);
EOF
	else
		java bsh.Interpreter <<EOF
			m = load("$FUNCTION");
			keys = new FileLinesCollection("$KEYS", "UTF-8").iterator();
			used = new boolean[5];
			for(i=0; i<5; i++) if (!(used[m.getLong(keys.next())] ^= true)) System.exit(1);
EOF
	fi

done




rm -f $KEYS $FUNCTION $VALUES

echo
echo "Test OK"
