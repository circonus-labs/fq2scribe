MAVEN=mvn
JAVA=/opt/circonus/java/bin/java
PREFIX=/opt/circonus

SINGLEJAR=fq2scribe-1.0-jar-with-dependencies.jar

all:	target/$(SINGLEJAR) bin/fq2scribe

target/$(SINGLEJAR):
	(cd lib && ./stub-as-maven.sh)
	$(MAVEN) compile assembly:single

bin/fq2scribe:	bin/fq2scribe.in
	sed -e "s#@JAVA@#$(JAVA)#g" \
	    -e "s#@PREFIX@#$(PREFIX)#g" \
	    < $< > $@
	chmod 755 $@

install:	all
	mkdir -p $(DESTDIR)$(PREFIX)/bin
	cp -f bin/fq2scribe $(DESTDIR)$(PREFIX)/bin/fq2scribe
	chmod 555 $(DESTDIR)$(PREFIX)/bin/fq2scribe
	mkdir -p $(DESTDIR)$(PREFIX)/lib/java
	cp -f target/$(SINGLEJAR) $(DESTDIR)$(PREFIX)/lib/java/$(SINGLEJAR)
	chmod 444 $(DESTDIR)$(PREFIX)/lib/java/$(SINGLEJAR)

clean:
	rm -f target/$(SINGLEJAR)
	rm -f bin/fq2scribe
