package com.swilkins.ScrabbleVisualizer.utility;

import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;

public interface Unpacker {

  Object unpack(ObjectReference object, ThreadReference thread);

}
