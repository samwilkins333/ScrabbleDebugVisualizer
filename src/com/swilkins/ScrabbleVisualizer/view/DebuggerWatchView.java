package com.swilkins.ScrabbleVisualizer.view;

import com.sun.jdi.Location;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

public abstract class DebuggerWatchView extends JPanel {

  private final Map<String[], BiConsumer<Location, Iterator<Object>>> updaters = new LinkedHashMap<>();

  public DebuggerWatchView() {
    super();
    registerUpdaters();
  }

  public abstract void initialize(Dimension initialDimension);

  protected abstract void onVariablesDeserialized(Map<String, Object> deserializedVariables);

  public abstract BiConsumer<Dimension, Integer> onSplitResize();

  protected void registerUpdater(BiConsumer<Location, Iterator<Object>> updater, String... variableDependencyNames) {
    updaters.put(variableDependencyNames, updater);
  }

  protected abstract void registerUpdaters();

  public void updateFrom(Location location, Map<String, Object> deserializedVariables) {
    onVariablesDeserialized(deserializedVariables);

    for (Map.Entry<String[], BiConsumer<Location, Iterator<Object>>> entry : updaters.entrySet()) {
      String[] dependencies = entry.getKey();
      List<Object> args = new ArrayList<>();
      for (String dependencyName : entry.getKey()) {
        Object arg = deserializedVariables.get(dependencyName);
        if (arg != null) {
          args.add(arg);
        }
      }
      if (dependencies.length == args.size()) {
        entry.getValue().accept(location, args.iterator());
      }
    }
  }

  public abstract void clean();

}
