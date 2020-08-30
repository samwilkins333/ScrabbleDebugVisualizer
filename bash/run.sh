#!/usr/bin/env bash

cd src;
find . -name "*.java" | xargs javac -g -cp ".:$1";
java -cp ".:$1" "com/swilkins/ScrabbleVisualizer/executable/$2";