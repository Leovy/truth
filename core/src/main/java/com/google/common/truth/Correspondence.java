/*
 * Copyright (c) 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.common.truth;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.DoubleSubject.checkTolerance;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Facts.facts;
import static com.google.common.truth.Platform.getStackTraceAsString;
import static java.util.Arrays.asList;

import com.google.common.base.Strings;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

/**
 * Determines whether an instance of type {@code A} corresponds in some way to an instance of type
 * {@code E} for the purposes of a test assertion. For example, the implementation returned by the
 * {@link #tolerance(double)} factory method implements approximate equality between numeric values,
 * with values being said to correspond if the difference between them is does not exceed some fixed
 * tolerance. The instances of type {@code A} are typically actual values from a collection returned
 * by the code under test; the instances of type {@code E} are typically expected values with which
 * the actual values are compared by the test.
 *
 * <p>The correspondence is required to be consistent: for any given values {@code actual} and
 * {@code expected}, multiple invocations of {@code compare(actual, expected)} must consistently
 * return {@code true} or consistently return {@code false} (provided that neither value is
 * modified). Although {@code A} and {@code E} will often be the same types, they are <i>not</i>
 * required to be the same, and even if they are it is <i>not</i> required that the correspondence
 * should have any of the other properties of an equivalence relation (reflexivity, symmetry, or
 * transitivity).
 *
 * <p>Subclasses may optionally override {@link #formatDiff}. This results in failure messages
 * including formatted diffs between expected and actual elements, where possible.
 *
 * <p>Instances of this are typically used via {@link IterableSubject#comparingElementsUsing},
 * {@link MapSubject#comparingValuesUsing}, or {@link MultimapSubject#comparingValuesUsing}.
 *
 * @author Pete Gillin
 */
public abstract class Correspondence<A, E> {

  /**
   * Returns a {@link Correspondence} between {@link Number} instances that considers instances to
   * correspond (i.e. {@link Correspondence#compare(Object, Object)} returns {@code true}) if the
   * double values of each instance (i.e. the result of calling {@link Number#doubleValue()} on
   * them) are finite values within {@code tolerance} of each other.
   *
   * <ul>
   *   <li>It does not consider instances to correspond if either value is infinite or NaN.
   *   <li>The conversion to double may result in a loss of precision for some numeric types.
   *   <li>The {@link Correspondence#compare(Object, Object)} method throws a {@link
   *       NullPointerException} if either {@link Number} instance is null.
   * </ul>
   *
   * @param tolerance an inclusive upper bound on the difference between the double values of the
   *     two {@link Number} instances, which must be a non-negative finite value, i.e. not {@link
   *     Double#NaN}, {@link Double#POSITIVE_INFINITY}, or negative, including {@code -0.0}
   */
  public static Correspondence<Number, Number> tolerance(double tolerance) {
    return new TolerantNumericEquality(tolerance);
  }

  private static final class TolerantNumericEquality extends Correspondence<Number, Number> {

    private final double tolerance;

    private TolerantNumericEquality(double tolerance) {
      this.tolerance = tolerance;
    }

    @Override
    public boolean compare(Number actual, Number expected) {
      checkTolerance(tolerance);
      double actualDouble = checkNotNull(actual).doubleValue();
      double expectedDouble = checkNotNull(expected).doubleValue();
      return MathUtil.equalWithinTolerance(actualDouble, expectedDouble, tolerance);
    }

    @Override
    public String toString() {
      return "is a finite number within " + tolerance + " of";
    }
  }

  /**
   * Returns whether or not the {@code actual} value is said to correspond to the {@code expected}
   * value for the purposes of this test.
   *
   * <h3>Exception handling</h3>
   *
   * <p>Throwing a {@link RuntimeException} from this method indicates that this {@link
   * Correspondence} cannot compare the given values. Any assertion which encounters such an
   * exception during the course of evaluating its condition must not pass. However, an assertion is
   * not required to invoke this method for every pair of values in its input just in order to check
   * for exceptions, if it is able to evaluate its condition without doing so.
   *
   * <h4>Conventions for handling exceptions</h4>
   *
   * <p>(N.B. This section is only really of interest when implementing assertion methods that call
   * {@link Correspondence#compare}, not to users making such assertions in their tests.)
   *
   * <p>The only requirement on an assertion is that, if it encounters an exception from this
   * method, it must not pass. The simplest implementation choice is simply to allow the exception
   * to propagate. However, it is normally more helpful to catch the exception and instead fail with
   * a message which includes more information about the assertion in progress and the nature of the
   * failure.
   *
   * <p>By convention, an assertion may catch and store the exception and continue evaluating the
   * condition as if the method had returned false instead of throwing. If the assertion's condition
   * does not hold with this alternative behaviour, it may choose to fail with a message that gives
   * details about how the condition does not hold, additionally mentioning that assertions were
   * encountered and giving details about one of the stored exceptions. (See the first example
   * below.) If the assertion's condition does hold with this alternative behaviour, the requirement
   * that the assertion must not pass still applies, so it should fail with a message giving details
   * about one of the stored exceptions. (See the second and third examples below.)
   *
   * <p>This behaviour is only a convention and should only be implemented when it makes sense to do
   * so. In particular, in an assertion that has multiple stages, it may be better to only continue
   * evaluation to the end of the current stage, and fail citing a stored exception at the end of
   * the stage, rather than accumulating exceptions through the multiple stages.
   *
   * <h4>Examples of exception handling</h4>
   *
   * <p>Suppose that {@code CASE_INSENSITIVE_EQUALITY} is a {@code Correspondence<String, String>}
   * whose {@code compare} method calls {@link actual.equalsIgnoreCase(expected)} and therefore
   * throws {@link NullPointerException} if the actual value is null. The assertion
   *
   * <pre>{@code
   * assertThat(asList(null, "xyz", "abc", "def"))
   *     .comparingElementsUsing(CASE_INSENSITIVE_EQUALITY)
   *     .containsExactly("ABC", "DEF", "GHI", "JKL");
   * }</pre>
   *
   * may fail saying that the actual iterable contains unexpected values {@code null} and {@code
   * xyz} and is missing values corresponding to {@code GHI} and {@code JKL}, which is what it would
   * do if the {@code compare} method returned false instead of throwing, and additionally mention
   * the exception. (This is more helpful than allowing the {@link NullPointerException} to
   * propagate to the caller, or than failing with only a description of the exception.)
   *
   * <p>However, the assertions
   *
   * <pre>{@code
   * assertThat(asList(null, "xyz", "abc", "def"))
   *     .comparingElementsUsing(CASE_INSENSITIVE_EQUALITY)
   *     .doesNotContain("MNO");
   * }</pre>
   *
   * and
   *
   * <pre>{@code
   * assertThat(asList(null, "xyz", "abc", "def"))
   *     .comparingElementsUsing(CASE_INSENSITIVE_EQUALITY)
   *     .doesNotContain(null);
   * }</pre>
   *
   * must both fail citing the exception, even though they would pass if the {@code compare} method
   * returned false. (Note that, in the latter case at least, it is likely that the test author's
   * intention was <i>not</i> for the test to pass with these values.)
   */
  // TODO(b/119038411): Ensure that all callers in Truth handle exceptions sensibly
  // TODO(b/119038894): Simplify the 'for example' by using a factory method when it's ready
  public abstract boolean compare(@NullableDecl A actual, @NullableDecl E expected);

  /**
   * Helper object to store exceptions encountered while executing a {@link Correspondence} method.
   */
  static final class ExceptionStore {

    private final String context;
    private boolean empty = true;
    private Exception firstException;
    private String firstMethod;
    private List<Object> firstArguments;

    static ExceptionStore forCompare() {
      return new ExceptionStore("comparing elements");
    }

    private ExceptionStore(String context) {
      this.context = context;
    }

    /**
     * Adds an exception to the store.
     *
     * @param callingClass The class from which the {@link Correspondence} method was called. When
     *     reporting failures, stack traces will be truncated above elements in this class.
     * @param exception The exception encountered
     * @param method The name of the {@link Correspondence} method during which the exception was
     *     encountered (e.g. {@code "compare"})
     * @param arguments The arguments to the {@link Correspondence} method call during which the
     *     exception was encountered
     */
    void add(Class<?> callingClass, Exception exception, String method, Object... arguments) {
      if (empty) {
        empty = false;
        truncateStackTrace(exception, callingClass);
        firstException = exception;
        firstMethod = method;
        firstArguments = asList(arguments);
      }
    }

    /** Returns whether the store is empty (i.e. no calls to {@link #add} were made). */
    boolean isEmpty() {
      return empty;
    }

    /**
     * Returns facts to use in a failure message when the exceptions are the main cause of the
     * failure. This method must not be called when the store is empty. Assertions should use this
     * when exceptions were thrown while comparing elements and no more meaningful failure was
     * discovered by assuming a false return and continuing (see the javadoc for {@link
     * Correspondence#compare}). C.f. {@link #describeAsAdditionalInfo}.
     */
    Facts describeAsMainCause() {
      checkState(!empty);
      return facts(
          simpleFact("one or more exceptions were thrown while " + context), firstExceptionFact());
    }

    /**
     * If any exceptions are stored, returns facts to use in a failure message when the exceptions
     * should be noted as additional info; if empty, returns an empty list. Assertions should use
     * this when exceptions were thrown while comparing elements but more meaningful failures were
     * discovered by assuming a false return and continuing (see the javadoc for {@link
     * Correspondence#compare}), or when exceptions were thrown by other methods while generating
     * the failure message. C.f. {@link #describeAsMainCause}.
     */
    Facts describeAsAdditionalInfo() {
      if (!empty) {
        return facts(
            simpleFact("additionally, one or more exceptions were thrown while " + context),
            firstExceptionFact());
      } else {
        return facts();
      }
    }

    private Fact firstExceptionFact() {
      return fact(
          "first exception",
          Strings.lenientFormat(
              "%s(%s) threw %s at %s",
              firstMethod, firstArguments, firstException, getStackTraceAsString(firstException)));
    }

    private static void truncateStackTrace(Exception exception, Class<?> callingClass) {
      StackTraceElement[] original = exception.getStackTrace();
      int keep = 0;
      while (keep < original.length
          && !original[keep].getClassName().equals(callingClass.getName())) {
        keep++;
      }
      exception.setStackTrace(Arrays.copyOf(original, keep));
    }
  }

  /**
   * Invokes {@link #compare}, catching any exceptions. If the comparison does not throw, returns
   * the result. If it does throw, adds the exception to the given {@link ExceptionStore} and
   * returns false. This method can help with implementing the exception-handling policy described
   * above, but note that assertions using it <i>must</i> fail later if an exception was stored.
   */
  final boolean safeCompare(
      @NullableDecl A actual, @NullableDecl E expected, ExceptionStore exceptions) {
    try {
      return compare(actual, expected);
    } catch (RuntimeException e) {
      exceptions.add(Correspondence.class, e, "compare", actual, expected);
      return false;
    }
  }

  /**
   * Returns a {@link String} describing the difference between the {@code actual} and {@code
   * expected} values, if possible, or {@code null} if not.
   *
   * <p>The implementation on the {@link Correspondence} base class always returns {@code null}. To
   * enable diffing, subclasses should override this method.
   *
   * <p>Assertions should only invoke this with parameters for which {@link #compare} returns {@code
   * false}.
   */
  @NullableDecl
  public String formatDiff(@NullableDecl A actual, @NullableDecl E expected) {
    return null;
  }

  /**
   * Returns a description of the correspondence, suitable to fill the gap in a failure message of
   * the form {@code "<some actual element> is an element that ... <some expected element>"}. Note
   * that this is a fragment of a verb phrase which takes a singular subject.
   *
   * <p>Example 1: For a {@code Correspondence<String, Integer>} that tests whether the actual
   * string parses to the expected integer, this would return {@code "parses to"} to result in a
   * failure message of the form {@code "<some actual string> is an element that parses to <some
   * expected integer>"}.
   *
   * <p>Example 2: For the {@code Correspondence<Number, Number>} returns by {@link #tolerance} this
   * returns {@code "is a finite number within " + tolerance + " of"} to result in a failure message
   * of the form {@code "<some actual number> is an element that is a finite number within 0.0001 of
   * <some expected number>"}.
   */
  @Override
  public abstract String toString();

  /**
   * @throws UnsupportedOperationException always
   * @deprecated {@link Object#equals(Object)} is not supported. If you meant to compare objects
   *     using this {@link Correspondence}, use {@link #compare}.
   */
  @Deprecated
  @Override
  public final boolean equals(@NullableDecl Object o) {
    throw new UnsupportedOperationException(
        "Correspondence.equals(object) is not supported. If you meant to compare objects, use"
            + " .compare(actual, expected) instead.");
  }

  /**
   * @throws UnsupportedOperationException always
   * @deprecated {@link Object#hashCode()} is not supported.
   */
  @Deprecated
  @Override
  public final int hashCode() {
    throw new UnsupportedOperationException("Correspondence.hashCode() is not supported.");
  }
}
