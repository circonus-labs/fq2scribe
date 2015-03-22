# fq2scribe

A very simple bridge pulling from N Fq nodes and publishing to 1 Scribe node.

```
fq2scribe -h
```

## BUILDING

```
make MAVEN=/path/to/mvn JAVA=/path/to/java PREFIX=/install/prefix install
```

Defaults:

    * PREFIX=/opt/circonus
    * JAVA=/opt/circonus/java/bin/java
    * MAVEN=mvn
