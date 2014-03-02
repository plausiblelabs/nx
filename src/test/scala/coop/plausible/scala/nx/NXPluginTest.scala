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
import scala.tools.nsc.interpreter.IMain

/**
 * Test compiler plugin-based execution. This assumes that the build environment (sbt by default) is configured to
 * execute our tests with the plugin enabled (eg, via scalacOptions).
 *
 * This is a basic smoke test for the plugin; the actual core implementation is tested in NXTest.
 */
class NXPluginTest extends Specification {
  "NXPlugin" should {
    "run as a compiler plugin" in {
      /* Configure the compiler */
      val settings = new scala.tools.nsc.Settings

      /* Inherit our Java classpath */
      settings.usejavacp.value = true

      /* Set up the compiler. We use the interpreter API to keep things simple */
      val interp =  new IMain(settings)

      /* Run our smoke test */
      interp.interpret(
        """
          |def foo (value: Int): Int = 5 + 5
          |val ret = foo(5)
        """.stripMargin)
      interp.valueOfTerm("ret") must beSome(10)
    }
  }
}
