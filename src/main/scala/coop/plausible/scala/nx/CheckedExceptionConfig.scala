/*
 * Copyright (c) 2014 Plausible Labs Cooperative, Inc.
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package coop.plausible.scala.nx

/**
 * Checked exception configurations supported by [[NX]].
 */
object CheckedExceptionConfig {
  /**
   * The standard (Java) check strategy. Subtypes of `RuntimeException` and `Error` will be treated as
   * unchecked.
   */
  case object Standard extends CheckedExceptionConfig

  /**
   * Strict exception check strategy. Only subtypes of `Error` will be treated as unchecked.
   */
  case object Strict extends CheckedExceptionConfig

  /**
   * Non-fatal check strategy. Only VM "fatal" exceptions will be treated as unchecked. These include:
   *
   * - VirtualMachineError
   * - AssertionError
   * - LinkageError
   */
  case object Fatal extends CheckedExceptionConfig
}

/**
 * Defines exception types to be considered "unchecked" by [[NX]].
 *
 * Unchecked exceptions will not trigger an error if left unhandled.
 */
sealed trait CheckedExceptionConfig