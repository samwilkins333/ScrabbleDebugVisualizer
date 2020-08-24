package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.Location;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.StepEvent;
import com.swilkins.ScrabbleBase.Board.Location.TilePlacement;
import com.swilkins.ScrabbleBase.Board.State.BoardSquare;
import com.swilkins.ScrabbleBase.Board.State.Tile;
import com.swilkins.ScrabbleBase.Generation.Candidate;
import com.swilkins.ScrabbleBase.Generation.CrossedTilePlacement;
import com.swilkins.ScrabbleBase.Generation.Direction;
import com.swilkins.ScrabbleBase.Vocabulary.PermutationTrie;
import com.swilkins.ScrabbleVisualizer.executable.GeneratorTarget;
import com.swilkins.ScrabbleVisualizer.view.WatchView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import static com.swilkins.ScrabbleVisualizer.debug.DefaultDebuggerControl.*;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public class ScrabbleVisualizer extends Debugger {

  private static final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
  private WatchView watchView;
  private JFrame frame;

  public ScrabbleVisualizer() throws Exception {
    super(GeneratorTarget.class);

    frame = new JFrame(ScrabbleVisualizer.class.getSimpleName());
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setSize(screenSize.width, screenSize.height);
    frame.setResizable(false);

    JPanel panel = new JPanel();
    panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
    panel.add(view);
    panel.add(watchView = new WatchView(new Dimension(screenSize.width / 3, screenSize.height / 3)));

    frame.getContentPane().add(panel);
    frame.setVisible(true);
  }

  @Override
  protected void configureView() {
    view.setOptions(null);

    Dimension topThird = new Dimension(screenSize.width, screenSize.height / 3);
    view.setPreferredSize(topThird);
    view.setMinimumSize(topThird);
    view.setMaximumSize(topThird);
    view.setSize(topThird);

    view.addDefaultControlButton(RESUME);
    view.addDefaultControlButton(STEP_OVER);
    view.addDefaultControlButton(STEP_INTO);
    view.addDefaultControlButton(STEP_OUT);
    view.addDefaultControlButton(TOGGLE_BREAKPOINT);
  }

  @Override
  protected void configureModel() throws IOException, ClassNotFoundException {
    model.addDebugClassSource(
            GeneratorTarget.class,
            new DebugClassSource(23, 34) {
              @Override
              public String getContentsAsString() {
                InputStream debugClassStream = ScrabbleVisualizer.class.getResourceAsStream("../executable/GeneratorTarget.java");
                return inputStreamToString(debugClassStream);
              }
            }
    );
    String jarPath = "../lib/scrabble-base-jar-with-dependencies.jar";
    Set<Class<?>> representedClasses = model.addDebugClassSourcesFromJar(jarPath, null);
    if (representedClasses.contains(PermutationTrie.class)) {
      model.getDebugClassSourceFor(PermutationTrie.class).addCompileTimeBreakpoints(26);
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
  protected void onVirtualMachineLaunch(Map<String, Connector.Argument> arguments) {
    arguments.get("options").setValue("-cp \".:../lib/scrabble-base-jar-with-dependencies.jar\"");
  }

  @Override
  protected void onVirtualMachineEvent(Event event) throws Exception {
    if (event instanceof BreakpointEvent) {
      deleteActiveStepRequest();
      suspend((LocatableEvent) event);
    } else if (event instanceof StepEvent) {
      Class<?> clazz = toClass(((StepEvent) event).location());
      if (clazz != null && model.getDebugClassFor(clazz) != null) {
        suspend((StepEvent) event);
      }
    }
    virtualMachine.resume();
  }

  @Override
  protected void onVirtualMachineSuspension(Location location, Map<String, Object> unpackedVariables) {
    watchView.updateFrom(location, unpackedVariables);
  }

  @Override
  protected void onVirtualMachineContinuation() {
    watchView.clean();
  }

  @Override
  protected void onVirtualMachineTermination() {
    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
  }

}