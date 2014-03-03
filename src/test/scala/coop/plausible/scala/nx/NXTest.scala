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

import org.specs2.mutable.Specification
import java.io.IOException
import java.net.UnknownHostException

/**
 * NX implementation tests.
 */
class NXTest extends Specification {
  import NX.nx

  /**
   * Generate a Scala specification reference.
   *
   * @param section Scala specification section number.
   */
  private def specRef (section: String): String = {
    /** The Scala specification version used in references. If you add a new reference to the spec,
      * make sure to update this. */
    val specVersion = "Scala Spec 2.9, March 3, 2014"

    s"$specVersion, Section $section"
  }

  /*
   * NOTES:
   * - The NX.check() macro used below will elide all of the code within it from
   *   the final output. /None/ of code found within the macro blocks is actually run.
   * - We use bool flags in the test code to avoid 'unreachable code' warnings triggered by
   *   non-conditionally throwing exceptions
   */

  "NX throwable detection" should {
    "find throw statements within method blocks" in NX.check {
      def doSomething (flag: Boolean) { if (flag) throw new IOException() }
    }.mustEqual(Set(classOf[IOException]))

    s"find throw statements within class 'primary constructors' (${specRef("5.3")}" in NX.check {
      class MyClass (flag: Boolean) { if (flag) throw new IOException() }
    }.mustEqual(Set(classOf[IOException]))

    s"find throw statements within class 'auxiliary constructors' (${specRef("5.3.1")}" in NX.check {
      class MyClass {
        def this (flag: Boolean) = {
          this()
          if (flag) throw new IOException()
        }
      }
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala methods" in NX.check {
      @throws[IOException]("") def thrower (): Unit = ()
      thrower()
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on called Scala methods that use the Scala 2.9 @throws constructor" in NX.check {
      @throws(classOf[IOException]) def thrower (): Unit = ()
      thrower()
    }.mustEqual(Set(classOf[IOException]))

    "find throw annotations on Java methods" in NX.check {
      /* Defined to throw an UnknownHostException */
      java.net.InetAddress.getByName("")
    }.mustEqual(Set(classOf[UnknownHostException]))
  }

  "NX def-level @throws annotation filtering" should {
    "filter exactly matching throwables" in NX.check {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new IOException() }
    }.mustEqual(Set())

    "filter subtype matching throwables" in NX.check {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new IOException() }
    }.mustEqual(Set())

    "propagate non-matching throwables" in NX.check {
      @throws[IOException]("explanation")
      def defExpr (flag:Boolean) = { if (!flag) throw new Exception() }
    }.mustEqual(Set(classOf[Exception]))
  }
}
