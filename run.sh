#!/usr/bin/env bash
rm *.class
javac -g -classpath lib/*.jar *.java
java CandidateGenerationVisualizer