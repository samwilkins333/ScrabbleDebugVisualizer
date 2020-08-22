package com.swilkins.ScrabbleViz.utility;

import com.sun.jdi.*;
import com.swilkins.ScrabbleBase.Board.State.BoardSquare;
import com.swilkins.ScrabbleBase.Board.State.Tile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Unpackers {

  private static final Unpacker fallback = (object, thread) -> invoke(object, thread, object.referenceType()
          .methodsByName("toString", "()Ljava/lang/String;").get(0));

  public static Unpacker getFor(ObjectReference object) {
    Unpacker existing = agents.get(object.referenceType().name());
    return existing != null ? existing : fallback;
  }

  private static final Map<String, Unpacker> agents = new HashMap<>();
  static {
    agents.put(BoardSquare.class.getName(), (boardSquare, thread) -> {
      Method target;
      target = boardSquare.referenceType().methodsByName("getTile").get(0);
      ObjectReference tileReference = (ObjectReference) invoke(boardSquare, thread, target);
      if (tileReference == null) {
        return null;
      }
      target = tileReference.referenceType().methodsByName("getResolvedLetter").get(0);
      CharValue letter = (CharValue) invoke(tileReference, thread, target);
      assert letter != null;
      return letter.value();
    });
  }

  private static Value invoke(ObjectReference object, ThreadReference thread, Method toInvoke) {
    try {
      return object.invokeMethod(thread, toInvoke, Collections.emptyList(), 0);
    } catch (Exception e) {
      return null;
    }
  }

}
