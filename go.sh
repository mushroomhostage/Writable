#!/bin/sh -x
CLASSPATH=../craftbukkit-1.0.1-R1.jar javac *.java -Xlint:unchecked -Xlint:deprecation
rm -rf me 
mkdir -p me/exphc/Writable
mv *.class me/exphc/Writable
jar cf Writable.jar me/ *.yml
cp Writable.jar ../plugins/
