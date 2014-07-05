viscimja
========

A portable SYCPOL-SCIM in Java.

Features
========

* Full compatibility with Standard SYCPOL
* MODULE statements and other essential features from RS-SYCPOL
* Support for filesystem access
* Advanced debug mode
* Verbose error messages

Building
========

VISCIM-JA requires Java 7.

Following should work:
```
$ mkdir classes
$ make
```

Or if you aren't able or would rather not use Makefiles:
```
$ java -classpath classes -sourcepath src src/sycpol/Sycpol.java -d classes
```

Usage
=====

See manpage for full documentation.
