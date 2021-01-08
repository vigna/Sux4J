#!/bin/bash -ex

# Tests main methods

KEYS=$(mktemp)
FUNCTION=$(mktemp)
VALUES=$(mktemp)

LANG="en_US.UTF-8" cat >$KEYS <<EOF
b
d
©
à
è
EOF

gzip <$KEYS >$KEYS.gz
zstd $KEYS

java bsh.Interpreter <<EOF
f = new DataOutputStream(new FastBufferedOutputStream(new FileOutputStream("$VALUES")));
for(i=0; i<5; i++) f.writeLong((i+2)%3);
f.close();
EOF

for comp in "" "-z" "-d com.github.luben.zstd.ZstdInputStream"; do
	if [[ "$comp" == "-z" ]]; then
		export KEYFILE=$KEYS.gz
		export DECOMPRESSOR=java.util.zip.GZIPInputStream.class
	elif [[ "$comp" == "-d com.github.luben.zstd.ZstdInputStream" ]]; then
		export KEYFILE=$KEYS.zst
		export DECOMPRESSOR=com.github.luben.zstd.ZstdInputStream.class
	else 
		export KEYFILE=$KEYS
		export DECOMPRESSOR=null
	fi

	for input in "--iso" "--utf-32" "--byte-array" ""; do
		# Check (compressed) functions

		for class in GOV3Function GOV4Function GV3CompressedFunction GV4CompressedFunction TwoStepsGOV3Function; do

			if [[ "$class" != TwoStepsGOV3Function ]]; then
				# No values
				java -ea it.unimi.dsi.sux4j.mph.$class $comp $input $FUNCTION $KEYFILE

				if [[ $input == --byte-array ]]; then
					java -ea it.unimi.dsi.sux4j.test.ByteArrayFunctionSpeedTest $comp -c $FUNCTION $KEYFILE
				else
					java -ea it.unimi.dsi.sux4j.test.FunctionSpeedTest $comp -c $FUNCTION $KEYFILE
				fi
			fi

			java -ea it.unimi.dsi.sux4j.mph.$class $comp $input $FUNCTION -v $VALUES $KEYFILE

			if [[ "$input" == --byte-array ]]; then
				java bsh.Interpreter <<EOF
					m = load("$FUNCTION");
					keys = new it.unimi.dsi.io.FileLinesByteArrayIterable("$KEYFILE", $DECOMPRESSOR).iterator();
					for(i=0; i<5; i++) if (m.getLong(keys.next()) != (i+2)%3) System.exit(1);
EOF
			else
					java bsh.Interpreter <<EOF
					m = load("$FUNCTION");
					keys = new it.unimi.dsi.io.FileLinesMutableStringIterable("$KEYFILE", "UTF-8", $DECOMPRESSOR).iterator();
					for(i=0; i<5; i++) if (m.getLong(keys.next()) != (i+2)%3) System.exit(1);
EOF
			fi
		done

		# Check MPHFs

		for class in GOVMinimalPerfectHashFunction LcpMonotoneMinimalPerfectHashFunction ZFastTrieDistributorMonotoneMinimalPerfectHashFunction VLPaCoTrieDistributorMonotoneMinimalPerfectHashFunction VLLcpMonotoneMinimalPerfectHashFunction TwoStepsLcpMonotoneMinimalPerfectHashFunction PaCoTrieDistributorMonotoneMinimalPerfectHashFunction HollowTrieMonotoneMinimalPerfectHashFunction HollowTrieDistributorMonotoneMinimalPerfectHashFunction; do
			if [[ "$input" == --byte-array && $class != GOVMinimalPerfectHashFunction && $class != LcpMonotoneMinimalPerfectHashFunction ]]; then continue; fi

			java -ea it.unimi.dsi.sux4j.mph.$class $comp $input $FUNCTION $KEYFILE

			if [[ "$input" == --byte-array ]]; then
				java bsh.Interpreter <<EOF
					m = load("$FUNCTION");
					keys = new it.unimi.dsi.io.FileLinesByteArrayIterable("$KEYFILE", $DECOMPRESSOR).iterator();
					used = new int[5];
					for(i=0; i<5; i++) if (used[m.getLong(keys.next())]++ != 0) System.exit(1);
EOF
			else
				java bsh.Interpreter <<EOF
					m = load("$FUNCTION");
					keys = new it.unimi.dsi.io.FileLinesMutableStringIterable("$KEYFILE", "UTF-8", $DECOMPRESSOR).iterator();
					used = new int[5];
					for(i=0; i<5; i++) if (used[m.getLong(keys.next())]++ != 0) System.exit(1);
EOF
			fi
		done

	done

done


rm -f $KEYS* $FUNCTION $VALUES

echo
echo "Test OK"
