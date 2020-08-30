package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.LocatableEvent;
import com.swilkins.ScrabbleBase.Board.Location.TilePlacement;
import com.swilkins.ScrabbleBase.Board.State.BoardSquare;
import com.swilkins.ScrabbleBase.Board.State.Tile;
import com.swilkins.ScrabbleBase.Generation.Candidate;
import com.swilkins.ScrabbleBase.Generation.CrossedTilePlacement;
import com.swilkins.ScrabbleBase.Generation.Direction;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleVisualizer.executable.GeneratorTarget;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import static com.swilkins.ScrabbleVisualizer.utility.Utilities.createImageIconFrom;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public class ScrabbleBaseDebugger extends Debugger {

  public static final Dimension ICON_DIMENSION = new Dimension(12, 12);

  public ScrabbleBaseDebugger() throws Exception {
    super(GeneratorTarget.class, new ScrabbleBaseVisualizer());
  }

  @Override
  protected void configureDebuggerModel() throws IOException, ClassNotFoundException {
    debuggerModel.addDebugClassSourcesFromJar("../lib/scrabble-base-jar-with-dependencies.jar", null);
    debuggerModel.getDebugClassSourceFor(Generator.class).setCached(true).addCompileTimeBreakpoints(203);
    debuggerModel.addDebugClassSource(GeneratorTarget.class, new DebugClassSource(true, 15, 17, 25) {
      @Override
      public String getContentsAsString() {
        InputStream debugClassStream = ScrabbleBaseDebugger.class.getResourceAsStream("../executable/GeneratorTarget.java");
        return inputStreamToString(debugClassStream);
      }
    });
  }

  @Override
  protected void configureDebuggerView() {
    debuggerSourceView.setOptions(null);

    for (DebuggerControl control : DebuggerControl.values()) {
      JButton controlButton = debuggerSourceView.addDefaultControlButton(control);
      URL iconUrl = getClass().getResource(String.format("../resource/icons/%s.png", control.getLabel()));
      controlButton.setIcon(createImageIconFrom(iconUrl, ICON_DIMENSION));
      controlButton.setFocusPainted(false);
    }
  }

  @Override
  protected void configureDereferencers() {
    Dereferencer unpackTileWrapper = (tileWrapper, thread) -> {
      Value tileReference = invoke(tileWrapper, thread, "getTile", null, null);
      return dereferenceValue(thread, tileReference);
    };
    dereferencerMap.put(BoardSquare.class.getName(), unpackTileWrapper);
    dereferencerMap.put(Direction.class.getName(), (direction, thread) -> {
      ObjectReference directionNameReference = (ObjectReference) invoke(direction, thread, "name", null, null);
      return dereferenceValue(thread, invoke(directionNameReference, thread, "toString", null, null));
    });
    dereferencerMap.put(Tile.class.getName(), (tile, thread) -> {
      Value letter = invoke(tile, thread, "getLetter", null, null);
      Value proxy = invoke(tile, thread, "getLetterProxy", null, null);
      return new Object[]{dereferenceValue(thread, letter), dereferenceValue(thread, proxy)};
    });
    dereferencerMap.put(Character.class.getName(), (character, thread) -> {
      Value value = invoke(character, thread, "charValue", null, null);
      return dereferenceValue(thread, value);
    });
    dereferencerMap.put(Candidate.class.getName(), (candidate, thread) -> {
      int score = (int) dereferenceValue(thread, invoke(candidate, thread, "getScore", null, null));
      Value serialized = invoke(candidate, thread, "toString", null, null);
      return new Object[]{score, dereferenceValue(thread, serialized)};
    });
    dereferencerMap.put(TilePlacement.class.getName(), (tilePlacement, thread) -> {
      int x = (int) dereferenceValue(thread, invoke(tilePlacement, thread, "getX", null, null));
      int y = (int) dereferenceValue(thread, invoke(tilePlacement, thread, "getY", null, null));
      return new Object[]{x, y, unpackTileWrapper.dereference(tilePlacement, thread)};
    });
    dereferencerMap.put(CrossedTilePlacement.class.getName(), (crossedTilePlacement, thread) -> {
      Value tilePlacement = invoke(crossedTilePlacement, thread, "getRoot", null, null);
      return dereferenceValue(thread, tilePlacement);
    });
    Dereferencer unpackArrayable = (arrayable, thread) -> {
      Value asArray = invoke(arrayable, thread, "toArray", null, null);
      return dereferenceValue(thread, asArray);
    };
    dereferencerMap.put(HashSet.class.getName(), unpackArrayable);
    dereferencerMap.put(LinkedList.class.getName(), unpackArrayable);
  }

  @Override
  protected void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments) {
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
  }

  @Override
  protected void onVirtualMachineLocatableEvent(LocatableEvent event, int eventSetSize) throws Exception {
    super.onVirtualMachineLocatableEvent(event, eventSetSize);
  }

}