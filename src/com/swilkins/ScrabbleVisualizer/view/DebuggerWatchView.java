package com.swilkins.ScrabbleVisualizer.view;

import com.swilkins.ScrabbleVisualizer.debug.DebugClassLocation;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.BiConsumer;

public abstract class DebuggerWatchView extends JPanel {

  private final Map<String[], BiConsumer<DebugClassLocation, Iterator<Object>>> updaters = new LinkedHashMap<>();

  protected final JTextArea rawWatchedName = new JTextArea();
  protected final JTextArea rawWatchedValue = new JTextArea();

  public DebuggerWatchView() {
    super();
    registerUpdaters();
  }

  public abstract void initialize(Dimension initialDimension);

  public abstract BiConsumer<Dimension, Integer> onSplitResize();

  protected void registerUpdater(BiConsumer<DebugClassLocation, Iterator<Object>> updater, String... variableDependencyNames) {
    updaters.put(variableDependencyNames, updater);
  }

  protected abstract void registerUpdaters();

  public void updateFrom(DebugClassLocation location, Map<String, Object> dereferencedVariables) {
    onVariablesDereferenced(dereferencedVariables);

    for (Map.Entry<String[], BiConsumer<DebugClassLocation, Iterator<Object>>> entry : updaters.entrySet()) {
      String[] dependencies = entry.getKey();
      List<Object> args = new ArrayList<>();
      for (String dependencyName : entry.getKey()) {
        Object arg = dereferencedVariables.get(dependencyName);
        if (arg != null) {
          args.add(arg);
        }
      }
      if (dependencies.length == args.size()) {
        entry.getValue().accept(location, args.iterator());
      }
    }
  }

  protected void onVariablesDereferenced(Map<String, Object> dereferencedVariables) {
    StringBuilder rawNameBuilder = new StringBuilder();
    StringBuilder rawValueBuilder = new StringBuilder();

    List<Map.Entry<String, Object>> variables = new ArrayList<>(dereferencedVariables.entrySet());
    variables.sort(Map.Entry.comparingByKey());

    for (Map.Entry<String, Object> entry : variables) {
      rawNameBuilder.append(entry.getKey()).append("\n");
      Object value = entry.getValue();
      if (value.getClass().isArray()) {
        value = Arrays.deepToString((Object[]) value);
      }
      rawValueBuilder.append(value).append("\n");
    }

    rawWatchedName.setText(rawNameBuilder.toString());
    rawWatchedValue.setText(rawValueBuilder.toString());
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    for (Component component : getComponents()) {
      component.setEnabled(enabled);
    }
  }

  public abstract void clean();

}
