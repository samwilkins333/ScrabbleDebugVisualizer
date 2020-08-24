package com.swilkins.ScrabbleViz.utility;

import com.sun.jdi.*;
import com.swilkins.ScrabbleBase.Board.Location.TilePlacement;
import com.swilkins.ScrabbleBase.Board.State.BoardSquare;
import com.swilkins.ScrabbleBase.Board.State.Tile;
import com.swilkins.ScrabbleBase.Generation.Candidate;
import com.swilkins.ScrabbleBase.Generation.CrossedTilePlacement;
import com.swilkins.ScrabbleBase.Generation.Direction;

import java.util.*;

public class Unpackers {

  private static final Unpacker toString = (object, thread) ->
          unpackReference(thread, invoke(object, thread, "toString", "()Ljava/lang/String;"));

  public static Unpacker getFor(ObjectReference object) {
    try {
      Class<?> clazz = Class.forName(object.referenceType().name());
      while (clazz != Object.class) {
        Unpacker existing = agents.get(clazz.getName());
        if (existing != null) {
          return existing;
        }
        clazz = clazz.getSuperclass();
      }
    } catch (ClassNotFoundException e) {
      return toString;
    }
    return toString;
  }

  private static final Map<String, Unpacker> agents = new HashMap<>();

  static {
    Unpacker unpackTileWrapper = (tileWrapper, thread) -> {
      Value tileReference = invoke(tileWrapper, thread, "getTile", null);
      return unpackReference(thread, tileReference);
    };
    agents.put(BoardSquare.class.getName(), unpackTileWrapper);
    agents.put(Direction.class.getName(), (direction, thread) -> {
      ObjectReference directionNameReference = (ObjectReference) invoke(direction, thread, "name", null);
      return unpackReference(thread, invoke(directionNameReference, thread, "toString", null));
    });
    agents.put(Tile.class.getName(), (tile, thread) -> {
      Value letter = invoke(tile, thread, "getLetter", null);
      Value proxy = invoke(tile, thread, "getLetterProxy", null);
      return new Object[]{unpackReference(thread, letter), unpackReference(thread, proxy)};
    });
    agents.put(Character.class.getName(), (character, thread) -> {
      Value value = invoke(character, thread, "charValue", null);
      return unpackReference(thread, value);
    });
    agents.put(Candidate.class.getName(), (candidate, thread) -> {
      int score = getInt(candidate, "getScore", thread);
      Value serialized = invoke(candidate, thread, "toString", null);
      return new Object[]{score, unpackReference(thread, serialized)};
    });
    agents.put(TilePlacement.class.getName(), (tilePlacement, thread) -> {
      int x = getInt(tilePlacement, "getX", thread);
      int y = getInt(tilePlacement, "getY", thread);
      return new Object[]{x, y, unpackTileWrapper.unpack(tilePlacement, thread)};
    });
    agents.put(CrossedTilePlacement.class.getName(), (crossedTilePlacement, thread) -> {
      Value tilePlacement = invoke(crossedTilePlacement, thread, "getRoot", null);
      return unpackReference(thread, tilePlacement);
    });
    Unpacker unpackArrayable = (arrayable, thread) -> {
      Value asArray = invoke(arrayable, thread, "toArray", null);
      return unpackReference(thread, asArray);
    };
    agents.put(HashSet.class.getName(), unpackArrayable);
    agents.put(LinkedList.class.getName(), unpackArrayable);
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

  public static Map<String, Object> unpackVariables(ThreadReference thread) throws AbsentInformationException, IncompatibleThreadStateException {
    StackFrame frame = thread.frame(0);
    Map<String, Object> unpackedVariables = new HashMap<>();
    for (Map.Entry<LocalVariable, Value> entry : frame.getValues(frame.visibleVariables()).entrySet()) {
      unpackedVariables.put(entry.getKey().name(), unpackReference(thread, entry.getValue()));
    }
    return unpackedVariables;
  }

  public static Object unpackReference(ThreadReference thread, Value value) {
    if (value instanceof ObjectReference) {
      ((ObjectReference) value).disableCollection();
    }
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
