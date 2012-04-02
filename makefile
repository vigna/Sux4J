include build.properties

TAR=tar

source: source2
	gunzip sux4j-$(version)-src.tar.gz
	$(TAR) --delete --wildcards -v -f sux4j-$(version)-src.tar \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/scratch/*.java \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/test/*.java
	gzip sux4j-$(version)-src.tar

source2:
	-rm -fr sux4j-$(version)
	ln -s . sux4j-$(version)
	ant clean
	./genz.sh
	$(TAR) zhcvf sux4j-$(version)-src.tar.gz --owner=0 --group=0 \
		sux4j-$(version)/CHANGES \
		sux4j-$(version)/COPYING \
		sux4j-$(version)/COPYING.LESSER \
		sux4j-$(version)/JavaBig.pdf \
		sux4j-$(version)/build.xml \
		sux4j-$(version)/ivy.xml \
		sux4j-$(version)/pom-model.xml \
		sux4j-$(version)/build.properties \
		$$(find sux4j-$(version)/src/it/unimi/dsi/sux4j -iname \*.java -or -iname \*.html) \
		$$(find sux4j-$(version)/test/it/unimi/dsi/sux4j -iname \*.java) \
		$$(find sux4j-$(version)/slow/it/unimi/dsi/sux4j -iname \*.java) \
		sux4j-$(version)/src/overview.html
	rm sux4j-$(version)

binary:
	-rm -fr sux4j-$(version)
	$(TAR) zxvf sux4j-$(version)-src.tar.gz
	(cd sux4j-$(version); unset LOCAL_IVY_SETTINGS; ant clean ivy-setupjars jar javadoc)
	$(TAR) zcvf sux4j-$(version)-bin.tar.gz --owner=0 --group=0 \
		sux4j-$(version)/CHANGES \
		sux4j-$(version)/COPYING \
		sux4j-$(version)/COPYING.LESSER \
		sux4j-$(version)/JavaBig.pdf \
		sux4j-$(version)/sux4j-$(version).jar \
		sux4j-$(version)/docs

stage:
	(cd sux4j-$(version); unset LOCAL_IVY_SETTINGS; ant stage)
