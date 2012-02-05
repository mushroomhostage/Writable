#!/bin/sh -x
CLASSPATH=../craftbukkit-1.1-R3.jar javac *.java -Xlint:unchecked -Xlint:deprecation
rm -rf me 
mkdir -p me/exphc/Writable
mv *.class me/exphc/Writable
jar cf Writable.jar me/ *.yml README.md ChangeLog *.java LICENSE
cp Writable.jar ../plugins/
