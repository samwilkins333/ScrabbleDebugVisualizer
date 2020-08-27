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
import com.swilkins.ScrabbleVisualizer.view.DebuggerWatchView;

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

  public ScrabbleBaseDebugger(DebuggerWatchView debuggerWatchView) throws Exception {
    super(GeneratorTarget.class, debuggerWatchView);
  }

  @Override
  protected void configureDebuggerModel() throws IOException, ClassNotFoundException {
    debuggerModel.addDebugClassSourcesFromJar("../lib/scrabble-base-jar-with-dependencies.jar", null);
    debuggerModel.getDebugClassSourceFor(Generator.class).setCached(true).addCompileTimeBreakpoints(210);
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
  protected void configureDeserializers() {
    Deserializer unpackTileWrapper = (tileWrapper, thread) -> {
      Value tileReference = invoke(tileWrapper, thread, "getTile", null);
      return deserializeReference(thread, tileReference);
    };
    deserializers.put(BoardSquare.class.getName(), unpackTileWrapper);
    deserializers.put(Direction.class.getName(), (direction, thread) -> {
      ObjectReference directionNameReference = (ObjectReference) invoke(direction, thread, "name", null);
      return deserializeReference(thread, invoke(directionNameReference, thread, "toString", null));
    });
    deserializers.put(Tile.class.getName(), (tile, thread) -> {
      Value letter = invoke(tile, thread, "getLetter", null);
      Value proxy = invoke(tile, thread, "getLetterProxy", null);
      return new Object[]{deserializeReference(thread, letter), deserializeReference(thread, proxy)};
    });
    deserializers.put(Character.class.getName(), (character, thread) -> {
      Value value = invoke(character, thread, "charValue", null);
      return deserializeReference(thread, value);
    });
    deserializers.put(Candidate.class.getName(), (candidate, thread) -> {
      int score = (int) deserializeReference(thread, invoke(candidate, thread, "getScore", null));
      Value serialized = invoke(candidate, thread, "toString", null);
      return new Object[]{score, deserializeReference(thread, serialized)};
    });
    deserializers.put(TilePlacement.class.getName(), (tilePlacement, thread) -> {
      int x = (int) deserializeReference(thread, invoke(tilePlacement, thread, "getX", null));
      int y = (int) deserializeReference(thread, invoke(tilePlacement, thread, "getY", null));
      return new Object[]{x, y, unpackTileWrapper.deserialize(tilePlacement, thread)};
    });
    deserializers.put(CrossedTilePlacement.class.getName(), (crossedTilePlacement, thread) -> {
      Value tilePlacement = invoke(crossedTilePlacement, thread, "getRoot", null);
      return deserializeReference(thread, tilePlacement);
    });
    Deserializer unpackArrayable = (arrayable, thread) -> {
      Value asArray = invoke(arrayable, thread, "toArray", null);
      return deserializeReference(thread, asArray);
    };
    deserializers.put(HashSet.class.getName(), unpackArrayable);
    deserializers.put(LinkedList.class.getName(), unpackArrayable);
  }

  @Override
  protected void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments) {
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
  }

  @Override
  protected void onVirtualMachineEvent(Event event) throws Exception {
    if (event instanceof LocatableEvent) {
      LocatableEvent locatableEvent = (LocatableEvent) event;
      Class<?> clazz = toClass(locatableEvent.location());
      if (clazz != null && debuggerModel.getDebugClassFor(clazz) != null) {
        trySuspend(locatableEvent);
      }
    }
    virtualMachine.resume();
  }

  @Override
  protected void onVirtualMachineTermination(String virtualMachineOut, String virtualMachineError) {
    System.out.println(String.format("Output:\n%s\n\nError:\n%s", virtualMachineOut, virtualMachineError));
  }

}