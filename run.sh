#!/usr/bin/env bash
find . -name "*.class" | xargs rm;
find . -name "*.java" | xargs javac -g -cp ".:../lib/scrabble-base-jar-with-dependencies.jar";
java -cp ".:../lib/scrabble-base-jar-with-dependencies.jar" "com/swilkins/ScrabbleViz/executable/$@";