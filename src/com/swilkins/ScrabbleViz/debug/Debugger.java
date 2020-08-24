package com.swilkins.ScrabbleViz.debug;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;

import java.io.IOException;
import java.util.Map;

public abstract class Debugger {

  protected final VirtualMachine virtualMachine;
  protected final DebuggerView view;
  protected final DebuggerModel model;

  public Debugger(Class<?> virtualMachineTargetClass) throws Exception {
    LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager().defaultConnector();
    Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
    arguments.get("main").setValue(virtualMachineTargetClass.getName());
    this.configureVirtualMachineLaunch(arguments);
    virtualMachine = launchingConnector.launch(arguments);

    this.view = new DebuggerView();
    this.configureView();
    this.model = new DebuggerModel();
    this.configureModel();
  }

  public abstract void configureVirtualMachineLaunch(Map<String, Connector.Argument> arguments);

  public abstract void configureView();

  public abstract void configureModel() throws IOException;

}
