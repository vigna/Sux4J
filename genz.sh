#!/bin/bash

cp src/it/unimi/dsi/sux4j/bits/SimpleSelect.java src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java

sed -i -e 's/bits\[ /~bits\[ /g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e 's/Select/SelectZero/g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e 's/select(/selectZero(/g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e '/numOnes = c/iif ( length % 64 != 0 ) c -= 64 - length % 64;' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
