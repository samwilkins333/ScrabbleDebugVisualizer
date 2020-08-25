package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public class DebuggerModel {

  private final Map<Class<?>, DebugClassSource> debugClassSources = new HashMap<>();
  private final Map<Class<?>, DebugClass> debugClasses = new HashMap<>();
  private static final String javaSuffix = ".java";

  public DebugClassSource addDebugClassSource(Class<?> clazz, DebugClassSource debugClassSource) {
    return debugClassSources.put(clazz, debugClassSource);
  }

  private Class<?> sourceToClass(String source) throws ClassNotFoundException {
    if (source.endsWith(javaSuffix)) {
      String entryClass = source.replace("/", ".").replace(javaSuffix, "");
      return Class.forName(entryClass);
    }
    return null;
  }

  public Set<Class<?>> addDebugClassSourcesFromJar(String jarPath, DebugClassSourceFilter filter) throws IOException, ClassNotFoundException {
    File file = new File(jarPath);
    JarFile jarFile = new JarFile(file);

    Enumeration<JarEntry> entries = jarFile.entries();
    List<String> sources = new ArrayList<>();
    while (entries.hasMoreElements()) {
      sources.add(entries.nextElement().getRealName());
    }
    return processSourcesList(sources, filter, source -> new DebugClassSource(false) {
      @Override
      public String getContentsAsString() throws Exception {
        return inputStreamToString(jarFile.getInputStream(jarFile.getEntry(source)));
      }
    });
  }

  public Set<Class<?>> addDebugClassesFromDirectory(String directoryPath, DebugClassSourceFilter filter) throws IOException, ClassNotFoundException {
    File directory = new File(directoryPath);
    if (!directory.isDirectory()) {
      throw new IllegalArgumentException();
    }
    List<String> sources = Files.list(directory.toPath()).map(Path::toString).collect(Collectors.toList());
    return processSourcesList(sources, filter, source -> new DebugClassSource(false) {
      @Override
      public String getContentsAsString() throws Exception {
        return inputStreamToString(new FileInputStream(new File(source)));
      }
    });
  }

  private Set<Class<?>> processSourcesList(List<String> sources, DebugClassSourceFilter filter, Function<String, DebugClassSource> debugClassSourceProvider) throws ClassNotFoundException {
    Map<Class<?>, String> representedClasses = new HashMap<>();

    for (String source : sources) {
      Class<?> representedClass = sourceToClass(source);
      if (representedClass != null) {
        representedClasses.put(representedClass, source);
      }
    }

    if (filter != null) {
      Set<Class<?>> filteredClasses = filter.getFilteredClasses();
      Set<Class<?>> excludes;

      Set<Class<?>> representedClassesKeySet = representedClasses.keySet();
      if (filter.getFilterType() == DebugClassSourceFilterType.INCLUDE) {
        excludes = new HashSet<>(representedClassesKeySet);
        excludes.removeAll(filteredClasses);
      } else {
        excludes = filteredClasses;
      }

      representedClassesKeySet.removeAll(excludes);
    }

    for (Map.Entry<Class<?>, String> representedClass : representedClasses.entrySet()) {
      addDebugClassSource(representedClass.getKey(), debugClassSourceProvider.apply(representedClass.getValue()));
    }

    return representedClasses.keySet();
  }

  public void submitDebugClassSources(EventRequestManager eventRequestManager) {
    for (Class<?> clazz : debugClassSources.keySet()) {
      ClassPrepareRequest request = eventRequestManager.createClassPrepareRequest();
      request.addClassFilter(clazz.getName());
      request.enable();
    }
  }

  public void enableExceptionReporting(EventRequestManager eventRequestManager, boolean notifyCaught) {
    eventRequestManager.createExceptionRequest(null, notifyCaught, true).enable();
  }

  public void createDebugClassFor(EventRequestManager eventRequestManager, ClassPrepareEvent event) throws ClassNotFoundException, AbsentInformationException {
    ReferenceType referenceType = event.referenceType();
    Class<?> clazz = Class.forName(referenceType.name());
    DebugClassOperations operations = new DebugClassOperations(
            referenceType::locationsOfLine,
            eventRequestManager::createBreakpointRequest,
            eventRequestManager::deleteEventRequest
    );
    DebugClassSource debugClassSource = debugClassSources.get(clazz);
    DebugClass debugClass = new DebugClass(clazz, debugClassSource, operations);
    debugClass.setCached(debugClassSource.isCached());
    for (int compileTimeBreakpoint : debugClassSource.getCompileTimeBreakpoints()) {
      debugClass.requestBreakpointAt(compileTimeBreakpoint);
    }
    debugClasses.put(clazz, debugClass);
  }

  public DebugClassSource getDebugClassSourceFor(Class<?> clazz) {
    return debugClassSources.get(clazz);
  }

  public DebugClass getDebugClassFor(Class<?> clazz) {
    return debugClasses.get(clazz);
  }

}
