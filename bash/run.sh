#!/usr/bin/env bash

find . -name "*.java" | xargs javac -g -cp ".:../lib/scrabble-base-jar-with-dependencies.jar"
java -cp ".:../lib/scrabble-base-jar-with-dependencies.jar" "com/swilkins/ScrabbleVisualizer/executable/$@";