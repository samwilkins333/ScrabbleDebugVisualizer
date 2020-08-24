package com.swilkins.ScrabbleVisualizer.debug;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DebugClassSourceFilter {

  private final DebugClassSourceFilterType filterType;
  private final Set<Class<?>> filteredClasses = new HashSet<>();

  public DebugClassSourceFilter(DebugClassSourceFilterType filterType, Class<?>... filteredClasses) {
    this.filterType = filterType;
    this.filteredClasses.addAll(Arrays.asList(filteredClasses));
  }

  public void addFilteredClass(Class<?> filteredClass) {
    filteredClasses.add(filteredClass);
  }

  public DebugClassSourceFilterType getFilterType() {
    return filterType;
  }

  public Set<Class<?>> getFilteredClasses() {
    return filteredClasses;
  }

}
