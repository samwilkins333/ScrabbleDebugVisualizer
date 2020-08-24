package com.swilkins.ScrabbleVisualizer.debug;

import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;

public interface Deserializer {

  Object deserialize(ObjectReference object, ThreadReference thread);

}
