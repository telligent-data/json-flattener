/*
 * 
 * Copyright 2015 Wei-Ming Wu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */
package com.github.wnameless.json.flattener;

import static com.github.wnameless.json.flattener.IndexedPeekIterator.newIndexedPeekIterator;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.Validate.notNull;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;

/**
 * 
 * {@link JsonFlattener} flattens any JSON nested objects or arrays into a
 * flattened JSON string or a Map{@literal <Stirng, Object>}. The String key
 * will represents the corresponding position of value in the original nested
 * objects or arrays and the Object value are either String, Boolean, Long,
 * Double or null. <br>
 * <br>
 * For example:<br>
 * A nested JSON<br>
 * { "a" : { "b" : 1, "c": null, "d": [false, true] }, "e": "f", "g":2.3 }<br>
 * <br>
 * can be turned into a flattened JSON <br>
 * { "a.b": 1, "a.c": null, "a.d[0]": false, "a.d[1]": true, "e": "f", "g":2.3 }
 * <br>
 * <br>
 * or into a Map<br>
 * {<br>
 * &nbsp;&nbsp;a.b=1,<br>
 * &nbsp;&nbsp;a.c=null,<br>
 * &nbsp;&nbsp;a.d[0]=false,<br>
 * &nbsp;&nbsp;a.d[1]=true,<br>
 * &nbsp;&nbsp;e=f,<br>
 * &nbsp;&nbsp;g=2.3<br>
 * }
 *
 * @author Wei-Ming Wu
 *
 */
public final class JsonFlattener {

  /**
   * {@link ROOT} is the default key of the Map returned by
   * {@link #flattenAsMap}. When {@link JsonFlattener} processes a JSON string
   * which is not a JSON object or array, the final outcome may not suit in a
   * Java Map. At that moment, {@link JsonFlattener} will put the result in the
   * Map with {@link ROOT} as its key.
   */
  public static final String ROOT = "root";

  /**
   * Returns a flattened JSON string.
   * 
   * @param json
   *          the JSON string
   * @return a flattened JSON string.
   */
  public static String flatten(String json) {
    return new JsonFlattener(json).flatten();
  }

  /**
   * Returns a flattened JSON as Map.
   * 
   * @param json
   *          the JSON string
   * @return a flattened JSON as Map
   */
  public static Map<String, Object> flattenAsMap(String json) {
    return new JsonFlattener(json).flattenAsMap();
  }

  private final JsonValue source;
  private final Deque<IndexedPeekIterator<?>> elementIters =
      new ArrayDeque<IndexedPeekIterator<?>>();

  private JsonifyLinkedHashMap<String, Object> flattenedMap;
  private FlattenMode flattenMode = FlattenMode.NORMAL;
  private StringEscapePolicy policy = StringEscapePolicy.NORMAL;
  private Character separator = '.';
  private PrintMode printMode = PrintMode.MINIMAL;

  /**
   * Creates a JSON flattener.
   * 
   * @param json
   *          the JSON string
   */
  public JsonFlattener(String json) {
    source = Json.parse(json);
  }

  /**
   * A fluent setter to setup a mode of the {@link JsonFlattener}.
   * 
   * @param flattenMode
   *          a {@link FlattenMode}
   * @return this {@link JsonFlattener}
   */
  public JsonFlattener withFlattenMode(FlattenMode flattenMode) {
    this.flattenMode = notNull(flattenMode);
    flattenedMap = null;
    return this;
  }

  /**
   * A fluent setter to setup the JSON string escape policy.
   * 
   * @param policy
   *          a {@link StringEscapePolicy}
   * @return this {@link JsonFlattener}
   */
  public JsonFlattener withStringEscapePolicy(StringEscapePolicy policy) {
    this.policy = notNull(policy);
    flattenedMap = null;
    return this;
  }

  /**
   * A fluent setter to setup the separator within a key in the flattened JSON.
   * The default separator is a dot(.).
   * 
   * @param separator
   *          any character
   * @return this {@link JsonFlattener}
   */
  public JsonFlattener withSeparator(char separator) {
    this.separator = separator;
    flattenedMap = null;
    return this;
  }

  /**
   * A fluent setter to setup a print mode of the {@link JsonFlattener}. The
   * default print mode is minimal.
   * 
   * @param printMode
   *          a {@link PrintMode}
   * @return this {@link JsonFlattener}
   */
  public JsonFlattener withPrintMode(PrintMode printMode) {
    this.printMode = notNull(printMode);
    return this;
  }

  /**
   * Returns a flattened JSON string.
   * 
   * @return a flattened JSON string
   */
  public String flatten() {
    flattenAsMap();

    if (flattenedMap.containsKey(ROOT))
      return javaObj2Json(flattenedMap.get(ROOT));
    else
      return flattenedMap.toString(printMode);
  }

  private String javaObj2Json(Object obj) {
    if (obj == null) {
      return "null";
    } else if (obj instanceof CharSequence) {
      StringBuilder sb = new StringBuilder();
      sb.append('"');
      sb.append(
          policy.getCharSequenceTranslator().translate((CharSequence) obj));
      sb.append('"');
      return sb.toString();
    } else if (obj instanceof JsonifyArrayList) {
      JsonifyArrayList<?> list = (JsonifyArrayList<?>) obj;
      return list.toString(printMode);
    } else {
      return obj.toString();
    }
  }

  /**
   * Returns a flattened JSON as Map.
   * 
   * @return a flattened JSON as Map
   */
  public Map<String, Object> flattenAsMap() {
    if (flattenedMap != null) return flattenedMap;

    flattenedMap = newJsonifyLinkedHashMap();
    reduce(source);

    while (!elementIters.isEmpty()) {
      IndexedPeekIterator<?> deepestIter = elementIters.getLast();
      if (!deepestIter.hasNext()) {
        elementIters.removeLast();
      } else if (deepestIter.peek() instanceof Member) {
        Member mem = (Member) deepestIter.next();
        reduce(mem.getValue());
      } else { // JsonValue
        JsonValue val = (JsonValue) deepestIter.next();
        reduce(val);
      }
    }

    return flattenedMap;
  }

  private void reduce(JsonValue val) {
    if (val.isObject() && val.asObject().iterator().hasNext()) {
      elementIters.add(newIndexedPeekIterator(val.asObject()));
    } else if (val.isArray() && val.asArray().iterator().hasNext()) {
      switch (flattenMode) {
        case KEEP_ARRAYS:
          JsonifyArrayList<Object> array = newJsonifyArrayList();
          for (JsonValue value : val.asArray()) {
            array.add(jsonVal2Obj(value));
          }
          flattenedMap.put(computeKey(), array);
          break;
        default:
          elementIters.add(newIndexedPeekIterator(val.asArray()));
      }
    } else {
      String key = computeKey();
      Object value = jsonVal2Obj(val);
      // Check NOT empty JSON object
      if (!(ROOT.equals(key) && emptyMap().equals(value)))
        flattenedMap.put(key, jsonVal2Obj(val));
    }
  }

  private Object jsonVal2Obj(JsonValue val) {
    if (val.isBoolean()) return val.asBoolean();
    if (val.isString()) return val.asString();
    if (val.isNumber()) return new BigDecimal(val.toString());
    switch (flattenMode) {
      case KEEP_ARRAYS:
        if (val.isArray()) {
          JsonifyArrayList<Object> array = newJsonifyArrayList();
          for (JsonValue value : val.asArray()) {
            array.add(jsonVal2Obj(value));
          }
          return array;
        } else if (val.isObject()) {
          if (val.asObject().iterator().hasNext()) {
            return newJsonFlattener(val.toString()).flattenAsMap();
          } else {
            return newJsonifyLinkedHashMap();
          }
        }
      default:
        if (val.isArray()) {
          return newJsonifyArrayList();
        } else if (val.isObject()) {
          return newJsonifyLinkedHashMap();
        }
    }

    return null;
  }

  private String computeKey() {
    if (elementIters.isEmpty()) return ROOT;

    StringBuilder sb = new StringBuilder();

    for (IndexedPeekIterator<?> iter : elementIters) {
      if (iter.getCurrent() instanceof Member) {
        String key = ((Member) iter.getCurrent()).getName();
        if (key.contains(separator.toString())) {
          sb.append('[');
          sb.append('\\');
          sb.append('"');
          sb.append(policy.getCharSequenceTranslator().translate(key));
          sb.append('\\');
          sb.append('"');
          sb.append(']');
        } else {
          if (sb.length() != 0) sb.append(separator);
          sb.append(policy.getCharSequenceTranslator().translate(key));
        }
      } else { // JsonValue
        sb.append('[');
        sb.append(iter.getIndex());
        sb.append(']');
      }
    }

    return sb.toString();
  }

  private <T> JsonifyArrayList<T> newJsonifyArrayList() {
    JsonifyArrayList<T> array = new JsonifyArrayList<T>();
    array.setTranslator(policy.getCharSequenceTranslator());
    return array;
  }

  private <K, V> JsonifyLinkedHashMap<K, V> newJsonifyLinkedHashMap() {
    JsonifyLinkedHashMap<K, V> map = new JsonifyLinkedHashMap<K, V>();
    map.setTranslator(policy.getCharSequenceTranslator());
    return map;
  }

  private JsonFlattener newJsonFlattener(String json) {
    return new JsonFlattener(json).withFlattenMode(flattenMode)
        .withSeparator(separator).withStringEscapePolicy(policy)
        .withPrintMode(printMode);
  }

  @Override
  public int hashCode() {
    int result = 27;
    result = 31 * result + source.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JsonFlattener)) return false;
    return source.equals(((JsonFlattener) o).source);
  }

  @Override
  public String toString() {
    return "JsonFlattener{source=" + source + "}";
  }

}
