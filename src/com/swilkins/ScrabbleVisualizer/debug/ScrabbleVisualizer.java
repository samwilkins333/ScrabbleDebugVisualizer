package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.request.StepRequest;
import com.swilkins.ScrabbleBase.Generation.Generator;
import com.swilkins.ScrabbleVisualizer.executable.GeneratorTarget;
import com.swilkins.ScrabbleVisualizer.view.WatchView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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

    watchView = new WatchView(new Dimension(screenSize.width / 3, screenSize.height / 3));

    JPanel controls = new JPanel();
    controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
    JButton resume = new JButton("Resume");
    resume.addActionListener(e -> resumeEventProcessing());
    controls.add(resume);

    JButton controlButton;

    controlButton = new JButton("Step Over");
    controlButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_OVER));
    controls.add(controlButton);

    controlButton = new JButton("Step Into");
    controlButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_INTO));
    controls.add(controlButton);

    controlButton = new JButton("Step Out");
    controlButton.addActionListener(e -> activateStepRequest(StepRequest.STEP_OUT));
    controls.add(controlButton);

    controlButton = new JButton("Toggle Breakpoint");
    controlButton.addActionListener(e -> {
      try {
        view.toggleBreakpointAt(view.getSelectedLocation());
      } catch (AbsentInformationException ex) {
        view.reportException(ex);
      }
    });
    controls.add(controlButton);

    panel.add(controls);
    panel.add(watchView);

    frame.getContentPane().add(panel);

    frame.setVisible(true);
  }

  @Override
  protected void configureView() {
    DebuggerViewOptions options = new DebuggerViewOptions();
    options.setTextColor(Color.WHITE);
    options.setBackgroundColor(Color.BLACK);
    view.setOptions(options);

    Dimension topThird = new Dimension(screenSize.width, screenSize.height / 3);
    view.setPreferredSize(topThird);
    view.setMinimumSize(topThird);
    view.setMaximumSize(topThird);
    view.setSize(topThird);
  }

  @Override
  protected void configureModel() throws IOException {
    model.addDebugClassSource(
            GeneratorTarget.class,
            new DebugClassSource(22) {
              @Override
              public String getContentsAsString() {
                InputStream debugClassStream = ScrabbleVisualizer.class.getResourceAsStream("../executable/GeneratorTarget.java");
                return inputStreamToString(debugClassStream);
              }
            }
    );
    File file = new File("../lib/scrabble-base-jar-with-dependencies.jar");
    JarFile jarFile = new JarFile(file);
    JarEntry generator = jarFile.getJarEntry("com/swilkins/ScrabbleBase/Generation/Generator.java");
    model.addDebugClassSource(
            Generator.class,
            new DebugClassSource() {
              @Override
              public String getContentsAsString() {
                try {
                  InputStream debugClassStream = jarFile.getInputStream(generator);
                  return inputStreamToString(debugClassStream);
                } catch (IOException e) {
                  return null;
                }
              }
            }
    );
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