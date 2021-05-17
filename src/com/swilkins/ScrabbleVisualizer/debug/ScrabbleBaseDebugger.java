package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.ObjectReference;
import com.sun.jdi.connect.Connector;
import com.swilkins.ScrabbleBase.Board.Location.Coordinates;
import com.swilkins.ScrabbleBase.Board.Location.TilePlacement;
import com.swilkins.ScrabbleBase.Board.State.BoardSquare;
import com.swilkins.ScrabbleBase.Board.State.Tile;
import com.swilkins.ScrabbleBase.Generation.Candidate;
import com.swilkins.ScrabbleBase.Generation.CrossedTilePlacement;
import com.swilkins.ScrabbleBase.Generation.Direction;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleVisualizer.executable.GeneratorTarget;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class ScrabbleBaseDebugger extends Debugger {

  public static final Dimension ICON_DIMENSION = new Dimension(12, 12);

  public ScrabbleBaseDebugger() throws Exception {
    super(GeneratorTarget.class, new ScrabbleBaseVisualizer());
  }

  @Override
  protected void configureDebuggerModel() throws IOException, ClassNotFoundException {
    debuggerModel.addDebugClassSourcesFromJar("../lib/scrabble-base-jar-with-dependencies.jar", null);
    debuggerModel.getDebugClassSourceFor(Generator.class).setCached(true).addCompileTimeBreakpoints(247);
    debuggerModel.addDebugClassSource(GeneratorTarget.class, new DebugClassSource(true, 18) {
      @Override
      public String getContentsAsString() throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), "com/swilkins/ScrabbleVisualizer/executable/GeneratorTarget.java");
        return Files.readString(path);
      }
    });
  }

  @Override
  protected void configureDereferencers() {
    Dereferencer fromTileContainer = (tileWrapper, thread) -> standardDereference(tileWrapper, "getTile", thread);
    dereferencerMap.put(BoardSquare.class, fromTileContainer);
    dereferencerMap.put(TilePlacement.class, (tilePlacement, thread) -> new Object[]{
            standardDereference(tilePlacement, "getX", thread),
            standardDereference(tilePlacement, "getY", thread),
            fromTileContainer.dereference(tilePlacement, thread)
    });
    dereferencerMap.put(Tile.class, (tile, thread) -> new Object[]{
            standardDereference(tile, "getLetter", thread),
            standardDereference(tile, "getLetterProxy", thread)
    });
    dereferencerMap.put(Direction.class, (direction, thread) -> {
      ObjectReference directionNameReference = (ObjectReference) invoke(direction, thread, "name", null, null);
      return toString.dereference(directionNameReference, thread);
    });
    dereferencerMap.put(Character.class, (character, thread) -> standardDereference(character, "charValue", thread));
    dereferencerMap.put(Candidate.class, (candidate, thread) -> new Object[]{
            standardDereference(candidate, "getScore", thread),
            toString.dereference(candidate, thread)
    });
    dereferencerMap.put(Coordinates.class, (coordinates, thread) -> new Object[]{
            standardDereference(coordinates, "getX", thread),
            standardDereference(coordinates, "getY", thread)
    });
    dereferencerMap.put(CrossedTilePlacement.class, (crossedTilePlacement, thread) -> standardDereference(crossedTilePlacement, "getRoot", thread));
  }

  @Override
  protected void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments) {
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
  }

}