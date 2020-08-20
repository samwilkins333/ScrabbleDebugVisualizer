#!/usr/bin/env bash
rm *.class
javac -g -classpath ".:../lib/scrabble-base-jar-with-dependencies.jar" *.java
java CandidateGenerationVisualizer