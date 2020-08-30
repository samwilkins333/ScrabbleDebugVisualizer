package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.*;
import com.swilkins.ScrabbleVisualizer.debug.interfaces.Invokable;
import com.swilkins.ScrabbleVisualizer.utility.Utilities;

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

import static com.sun.jdi.request.StepRequest.STEP_LINE;
import static com.swilkins.ScrabbleVisualizer.utility.Utilities.inputStreamToString;

public class DebuggerModel {

  private final Map<Class<?>, DebugClassSource> debugClassSources = new LinkedHashMap<>();
  private final Map<Class<?>, DebugClass> debugClasses = new LinkedHashMap<>();
  private static final String JAVA_SUFFIX = ".java";
  private String globalClassFilter = null;
  private EventRequestManager eventRequestManager;
  private final Map<EventRequest, Boolean> eventRequestStateMap = new HashMap<>();

  private final Object eventProcessingControl = new Object();
  private final Object stepRequestControl = new Object();
  private final Object threadReferenceControl = new Object();

  private Integer activeStepRequestDepth;
  private Integer requestedStepRequestDepth;
  private final Map<Integer, StepRequest> stepRequestMap = new HashMap<>(3);

  public void setEventRequestManager(EventRequestManager eventRequestManager) {
    this.eventRequestManager = eventRequestManager;
  }

  public DebugClassSource addDebugClassSource(Class<?> clazz, DebugClassSource debugClassSource) {
    debugClassSources.put(clazz, debugClassSource);
    return debugClassSource;
  }

  private Class<?> sourceToClass(String source) throws ClassNotFoundException {
    if (source.endsWith(JAVA_SUFFIX)) {
      String entryClass = source.replace("/", ".").replace(JAVA_SUFFIX, "");
      return Class.forName(entryClass);
    }
    return null;
  }

  public Class<?>[] getAllClasses() {
    Class<?>[] representedClasses = new Class<?>[debugClassSources.size()];
    int i = 0;
    for (Map.Entry<Class<?>, DebugClassSource> debugClassSourceEntry : debugClassSources.entrySet()) {
      representedClasses[i++] = debugClassSourceEntry.getKey();
    }
    Arrays.sort(representedClasses, Comparator.comparing(Class::getName));
    return representedClasses;
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

  public void submitDebugClassSources() {
    List<String> classNames = new ArrayList<>(debugClassSources.size());
    for (Class<?> clazz : debugClassSources.keySet()) {
      ClassPrepareRequest request = eventRequestManager.createClassPrepareRequest();
      String className = clazz.getName();
      request.addClassFilter(className);
      classNames.add(className);
      setEventRequestEnabled(request, true);
    }
    String globalClassFilter = Utilities.longestCommonPrefix(classNames);
    if (!globalClassFilter.isEmpty()) {
      this.globalClassFilter = globalClassFilter + "*";
    }
  }

  public void enableExceptionReporting(boolean notifyCaught, boolean notifyUncaught) {
    setEventRequestEnabled(eventRequestManager.createExceptionRequest(null, notifyCaught, notifyUncaught), true);
  }

  public void createDebugClassFrom(ClassPrepareEvent event) throws ClassNotFoundException, AbsentInformationException {
    ReferenceType referenceType = event.referenceType();
    Class<?> clazz = Class.forName(referenceType.name());
    DebugClassSource debugClassSource = debugClassSources.get(clazz);
    DebugClass debugClass = new DebugClass(clazz, debugClassSource, referenceType::locationsOfLine);
    debugClass.setCached(debugClassSource.isCached());
    for (int compileTimeBreakpoint : debugClassSource.getCompileTimeBreakpoints()) {
      createBreakpointRequest(new DebugClassLocation(debugClass, compileTimeBreakpoint));
    }
    debugClasses.put(clazz, debugClass);
  }

  public BreakpointRequest getBreakpointRequestAt(DebugClassLocation selectedLocation) {
    Class<?> clazz = selectedLocation.getDebugClass().getClazz();
    int lineNumber = selectedLocation.getLineNumber();
    return getDebugClassFor(clazz).getBreakpointRequest(lineNumber);
  }

  public void createBreakpointRequest(DebugClassLocation breakpointLocation) throws AbsentInformationException {
    DebugClass debugClass = breakpointLocation.getDebugClass();
    int lineNumber = breakpointLocation.getLineNumber();
    Location location = debugClass.getLocationOf(lineNumber);
    if (location != null) {
      BreakpointRequest breakpointRequest = eventRequestManager.createBreakpointRequest(location);
      setEventRequestEnabled(breakpointRequest, true);
      debugClass.addBreakpointRequest(lineNumber, breakpointRequest);
    }
  }

  public DebugClassLocation toDebugClassLocation(Location location) {
    DebugClassLocation debugClassLocation = null;
    String className = location.toString().split(":")[0];
    try {
      String[] classNameComponents = className.split("\\$");
      if (classNameComponents.length > 1) {
        className = classNameComponents[0];
      }
      Class<?> clazz = Class.forName(className);
      DebugClass debugClass = debugClasses.get(clazz);
      if (debugClass != null) {
        debugClassLocation = new DebugClassLocation(debugClass, location.lineNumber());
      }
    } catch (ClassNotFoundException ignored) {
    }
    return debugClassLocation;
  }

  public DebugClassSource getDebugClassSourceFor(Class<?> clazz) {
    return debugClassSources.get(clazz);
  }

  public DebugClass getDebugClassFor(Class<?> clazz) {
    return debugClasses.get(clazz);
  }

  public void setRequestedStepRequestDepth(Integer requestedStepRequestDepth) {
    this.requestedStepRequestDepth = requestedStepRequestDepth;
  }

  public void respondToRequestedStepRequestDepth(ThreadReference threadReference) {
    synchronized (stepRequestControl) {
      disableActiveStepRequest();
      if (requestedStepRequestDepth == null) {
        return;
      }
      if (activeStepRequestDepth == null || !activeStepRequestDepth.equals(requestedStepRequestDepth)) {
        StepRequest requestedStepRequest = stepRequestMap.get(requestedStepRequestDepth);
        if (requestedStepRequest == null) {
          synchronized (threadReferenceControl) {
            requestedStepRequest = eventRequestManager.createStepRequest(threadReference, STEP_LINE, requestedStepRequestDepth);
          }
          if (globalClassFilter != null) {
            requestedStepRequest.addClassFilter(globalClassFilter);
          }
          stepRequestMap.put(requestedStepRequestDepth, requestedStepRequest);
        }
        setEventRequestEnabled(requestedStepRequest, true);
        activeStepRequestDepth = requestedStepRequestDepth;
      }
    }
  }

  private void disableActiveStepRequest() {
    synchronized (stepRequestControl) {
      if (activeStepRequestDepth != null) {
        StepRequest activeStepRequest = stepRequestMap.get(activeStepRequestDepth);
        if (activeStepRequest != null) {
          setEventRequestEnabled(activeStepRequest, false);
        }
        activeStepRequestDepth = null;
      }
    }
  }

  public void awaitEventProcessingContinuation() {
    synchronized (eventProcessingControl) {
      try {
        eventProcessingControl.wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
  }

  public void resumeEventProcessing() {
    synchronized (eventProcessingControl) {
      eventProcessingControl.notify();
    }
  }

  public void setEventRequestEnabled(EventRequest eventRequest, boolean enabled) {
    eventRequest.setEnabled(enabled);
    eventRequestStateMap.put(eventRequest, enabled);
  }

  public <T, R> void deadlockSafeInvoke(Invokable toInvoke) {
    for (Map.Entry<EventRequest, Boolean> eventRequestEntry : eventRequestStateMap.entrySet()) {
      eventRequestEntry.getKey().setEnabled(false);
    }
    toInvoke.invoke();
    for (Map.Entry<EventRequest, Boolean> eventRequestEntry : eventRequestStateMap.entrySet()) {
      eventRequestEntry.getKey().setEnabled(eventRequestEntry.getValue());
    }
  }

}
