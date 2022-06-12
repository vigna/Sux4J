#!/bin/bash

cp src/it/unimi/dsi/sux4j/bits/SimpleSelect.java src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java

sed -i -e 's/bits\[\([^]]\)/~bits\[\1/g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e 's/Select/SelectZero/g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e 's/select(/selectZero(/g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e 's/Fast\.selectZero(/Fast\.select(/g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e '/numOnes = c/i\
if (length % 64 != 0) c -= 64 - length % 64;' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java
sed -i -e 's/select implementation/zero-select implementation/g' src/it/unimi/dsi/sux4j/bits/SimpleSelectZero.java

cp src/it/unimi/dsi/sux4j/bits/SimpleBigSelect.java src/it/unimi/dsi/sux4j/bits/SimpleBigSelectZero.java

sed -i -e 's/bits\[\([^]]\)/~bits\[\1/g' src/it/unimi/dsi/sux4j/bits/SimpleBigSelectZero.java
sed -i -e 's/Select/SelectZero/g' src/it/unimi/dsi/sux4j/bits/SimpleBigSelectZero.java
sed -i -e 's/select(/selectZero(/g' src/it/unimi/dsi/sux4j/bits/SimpleBigSelectZero.java
sed -i -e 's/Fast\.selectZero(/Fast\.select(/g' src/it/unimi/dsi/sux4j/bits/SimpleBigSelectZero.java
sed -i -e '/numOnes = c/i\
if (length % 64 != 0) c -= 64 - length % 64;' src/it/unimi/dsi/sux4j/bits/SimpleBigSelectZero.java
sed -i -e 's/select implementation/zero-select implementation/g' src/it/unimi/dsi/sux4j/bits/SimpleBigSelectZero.java
