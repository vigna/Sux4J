include build.properties

TAR=tar

source: 
	rm -fr sux4j-$(version)
	ant clean
	ln -s . sux4j-$(version)
	./genz.sh
	$(TAR) chvf sux4j-$(version)-src.tar --owner=0 --group=0 \
		sux4j-$(version)/CHANGES \
		sux4j-$(version)/COPYING \
		sux4j-$(version)/COPYING.LESSER \
		sux4j-$(version)/build.xml \
		sux4j-$(version)/ivy.xml \
		sux4j-$(version)/sux4j.bnd \
		sux4j-$(version)/pom-model.xml \
		sux4j-$(version)/build.properties \
		$$(find sux4j-$(version)/src/it/unimi/dsi/sux4j -iname \*.java -or -iname \*.html) \
		$$(find sux4j-$(version)/test/it/unimi/dsi/sux4j -iname \*.java) \
		$$(find sux4j-$(version)/slow/it/unimi/dsi/sux4j -iname \*.java) \
		sux4j-$(version)/src/overview.html
	$(TAR) --delete --wildcards -v -f sux4j-$(version)-src.tar \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/mph/solve/Modulo2SparseSystem.java \
		sux4j-$(version)/test/it/unimi/dsi/sux4j/mph/solve/Modulo2SparseSystemTest.java \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/bits/Rank12.java \
		sux4j-$(version)/test/it/unimi/dsi/sux4j/bits/Rank12Test.java \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/scratch/*.java \
		sux4j-$(version)/test/it/unimi/dsi/sux4j/scratch/*.java \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/test/*.java
	gzip -f sux4j-$(version)-src.tar
	-rm -fr sux4j-$(version)

binary:
	rm -fr sux4j-$(version)
	tar zxvf sux4j-$(version)-src.tar.gz
	(cd sux4j-$(version); unset LOCAL_IVY_SETTINGS; ant ivy-setupjars; ant jar javadoc)
	$(TAR) zcvf sux4j-$(version)-bin.tar.gz --owner=0 --group=0 \
		sux4j-$(version)/CHANGES \
		sux4j-$(version)/COPYING \
		sux4j-$(version)/COPYING.LESSER \
		sux4j-$(version)/sux4j-$(version).jar \
		sux4j-$(version)/docs
	$(TAR) zcvf sux4j-$(version)-deps.tar.gz --owner=0 --group=0 --transform='s|.*/||' $$(find sux4j-$(version)/jars/runtime -iname \*.jar -exec readlink {} \;) 

stage:
	rm -fr sux4j-$(version)
	tar zxvf sux4j-$(version)-src.tar.gz
	cp -fr lib dsiutils-$(version)
	(cd sux4j-$(version); unset LOCAL_IVY_SETTINGS; ant ivy-setupjars; ant stage)
