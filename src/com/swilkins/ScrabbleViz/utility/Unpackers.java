package com.swilkins.ScrabbleViz.utility;

import com.sun.jdi.*;
import com.swilkins.ScrabbleBase.Board.Location.TilePlacement;
import com.swilkins.ScrabbleBase.Board.State.BoardSquare;
import com.swilkins.ScrabbleBase.Generation.CrossedTilePlacement;
import com.swilkins.ScrabbleBase.Generation.Direction;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class Unpackers {

  private static final Unpacker fallback = (object, thread) -> invoke(object, thread, "toString", "()Ljava/lang/String;");

  public static Unpacker getFor(ObjectReference object) {
    Unpacker existing = agents.get(object.referenceType().name());
    return existing != null ? existing : fallback;
  }

  private static final Map<String, Unpacker> agents = new HashMap<>();

  static {
    Unpacker unpackTileWrapper = (tileWrapper, thread) -> {
      ObjectReference tileReference = (ObjectReference) invoke(tileWrapper, thread, "getTile", null);
      if (tileReference == null) {
        return null;
      }
      CharValue letter = (CharValue) invoke(tileReference, thread, "getResolvedLetter", null);
      assert letter != null;
      return letter.value();
    };
    agents.put(BoardSquare.class.getName(), unpackTileWrapper);
    agents.put(Direction.class.getName(), (direction, thread) -> {
      ObjectReference directionNameReference = (ObjectReference) invoke(direction, thread, "name", null);
      assert directionNameReference != null;
      StringReference name = (StringReference) invoke(directionNameReference, thread, "toString", null);
      assert name != null;
      return name.value();
    });
    agents.put(TilePlacement.class.getName(), (tilePlacement, thread) -> {
      int x = getInt(tilePlacement, "getX", thread);
      int y = getInt(tilePlacement, "getY", thread);
      return new Object[]{x, y, unpackTileWrapper.unpack(tilePlacement, thread)};
    });
    agents.put(CrossedTilePlacement.class.getName(), (crossedTilePlacement, thread) -> {
      ObjectReference tilePlacement = (ObjectReference) invoke(crossedTilePlacement, thread, "getRoot", null);
      return unpackReference(thread, tilePlacement);
    });
    agents.put(LinkedList.class.getName(), (linkedList, thread) -> {
      ArrayReference asArray = (ArrayReference) invoke(linkedList, thread, "toArray", null);
      assert asArray != null;
      return unpackReference(thread, asArray);
    });
  }

  private static int getInt(ObjectReference object, String accessor, ThreadReference thread) {
    return (int) unpackReference(thread, invoke(object, thread, accessor, null));
  }

  private static Value invoke(ObjectReference object, ThreadReference thread, String toInvokeName, String signature) {
    try {
      Method toInvoke;
      ReferenceType referenceType = object.referenceType();
      if (signature != null) {
        toInvoke = referenceType.methodsByName(toInvokeName, signature).get(0);
      } else {
        toInvoke = referenceType.methodsByName(toInvokeName).get(0);
      }
      return object.invokeMethod(thread, toInvoke, Collections.emptyList(), 0);
    } catch (Exception e) {
      return null;
    }
  }

  public static Object unpackReference(ThreadReference thread, Value value) {
    if (value instanceof ArrayReference) {
      ArrayReference arrayReference = (ArrayReference) value;
      Object[] collector = new Object[arrayReference.length()];
      for (int i = 0; i < arrayReference.length(); i++) {
        collector[i] = (unpackReference(thread, arrayReference.getValue(i)));
      }
      return collector;
    } else if (value instanceof StringReference) {
      return ((StringReference) value).value();
    } else if (value instanceof ObjectReference) {
      ObjectReference ref = (ObjectReference) value;
      return Unpackers.getFor(ref).unpack(ref, thread);
    } else if (value instanceof PrimitiveValue) {
      PrimitiveValue primitiveValue = (PrimitiveValue) value;
      String subType = value.type().name();
      if (subType.equals("char")) {
        return primitiveValue.charValue();
      }
      if (subType.equals("boolean")) {
        return primitiveValue.booleanValue();
      }
      if (subType.equals("byte")) {
        return primitiveValue.byteValue();
      }
      if (subType.equals("double")) {
        return primitiveValue.doubleValue();
      }
      if (subType.equals("float")) {
        return primitiveValue.floatValue();
      }
      if (subType.equals("int")) {
        return primitiveValue.intValue();
      }
      if (subType.equals("long")) {
        return primitiveValue.longValue();
      }
      if (subType.equals("short")) {
        return primitiveValue.shortValue();
      }
    }
    return value;
  }

}
