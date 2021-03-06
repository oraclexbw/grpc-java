/*
 * Copyright 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *
 *    * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.grpc.benchmarks.qps;

import static java.lang.Math.max;
import static java.lang.String.CASE_INSENSITIVE_ORDER;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Abstract base class for all {@link Configuration.Builder}s.
 */
public abstract class AbstractConfigurationBuilder<T extends Configuration>
    implements Configuration.Builder<T> {

  private static final Param HELP = new Param() {
    @Override
    public String getName() {
      return "help";
    }

    @Override
    public String getType() {
      return "";
    }

    @Override
    public String getDescription() {
      return "Print this text.";
    }

    @Override
    public boolean isRequired() {
      return false;
    }

    public String getDefaultValue() {
      return null;
    }

    @Override
    public void setValue(Configuration config, String value) {
      throw new UnsupportedOperationException();
    }
  };

  /**
   * A single application parameter supported by this builder.
   */
  protected interface Param {
    /**
     * The name of the parameter as it would appear on the command-line.
     */
    String getName();

    /**
     * A string representation of the parameter type. If not applicable, just returns an empty
     * string.
     */
    String getType();

    /**
     * A description of this parameter used when printing usage.
     */
    String getDescription();

    /**
     * The default value used when not set explicitly. Ignored if {@link #isRequired()} is {@code
     * true}.
     */
    String getDefaultValue();

    /**
     * Indicates whether or not this parameter is required and must therefore be set before the
     * configuration can be successfully built.
     */
    boolean isRequired();

    /**
     * Sets this parameter on the given configuration instance.
     */
    void setValue(Configuration config, String value);
  }

  @Override
  public final T build(String[] args) {
    T config = newConfiguration();
    Map<String, Param> paramMap = getParamMap();
    Set<String> appliedParams = new TreeSet<String>(CASE_INSENSITIVE_ORDER);

    for (String arg : args) {
      if (!arg.startsWith("--")) {
        throw new IllegalArgumentException("All arguments must start with '--': " + arg);
      }
      String[] pair = arg.substring(2).split("=", 2);
      String key = pair[0];
      String value = "";
      if (pair.length == 2) {
        value = pair[1];
      }

      // If help was requested, just throw now to print out the usage.
      if (HELP.getName().equalsIgnoreCase(key)) {
        throw new IllegalArgumentException("Help requested");
      }

      Param param = paramMap.get(key);
      if (param == null) {
        throw new IllegalArgumentException("Unsupported argument: " + key);
      }
      param.setValue(config, value);
      appliedParams.add(key);
    }

    // Ensure that all required options have been provided.
    for (Param param : getParams()) {
      if (param.isRequired() && !appliedParams.contains(param.getName())) {
        throw new IllegalArgumentException("Missing required option '--"
            + param.getName() + "'.");
      }
    }

    return build0(config);
  }

  @Override
  public final void printUsage() {
    System.out.println("Usage: [ARGS...]");
    int column1Width = 0;
    List<Param> params = new ArrayList<Param>();
    params.add(HELP);
    params.addAll(getParams());

    for (Param param : params) {
      column1Width = max(commandLineFlag(param).length(), column1Width);
    }
    int column1Start = 2;
    int column2Start = column1Start + column1Width + 2;
    for (Param param : params) {
      StringBuilder sb = new StringBuilder();
      sb.append(Strings.repeat(" ", column1Start));
      sb.append(commandLineFlag(param));
      sb.append(Strings.repeat(" ", column2Start - sb.length()));
      String message = param.getDescription();
      sb.append(wordWrap(message, column2Start, 80));
      if (param.isRequired()) {
        sb.append(Strings.repeat(" ", column2Start));
        sb.append("[Required]\n");
      } else if (param.getDefaultValue() != null && !param.getDefaultValue().isEmpty()) {
        sb.append(Strings.repeat(" ", column2Start));
        sb.append("[Default=" + param.getDefaultValue() + "]\n");
      }
      System.out.println(sb);
    }
    System.out.println();
  }

  /**
   * Creates a new configuration instance which will be used as the target for command-line
   * arguments.
   */
  protected abstract T newConfiguration();

  /**
   * Returns the valid parameters supported by the configuration.
   */
  protected abstract Collection<Param> getParams();

  /**
   * Called by {@link #build(String[])} after verifying that all required options have been set.
   * Performs any final validation and modifications to the configuration. If successful, returns
   * the fully built configuration.
   */
  protected abstract T build0(T config);

  private Map<String, Param> getParamMap() {
    Map<String, Param> map = new TreeMap<String, Param>(CASE_INSENSITIVE_ORDER);
    for (Param param : getParams()) {
      map.put(param.getName(), param);
    }
    return map;
  }

  private static String commandLineFlag(Param param) {
    String name = param.getName().toLowerCase();
    String type = (!param.getType().isEmpty() ? '=' + param.getType() : "");
    return "--" + name + type;
  }

  private static String wordWrap(String text, int startPos, int maxPos) {
    StringBuilder builder = new StringBuilder();
    int pos = startPos;
    String[] parts = text.split("\\n");
    boolean isBulleted = parts.length > 1;
    for (String part : parts) {
      int lineStart = startPos;
      while (!part.isEmpty()) {
        if (pos < lineStart) {
          builder.append(Strings.repeat(" ", lineStart - pos));
          pos = lineStart;
        }
        int maxLength = maxPos - pos;
        int length = part.length();
        if (length > maxLength) {
          length = part.lastIndexOf(' ', maxPos - pos) + 1;
          if (length == 0) {
            length = part.length();
          }
        }
        builder.append(part.substring(0, length));
        part = part.substring(length);

        // Wrap to the next line.
        builder.append("\n");
        pos = 0;
        lineStart = isBulleted ? startPos + 2 : startPos;
      }
    }
    return builder.toString();
  }
}
