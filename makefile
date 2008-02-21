include build.properties

source: source2
	gunzip sux4j-$(version)-src.tar.gz
	tar --delete --wildcards -v -f sux4j-$(version)-src.tar \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/mph/\*Hollow\*.java \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/mph/\*Lcp\*.java \
		sux4j-$(version)/src/it/unimi/dsi/sux4j/test/\*Hollow\*.java \
		sux4j-$(version)/src/test/it/unimi/dsi/sux4j/mph/\*Hollow\*.java \
		sux4j-$(version)/src/test/it/unimi/dsi/sux4j/mph/\*Lcp\*.java
	gzip sux4j-$(version)-src.tar

source2:
	-rm -fr sux4j-$(version)
	ln -s . sux4j-$(version)
	ant clean
	tar zcvf sux4j-$(version)-src.tar.gz --owner=0 --group=0 \
		sux4j-$(version)/CHANGES \
		sux4j-$(version)/COPYING.LIB \
		sux4j-$(version)/build.xml \
		sux4j-$(version)/build.properties \
		$$(find sux4j-$(version)/src/it/unimi/dsi/sux4j -iname \*.java -or -iname \*.html) \
		$$(find sux4j-$(version)/src/test/it/unimi/dsi/sux4j -iname \*.java) \
		sux4j-$(version)/src/overview.html
	rm sux4j-$(version)

bin:
	-rm -fr sux4j-$(version)
	tar zxvf sux4j-$(version)-src.tar.gz
	(cd sux4j-$(version); ant clean jar javadoc)
	tar zcvf sux4j-$(version)-bin.tar.gz --owner=0 --group=0 \
		sux4j-$(version)/CHANGES \
		sux4j-$(version)/COPYING.LIB \
		sux4j-$(version)/sux4j-$(version).jar \
		sux4j-$(version)/docs
	-rm -r sux4j-$(version)
