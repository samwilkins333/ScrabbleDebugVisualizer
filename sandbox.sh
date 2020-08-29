#!/usr/bin/env bash

javac -g -cp ".:../lib/scrabble-base-jar-with-dependencies.jar" com/swilkins/ScrabbleVisualizer/executable/isolated/*.java;
java -cp ".:../lib/scrabble-base-jar-with-dependencies.jar" com/swilkins/ScrabbleVisualizer/executable/isolated/Debugger;
