package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public class DebuggerModel {

  private final Map<Class<?>, DebugClassSource> debugClassSources = new HashMap<>();
  private final Map<Class<?>, DebugClass> debugClasses = new HashMap<>();
  private static final String sourceSuffix = ".java";

  public void addDebugClassSource(Class<?> clazz, DebugClassSource debugClassSource) {
    debugClassSources.put(clazz, debugClassSource);
  }

  public Set<Class<?>> addDebugClassSourcesFromJar(String jarPath, DebugClassSourceFilter filter) throws IOException, ClassNotFoundException {
    File file = new File(jarPath);
    JarFile jarFile = new JarFile(file);

    Map<Class<?>, String> representedClasses = new HashMap<>();
    Enumeration<JarEntry> entries = jarFile.entries();
    while (entries.hasMoreElements()) {
      String entry = entries.nextElement().getRealName();
      if (entry.endsWith(sourceSuffix)) {
        String entryClass = entry.replace("/", ".").replace(sourceSuffix, "");
        Class<?> representedClass = Class.forName(entryClass);
        representedClasses.put(representedClass, entry);
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
      addDebugClassSource(representedClass.getKey(), new DebugClassSource() {
        @Override
        public String getContentsAsString() throws Exception {
          return inputStreamToString(jarFile.getInputStream(jarFile.getEntry(representedClass.getValue())));
        }
      });
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
