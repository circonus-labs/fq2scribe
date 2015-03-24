MAVEN=mvn
JAVA=/opt/circonus/java/bin/java
PREFIX=/opt/circonus

FILES=src/main/java/com/circonus/fq2scribe/Fq2Scribe.java \
    src/main/java/com/facebook/fb303/FacebookService.java \
    src/main/java/com/facebook/fb303/fb_status.java \
    src/main/java/scribe/LogEntry.java \
    src/main/java/scribe/ResultCode.java \
    src/main/java/scribe/scribe.java

SINGLEJAR=fq2scribe-1.0-jar-with-dependencies.jar

all:	target/$(SINGLEJAR) bin/fq2scribe

target/$(SINGLEJAR):	$(FILES)
	(cd lib && ./stub-as-maven.sh)
	$(MAVEN) compile assembly:single

bin/fq2scribe:	bin/fq2scribe.in
	sed -e "s#@JAVA@#$(JAVA)#g" \
	    -e 's#@JARPREFIX@#$$DIRNAME/../target#g' \
	    < $< > $@
	chmod 755 $@

install:	all
	mkdir -p $(DESTDIR)$(PREFIX)/bin
	rm -f $(DESTDIR)$(PREFIX)/bin/fq2scribe
	sed -e "s#@JAVA@#$(JAVA)#g" \
	    -e "s#@JARPREFIX@#$(PREFIX)/lib/java#g" \
	    < bin/fq2scribe.in > $(DESTDIR)$(PREFIX)/bin/fq2scribe
	chmod 555 $(DESTDIR)$(PREFIX)/bin/fq2scribe
	mkdir -p $(DESTDIR)$(PREFIX)/lib/java
	cp -f target/$(SINGLEJAR) $(DESTDIR)$(PREFIX)/lib/java/$(SINGLEJAR)
	chmod 444 $(DESTDIR)$(PREFIX)/lib/java/$(SINGLEJAR)

clean:
	rm -f ~/.m2/repository/fqclient/fqclient/0.1/fqclient-0.1.jar
	rm -f target/$(SINGLEJAR)
	rm -f bin/fq2scribe
