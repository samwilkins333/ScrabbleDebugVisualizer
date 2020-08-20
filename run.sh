#!/usr/bin/env bash
rm *.class
javac -g -cp ".:../lib/scrabble-base-jar-with-dependencies.jar" *.java
java CandidateGenerationVisualizer